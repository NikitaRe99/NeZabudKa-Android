package ru.nezabudka.app.speech

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import ru.nezabudka.app.ui.InterfaceReviewActivity
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.UserModelsStorage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import java.util.concurrent.TimeoutException

/** Compares identical copied audio. Never changes settings, notes, or the user's recording. */
@RunWith(AndroidJUnit4::class)
class WhisperAccuracyModesTest {
    @Test fun silence_is_rejected_and_modes_are_compared_on_the_same_audio() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val foreground = ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
            .putExtra("gallery_message", "Whisper: проверка шума и ограничений времени"))
        foreground.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        Thread.sleep(750)
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        assumeTrue("Install a model first", manager.state.value.selectedName != null)
        val source = File(context.cacheDir, "whisper-tuning-reference.wav").takeIf { it.isFile }
            ?: File(context.filesDir, "recordings/last_processed.wav").takeIf { it.isFile }
            ?: File(context.filesDir, "recordings/last_raw.wav")
        assumeTrue("Record a short phrase first", source.isFile && source.length() in 32044..480044)
        val directory = File(context.cacheDir, "whisper-quality-${System.nanoTime()}").apply { mkdirs() }
        val report = JSONArray()
        var accurate = false
        val engine = WhisperCppSttEngine(manager, WhisperVadModelProvider(context), File(directory, "segments"),
            keepModelLoaded = { true }, configuredThreadCount = { 6 }, accurateMode = { accurate })
        fun wav(name: String, pcm: ByteArray): File {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + pcm.size)
                .put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(16000).putInt(32000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(pcm.size).array()
            return File(directory, name).apply { outputStream().use { it.write(header); it.write(pcm) } }
        }
        try {
            val silence = wav("silence.wav", ByteArray(3 * 32000))
            val random = Random(42)
            val noisePcm = ByteBuffer.allocate(3 * 32000).order(ByteOrder.LITTLE_ENDIAN)
            repeat(3 * 16000) { noisePcm.putShort(random.nextInt(-400, 401).toShort()) }
            val noise = wav("noise.wav", noisePcm.array())
            for (file in listOf(silence, noise)) {
                val error = runCatching { engine.transcribe(file) }.exceptionOrNull()
                assertTrue("${file.name} must be rejected before decoding: $error", error?.message.orEmpty().contains("Речь не обнаружена"))
                assertEquals("Речь не обнаружена", engine.runtime.value.stage)
            }
            val pcm = source.readBytes().copyOfRange(44, source.length().toInt())
            val quiet = ByteBuffer.allocate(pcm.size).order(ByteOrder.LITTLE_ENDIAN)
            val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
            while (input.hasRemaining()) quiet.putShort((input.short * 0.3).toInt().toShort())
            val noisy = ByteBuffer.allocate(pcm.size).order(ByteOrder.LITTLE_ENDIAN)
            input.rewind()
            while (input.hasRemaining()) noisy.putShort((input.short + random.nextInt(-350, 351))
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            val cases = listOf(
                wav("phrase.wav", pcm),
                wav("pauses.wav", ByteArray(32000) + pcm + ByteArray(32000)),
                wav("quiet.wav", quiet.array()),
                wav("background-noise.wav", noisy.array()),
            )
            // Force native timeout once and verify the next fresh request still works.
            val model = manager.selectedModel(pcmWavBytes = cases.first().length())
            val timeout = runCatching {
                WhisperNative.transcribeWav(model.absolutePath, cases.first().absolutePath, "", "ru", "",
                    6, 0, 96, true, 5, 1)
            }.exceptionOrNull()
            assertTrue("Native deadline must interrupt decoding: $timeout", timeout is TimeoutException)
            for (file in cases) for (mode in listOf(false, true)) {
                accurate = mode
                val started = SystemClock.elapsedRealtime()
                val result = engine.transcribe(file)
                assertTrue("${file.name} / $mode must contain speech", result.text.isNotBlank())
                assertTrue("Short audio uses at most two native passes", engine.runtime.value.nativePasses in 1..2)
                report.put(JSONObject().put("case", file.name).put("mode", if (mode) "accurate" else "fast")
                    .put("durationMs", SystemClock.elapsedRealtime() - started).put("text", result.text)
                    .put("fastFallback", engine.runtime.value.usedFastFallback)
                    .put("fullRetry", engine.runtime.value.usedFullRetry)
                    .put("requiresConfirmation", result.requiresConfirmation)
                    .put("confidence", result.confidence))
            }
            File(context.filesDir, "whisper-quality-report.json").writeText(report.toString(2))
            println("WHISPER_QUALITY_REPORT $report")
        } finally {
            engine.release()
            directory.deleteRecursively()
            foreground.close()
        }
    }
}

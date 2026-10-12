package ru.nezabudka.app.speech

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.UserModelsStorage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/** Diagnostic comparison on an immutable copied recording; does not change settings. */
@RunWith(AndroidJUnit4::class)
class WhisperTuningTest {
    @Test fun compare_encoder_windows_beams_and_prompts() = runBlocking(Dispatchers.Default) {
        assumeTrue("Optional tuning diagnostic", InstrumentationRegistry.getArguments().getString("run_whisper_tuning") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        assumeTrue(manager.state.value.selectedName != null)
        val source = File(context.cacheDir, "whisper-tuning-reference.wav")
        assumeTrue(source.isFile && source.length() in 32044..480044)
        val directory = File(context.cacheDir, "whisper-tuning-${System.nanoTime()}").apply { mkdirs() }
        val report = JSONArray()
        data class Candidate(val name: String, val window: Int, val beams: Int, val prompt: String)
        val previousPrompt = "Напомни через три дня позвонить Кате. Напомни о встрече завтра в восемь утра. " +
            "Создай заметку для работы. Покажи планы на следующую неделю. Удали напоминание о встрече."
        val vocabulary = "Напомни напоминание заметка создай удали покажи сегодня завтра послезавтра утром днём вечером через минут часов дней"
        val candidates = listOf(
            Candidate("baseline-0.2.5-full-5", 0, 5, previousPrompt),
            Candidate("window-768-3-vocabulary-space", 768, 3, vocabulary),
            Candidate("full-5-vocabulary-space", 0, 5, vocabulary),
        )
        fun wav(name: String, pcm: ByteArray): File {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size).array()
            return File(directory, name).apply { outputStream().use { it.write(header); it.write(pcm) } }
        }
        try {
            val pcm = source.readBytes().copyOfRange(44, source.length().toInt())
            val noisy = ByteBuffer.allocate(pcm.size).order(ByteOrder.LITTLE_ENDIAN)
            val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
            val random = Random(42)
            while (input.hasRemaining()) noisy.putShort((input.short + random.nextInt(-350, 351))
                .coerceIn(-32768, 32767).toShort())
            val model = manager.selectedModel(pcmWavBytes = source.length())
            val vad = WhisperVadModelProvider(context).modelFile()
            for (file in listOf(source, wav("noise.wav", noisy.array()))) {
                val boundaries = WhisperNative.detectSpeechSegments(vad.absolutePath, file.absolutePath, 6, 19f)
                if (!hasWhisperSpeechRanges(boundaries)) continue
                val segments = PcmWavSegmenter.splitAtSpeechRanges(file, File(directory, "segments"), boundaries, 19)
                try {
                    val sample = segments.single()
                    // Warm up model weights before measuring candidates.
                    WhisperNative.transcribeWav(model.absolutePath, sample.absolutePath, "", "ru", "", 6, 512, 96, true)
                    for (candidate in candidates) {
                        val started = SystemClock.elapsedRealtime()
                        val result = runCatching {
                            WhisperNative.transcribeWav(model.absolutePath, sample.absolutePath, "", "ru", candidate.prompt,
                                6, candidate.window, 96, true, candidate.beams, 20000)
                                .collapseWhisperRepetition().normalizeWhisperTranscript()
                        }
                        report.put(JSONObject().put("case", file.name).put("candidate", candidate.name)
                            .put("durationMs", SystemClock.elapsedRealtime() - started)
                            .put("text", result.getOrNull()).put("error", result.exceptionOrNull()?.message)
                            .put("confidence", if (result.isSuccess) WhisperNative.lastConfidence() else 0f))
                    }
                } finally { PcmWavSegmenter.cleanup(segments, file) }
            }
            File(context.filesDir, "whisper-tuning-report.json").writeText(report.toString(2))
        } finally { WhisperNative.release(); directory.deleteRecursively() }
    }
}

package ru.nezabudka.app.speech

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.ui.InterfaceReviewActivity
import ru.nezabudka.app.data.UserModelsStorage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Compares capture profiles and the actual samples recorded after finger release. */
@RunWith(AndroidJUnit4::class)
class AudioCaptureComparisonTest {
    @Test fun compare_capture_profiles_and_tail() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_capture_audit") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue((0..2).all { File(context.filesDir, "capture-audit-044-$it.wav").isFile })
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        val model = manager.selectedModel()
        val expected = "Напомни завтра утром сходить к врачу и купить бумаги"
        val rows = JSONArray()
        val directory = File(context.cacheDir, "capture-comparison-${System.nanoTime()}").apply { mkdirs() }
        val foreground = ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
            .putExtra("gallery_message", "Whisper: проверка способов записи"))
        foreground.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        Thread.sleep(750)
        try {
            for (index in 0..2) {
                val full = File(context.filesDir, "capture-audit-044-$index.wav")
                val bytes = full.readBytes()
                require(bytes.size > 44 + 5760)
                val cut = bytes.copyOf(bytes.size - 5760) // Remove the recorded 180 ms tail.
                ByteBuffer.wrap(cut).order(ByteOrder.LITTLE_ENDIAN).putInt(4, cut.size - 8).putInt(40, cut.size - 44)
                val trimmed = File(directory, "without-tail-$index.wav").apply { writeBytes(cut) }
                for (sample in listOf(full, trimmed)) for (mode in 0..2) {
                    val policy = whisperInferencePolicy(sample.length())
                    val window = when (mode) { 0 -> policy.audioContext; 1 -> maxOf(512, policy.audioContext); else -> maxOf(768, policy.audioContext) }
                    val beam = if (mode == 2) 3 else 1
                    val started = SystemClock.elapsedRealtime()
                    val result = runCatching { WhisperNative.transcribeWav(model.absolutePath, sample.absolutePath, "", "ru",
                        whisperRequestPrompt(listOf("Работа"), WhisperRequestContext.COMMAND, window), 6, window, 0, true, beam, 15_000, 0f) }
                    val row = JSONObject().put("profile", index).put("tail", sample == full).put("mode", mode)
                        .put("expected", expected).put("text", result.getOrNull()).put("error", result.exceptionOrNull()?.message)
                        .put("durationMs", SystemClock.elapsedRealtime() - started)
                    if (result.isSuccess) row.put("metrics", JSONArray(WhisperNative.lastMetrics().toList()))
                    rows.put(row)
                    File(context.filesDir, "capture-comparison-045.json").writeText(rows.toString(2))
                }
            }
        } finally { WhisperNative.release(); foreground.close(); directory.deleteRecursively() }
    }
}

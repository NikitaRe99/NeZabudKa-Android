package ru.nezabudka.app.speech

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.UserModelsStorage
import ru.nezabudka.app.ui.InterfaceReviewActivity
import java.io.File

@RunWith(AndroidJUnit4::class)
class WhisperDateContextTest {
    @Test fun compare_date_windows() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_date_matrix") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.filesDir, "capture-date-audit-0.wav")
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        val model = manager.selectedModel()
        val foreground = ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
            .putExtra("gallery_message", "Whisper: сравнение окон для ответа о дате"))
        foreground.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        Thread.sleep(750)
        val rows = JSONArray()
        try {
            for (window in listOf(208, 512, 768, 0)) for (beam in listOf(1, 3)) {
                val started = SystemClock.elapsedRealtime()
                val result = runCatching { WhisperNative.transcribeWav(model.absolutePath, source.absolutePath, "", "ru",
                    whisperRequestPrompt(emptyList(), WhisperRequestContext.DATE_REPLY, window), 6, window, 0, true, beam, 15_000, 0f) }
                rows.put(JSONObject().put("window", window).put("beam", beam).put("text", result.getOrNull())
                    .put("error", result.exceptionOrNull()?.message).put("durationMs", SystemClock.elapsedRealtime() - started))
                File(context.filesDir, "date-window-matrix.json").writeText(rows.toString(2))
            }
        } finally { WhisperNative.release(); foreground.close() }
    }
    @Test fun uncertain_date_is_not_silently_accepted() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_date_audit") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.filesDir, "capture-date-audit-0.wav")
        require(source.isFile)
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        val foreground = ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
            .putExtra("gallery_message", "Whisper: проверка ответа о времени"))
        foreground.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        Thread.sleep(750)
        val engine = WhisperCppSttEngine(manager, WhisperVadModelProvider(context), File(context.cacheDir, "date-audit-segments"),
            keepModelLoaded = { true }, configuredThreadCount = { 6 })
        val rows = JSONArray()
        try {
            for (date in listOf(false, true)) {
                engine.setAwaitingDateResponse(date)
                val started = SystemClock.elapsedRealtime()
                val result = engine.transcribe(source)
                rows.put(JSONObject().put("dateContext", date).put("text", result.text).put("durationMs", SystemClock.elapsedRealtime() - started)
                    .put("passes", engine.runtime.value.nativePasses))
                rows.getJSONObject(rows.length() - 1).put("requiresConfirmation", result.requiresConfirmation)
                File(context.filesDir, "date-context-audit.json").writeText(rows.toString(2))
                if (date) {
                    val now = java.time.LocalDateTime.of(2026, 10, 12, 20, 30)
                    val parsed = ru.nezabudka.app.domain.RussianTemporalParser().parse(result.text, now)
                    val words = Regex("[\\p{L}\\p{N}]+").findAll(result.text.lowercase()).map { it.value }.toList()
                    val correctDate = parsed.remindAt == now.toLocalDate().plusDays(1).atTime(10, 25)
                    if (!correctDate) {
                        assertTrue("Incorrect acoustic result needs user correction", result.requiresConfirmation)
                        assertTrue("Raw calendar word must not be silently rewritten", result.text.contains("савтра", true))
                    }
                    assertTrue("10:25 retained: ${result.text}", ("десять" in words && "двадцать" in words && "пять" in words) ||
                        result.text.contains("10:25"))
                }
            }
        } finally { engine.release(); foreground.close() }
    }
}

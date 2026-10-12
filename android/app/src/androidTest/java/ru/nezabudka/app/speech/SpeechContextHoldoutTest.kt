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
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.UserModelsStorage
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.ui.InterfaceReviewActivity
import java.io.File
import java.security.MessageDigest

/** Parameters are frozen before the user supplies the unseen audio/reference. Never passes the reference to the decoder. */
@RunWith(AndroidJUnit4::class)
class SpeechContextHoldoutTest {
    @Test fun compare_locked_domains_on_new_user_recording() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_context_holdout") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sample = File(context.filesDir, "capture-context-holdout-0.wav")
        require(sample.isFile)
        val reference = File(context.filesDir, "context-holdout-reference.json").takeIf(File::isFile)
            ?.let { JSONObject(it.readText()).getString("text") }
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        val foreground = ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
            .putExtra("gallery_message", "Whisper: независимая проверка новой команды"))
        foreground.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        Thread.sleep(750)
        val engine = WhisperCppSttEngine(manager, WhisperVadModelProvider(context), File(context.cacheDir, "context-holdout-segments"),
            keepModelLoaded = { true }, configuredThreadCount = { 6 })
        val rows = JSONArray()
        val report = JSONObject().put("reference", reference ?: JSONObject.NULL).put("settingsFrozen", true).put("model", manager.state.value.selectedName)
            .put("sha256", MessageDigest.getInstance("SHA-256").digest(sample.readBytes()).joinToString("") { "%02x".format(it) })
            .put("results", rows)
        try {
            // Alternate order; each domain gets a cold and a warm comparison.
            for (scope in listOf(CommandScope.NOTES, CommandScope.ASSISTANT, CommandScope.ASSISTANT, CommandScope.NOTES)) {
                engine.setCommandScope(scope)
                val started = SystemClock.elapsedRealtime()
                val result = engine.transcribe(sample)
                val row = JSONObject().put("scope", scope.name).put("text", result.text)
                    .put("durationMs", SystemClock.elapsedRealtime() - started)
                    .put("requiresConfirmation", result.requiresConfirmation).put("passes", engine.runtime.value.nativePasses)
                if (reference != null) row.put("wordErrors", distance(words(reference), words(result.text)))
                    .put("referenceWords", words(reference).size)
                rows.put(row)
                File(context.filesDir, "context-holdout-report.json").writeText(report.toString(2))
            }
        } finally { engine.release(); foreground.close() }
    }
    private fun words(text: String) = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase().replace('ё', 'е')).map { it.value }.toList()
    private fun distance(reference: List<String>, actual: List<String>): Int {
        var previous = IntArray(actual.size + 1) { it }
        reference.forEachIndexed { row, word ->
            val current = IntArray(actual.size + 1); current[0] = row + 1
            actual.forEachIndexed { column, other -> current[column + 1] = minOf(current[column] + 1, previous[column + 1] + 1,
                previous[column] + if (word == other) 0 else 1) }
            previous = current
        }
        return previous.last()
    }
}

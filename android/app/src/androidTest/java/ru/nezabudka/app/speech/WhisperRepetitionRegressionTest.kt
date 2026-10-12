package ru.nezabudka.app.speech

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import ru.nezabudka.app.ui.InterfaceReviewActivity
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.UserModelsStorage
import ru.nezabudka.app.domain.CommandScope
import java.io.File

/** Immutable user-confirmed live recordings; never substitute last_processed.wav. */
@RunWith(AndroidJUnit4::class)
class WhisperRepetitionRegressionTest {
    @Test fun single_word_is_not_duplicated() = runBlocking(Dispatchers.Default) {
        checkRecording("whisper-repeat-reference.wav", listOf("привет"))
    }

    @Test fun last_recorded_word_is_not_duplicated() = runBlocking(Dispatchers.Default) {
        checkRecording("whisper-repeat-current-20261012.wav", listOf("привет"))
    }

    @Test fun spoken_repetitions_are_preserved() = runBlocking(Dispatchers.Default) {
        checkRecording("whisper-intentional-repeat-20261012.wav",
            listOf("привет", "привет", "очень", "очень", "важно"))
    }

    private suspend fun checkRecording(name: String, expected: List<String>) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val foreground = ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
            .putExtra("gallery_message", "Whisper: проверка повторов"))
        foreground.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        Thread.sleep(750)
        val reference = File(context.filesDir, name)
        assumeTrue("Requires the confirmed immutable recording: $name", reference.isFile)
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        assumeTrue(manager.state.value.selectedName != null)
        val directory = File(context.cacheDir, "whisper-repeat-regression-${System.nanoTime()}").apply { mkdirs() }
        val engine = WhisperCppSttEngine(manager, WhisperVadModelProvider(context), File(directory, "segments"),
            keepModelLoaded = { true }, configuredThreadCount = { 6 }, accurateMode = { false })
        val report = JSONArray()
        try {
            val copy = reference.copyTo(File(directory, name))
            for ((commandMode, scope) in listOf(true to CommandScope.ASSISTANT, true to CommandScope.NOTES,
                true to CommandScope.REMINDERS, false to CommandScope.ASSISTANT)) {
                engine.setCommandVocabularyEnabled(commandMode)
                engine.setCommandScope(scope)
                val started = SystemClock.elapsedRealtime()
                val recognition = engine.transcribe(copy)
                val words = Regex("[\\p{L}]+").findAll(recognition.text.lowercase()).map { it.value }.toList()
                report.put(JSONObject().put("case", name).put("commandMode", commandMode)
                    .put("scope", scope.name)
                    .put("text", recognition.text).put("durationMs", SystemClock.elapsedRealtime() - started)
                    .put("threads", engine.runtime.value.threads)
                    .put("repetitionRetry", engine.runtime.value.usedRepetitionRetry))
                // Persist even a failed comparison so the discrepancy can be inspected.
                File(context.filesDir, "whisper-repetition-regression-report.json").writeText(report.toString(2))
                File(context.filesDir, "whisper-regression-${reference.nameWithoutExtension}.json").writeText(report.toString(2))
                assertEquals("$name, command=$commandMode, scope=$scope", expected, words)
                assertFalse("The fix must not add a second decode for word repetition", engine.runtime.value.usedRepetitionRetry)
            }
        } finally {
            engine.release()
            directory.deleteRecursively()
            foreground.close()
        }
    }
}

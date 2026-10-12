package ru.nezabudka.app.speech

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import ru.nezabudka.app.ui.InterfaceReviewActivity
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
import java.security.MessageDigest

/** Immutable references. Raw native decoding only; no execution or setting changes. */
@RunWith(AndroidJUnit4::class)
class WhisperPrecisionAuditTest {
    @Test fun compare_windows_search_and_context() = runBlocking(Dispatchers.Default) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_precision_audit") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        val model = manager.selectedModel()
        val cases = listOf(
            "whisper-repeat-reference.wav" to "привет",
            "whisper-repeat-current-20261012.wav" to "привет",
            "whisper-intentional-repeat-20261012.wav" to "Привет привет очень очень важно",
            "whisper-count-reference-audit.wav" to "раз два три",
            "folder-command-reference.wav" to "В папку работа добавь заметку сходить к врачу и купить бумаги",
            "whisper-latest-audit-044.wav" to "",
        )
        val vocabulary = WHISPER_COMMAND_VOCABULARY.split(" ").joinToString(", ") + "."
        val expanded = vocabulary.dropLast(1) + ", добавь, запиши, сохрани, выполни, перенеси, каждый, ежедневно, еженедельно, ежемесячно, папка, работа."
        data class Candidate(val name: String, val window: Int, val beam: Int = 1, val prompt: String = vocabulary, val fallback: Float = 0f)
        val candidates = listOf(
            Candidate("baseline", -1, fallback = 0.2f),
            Candidate("same-window-no-fallback", -1),
            Candidate("window-512", 512),
            Candidate("window-768", 768),
            Candidate("window-full", 0),
            Candidate("beam-3", 768, 3),
            Candidate("beam-5", 768, 5),
            Candidate("expanded-vocabulary", 512, prompt = expanded),
            Candidate("dictation-no-prompt", 512, prompt = ""),
        )
        val rows = JSONArray()
        val foreground = ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
            .putExtra("gallery_message", "Whisper: сравнение сохранённых записей"))
        foreground.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        Thread.sleep(750)
        val report = JSONObject().put("model", model.name).put("threads", 6)
            .put("comparison", "Same unmodified WAV; raw output, no VAD edits or Kotlin retries")
            .put("results", rows)
        fun save() = File(context.filesDir, "whisper-precision-audit.json").writeText(report.toString(2))
        try {
            for ((caseIndex, pair) in cases.withIndex()) {
                val (name, expected) = pair
                val source = File(context.filesDir, name)
                require(source.isFile) { "Missing immutable reference $name" }
                val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
                val policy = whisperInferencePolicy(source.length())
                // Warm loading once; alternate order to reduce order/heat bias.
                if (caseIndex == 0) WhisperNative.transcribeWav(model.absolutePath, source.absolutePath, "", "ru", vocabulary,
                    6, 512, 0, true, 1, 15_000, 0f)
                val selected = if (InstrumentationRegistry.getArguments().getString("quick_audit") == "true")
                    candidates.filter { it.name in setOf("baseline", "same-window-no-fallback", "window-512", "beam-3", "expanded-vocabulary") } else candidates
                for (candidate in if (caseIndex % 2 == 0) selected else selected.reversed()) {
                    val window = if (candidate.window < 0) policy.audioContext else candidate.window
                    val prompt = if (candidate.name == "baseline" || candidate.name == "same-window-no-fallback")
                        whisperPromptForInference(contextualWhisperPrompt(emptyList()), true, window, 1) else candidate.prompt
                    val started = SystemClock.elapsedRealtime()
                    val result = runCatching { WhisperNative.transcribeWav(model.absolutePath, source.absolutePath, "", "ru", prompt,
                        6, window, 0, policy.singleSegment, candidate.beam, 15_000, candidate.fallback) }
                    val row = JSONObject().put("case", name).put("sha256", hash).put("expected", expected)
                        .put("candidate", candidate.name).put("window", window).put("beam", candidate.beam)
                        .put("prompt", prompt).put("durationMs", SystemClock.elapsedRealtime() - started)
                        .put("text", result.getOrNull()).put("error", result.exceptionOrNull()?.toString())
                    if (result.isSuccess) {
                        row.put("metrics", JSONArray(WhisperNative.lastMetrics().toList()))
                        if (expected.isNotBlank()) row.put("wordErrors", distance(words(expected), words(result.getOrThrow())))
                    }
                    rows.put(row); save()
                    println("PRECISION_CASE ${row}")
                }
            }
        } finally { save(); WhisperNative.release(); foreground.close() }
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

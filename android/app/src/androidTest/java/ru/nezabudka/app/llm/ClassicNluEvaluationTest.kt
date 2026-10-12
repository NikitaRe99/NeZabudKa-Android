package ru.nezabudka.app.llm

import android.content.Context
import android.os.SystemClock
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
import ru.nezabudka.app.domain.CommandContext
import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.domain.CommandScope
import java.io.File
import java.time.LocalDateTime

/** Evaluation only: never executes a command or changes notes/reminders. */
@RunWith(AndroidJUnit4::class)
class ClassicNluEvaluationTest {
    @Test fun evaluate_installed_model_and_payloads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val client = ClassicNluClient(context, ClassicNluCommandMapper(), UserModelsStorage(context))
        assumeTrue("Install NLU model first", client.modelState.value.installed)
        val report = JSONArray()
        try {
            for (name in listOf("command_corpus_ru.json", "command_corpus_ru_extended.json", "nlu_heldout_evaluation.json", "nlu_new_commands.json", "nlu_user_trial_v6.json", "nlu_frozen_v6.json", "nlu_final_unseen_v6.json")) {
                val cases = JSONArray(assets.open("llm/$name").bufferedReader().use { it.readText() })
                for (index in 0 until cases.length()) {
                    val case = cases.getJSONObject(index)
                    val scope = when (case.optString("scope", "assistant")) {
                        "notes" -> CommandScope.NOTES
                        "reminders" -> CommandScope.REMINDERS
                        else -> CommandScope.ASSISTANT
                    }
                    val started = SystemClock.elapsedRealtime()
                    val actual = client.understand(case.getString("utterance"), LocalDateTime.of(2026, 8, 8, 12, 0), CommandContext(scope, "ru"))
                    val diagnostic = client.diagnostic.value
                    val payload = when (actual.intent) {
                        CommandIntent.CREATE_REMINDER -> actual.text
                        CommandIntent.CREATE_NOTE -> actual.noteText
                        CommandIntent.DELETE_REMINDER -> actual.targetText
                        CommandIntent.CREATE_FOLDER, CommandIntent.DELETE_FOLDER -> actual.noteFolder
                        else -> null
                    }
                    report.put(JSONObject().put("corpus", name).put("case", case).put("actualIntent", actual.intent.wireName)
                        .put("payload", payload).put("folder", actual.noteFolder).put("remindAt", actual.remindAt?.toString())
                        .put("recurrence", actual.recurrence).put("periodStart", actual.periodStart?.toString()).put("periodEnd", actual.periodEnd?.toString())
                        .put("confidence", diagnostic.confidence).put("rawIntent", diagnostic.selectedIntent)
                        .put("decision", diagnostic.decision).put("slots", JSONObject(diagnostic.slots))
                        .put("error", diagnostic.error).put("durationMs", SystemClock.elapsedRealtime() - started))
                }
            }
        } finally {
            File(context.filesDir, "nlu-evaluation-report.json").writeText(report.toString(2))
            client.release()
        }
    }
}

package ru.nezabudka.app.llm

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.AssistantApplication
import ru.nezabudka.app.application.ExecuteTextCommand
import ru.nezabudka.app.domain.AssistantCommand
import ru.nezabudka.app.domain.CommandContext
import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.domain.CustomReminderRecurrence
import ru.nezabudka.app.domain.RussianTemporalParser
import java.io.File
import java.time.LocalDateTime
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Read-only: invoke the actual production parser before dispatch, never ExecuteTextCommand.invoke. */
@RunWith(AndroidJUnit4::class)
class FreshCommandSetTest {
    @Test fun evaluate_frozen_set_without_executing_actions() = runBlocking(Dispatchers.Default) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.filesDir, "command-set-2026-10-12.json")
        assumeTrue("Provide the independently frozen set", source.isFile)
        val container = (context.applicationContext as AssistantApplication).container
        container.startSettingsResources()
        val now = LocalDateTime.of(2026, 10, 12, 12, 0)
        val method = ExecuteTextCommand::class.java.getDeclaredMethod("understandCandidate",
            String::class.java, LocalDateTime::class.java, CommandContext::class.java, Continuation::class.java)
            .apply { isAccessible = true }
        suspend fun parse(text: String): AssistantCommand = suspendCoroutineUninterceptedOrReturn { continuation ->
            method.invoke(container.execute, text, now, CommandContext(CommandScope.ASSISTANT), continuation)
        }
        val cases = JSONArray(source.readText())
        val rows = JSONArray()
        for (index in 0 until cases.length()) {
            val expected = cases.getJSONObject(index)
            val started = SystemClock.elapsedRealtime()
            val result = runCatching {
                val text = expected.getString("utterance")
                val parsed = parse(text)
                if (parsed.intent == CommandIntent.LIST_REMINDERS && parsed.periodStart == null && parsed.periodEnd == null) {
                    val temporal = RussianTemporalParser()
                    temporal.extractDateRange(text, now)?.let { range ->
                        parsed.copy(periodStart = range.start.atStartOfDay(), periodEnd = range.endExclusive.atStartOfDay())
                    } ?: temporal.extractDate(text, now)?.let { date ->
                        parsed.copy(periodStart = date.atStartOfDay(), periodEnd = date.plusDays(1).atStartOfDay())
                    } ?: parsed
                } else parsed
            }
            val actual = result.getOrNull()
            val differences = JSONArray()
            fun check(key: String, value: String?) {
                if (!expected.has(key)) return
                fun normalize(text: String?): String? = text?.trim()?.trim('.', '!', '?')?.lowercase()?.replace('ё', 'е')
                if (normalize(expected.getString(key)) != normalize(value)) {
                    differences.put("$key: ${expected.getString(key)} -> $value")
                }
            }
            check("intent", actual?.intent?.wireName)
            check("text", actual?.text ?: actual?.noteText)
            check("folder", actual?.noteFolder)
            check("target", actual?.targetText)
            check("remind_at", actual?.remindAt?.toString())
            check("period_start", actual?.periodStart?.toString())
            check("period_end", actual?.periodEnd?.toString())
            check("recurrence", actual?.recurrence)
            val recurrence = CustomReminderRecurrence.fromStored(actual?.recurrence)
            check("repeat_interval", recurrence?.interval?.toString())
            check("repeat_unit", recurrence?.unit?.storedValue)
            check("repeat_count", recurrence?.repeatCount?.plus(1)?.toString())
            if (expected.has("repeat_days")) {
                val days = expected.getJSONArray("repeat_days")
                val expectedDays = (0 until days.length()).map { days.getInt(it) }.toSet()
                if (expectedDays != recurrence?.weekdays?.map { it.value }?.toSet()) differences.put("repeat_days")
            }
            result.exceptionOrNull()?.let { differences.put("error: ${it.cause?.message ?: it.message}") }
            rows.put(JSONObject().put("id", expected.getInt("id")).put("expected", expected)
                .put("actualIntent", actual?.intent?.wireName).put("text", actual?.text ?: actual?.noteText)
                .put("folder", actual?.noteFolder).put("target", actual?.targetText)
                .put("remindAt", actual?.remindAt?.toString()).put("recurrence", actual?.recurrence)
                .put("periodStart", actual?.periodStart?.toString()).put("periodEnd", actual?.periodEnd?.toString())
                .put("durationMs", SystemClock.elapsedRealtime() - started)
                .put("differences", differences).put("passed", differences.length() == 0))
        }
        val report = JSONObject().put("now", now.toString()).put("scope", "assistant")
            .put("model", container.classicNlu.modelState.value.selectedName)
            .put("nluEnabled", container.settings.state.value.classicNluEnabled)
            .put("route", "production understandCandidate; before dispatch; no actions executed")
            .put("results", rows)
        File(context.filesDir, "fresh-command-set-report.json").writeText(report.toString(2))
        println("FRESH_COMMAND_SET_REPORT $report")
    }
}

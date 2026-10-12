package ru.nezabudka.app.llm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.nezabudka.app.application.Dispatcher
import ru.nezabudka.app.application.ExecuteTextCommand
import ru.nezabudka.app.domain.*
import ru.nezabudka.app.ports.*
import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDateTime
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

class FreshOfflineCommandSetTest {
    @Test fun evaluate_actual_onnx_outputs_with_production_command_parser() = runBlocking {
        val path = System.getenv("NLU_NEW_SET_EVALUATION")
        assumeTrue("Optional independently frozen evaluation", path != null && File(path).isFile)
        val source = File(path!!)
        val input = Json.parseToJsonElement(source.readText()).jsonArray
        val mapper = ClassicNluCommandMapper()
        // Every repository operation fails: this test is strictly parsing only.
        fun <T : Any> forbidden(type: Class<T>): T = requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, _, _ ->
            error("Evaluation must never dispatch or access user data")
        }))
        val dispatcher = Dispatcher(forbidden(ReminderRepository::class.java), forbidden(NotesRepository::class.java),
            forbidden(ReminderScheduler::class.java))
        val method = ExecuteTextCommand::class.java.getDeclaredMethod("understandCandidate", String::class.java,
            LocalDateTime::class.java, CommandContext::class.java, Continuation::class.java).apply { isAccessible = true }
        val report = buildJsonArray {
            for (element in input) {
                val row = element.jsonObject
                val case = row.getValue("case").jsonObject
                val text = case.getValue("utterance").jsonPrimitive.content
                val now = LocalDateTime.parse(row.getValue("now").jsonPrimitive.content)
                val intent = CommandIntent.entries.first { it.wireName == row.getValue("intent").jsonPrimitive.content }
                val slots = row.getValue("slots").jsonObject.mapValues { it.value.jsonPrimitive.content }
                val understanding = CommandUnderstandingPort { value, time, context ->
                    mapper.explicitCommand(value, time, context.scope) ?: mapper.map(intent, slots, value, time)
                }
                val execute = ExecuteTextCommand(understanding, dispatcher)
                var error: String? = null
                val actual = try {
                    val parsed = suspendCoroutineUninterceptedOrReturn<AssistantCommand> { continuation ->
                        method.invoke(execute, text, now, CommandContext(), continuation)
                    }
                    // ExecuteTextCommand fills a missing list period after understanding.
                    if (parsed.intent == CommandIntent.LIST_REMINDERS && parsed.periodStart == null && parsed.periodEnd == null) {
                        val temporal = RussianTemporalParser()
                        temporal.extractDateRange(text, now)?.let { range ->
                            parsed.copy(periodStart = range.start.atStartOfDay(), periodEnd = range.endExclusive.atStartOfDay())
                        } ?: temporal.extractDate(text, now)?.let { date ->
                            parsed.copy(periodStart = date.atStartOfDay(), periodEnd = date.plusDays(1).atStartOfDay())
                        } ?: parsed
                    } else parsed
                } catch (failure: Exception) { error = failure.cause?.message ?: failure.message; null }
                val differences = mutableListOf<String>()
                fun check(key: String, value: String?) {
                    val expected = case[key]?.jsonPrimitive?.content ?: return
                    fun normalize(v: String?): String? = v?.trim()?.trim('.', '!', '?')?.lowercase()?.replace('ё', 'е')
                    if (normalize(expected) != normalize(value)) differences += "$key: $expected -> $value"
                }
                check("intent", actual?.intent?.wireName)
                check("text", actual?.text ?: actual?.noteText)
                check("folder", actual?.noteFolder); check("target", actual?.targetText)
                check("remind_at", actual?.remindAt?.toString())
                check("period_start", actual?.periodStart?.toString()); check("period_end", actual?.periodEnd?.toString())
                check("recurrence", actual?.recurrence)
                val repeat = CustomReminderRecurrence.fromStored(actual?.recurrence)
                check("repeat_interval", repeat?.interval?.toString()); check("repeat_unit", repeat?.unit?.storedValue)
                check("repeat_count", repeat?.repeatCount?.plus(1)?.toString())
                case["repeat_days"]?.jsonArray?.let { days ->
                    if (days.map { it.jsonPrimitive.int }.toSet() != repeat?.weekdays?.map { it.value }?.toSet()) differences += "repeat_days"
                }
                if (error != null) differences += "error: $error"
                add(buildJsonObject {
                    put("id", case.getValue("id")); put("expected", case)
                    put("actualIntent", actual?.intent?.wireName); put("text", actual?.text ?: actual?.noteText)
                    put("folder", actual?.noteFolder); put("target", actual?.targetText)
                    put("remindAt", actual?.remindAt?.toString()); put("recurrence", actual?.recurrence)
                    put("periodStart", actual?.periodStart?.toString()); put("periodEnd", actual?.periodEnd?.toString())
                    put("rawIntent", row.getValue("raw_intent")); put("confidence", row.getValue("confidence"))
                    put("differences", buildJsonArray { differences.forEach { add(it) } }); put("passed", differences.isEmpty())
                })
            }
        }
        source.resolveSibling("fresh_command_set_report.json").writeText(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), report))
    }
}

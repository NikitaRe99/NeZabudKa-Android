package ru.nezabudka.app.llm

import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nezabudka.app.domain.*
import java.io.File
import java.time.LocalDateTime

/** Actual exported ONNX outputs plus production Kotlin mapping; optional local training audit. */
class FrozenNluMappingTest {
    @Test fun evaluate_actual_export_with_production_mapping() {
        val input = System.getenv("NLU_V6_EVALUATION")
        assumeTrue("Generate candidate predictions first", input != null && File(input).isFile)
        val rows = Json.parseToJsonElement(File(input!!).readText()).jsonArray
        val now = LocalDateTime.of(2026, 8, 8, 12, 0)
        val mapper = ClassicNluCommandMapper()
        val report = buildJsonArray {
            rows.forEach { element ->
                val row = element.jsonObject
                val case = row.getValue("case").jsonObject
                val utterance = case.getValue("utterance").jsonPrimitive.content
                val scope = when (case["scope"]?.jsonPrimitive?.content) {
                    "notes" -> CommandScope.NOTES
                    "reminders" -> CommandScope.REMINDERS
                    else -> CommandScope.ASSISTANT
                }
                val intent = CommandIntent.entries.first { it.wireName == row.getValue("intent").jsonPrimitive.content }
                val slots = row.getValue("slots").jsonObject.mapValues { it.value.jsonPrimitive.content }
                var error: String? = null
                val command = try {
                    if (isUnsupportedCommandMutation(utterance) || isUnsupportedMediaCommand(utterance) || isCompletedActionStatement(utterance) || isNonCommandSpeechStatement(utterance)) AssistantCommand(CommandIntent.UNKNOWN)
                    else mapper.explicitCommand(utterance, now, scope) ?: mapper.map(intent, slots, utterance, now)
                } catch (failure: Exception) { error = failure.message; AssistantCommand(CommandIntent.UNKNOWN) }
                val differences = mutableListOf<String>()
                fun check(key: String, actual: String?) {
                    val expected = case[key]?.jsonPrimitive?.content ?: return
                    if (expected.trim().trim('.', '!', '?').lowercase() != actual?.trim()?.trim('.', '!', '?')?.lowercase()) differences += "$key: $expected -> $actual"
                }
                check("intent", command.intent.wireName)
                check("text", command.text ?: command.noteText)
                check("target", command.targetText)
                check("folder", command.noteFolder)
                check("remind_at", command.remindAt?.toString())
                check("period_start", command.periodStart?.toString())
                check("period_end", command.periodEnd?.toString())
                check("recurrence", command.recurrence)
                val rule = CustomReminderRecurrence.fromStored(command.recurrence)
                check("repeat_interval", rule?.interval?.toString())
                check("repeat_unit", rule?.unit?.storedValue)
                // "всего N раз" includes the first firing; scheduler count stores additional repeats.
                check("repeat_count", rule?.repeatCount?.plus(1)?.toString())
                case["repeat_days"]?.jsonArray?.let { expected ->
                    if (expected.map { it.jsonPrimitive.int }.toSet() != rule?.weekdays?.map { it.value }?.toSet()) differences += "repeat_days"
                }
                add(buildJsonObject {
                    put("corpus", row.getValue("corpus")); put("case", case); put("actualIntent", command.intent.wireName)
                    put("payload", command.text ?: command.noteText ?: command.targetText ?: command.noteFolder)
                    put("folder", command.noteFolder); put("recurrence", command.recurrence)
                    put("remindAt", command.remindAt?.toString()); put("error", error)
                    put("differences", buildJsonArray { differences.forEach { add(it) } })
                })
            }
        }
        File(input).resolveSibling("offline_mapping_report.json").writeText(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), report))
        val unsafe = report.filter { row -> row.jsonObject.getValue("case").jsonObject.getValue("intent").jsonPrimitive.content == "unknown" && row.jsonObject.getValue("actualIntent").jsonPrimitive.content != "unknown" }
        assertTrue("Unsupported commands must not execute: $unsafe", unsafe.isEmpty())
    }
}

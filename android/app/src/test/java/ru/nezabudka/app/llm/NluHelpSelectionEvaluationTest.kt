package ru.nezabudka.app.llm

import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import ru.nezabudka.app.application.Dispatcher
import ru.nezabudka.app.application.ExecuteTextCommand
import ru.nezabudka.app.domain.*
import ru.nezabudka.app.ports.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Optional read-only audit: real app tokenizer + ONNX logits + unchanged production decoder.
 * Native inference runs separately on desktop; no Android model files or user data are touched.
 * Unsafe allocates only the decoder shell to avoid starting Android services in a JVM test.
 */
class NluHelpSelectionEvaluationTest {
    private val now = LocalDateTime.of(2026, 10, 14, 12, 0)
    private val json = Json { prettyPrint = true }
    private fun tokenizer() = ClassicWordPieceTokenizer(File("src/main/assets/nlu/vocab.txt").readLines(), 64)
    private fun dispatcher(): Dispatcher {
        fun <T : Any> forbidden(type: Class<T>): T = requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader,
            arrayOf(type)) { _, _, _ -> error("Read-only audit must never access repositories or schedule reminders") }))
        return Dispatcher(forbidden(ReminderRepository::class.java), forbidden(NotesRepository::class.java),
            forbidden(ReminderScheduler::class.java))
    }
    private suspend fun parse(execute: ExecuteTextCommand, text: String): AssistantCommand {
        val method = ExecuteTextCommand::class.java.getDeclaredMethod("understandCandidate", String::class.java,
            LocalDateTime::class.java, CommandContext::class.java, Continuation::class.java).apply { isAccessible = true }
        val parsed = suspendCoroutineUninterceptedOrReturn<AssistantCommand> { continuation ->
            method.invoke(execute, text, now, CommandContext(), continuation)
        }
        if (parsed.intent != CommandIntent.LIST_REMINDERS || parsed.periodStart != null) return parsed
        val temporal = RussianTemporalParser()
        return temporal.extractDateRange(text, now)?.let {
            parsed.copy(periodStart = it.start.atStartOfDay(), periodEnd = it.endExclusive.atStartOfDay())
        } ?: temporal.extractDate(text, now)?.let {
            parsed.copy(periodStart = it.atStartOfDay(), periodEnd = it.plusDays(1).atStartOfDay())
        } ?: parsed
    }
    @Test fun prepare_actual_app_tokens_and_disabled_nlu_baseline() = runBlocking {
        assumeTrue(System.getenv("NLU_HELP_PHASE") == "prepare")
        val cases = json.parseToJsonElement(File("../../docs/nlu/help-candidates-2026-10-14.json").readText()).jsonArray
        val execute = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher())
        val tokenizer = tokenizer()
        val rows = buildJsonArray {
            for (element in cases) {
                val case = element.jsonObject
                val text = case.getValue("utterance").jsonPrimitive.content
                val modelInput = normalizeCommandVerbForm(text)
                val tokens = tokenizer.encode(modelInput)
                val baseline = parse(execute, text)
                add(buildJsonObject {
                    put("case", case); put("model_input", modelInput)
                    put("baseline", payload(baseline))
                    put("input_ids", buildJsonArray { tokens.inputIds.forEach { add(it) } })
                    put("attention_mask", buildJsonArray { tokens.attentionMask.forEach { add(it) } })
                    put("token_type_ids", buildJsonArray { tokens.tokenTypeIds.forEach { add(it) } })
                })
            }
        }
        File("build/tmp/nlu-help-tokens.json").writeText(json.encodeToString(JsonArray.serializer(), rows))
    }
    @Test fun decode_real_logits_with_production_guard_mapper_and_routing() = runBlocking {
        assumeTrue(System.getenv("NLU_HELP_PHASE") == "evaluate")
        val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe
        val client = unsafe.allocateInstance(ClassicNluClient::class.java) as ClassicNluClient
        ClassicNluClient::class.java.getDeclaredField("mapper").apply { isAccessible = true }.set(client, ClassicNluCommandMapper())
        val decode = ClassicNluClient::class.java.declaredMethods.single { it.name == "decode" }.apply { isAccessible = true }
        val rows = json.parseToJsonElement(File("build/tmp/nlu-help-logits.json").readText()).jsonArray
        val tokenizer = tokenizer()
        val report = buildJsonArray {
            for (element in rows) {
                val row = element.jsonObject
                val case = row.getValue("case").jsonObject
                val input = row.getValue("model_input").jsonPrimitive.content
                val tokens = tokenizer.encode(input)
                assertEquals(row.getValue("input_ids").jsonArray.map { it.jsonPrimitive.long }, tokens.inputIds.toList())
                val intents = row.getValue("intent_logits").jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
                val slots = row.getValue("slot_logits").jsonArray.map { line -> line.jsonArray.map { it.jsonPrimitive.float }.toFloatArray() }.toTypedArray()
                var decision = "NLU not called: handled by standard parser"
                var confidence: Double? = null
                var called = false
                val execute = ExecuteTextCommand(CommandUnderstandingPort { value, time, context ->
                    called = true
                    val inference = decode.invoke(client, value, time, context, tokens, intents, slots)
                    fun field(name: String) = inference.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(inference)
                    decision = field("decision") as String
                    confidence = field("confidence") as Double
                    field("command") as AssistantCommand
                }, dispatcher())
                val parsed = parse(execute, case.getValue("utterance").jsonPrimitive.content)
                val actual = payload(parsed)
                val differences = buildJsonArray {
                    fun normal(v: String?) = v?.trim()?.trim('.', '!', '?')?.lowercase()?.replace('ё', 'е')
                    for (key in listOf("intent", "text", "folder", "target", "remind_at", "period_start", "period_end", "recurrence")) {
                        if (case[key] != null && normal(case[key]?.jsonPrimitive?.contentOrNull) != normal(actual[key]?.jsonPrimitive?.contentOrNull))
                            add("$key: ${case[key]} -> ${actual[key]}")
                    }
                }
                val id = case.getValue("id").jsonPrimitive.int
                if (id in setOf(7, 10, 17, 19, 20, 31)) {
                    assertEquals("Selected help case $id must require NLU", "unknown",
                        row.getValue("baseline").jsonObject.getValue("intent").jsonPrimitive.content)
                    assertEquals("Selected help case $id must call NLU", true, called)
                    assertEquals("Selected help case $id has wrong payload: $differences", true, differences.isEmpty())
                }
                if (id in setOf(18, 21)) assertEquals("Standard command $id must not be advertised as NLU-only", false, called)
                add(buildJsonObject {
                    put("case", case); put("model", row.getValue("model")); put("model_sha256", row.getValue("model_sha256"))
                    put("baseline", row.getValue("baseline")); put("actual", actual)
                    put("modelCalled", called); put("decision", decision); put("confidence", confidence)
                    put("passed", differences.isEmpty()); put("differences", differences)
                    put("requiresNlu", row.getValue("baseline").jsonObject.getValue("intent").jsonPrimitive.content == "unknown")
                })
            }
        }
        File("../../docs/nlu/help-selection-2026-10-14-results.json").writeText(json.encodeToString(JsonArray.serializer(), report))
    }
    private fun payload(c: AssistantCommand) = buildJsonObject {
        put("intent", c.intent.wireName); put("text", c.text ?: c.noteText); put("folder", c.noteFolder)
        put("target", c.targetText); put("remind_at", c.remindAt?.toString()); put("recurrence", c.recurrence)
        put("period_start", c.periodStart?.toString()); put("period_end", c.periodEnd?.toString())
    }
}

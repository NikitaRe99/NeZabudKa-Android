package ru.nezabudka.app.llm

import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import ru.nezabudka.app.application.Dispatcher
import ru.nezabudka.app.application.ExecuteTextCommand
import ru.nezabudka.app.domain.*
import ru.nezabudka.app.ports.*

/** Freeze a manual intent evaluation set; do not use these utterances for training. */
class NluOnlyManualSetRoutingTest {
    @Test fun `every manual utterance requires NLU rather than explicit rules or implicit fallback`() = runBlocking {
        fun <T : Any> forbidden(type: Class<T>): T = requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader,
            arrayOf(type)) { _, _, _ -> error("Routing audit must not access repositories") }))
        val dispatcher = Dispatcher(forbidden(ReminderRepository::class.java), forbidden(NotesRepository::class.java),
            forbidden(ReminderScheduler::class.java))
        val cases = Json.parseToJsonElement(requireNotNull(javaClass.getResource("/llm/nlu_only_manual_v1.json")).readText()).jsonArray
        for (case in cases) {
            val id = case.jsonObject.getValue("id").jsonPrimitive.int
            val text = case.jsonObject.getValue("utterance").jsonPrimitive.content
            var calls = 0
            val model = CommandUnderstandingPort { value, now, context ->
                calls++
                assertNull("Case $id has an explicit mapper shortcut", ClassicNluCommandMapper().explicitCommand(value, now, context.scope))
                AssistantCommand(CommandIntent.UNKNOWN)
            }
            val execute = ExecuteTextCommand(model, dispatcher, Clock.fixed(Instant.parse("2026-10-13T08:00:00Z"), ZoneOffset.UTC))
            val result = execute.preview(text, CommandContext())
            assertEquals("Case $id did not reach NLU: $text", 1, calls)
            assertEquals("Case $id can be recognized without the model", CommandIntent.UNKNOWN, result.intent)
        }
    }
}

package ru.nezabudka.app.application

import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.nezabudka.app.domain.*
import ru.nezabudka.app.ports.*

class AssistantHelpCommandsTest {
    @Test fun `help examples work with NLU disabled and keep their parameters`() = runBlocking {
        fun <T : Any> forbidden(type: Class<T>): T = requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader,
            arrayOf(type)) { _, _, _ -> error("Preview must not modify user data") }))
        val dispatcher = Dispatcher(forbidden(ReminderRepository::class.java), forbidden(NotesRepository::class.java),
            forbidden(ReminderScheduler::class.java))
        val execute = ExecuteTextCommand(OptionalCommandUnderstanding(
            CommandUnderstandingPort { text, _, _ -> error("Disabled NLU must not run: $text") }, { false }),
            dispatcher, Clock.fixed(Instant.parse("2026-10-14T08:00:00Z"), ZoneOffset.UTC))
        val examples = listOf(
            "Напомни завтра в девять утра полить цветы" to CommandIntent.CREATE_REMINDER,
            "Напомни каждый месяц поливать цветы" to CommandIntent.CREATE_REMINDER,
            "Покажи напоминания на завтра" to CommandIntent.LIST_REMINDERS,
            "Удали последнее напоминание" to CommandIntent.DELETE_REMINDER,
            "Создай заметку: идеи для выходных" to CommandIntent.CREATE_NOTE,
            "Создай папку Путешествия" to CommandIntent.CREATE_FOLDER,
            "В папку Путешествия добавь заметку взять зарядку" to CommandIntent.CREATE_NOTE,
            "Удали папку Путешествия" to CommandIntent.DELETE_FOLDER,
            "Отмени последнее напоминание" to CommandIntent.DELETE_REMINDER,
            "Создай напоминание завтра в 10:00 купить молоко" to CommandIntent.CREATE_REMINDER,
            "Надо не забыть завтра в 10:00 купить молоко" to CommandIntent.CREATE_REMINDER,
            "Какие планы на завтра" to CommandIntent.LIST_REMINDERS,
            "Что у меня на завтра" to CommandIntent.LIST_REMINDERS,
            "Удали напоминание про купить молоко" to CommandIntent.DELETE_REMINDER,
        )
        for ((text, intent) in examples) assertEquals(text, intent, execute.preview(text, CommandContext()).intent)
        val note = execute.preview(examples[6].first, CommandContext())
        assertEquals("Путешествия", note.noteFolder)
        assertEquals("взять зарядку", note.noteText)
        assertNotNull(execute.preview(examples[1].first, CommandContext()).recurrence)
    }
}

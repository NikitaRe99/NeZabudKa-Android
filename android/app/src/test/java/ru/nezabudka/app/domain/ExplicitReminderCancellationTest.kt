package ru.nezabudka.app.domain

import org.junit.Assert.*
import org.junit.Test

class ExplicitReminderCancellationTest {
    @Test fun `full targets survive structured cancellation wording`() {
        assertEquals("проверить крепления двери", extractExplicitReminderCancellation("Переведи напоминание проверить крепления двери в отменённые")?.targetText)
        assertEquals("полить рассаду", extractExplicitReminderCancellation("Пожалуйста сними отметку о будущем деле полить рассаду")?.targetText)
        assertEquals(true, extractExplicitReminderCancellation("Мне не нужно последнее напоминание")?.targetLast)
    }

    @Test fun `future carrier and task verb do not become note text`() {
        val now = java.time.LocalDateTime.of(2026, 8, 8, 12, 0)
        val command = extractExplicitFutureReminder("Подай мне сигнал перед тем как записать показания счётчика завтра вечером", now, RussianTemporalParser { mapOf("вечером" to java.time.LocalTime.of(21, 0)) })!!
        assertEquals(CommandIntent.CREATE_REMINDER, command.intent)
        assertEquals("записать показания счётчика", command.text)
        assertEquals(now.toLocalDate().plusDays(1).atTime(21, 0), command.remindAt)
        assertNull(extractExplicitFutureReminder("Создай заметку подай мне сигнал перед тем как записать показания", now, RussianTemporalParser()))
    }
    @Test fun `quoted instructions and negated operations are not cancellation commands`() {
        listOf("Создай заметку Переведи напоминание проверить крепления двери в отменённые", "Отмени удаление папки Тестовый блокнот", "Не переводи напоминание полить рассаду в отменённые").forEach { assertNull(extractExplicitReminderCancellation(it)) }
    }
}

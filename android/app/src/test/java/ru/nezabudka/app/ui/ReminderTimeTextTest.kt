package ru.nezabudka.app.ui

import ru.nezabudka.app.domain.Reminder
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimeTextTest {
    private val now = LocalDateTime.of(2026, 7, 26, 14, 0)

    @Test
    fun `only active reminders with a past time are overdue`() {
        assertTrue(reminder(now.minusMinutes(1)).isOverdueAt(now))
        assertFalse(reminder(now).isOverdueAt(now))
        assertFalse(reminder(now.plusMinutes(1)).isOverdueAt(now))
        assertFalse(reminder(now.minusMinutes(1), status = "completed").isOverdueAt(now))
        assertFalse(reminder(null).isOverdueAt(now))
    }

    @Test
    fun `formats Russian relative overdue time`() {
        assertEquals("Меньше 5 минут назад", overdueReminderText(now.minusMinutes(1), now))
        assertEquals("Меньше 5 минут назад", overdueReminderText(now.minusMinutes(2), now))
        assertEquals("5 минут назад", overdueReminderText(now.minusMinutes(5), now))
        assertEquals("5 минут назад", overdueReminderText(now.minusMinutes(9), now))
        assertEquals("1 час назад", overdueReminderText(now.minusHours(1), now))
        assertEquals("1 день назад", overdueReminderText(now.minusDays(1), now))
        assertEquals("1 неделю назад", overdueReminderText(now.minusWeeks(1), now))
    }

    @Test
    fun `refresh waits until the next visible relative time change`() {
        assertEquals(10_000L, nextReminderUiRefreshDelayMillis(listOf(reminder(now.plusSeconds(10))), now))
        assertEquals(290_000L, nextReminderUiRefreshDelayMillis(listOf(reminder(now.minusSeconds(10))), now))
        assertEquals(30L * 60L * 1_000L, nextReminderUiRefreshDelayMillis(listOf(reminder(now.minusMinutes(90))), now))
        assertEquals(
            24L * 60L * 60L * 1_000L,
            nextReminderUiRefreshDelayMillis(listOf(reminder(now.minusMinutes(1), status = "completed")), now),
        )
    }

    private fun reminder(remindAt: LocalDateTime?, status: String = "active") = Reminder(
        id = 1,
        text = "Позвонить",
        remindAt = remindAt,
        recurrence = null,
        status = status,
        createdAt = now.minusDays(2),
    )
}

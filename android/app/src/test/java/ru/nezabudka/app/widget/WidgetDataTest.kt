package ru.nezabudka.app.widget

import org.junit.Assert.*
import org.junit.Test
import ru.nezabudka.app.domain.Reminder
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetDataTest {
    private val today = LocalDate.of(2026, 10, 12)
    private val created = today.atTime(8, 0)
    private fun reminder(at: LocalDateTime?, status: String = "active") = Reminder(1, "Полить цветы", at, "monthly", status, created)

    @Test fun `today includes overdue and today's last minute but excludes tomorrow undated and completed`() {
        assertTrue(isWidgetTodayReminder(reminder(today.minusDays(2).atTime(10, 0)), today))
        assertTrue(isWidgetTodayReminder(reminder(today.atTime(23, 59, 59)), today))
        assertFalse(isWidgetTodayReminder(reminder(today.plusDays(1).atStartOfDay()), today))
        assertFalse(isWidgetTodayReminder(reminder(null), today))
        assertFalse(isWidgetTodayReminder(reminder(created, "completed"), today))
    }
    @Test fun `stale click cannot complete next recurrence or a restored different item`() {
        val first = reminder(created)
        assertTrue(widgetCompletionMatches(first, created.toString(), created.toString()))
        assertFalse(widgetCompletionMatches(first.copy(remindAt = created.plusMonths(1)), created.toString(), created.toString()))
        assertFalse(widgetCompletionMatches(first.copy(createdAt = created.plusDays(1)), created.toString(), created.toString()))
        assertFalse(widgetCompletionMatches(first.copy(status = "completed"), created.toString(), created.toString()))
    }
    @Test fun `note title uses explicit title or first nonempty line`() {
        assertEquals("План", widgetNoteTitle(" План ", "Содержание"))
        assertEquals("Первая строка", widgetNoteTitle("", "\n Первая строка\nВторая строка"))
        assertEquals("Без названия", widgetNoteTitle(" ", "\n "))
    }
    @Test fun `snippet preserves words and line boundaries as spaces`() {
        assertEquals("Вторая строка", widgetNoteSnippet("", "Первая строка\nВторая строка"))
        assertEquals("один два три", widgetNoteSnippet("Заголовок", "один\nдва  три"))
        assertEquals("", widgetNoteSnippet("", "Одна мысль"))
    }
    @Test fun `dates use current local day`() {
        assertEquals("Сегодня, 08:00", widgetDate(created, today))
        assertEquals("Вчера, 08:00", widgetDate(created.minusDays(1), today))
        assertFalse(widgetDate(created.minusDays(2), today).contains("Сегодня"))
    }
    @Test fun `Russian note counts`() {
        assertEquals("1 заметка", widgetNoteCount(1))
        assertEquals("2 заметки", widgetNoteCount(2))
        assertEquals("5 заметок", widgetNoteCount(5))
        assertEquals("11 заметок", widgetNoteCount(11))
        assertEquals("21 заметка", widgetNoteCount(21))
        assertEquals("112 заметок", widgetNoteCount(112))
    }
}

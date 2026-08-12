package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderRecurrenceTest {
    private val thursday = LocalDateTime.of(2026, 7, 30, 9, 15)

    @Test
    fun `common recurrence rules calculate the next occurrence`() {
        assertEquals(thursday.plusDays(1), nextReminderOccurrence(thursday, "daily"))
        assertEquals(thursday.plusWeeks(1), nextReminderOccurrence(thursday, "weekly"))
        assertEquals(thursday.plusMonths(1), nextReminderOccurrence(thursday, "monthly"))
        assertEquals(thursday.plusYears(1), nextReminderOccurrence(thursday, "yearly"))
    }

    @Test
    fun `weekdays skip saturday and sunday`() {
        val friday = LocalDateTime.of(2026, 7, 31, 9, 15)
        assertEquals(LocalDateTime.of(2026, 8, 3, 9, 15), nextReminderOccurrence(friday, "weekdays"))
    }

    @Test
    fun `missed occurrences advance directly beyond requested time`() {
        val after = LocalDateTime.of(2026, 8, 20, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 27, 9, 15),
            nextReminderOccurrence(thursday, "weekly", after),
        )
    }

    @Test
    fun `unknown recurrence is not scheduled`() {
        assertNull(nextReminderOccurrence(thursday, "sometimes"))
    }

    @Test
    fun `custom weekly recurrence uses selected weekdays and interval`() {
        val stored = CustomReminderRecurrence(
            interval = 2,
            unit = ReminderRecurrenceUnit.WEEK,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            start = thursday,
        ).storedValue()

        assertEquals(stored, normalizeReminderRecurrence(stored))
        assertEquals(
            LocalDateTime.of(2026, 8, 10, 9, 15),
            nextReminderOccurrence(thursday, stored),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 13, 9, 15),
            nextReminderOccurrence(thursday, stored, LocalDateTime.of(2026, 8, 10, 10, 0)),
        )
    }

    @Test
    fun `custom recurrence respects date and count endings`() {
        val until = CustomReminderRecurrence(
            interval = 1,
            unit = ReminderRecurrenceUnit.DAY,
            weekdays = emptySet(),
            start = thursday,
            endDate = LocalDate.of(2026, 7, 31),
        ).storedValue()
        assertEquals(thursday.plusDays(1), nextReminderOccurrence(thursday, until))
        assertNull(nextReminderOccurrence(thursday, until, thursday.plusDays(1)))

        val twice = CustomReminderRecurrence(
            interval = 1,
            unit = ReminderRecurrenceUnit.DAY,
            weekdays = emptySet(),
            start = thursday,
            repeatCount = 2,
        ).storedValue()
        assertEquals(thursday.plusDays(2), nextReminderOccurrence(thursday, twice, thursday.plusDays(1)))
        assertNull(nextReminderOccurrence(thursday, twice, thursday.plusDays(2)))
    }
}

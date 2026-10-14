package ru.nezabudka.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class SpokenReminderRecurrenceTest {
    @Test fun `neuter weekdays and working day variants follow calendar rules`() {
        for (phrase in listOf("по рабочим дням", "каждый будний день")) {
            val result = parseRecurringReminder("Напомни $phrase утром проверить замок", now, parser)
            assertEquals("weekdays", result.recurrence)
            assertEquals(now.toLocalDate().plusDays(1).atTime(9, 0), result.temporal.remindAt)
            assertEquals("проверить замок", result.temporal.cleanText)
        }
        val sunday = parseRecurringReminder("Напомни каждое воскресенье утром проверить замок", now, parser)
        assertEquals(setOf(DayOfWeek.SUNDAY), CustomReminderRecurrence.fromStored(sunday.recurrence)!!.weekdays)
        val weekdaysMorning = parseRecurringReminder("Напомни по рабочим дням по утрам проверить замок", now, parser)
        assertEquals("weekdays", weekdaysMorning.recurrence)
        assertEquals("проверить замок", weekdaysMorning.temporal.cleanText)
        val dailyMorning = parseRecurringReminder("Напомни каждый день по утрам проверить замок", now, parser)
        assertEquals("daily", dailyMorning.recurrence)
        assertEquals("проверить замок", dailyMorning.temporal.cleanText)
    }

    @Test fun `daily time of day families use configured times and preserve payload`() {
        val configured = RussianTemporalParser { mapOf("утром" to java.time.LocalTime.of(7, 15),
            "вечером" to java.time.LocalTime.of(21, 10), "ночью" to java.time.LocalTime.of(0, 30)) }
        mapOf("каждое утро" to java.time.LocalTime.of(7, 15), "по утрам" to java.time.LocalTime.of(7, 15),
            "каждый вечер" to java.time.LocalTime.of(21, 10), "по вечерам" to java.time.LocalTime.of(21, 10),
            "каждую ночь" to java.time.LocalTime.of(0, 30), "по ночам" to java.time.LocalTime.of(0, 30)).forEach { (phrase, time) ->
            val result = parseRecurringReminder("Напомни $phrase осмотреть аквариум", now, configured)
            assertEquals("daily", result.recurrence)
            assertEquals("осмотреть аквариум", result.temporal.cleanText)
            val day = if (time > now.toLocalTime()) now.toLocalDate() else now.toLocalDate().plusDays(1)
            assertEquals(day.atTime(time), result.temporal.remindAt)
            assertNotNull(configured.namedTimePhraseUsed("Напомни $phrase осмотреть аквариум"))
        }
    }

    @Test fun `explicit clock overrides daily named time and repeated rules are not silently dropped`() {
        val result = parseRecurringReminder("Напомни каждое   утро в шесть двадцать осмотреть аквариум", now, parser)
        assertEquals(now.toLocalDate().plusDays(1).atTime(6, 20), result.temporal.remindAt)
        assertEquals("осмотреть аквариум", result.temporal.cleanText)
        assertTrue(runCatching { parseRecurringReminder("Напомни каждое утро и каждый вечер осмотреть аквариум", now, parser) }
            .exceptionOrNull() is CommandValidationException)
    }

    @Test fun `compound spoken intervals work and hourly repetition is not silently dropped`() {
        val rule = CustomReminderRecurrence.fromStored(parseRecurringReminder("Напомни каждые двадцать три дня завтра в девять проверить фильтр", now, parser).recurrence)!!
        assertEquals(23, rule.interval)
        assertEquals(rule.start.plusDays(23), nextReminderOccurrence(rule.start, rule.storedValue()))
        assertTrue(runCatching { parseRecurringReminder("Напомни каждый час проверить фильтр", now, parser) }.exceptionOrNull() is CommandValidationException)
    }
    private val now = LocalDateTime.of(2026, 10, 11, 8, 0) // Sunday
    private val parser = RussianTemporalParser()

    @Test fun `simple repeats are stored and removed from the task`() {
        mapOf("каждый день" to "daily", "по будням" to "weekdays", "каждую неделю" to "weekly", "ежемесячно" to "monthly", "каждый год" to "yearly").forEach { (spoken, stored) ->
            val result = parseRecurringReminder("Напомни $spoken в девять утра проверить крепления двери", now, parser)
            assertEquals(stored, result.recurrence)
            assertEquals("проверить крепления двери", result.temporal.cleanText)
            assertEquals(if (stored == "weekdays") now.toLocalDate().plusDays(1).atTime(9, 0) else now.toLocalDate().atTime(9, 0), result.temporal.remindAt)
        }
    }

    @Test fun `selected weekdays use the existing scheduler format`() {
        val result = parseRecurringReminder("Напомни по понедельникам и средам в девять утра проверить крепления двери", now, parser)
        val rule = CustomReminderRecurrence.fromStored(result.recurrence)!!
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), rule.weekdays)
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 0), result.temporal.remindAt)
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 0), nextReminderOccurrence(rule.start, result.recurrence))
        assertEquals("проверить крепления двери", result.temporal.cleanText)
    }

    @Test fun `interval and total repeat count agree with the scheduler`() {
        val result = parseRecurringReminder("Напомни каждые три дня завтра в десять двадцать пять полить рассаду всего три раза", now, parser)
        val rule = CustomReminderRecurrence.fromStored(result.recurrence)!!
        assertEquals(3, rule.interval)
        assertEquals(2, rule.repeatCount)
        assertEquals("полить рассаду", result.temporal.cleanText)
        val second = nextReminderOccurrence(rule.start, result.recurrence)!!
        val third = nextReminderOccurrence(second, result.recurrence)!!
        assertEquals(rule.start.plusDays(3), second)
        assertEquals(rule.start.plusDays(6), third)
        assertNull(nextReminderOccurrence(third, result.recurrence))
    }

    @Test fun `draft recurrence is reanchored after date clarification`() {
        val draft = parseRecurringReminder("Напомни каждый вторник полить рассаду", now, parser)
        assertNull(draft.temporal.remindAt)
        val (start, stored) = reanchorSpokenRecurrence(draft.recurrence, now.toLocalDate().plusDays(1).atTime(10, 0))
        assertEquals(LocalDateTime.of(2026, 10, 13, 10, 0), start)
        assertEquals(start, CustomReminderRecurrence.fromStored(stored)!!.start)
    }

    @Test fun `end date is separate from the starting date`() {
        val result = parseRecurringReminder("Напомни каждый день завтра в девять полить рассаду до 20 октября", now, parser)
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 0), result.temporal.remindAt)
        assertEquals(now.toLocalDate().withDayOfMonth(20), CustomReminderRecurrence.fromStored(result.recurrence)!!.endDate)
        assertEquals("полить рассаду", result.temporal.cleanText)
    }
}

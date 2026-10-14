package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RussianTemporalParserTest {
    @Test fun `future period in task content does not replace an earlier explicit reminder date`() {
        val result = parser.parse("Напомни завтра обсудить планы на следующий год", now)
        assertEquals(now.plusDays(1), result.remindAt)
        assertEquals("обсудить планы на следующий год", result.cleanText)
        assertNull(parser.pendingCalendarRange("Напомни завтра обсудить планы на следующий год", now))
    }

    @Test fun `parts of next month remain ranges instead of becoming tomorrow at given time`() {
        val parsed = parser.parse("Напомни в конце следующего месяца в восемь заменить коврик", now)
        assertNull(parsed.remindAt)
        assertEquals("заменить коврик", parsed.cleanText)
        val range = parser.pendingCalendarRange("в конце следующего месяца", now)!!
        assertEquals(LocalDate.of(2026, 8, 21), range.start)
        assertEquals(LocalDateTime.of(2026, 8, 25, 10, 20), parser.parseDateAnswer("25 числа", now, range, null).remindAt)
    }

    @Test fun `vague future periods do not invent a date even when a time is given`() {
        for (phrase in listOf("в следующем году", "на будущий год", "в следующем месяце", "следующего месяца")) {
            val result = parser.parse("Напомни $phrase вечером заменить коврик", now)
            assertNull(result.remindAt)
            assertEquals("заменить коврик", result.cleanText)
            assertTrue(parser.pendingCalendarRange(phrase, now) != null)
        }
    }

    @Test fun `explicit calendar fragments inherit next month or year`() {
        assertEquals(LocalDateTime.of(2027, 2, 5, 19, 0),
            parser.parse("Напомни пятого февраля следующего года вечером заменить коврик", now).remindAt)
        assertEquals(LocalDateTime.of(2026, 8, 18, 9, 0),
            parser.parse("Напомни в следующем месяце восемнадцатого числа утром заменить коврик", now).remindAt)
        val december = LocalDateTime.of(2026, 12, 31, 22, 0)
        assertEquals(LocalDateTime.of(2027, 1, 12, 8, 0),
            parser.parse("Напомни в следующем месяце 12 числа в восемь заменить коврик", december).remindAt)
    }

    @Test fun `next calendar periods filter lists without changing exact date filters`() {
        assertEquals(CalendarDateRange(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1)),
            parser.extractDateRange("Покажи напоминания в следующем году", now))
        assertEquals(CalendarDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)),
            parser.extractDateRange("Покажи напоминания в следующем месяце", now))
        assertNull(parser.extractDateRange("в следующем году 5 февраля", now))
        assertEquals(LocalDate.of(2027, 2, 5), parser.extractDate("в следующем году 5 февраля", now))
    }

    @Test fun `clarification inherits calendar scope and previous clock but not relative date meaning`() {
        val range = parser.pendingCalendarRange("в следующем году", now)!!
        assertEquals(LocalDateTime.of(2027, 2, 5, 19, 0),
            parser.parseDateAnswer("5 февраля", now, range, LocalTime.of(19, 0)).remindAt)
        assertEquals(now.toLocalDate().plusDays(1).atTime(19, 0),
            parser.parseDateAnswer("завтра", now, range, LocalTime.of(19, 0)).remindAt)
        assertNull(parser.parseDateAnswer("в десять", now, range, LocalTime.of(19, 0)).remindAt)
        assertTrue(runCatching { parser.parse("Напомни в следующем месяце 5 февраля заменить коврик", now) }
            .exceptionOrNull() is CommandValidationException)
    }

    private val parser = RussianTemporalParser()
    private val now = LocalDateTime.of(2026, 7, 14, 10, 20) // Tuesday

    @Test fun `thirty minutes are calculated by code and removed`() {
        val result = parser.parse("Напомни через 30 минут выключить духовку", now)
        assertEquals(LocalDateTime.of(2026, 7, 14, 10, 50), result.remindAt)
        assertEquals("выключить духовку", result.cleanText)
    }

    @Test fun `day after tomorrow and spoken afternoon hour`() {
        val result = parser.parse("Напомни позвонить Свете послезавтра в три часа дня", now)
        assertEquals(LocalDateTime.of(2026, 7, 16, 15, 0), result.remindAt)
        assertEquals("позвонить Свете", result.cleanText)
    }

    @Test fun `next Wednesday is in next calendar week`() {
        val result = parser.parse("Напомни проверить отчёт в следующую среду в 3 часа дня", now)
        assertEquals(LocalDateTime.of(2026, 7, 22, 15, 0), result.remindAt)
        assertEquals("проверить отчёт", result.cleanText)
    }

    @Test fun `bare weekday means nearest future weekday`() {
        val result = parser.parse("Напомни встречу в понедельник", now)
        assertEquals(LocalDateTime.of(2026, 7, 20, 10, 20), result.remindAt)
        assertEquals("встречу", result.cleanText)
    }

    @Test fun `time without date moves to tomorrow when passed`() {
        assertEquals(LocalDateTime.of(2026, 7, 15, 9, 0), parser.parse("Напомни зарядку утром", now).remindAt)
    }

    @Test fun `calendar month clamps last day`() {
        val jan31 = LocalDateTime.of(2024, 1, 31, 12, 0)
        assertEquals(LocalDateTime.of(2024, 2, 29, 12, 0), parser.addDelay(jan31, 1.0, DelayUnit.MONTH))
    }

    @Test fun `calendar year clamps leap day`() {
        val leapDay = LocalDateTime.of(2024, 2, 29, 12, 0)
        assertEquals(LocalDateTime.of(2025, 2, 28, 12, 0), parser.addDelay(leapDay, 1.0, DelayUnit.YEAR))
    }

    @Test fun `week month and year delays are supported`() {
        assertEquals(now.plusWeeks(1), parser.parse("Напомни через неделю проверить", now).remindAt)
        assertEquals(now.plusMonths(1), parser.parse("Напомни через месяц проверить", now).remindAt)
        assertEquals(now.plusYears(1), parser.parse("Напомни через год проверить", now).remindAt)
        assertEquals(now.plusHours(3), parser.parse("Напомни через три часа проверить", now).remindAt)
    }

    @Test fun `calendar delay can be used as list date`() {
        assertEquals(now.toLocalDate().plusDays(4), parser.extractDate("Что у меня через четыре дня?", now))
        assertEquals(now.toLocalDate().plusWeeks(2), parser.extractDate("Какие планы через две недели?", now))
    }

    @Test fun `month parts use stable ten day ranges`() {
        assertEquals(
            CalendarDateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 11)),
            parser.extractDateRange("Какие планы в начале месяца?", now),
        )
        assertEquals(
            CalendarDateRange(LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 21)),
            parser.extractDateRange("Что у меня в середине месяца?", now),
        )
        assertEquals(
            CalendarDateRange(LocalDate.of(2026, 7, 21), LocalDate.of(2026, 8, 1)),
            parser.extractDateRange("Какие планы ближе к концу месяца?", now),
        )
    }

    @Test fun `month parts support next and named months`() {
        assertEquals(
            CalendarDateRange(LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 21)),
            parser.extractDateRange("Что запланировано в середине следующего месяца?", now),
        )
        assertEquals(
            CalendarDateRange(LocalDate.of(2027, 2, 21), LocalDate.of(2027, 3, 1)),
            parser.extractDateRange("Какие планы в конце февраля 2027?", now),
        )
    }

    @Test fun `explicit morning time is applied after week delay`() {
        val result = parser.parse("Напомни позвонить через неделю в 8 утра", now)
        assertEquals(LocalDateTime.of(2026, 7, 21, 8, 0), result.remindAt)
        assertEquals("позвонить", result.cleanText)
    }

    @Test fun `common time forms are supported`() {
        assertEquals(LocalTime.of(15, 0), parser.extractTime("в три часа дня"))
        assertEquals(LocalTime.of(15, 0), parser.extractTime("в 3 часа дня"))
        assertEquals(LocalTime.of(19, 30), parser.extractTime("в 19:30"))
        assertEquals(LocalTime.of(14, 0), parser.extractTime("днём"))
        assertEquals(LocalTime.MIDNIGHT, parser.extractTime("ночью"))
    }

    @Test fun `configured evening time is used for reminder`() {
        val configured = RussianTemporalParser { mapOf("вечером" to LocalTime.of(20, 15)) }
        val result = configured.parse("Напомни позвонить Кате вечером", now)
        assertEquals(LocalDateTime.of(2026, 7, 14, 20, 15), result.remindAt)
        assertEquals("позвонить Кате", result.cleanText)
    }

    @Test fun `custom named time is recognized and removed from reminder text`() {
        val configured = RussianTemporalParser { mapOf("после работы" to LocalTime.of(18, 30)) }
        val result = configured.parse("Напомни купить продукты после работы", now)
        assertEquals(LocalDateTime.of(2026, 7, 14, 18, 30), result.remindAt)
        assertEquals("купить продукты", result.cleanText)
    }

    @Test fun `no temporal expression keeps null date`() {
        val result = parser.parse("Напомни купить молоко", now)
        assertNull(result.remindAt)
        assertEquals("купить молоко", result.cleanText)
    }

    @Test fun `day of month becomes upcoming date and is removed from text`() {
        val result = parser.parse("Напомни 28 числа оплатить интернет", now)
        assertEquals(LocalDateTime.of(2026, 7, 28, 10, 20), result.remindAt)
        assertEquals("оплатить интернет", result.cleanText)
    }

    @Test fun `past day of month moves to next valid month`() {
        val result = parser.parse("Создай мне напоминание на 5 число проверить счёт", now)
        assertEquals(LocalDateTime.of(2026, 8, 5, 10, 20), result.remindAt)
        assertEquals("проверить счёт", result.cleanText)
    }

    @Test fun `spoken ordinal day of month is supported`() {
        val result = parser.parse("Напомни двадцать восьмого числа оплатить интернет", now)
        assertEquals(LocalDateTime.of(2026, 7, 28, 10, 20), result.remindAt)
        assertEquals("оплатить интернет", result.cleanText)
    }

    @Test fun `numeric and named calendar dates are used and removed`() {
        val numeric = parser.parse("Напомни 28.07 отправить отчёт", now)
        val named = parser.parse("Напомни 28 июля отправить отчёт", now)
        assertEquals(LocalDateTime.of(2026, 7, 28, 10, 20), numeric.remindAt)
        assertEquals("отправить отчёт", numeric.cleanText)
        assertEquals(numeric.remindAt, named.remindAt)
        assertEquals(numeric.cleanText, named.cleanText)
    }

    @Test fun `spoken ordinal with named month and year is used and removed`() {
        val result = parser.parse("Напомни шестнадцатого августа 2027 отправить отчёт", now)

        assertEquals(LocalDateTime.of(2027, 8, 16, 10, 20), result.remindAt)
        assertEquals("отправить отчёт", result.cleanText)
    }

    @Test fun `different explicit reminder phrases are recognized`() {
        listOf(
            "Напомни проверить",
            "Создай напоминание проверить",
            "Создай мне напоминание проверить",
            "Сделай напоминание проверить",
            "Установи напоминание проверить",
            "Запланируй напоминание проверить",
        ).forEach { assertEquals(true, parser.isReminderCreationCommand(it)) }
    }

    @Test fun `reminder marker can follow date and time`() {
        val text = "Завтра в 8 напомни позвонить"
        val result = parser.parse(text, now)

        assertTrue(parser.isReminderCreationCommand(text))
        assertEquals(LocalDateTime.of(2026, 7, 15, 8, 0), result.remindAt)
        assertEquals("позвонить", result.cleanText)
    }

    @Test fun `infinitive inside a question is not a creation marker`() {
        assertFalse(parser.isReminderCreationCommand("Покажи, что я просил напомнить вчера"))
    }

}

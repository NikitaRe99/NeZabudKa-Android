package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class SpokenClockTest {
    private val parser = RussianTemporalParser()
    private val now = LocalDateTime.of(2026, 10, 11, 8, 0)

    @Test fun `split day after tomorrow consumes the whole date and advances two days`() {
        listOf("послезавтра", "после завтра", "после, завтра", "После,завтра", "после ,  завтра").forEach { day ->
            val result = parser.parse("Напомни $day в десять двадцать пять проверить фильтр", now)
            assertEquals(day, now.toLocalDate().plusDays(2).atTime(10, 25), result.remindAt)
            assertEquals(day, "проверить фильтр", result.cleanText)
            assertEquals(day, now.toLocalDate().plusDays(2), parser.extractDate("Покажи напоминания $day", now))
        }
        assertEquals(now.plusDays(1), parser.parse("завтра", now).remindAt)
        assertEquals(null, parser.extractDate("после завтрашней встречи", now))
        assertEquals(null, parser.extractDate("после завтрака", now))
    }

    @Test fun `compound minutes are consumed completely`() {
        listOf("двадцать пять" to 25, "тридцать пять" to 35, "сорок пять" to 45, "пятьдесят девять" to 59).forEach { (words, minute) ->
            val result = parser.parse("Напомни завтра в десять $words повернуть горшок с базиликом", now)
            assertEquals(now.toLocalDate().plusDays(1).atTime(10, minute), result.remindAt)
            assertEquals("повернуть горшок с базиликом", result.cleanText)
        }
    }

    @Test fun `compound hours do not become minutes`() {
        assertEquals(LocalTime.of(21, 35), parser.extractTime("в двадцать один тридцать пять"))
        assertEquals(LocalTime.of(22, 5), parser.extractTime("в двадцать два ноль пять"))
        assertEquals(LocalTime.of(23, 0), parser.extractTime("в двадцать три"))
        assertEquals(LocalTime.of(15, 25), parser.extractTime("в три двадцать пять дня"))
        assertEquals(LocalTime.of(10, 25), parser.extractTime("в 10:25"))
    }

    @Test fun `next three days is a bounded calendar range`() {
        assertEquals(CalendarDateRange(now.toLocalDate(), now.toLocalDate().plusDays(3)), parser.extractDateRange("Покажи чем заняты мои ближайшие три дня", now))
    }
}

package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class AfternoonTimeAliasTest {
    private val now = LocalDateTime.of(2026, 8, 8, 12, 0)

    @Test fun `after lunch uses configured afternoon time and is removed from payload`() {
        val parser = RussianTemporalParser { mapOf("днём" to LocalTime.of(15, 30)) }
        val result = parser.parse("В четверг после обеда получить справку", now)
        assertEquals(LocalDateTime.of(2026, 8, 13, 15, 30), result.remindAt)
        assertEquals("получить справку", result.cleanText)
    }

    @Test fun `explicit after lunch setting overrides the afternoon alias`() {
        val parser = RussianTemporalParser { mapOf("днём" to LocalTime.of(15, 30), "после обеда" to LocalTime.of(16, 45)) }
        assertEquals(LocalDateTime.of(2026, 8, 13, 16, 45), parser.parse("В четверг после обеда получить справку", now).remindAt)
    }
}

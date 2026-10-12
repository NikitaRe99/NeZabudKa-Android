package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedEntryTextRulesTest {
    @Test fun internalPunctuationAndNumbersArePreserved() {
        assertEquals("Проверить: 10:25, 12.5 — это важно.", normalizeSavedEntryText(" ; проверить: 10:25, 12.5 — это важно."))
        assertEquals("-5 Градусов", normalizeSavedEntryText("  -5 градусов"))
        assertEquals("Ёлка", normalizeSavedEntryText(" … ёлка"))
    }
}

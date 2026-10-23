package ru.nezabudka.app.domain

import org.junit.Assert.*
import org.junit.Test

class NotePaletteRulesTest {
    @Test fun additionalColorsKeepTheirPositionsAndSurviveNormalization() {
        val base = defaultCustomTheme("light")
        val colors = base.notePalette + listOf("123abc", "#fedcba", "invalid", "#112233")
        val result = normalizeNotePalette(colors, base.notePalette, base.noteDefault)
        assertEquals(15, result.size)
        assertEquals(base.notePalette, result.take(11))
        assertEquals(listOf("#123ABC", "#FEDCBA", base.noteDefault, "#112233"), result.drop(11))
    }
    @Test fun olderShortPalettesAreCompletedWithoutMovingStoredIndices() {
        val base = defaultCustomTheme("dark")
        val result = normalizeNotePalette(listOf("#123456"), base.notePalette, base.noteDefault)
        assertEquals("#123456", result.first())
        assertEquals(base.notePalette.drop(1), result.drop(1))
        assertEquals(MAX_NOTE_PALETTE_SIZE, normalizeNotePalette(List(100) { "#123456" }, base.notePalette, base.noteDefault).size)
    }
}

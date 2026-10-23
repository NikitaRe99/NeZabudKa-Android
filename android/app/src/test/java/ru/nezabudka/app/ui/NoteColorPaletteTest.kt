package ru.nezabudka.app.ui

import ru.nezabudka.app.ui.screens.notePaletteIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import ru.nezabudka.app.ui.screens.isSelectedNoteColor
import org.junit.Test

class NoteColorPaletteTest {
    @Test
    fun `stored note colors keep palette position across themes`() {
        assertEquals(0, notePaletteIndex("#F28B82"))
        assertEquals(0, notePaletteIndex("#6F3B38"))
        assertEquals(7, notePaletteIndex("theme-note:7"))
        assertEquals(14, notePaletteIndex("theme-note:14"))
        assertNull(notePaletteIndex("theme-note:-1"))
        assertFalse(isSelectedNoteColor("#123456", "#987654"))
        assertNull(notePaletteIndex("#123456"))
    }
}

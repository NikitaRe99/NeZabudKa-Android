package ru.nezabudka.app.ui.screens

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nezabudka.app.domain.Note

class NoteManualOrderTest {
    @Test
    fun moved_note_uses_next_visible_note_as_anchor() {
        assertEquals(4L, noteMoveBeforeId(listOf(1, 2, 3, 4), listOf(1, 3, 4), 3))
    }

    @Test
    fun moved_note_at_group_end_stays_before_next_hidden_note() {
        assertEquals(2L, noteMoveBeforeId(listOf(1, 2, 3, 4), listOf(1, 3), 3))
    }

    @Test
    fun single_visible_note_has_no_anchor() {
        assertNull(noteMoveBeforeId(listOf(1, 2, 3), listOf(3), 3))
    }

    @Test
    fun pending_order_is_not_reused_after_switching_folder() {
        assertTrue(sameNoteIds(listOf(note(2), note(1)), listOf(note(1), note(2))))
        assertFalse(sameNoteIds(listOf(note(2), note(1)), listOf(note(3))))
    }

    private fun note(id: Long) = Note(
        id = id,
        folderId = null,
        folderName = null,
        text = "note $id",
        createdAt = LocalDateTime.of(2026, 8, 12, 12, 0),
    )
}

package ru.nezabudka.app.ui.screens

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nezabudka.app.domain.Note

class NoteListProjectionTest {
    @Test fun cachedProjectionTracksSearchFolderAndSourceChanges() {
        val projector = NoteListProjector()
        val notes = listOf(note(2).copy(folderIds = listOf(7)), note(1).copy(title = "Поездка"))
        assertEquals(listOf(1L, 2L), projector.project(notes, NoteSort.MANUAL, null, NoteSection.ACTIVE, "").map { it.id })
        assertEquals(listOf(1L), projector.project(notes, NoteSort.MANUAL, null, NoteSection.ACTIVE, "поезд").map { it.id })
        assertEquals(listOf(2L), projector.project(notes, NoteSort.MANUAL, 7, NoteSection.ACTIVE, "").map { it.id })
        assertEquals(emptyList<Long>(), projector.project(notes.map { it.copy(trashed = true) }, NoteSort.MANUAL, null, NoteSection.ACTIVE, "").map { it.id })
    }
    private fun note(id: Long) = Note(id, null, null, "Заметка $id", LocalDateTime.of(2026, 10, 11, 12, 0))

    @Test fun sections_preserve_archive_trash_and_folder_rules() {
        val active = note(1).copy(folderIds = listOf(7))
        val archived = note(2).copy(archived = true)
        val trashed = note(3).copy(archived = true, trashed = true)
        val notes = listOf(active, archived, trashed, note(4))
        assertEquals(listOf(active), filterNotesForDisplay(notes, 7, NoteSection.ACTIVE, ""))
        assertEquals(listOf(archived), filterNotesForDisplay(notes, 7, NoteSection.ARCHIVE, ""))
        assertEquals(listOf(trashed), filterNotesForDisplay(notes, 7, NoteSection.TRASH, ""))
    }

    @Test fun search_reads_full_note_even_when_card_preview_is_shortened() {
        val longNote = note(1).copy(text = "а".repeat(100_000) + "Редкий запрос")
        assertEquals(listOf(longNote), filterNotesForDisplay(listOf(longNote), null, NoteSection.ACTIVE, "редкий"))
        assertEquals(2_001, noteCardPreview(longNote.text).length)
    }

    @Test fun search_matches_title_and_folder_without_changing_order() {
        val titleMatch = note(2).copy(title = "Поездка")
        val folderMatch = note(1).copy(folderNames = listOf("Поездки"))
        assertEquals(listOf(titleMatch, folderMatch),
            filterNotesForDisplay(listOf(titleMatch, folderMatch), null, NoteSection.ACTIVE, "ПОЕЗД"))
    }

    @Test fun manual_order_has_deterministic_ties() {
        val notes = listOf(note(3).copy(manualOrder = 1), note(2).copy(manualOrder = 1), note(1).copy(manualOrder = 2))
        assertEquals(listOf(2L, 3L, 1L), sortNotesForDisplay(notes, NoteSort.MANUAL).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), notes.map { it.id })
    }

    @Test fun preview_preserves_short_text_and_does_not_split_emoji() {
        assertEquals("Короткая заметка", noteCardPreview("Короткая заметка"))
        val preview = noteCardPreview("а".repeat(1_999) + "\uD83C\uDF3B" + "а".repeat(100))
        assertEquals(2_000, preview.length)
        assertTrue(preview.endsWith("а…"))
    }
}

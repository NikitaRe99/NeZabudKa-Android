package ru.nezabudka.app.data

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ImportDuplicateIndexTest {
    private val time = LocalDateTime.parse("2026-10-14T09:00:00")
    private val note = NoteEntity(id = 12, folderId = 7, text = "Текст", title = "Заголовок", createdAt = time)
    private val reminder = ReminderEntity(id = 15, text = "Позвонить", remindAt = time,
        recurrence = "monthly", createdAt = time)

    @Test fun `repeated note import ignores new ids timestamps order and folder list order`() {
        val index = ImportDuplicateIndex()
        assertTrue(index.addNote(note, listOf(7, 8)))
        assertFalse(index.addNote(note.copy(id = 0, createdAt = time.plusDays(1), updatedAt = time.plusDays(1), manualOrder = 99), listOf(8, 7, 8)))
        assertFalse(index.addNote(note, listOf(7, 8)))
    }

    @Test fun `different note content membership or state remains importable`() {
        val changes = listOf(note.copy(text = "текст"), note.copy(title = "Другой заголовок"),
            note.copy(pinned = true), note.copy(archived = true), note.copy(trashed = true),
            note.copy(color = "theme-note:1"), note.copy(folderId = 8))
        changes.forEach { changed ->
            val index = ImportDuplicateIndex()
            index.addNote(note, listOf(7, 8))
            assertTrue(index.addNote(changed, listOf(7, 8)))
        }
        val index = ImportDuplicateIndex()
        index.addNote(note, listOf(7))
        assertTrue(index.addNote(note, listOf(7, 8)))
    }

    @Test fun `identical reminder ignores source identifier and technical creation order`() {
        val index = ImportDuplicateIndex()
        assertTrue(index.addReminder(reminder))
        assertFalse(index.addReminder(reminder.copy(id = 0, createdAt = time.plusDays(4), manualOrder = 999)))
    }

    @Test fun `same reminder words with another date repeat or state are preserved`() {
        listOf(reminder.copy(remindAt = time.plusHours(1)), reminder.copy(recurrence = "daily"),
            reminder.copy(text = "Позвонить!"), reminder.copy(favorite = true),
            reminder.copy(status = "completed", completedAt = time), reminder.copy(completedAt = time)
        ).forEach { changed ->
            val index = ImportDuplicateIndex()
            index.addReminder(reminder)
            assertTrue(index.addReminder(changed))
        }
    }
}

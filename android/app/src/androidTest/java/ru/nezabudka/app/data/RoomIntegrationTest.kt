package ru.nezabudka.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ru.nezabudka.app.domain.Reminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RoomIntegrationTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val clock = Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC)
    @After fun close() = db.close()

    @Test fun reminders_are_sorted_and_can_be_updated_and_deleted() = runBlocking {
        val repo = RoomReminderRepository(db.reminderDao(), clock)
        repo.create("без даты", null, null)
        val late = repo.create("позже", LocalDateTime.of(2026, 7, 14, 15, 0), null)
        val early = repo.create("раньше", LocalDateTime.of(2026, 7, 14, 11, 0), null)
        assertEquals(listOf("Раньше", "Позже", "Без даты"), repo.list().map { it.text })
        repo.update(early.id, "самое раннее", LocalDateTime.of(2026, 7, 14, 10, 30))
        repo.delete(late.id)
        assertEquals(listOf("Самое раннее", "Без даты"), repo.list().map { it.text })
    }

    @Test fun note_folder_is_reused_case_insensitively() = runBlocking {
        val repo = RoomNotesRepository(db, clock)
        val first = repo.create("Проект X", "проверить API")
        val second = repo.create("проект x", "написать тест")
        assertEquals(first.folderId, second.folderId)
        assertEquals(first.folderId, repo.findFolder("пРОЕКТ x")?.id)
        assertEquals(1, repo.observeFolders().first().size)
        assertEquals(2, repo.observeNotes(requireNotNull(first.folderId)).first().size)
    }

    @Test fun folders_can_be_renamed_and_reordered() = runBlocking {
        val repo = RoomNotesRepository(db, clock)
        val first = repo.createFolder("Первая")
        val second = repo.createFolder("Вторая")
        val third = repo.createFolder("Третья")
        repo.moveFolder(third.id, first.id)
        repo.renameFolder(second.id, "Работа")
        assertEquals(listOf("Третья", "Первая", "Работа"), repo.observeFolders().first().map { it.name })
    }

    @Test fun note_title_is_saved_separately_and_title_only_note_is_allowed() = runBlocking {
        val repo = RoomNotesRepository(db, clock)
        val created = repo.save(
            id = null,
            folderNames = emptyList(),
            title = "План поездки",
            text = "",
            pinned = false,
            archived = false,
            color = "theme-note:3",
        )
        assertEquals("План поездки", created.title)
        assertEquals("", created.text)

        val updated = repo.save(
            id = created.id,
            folderNames = emptyList(),
            title = "Поездка в августе",
            text = "Купить билеты",
            pinned = true,
            archived = false,
            color = "theme-note:3",
        )
        assertEquals("Поездка в августе", updated.title)
        assertEquals("Купить билеты", updated.text)
        assertEquals(true, updated.pinned)
    }

    @Test fun deleted_folder_undo_restores_note_links() = runBlocking {
        val repo = RoomNotesRepository(db, clock)
        val note = repo.create("Проекты", "Проверить отчёт")
        val snapshot = requireNotNull(repo.deleteFolder(requireNotNull(note.folderId)))
        assertEquals(emptyList<String>(), repo.observeFolders().first().map { it.name })
        repo.restoreFolder(snapshot)
        assertEquals(listOf("Проекты"), repo.observeFolders().first().map { it.name })
        assertEquals(listOf("Проекты"), repo.observeAll().first().single().folderNames)
    }

    @Test fun batch_note_actions_update_all_selected_notes() = runBlocking {
        val repo = RoomNotesRepository(db, clock)
        val first = repo.create(null, "Первая")
        val second = repo.create(null, "Вторая")
        val ids = listOf(first.id, second.id)
        repo.setPinned(ids, true)
        repo.setColor(ids, "#FFF475")
        repo.setFolderMembership(ids, "Общее", true)
        var values = repo.observeAll().first().sortedBy { it.id }
        assertEquals(listOf(true, true), values.map { it.pinned })
        assertEquals(listOf("#FFF475", "#FFF475"), values.map { it.color })
        assertEquals(listOf(listOf("Общее"), listOf("Общее")), values.map { it.folderNames })
        repo.setFolderMembership(ids, "Общее", false)
        values = repo.observeAll().first().sortedBy { it.id }
        assertEquals(listOf(emptyList<String>(), emptyList()), values.map { it.folderNames })
    }

    @Test fun reminder_can_be_favorited_completed_reordered_and_completed_deleted() = runBlocking {
        val repo = RoomReminderRepository(db.reminderDao(), clock)
        val first = repo.create("первая", null, null)
        val second = repo.create("вторая", null, null)
        repo.setFavorite(first.id, true)
        repo.move(second.id, first.id)
        repo.setCompleted(first.id, true)
        val values = repo.observeAll().first()
        assertEquals(true, values.first { it.id == first.id }.favorite)
        assertEquals("completed", values.first { it.id == first.id }.status)
        assertEquals(second.id, values.filter { it.status == "active" }.first().id)
        repo.deleteCompleted()
        assertEquals(listOf(second.id), repo.observeAll().first().map { it.id })
    }

    @Test fun editing_a_completed_reminder_reactivates_and_saves_it() = runBlocking {
        val repo = RoomReminderRepository(db.reminderDao(), clock)
        val original = repo.create("позвонить", LocalDateTime.of(2026, 7, 14, 11, 0), null)
        repo.setCompleted(original.id, true)

        val newTime = LocalDateTime.of(2026, 7, 15, 18, 30)
        val updated = repo.saveEditor(
            original.id,
            "позвонить Кате",
            newTime,
            recurrence = "weekly",
            favorite = true,
            completed = false,
        )

        assertEquals("active", updated.status)
        assertEquals(null, updated.completedAt)
        assertEquals(newTime, updated.remindAt)
        assertEquals("Позвонить Кате", updated.text)
        assertEquals("weekly", updated.recurrence)
        assertEquals(true, updated.favorite)
    }

    @Test fun rescheduling_a_completed_reminder_returns_it_to_active() = runBlocking {
        val repo = RoomReminderRepository(db.reminderDao(), clock)
        val original = repo.create("встреча", LocalDateTime.of(2026, 7, 14, 11, 0), null)
        repo.setCompleted(original.id, true)

        val newTime = LocalDateTime.of(2026, 7, 16, 12, 45)
        val updated = repo.reschedule(original.id, original.text, newTime)

        assertEquals("active", updated.status)
        assertEquals(null, updated.completedAt)
        assertEquals(newTime, updated.remindAt)
    }
}

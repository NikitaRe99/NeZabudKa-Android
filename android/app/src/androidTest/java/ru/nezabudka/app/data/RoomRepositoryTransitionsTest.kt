package ru.nezabudka.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoryTransitionsTest {
    private lateinit var database: AppDatabase
    private val clock = Clock.fixed(Instant.parse("2026-07-26T08:00:00Z"), ZoneOffset.UTC)

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun note_moves_between_active_archive_and_trash_without_data_loss() = runBlocking {
        val repository = RoomNotesRepository(database, clock)
        val created = repository.create("Работа", "Подготовить отчёт")

        repository.setArchived(created.id, true)
        val archived = repository.observeAll().first().single()
        assertTrue(archived.archived)
        assertFalse(archived.trashed)

        repository.setTrashed(created.id, true)
        val trashed = repository.observeAll().first().single()
        assertTrue(trashed.archived)
        assertTrue(trashed.trashed)
        assertTrue(trashed.folderNames.contains("Работа"))

        repository.setTrashed(created.id, false)
        repository.setArchived(created.id, false)
        val restored = repository.observeAll().first().single()
        assertFalse(restored.archived)
        assertFalse(restored.trashed)
        assertTrue(restored.folderNames.contains("Работа"))
    }

    @Test
    fun trash_cleanup_deletes_only_notes_older_than_cutoff() = runBlocking {
        val oldClock = Clock.fixed(Instant.parse("2026-07-01T08:00:00Z"), ZoneOffset.UTC)
        val recentClock = Clock.fixed(Instant.parse("2026-07-25T08:00:00Z"), ZoneOffset.UTC)
        val oldRepository = RoomNotesRepository(database, oldClock)
        val old = oldRepository.create(null, "Старая заметка")
        oldRepository.setTrashed(old.id, true)
        val recentRepository = RoomNotesRepository(database, recentClock)
        val recent = recentRepository.create(null, "Новая заметка")
        recentRepository.setTrashed(recent.id, true)

        recentRepository.deleteTrashedBefore(
            java.time.LocalDateTime.of(2026, 7, 20, 0, 0),
        )

        val remaining = recentRepository.observeAll().first()
        assertEquals(listOf(recent.id), remaining.map { it.id })
        assertTrue(remaining.single().trashed)
    }

    @Test
    fun reminder_returns_from_completed_to_active_with_completed_time_cleared() = runBlocking {
        val repository = RoomReminderRepository(database.reminderDao(), clock)
        val created = repository.create("Позвонить", null, null)

        val completed = repository.setCompleted(created.id, true)
        assertTrue(completed.status == "completed")
        assertTrue(completed.completedAt != null)

        val active = repository.setCompleted(created.id, false)
        assertTrue(active.status == "active")
        assertNull(active.completedAt)
    }
}

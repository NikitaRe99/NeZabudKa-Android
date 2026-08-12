package ru.nezabudka.app.performance

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.NoteEntity
import ru.nezabudka.app.data.ReminderEntity
import ru.nezabudka.app.notifications.AndroidReminderScheduler
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Creates an explicitly marked, disposable data set for on-device frame measurements.
 * User-created rows are never updated or deleted.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceDataInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = AppDatabase.get(context)

    @Test
    fun seedPerformanceData() = runBlocking {
        removePerformanceRows()
        val now = LocalDateTime.now()
        database.withTransaction {
            repeat(NOTE_COUNT) { index ->
                database.notesDao().insertNote(
                    NoteEntity(
                        title = "$MARKER Заметка ${index + 1}",
                        text = buildString {
                            append("Тест прокрутки, строка ")
                            append(index + 1)
                            append(". ")
                            repeat(index % 5) { append("Дополнительный текст карточки. ") }
                        },
                        folderId = null,
                        createdAt = now.minusMinutes(index.toLong()),
                        updatedAt = now.minusMinutes(index.toLong()),
                        pinned = index % 17 == 0,
                        manualOrder = index.toLong(),
                    ),
                )
            }
            repeat(REMINDER_COUNT) { index ->
                database.reminderDao().insert(
                    ReminderEntity(
                        text = "$MARKER Напоминание ${index + 1}",
                        remindAt = now.plusMinutes((index - 40).toLong()),
                        recurrence = null,
                        status = if (index % 11 == 0) "completed" else "active",
                        createdAt = now.minusMinutes(index.toLong()),
                        favorite = index % 19 == 0,
                        manualOrder = index.toLong(),
                        completedAt = if (index % 11 == 0) now.minusMinutes(index.toLong()) else null,
                    ),
                )
            }
        }

        assertEquals(NOTE_COUNT, performanceNoteCount())
        assertEquals(REMINDER_COUNT, performanceReminderCount())
    }

    @Test
    fun cleanupPerformanceData() = runBlocking {
        removePerformanceRows()
        assertEquals(0, performanceNoteCount())
        assertEquals(0, performanceReminderCount())
    }

    private suspend fun removePerformanceRows() {
        val scheduler = AndroidReminderScheduler(context)
        val reminderIds = database.openHelper.readableDatabase.query(
            "SELECT id FROM reminders WHERE text LIKE ?",
            arrayOf("$MARKER%"),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        }
        reminderIds.forEach(scheduler::cancel)
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM notes WHERE title LIKE ?",
                arrayOf("$MARKER%"),
            )
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM reminders WHERE text LIKE ?",
                arrayOf("$MARKER%"),
            )
        }
    }

    private fun performanceNoteCount(): Int = database.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM notes WHERE title LIKE ?",
        arrayOf("$MARKER%"),
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private fun performanceReminderCount(): Int = database.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM reminders WHERE text LIKE ?",
        arrayOf("$MARKER%"),
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private companion object {
        const val MARKER = "[PERF]"
        const val NOTE_COUNT = 300
        const val REMINDER_COUNT = 240
    }
}

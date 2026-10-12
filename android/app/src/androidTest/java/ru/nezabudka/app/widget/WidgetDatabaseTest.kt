package ru.nezabudka.app.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.application.ReminderActions
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.RoomNotesRepository
import ru.nezabudka.app.data.RoomReminderRepository
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.ports.ReminderScheduler
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class WidgetDatabaseTest {
    private lateinit var database: AppDatabase
    private val clock = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneOffset.UTC)
    private val scheduler = object : ReminderScheduler {
        override fun schedule(reminder: Reminder) {}
        override fun cancel(reminderId: Long) {}
    }
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    }
    @After fun cleanup() { database.close() }

    @Test fun completionIsPersistentAndRepeatedClickDoesNotSkipARepeat() = runBlocking {
        val repository = RoomReminderRepository(database.reminderDao(), clock)
        val actions = ReminderActions(repository, scheduler, clock)
        val at = LocalDateTime.of(2026, 10, 12, 20, 0)
        val item = repository.create("Полить цветы", at, "monthly")
        assertTrue(completeWidgetReminder(database, actions, item.id, at.toString(), item.createdAt.toString()))
        assertFalse(completeWidgetReminder(database, actions, item.id, at.toString(), item.createdAt.toString()))
        assertEquals(at.plusMonths(1), database.reminderDao().byId(item.id)!!.remindAt)
        assertEquals("active", database.reminderDao().byId(item.id)!!.status)
        val ordinary = repository.create("Позвонить", at, null)
        assertTrue(completeWidgetReminder(database, actions, ordinary.id, at.toString(), ordinary.createdAt.toString()))
        assertFalse(completeWidgetReminder(database, actions, ordinary.id, at.toString(), ordinary.createdAt.toString()))
        assertEquals("completed", database.reminderDao().byId(ordinary.id)!!.status)
    }

    @Test fun folderQueriesRespectMembershipPinnedArchiveAndTrash() = runBlocking {
        val repository = RoomNotesRepository(database, clock)
        val work = repository.createFolder("Работа")
        val personal = repository.createFolder("Личное")
        val shared = repository.save(null, listOf(work.name, personal.name), "Общая", "Текст", false, false, null)
        val pinned = repository.save(null, listOf(work.name), "Важная", "Текст", true, false, null)
        val archived = repository.save(null, listOf(work.name), "Архив", "Текст", false, true, null)
        val trash = repository.save(null, listOf(work.name), "Корзина", "Текст", false, false, null)
        repository.setTrashed(trash.id, true)
        assertEquals(listOf(pinned.id, shared.id), database.notesDao().widgetRows(work.id).map { it.id })
        assertEquals(listOf(shared.id), database.notesDao().widgetRows(personal.id).map { it.id })
        assertEquals(2, database.notesDao().widgetCount(null))
        assertTrue(database.notesDao().widgetRows(null).none { it.id == archived.id || it.id == trash.id })
        assertEquals(1, database.notesDao().widgetRows(work.id, limit = 1).size)
        assertEquals(2, database.notesDao().widgetCount(work.id))
    }
    @Test fun undoRestoresOccurrenceAndRejectsExpiredOrNewerChanges() = runBlocking {
        val repository = RoomReminderRepository(database.reminderDao(), clock)
        val actions = ReminderActions(repository, scheduler, clock)
        val at = LocalDateTime.of(2026, 10, 12, 20, 0)
        val item = repository.create("Полить цветы", at, "monthly")
        val undo = completeWidgetReminderWithUndo(database, actions, item.id, at.toString(), item.createdAt.toString())!!
        assertFalse(undoWidgetReminder(database, actions, undo.copy(expiresAt = 0)))
        assertTrue(undoWidgetReminder(database, actions, undo))
        assertEquals(at, database.reminderDao().byId(item.id)!!.remindAt)
        assertFalse(undoWidgetReminder(database, actions, undo))
        val another = completeWidgetReminderWithUndo(database, actions, item.id, at.toString(), item.createdAt.toString())!!
        repository.update(item.id, "Новый текст", null)
        assertFalse(undoWidgetReminder(database, actions, another))
        assertEquals("Новый текст", database.reminderDao().byId(item.id)!!.text)
    }
    @Test fun completedRowsFollowActiveAndRepeatSnapshotSurvivesReload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "widget_test_" + java.util.UUID.randomUUID()
        val prefs = WidgetPreferences(context, name)
        try {
            // This widget rule follows the device's current day; use that same clock for completion.
            val widgetClock = Clock.systemDefaultZone()
            val repository = RoomReminderRepository(database.reminderDao(), widgetClock)
            val actions = ReminderActions(repository, scheduler, widgetClock)
            val today = java.time.LocalDate.now()
            val at = today.atTime(20, 0)
            val active = repository.create("Активное", at, null)
            val ordinary = repository.create("Без времени", null, null)
            actions.setCompleted(ordinary.id, true)
            val repeat = repository.create("Повтор", at, "monthly")
            val receipt = completeWidgetReminderWithUndo(database, actions, repeat.id, at.toString(), repeat.createdAt.toString())!!
            prefs.recordCompletion(receipt)
            val rows = widgetReminderRows(database, WidgetPreferences(context, name), today)
            assertEquals(active.id, rows.rows.first().item.id)
            assertEquals(1, rows.activeCount)
            assertEquals(2, rows.completedCount)
            assertTrue(rows.rows.drop(1).all { it.completed })
            assertEquals(rows.rows.size, rows.rows.map { it.stableId }.distinct().size)
            val completed = rows.rows.last()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val views = WidgetRenderer.reminderRow(context, completed.item, WidgetPalette.read(context), true, today, true, completed.token)
                    .apply(context, android.widget.FrameLayout(context))
                val title = views.findViewById<android.widget.TextView>(ru.nezabudka.app.R.id.widget_row_title)
                assertTrue(title.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG != 0)
            }
            assertTrue(undoWidgetReminder(database, actions, receipt.copy(expiresAt = System.currentTimeMillis() + 10_000)))
            prefs.removeCompletion(receipt.token)
            val restored = widgetReminderRows(database, prefs, today)
            assertEquals(2, restored.activeCount)
            assertEquals(1, restored.completedCount)
            assertTrue(WidgetPreferences(context, name).completedOccurrences(today.plusDays(1)).isEmpty())
        } finally { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }

}

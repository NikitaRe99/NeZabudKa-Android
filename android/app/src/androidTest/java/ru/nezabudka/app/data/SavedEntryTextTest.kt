package ru.nezabudka.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedEntryTextTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    @After fun close() = database.close()

    @Test fun creationAndEditorSaveUseTheSameTextRule() = runBlocking {
        val notes = RoomNotesRepository(database)
        val created = notes.create(null, "  …, — ; купить бумагу.\nВторая строка.")
        assertEquals("Купить бумагу.\nВторая строка.", created.text)
        val saved = notes.save(created.id, emptyList(), " : план", "\n• проверить макет, затем отправить.", false, false, null)
        assertEquals("План", saved.title)
        assertEquals("Проверить макет, затем отправить.", database.notesDao().noteById(created.id)!!.text)

        val reminders = RoomReminderRepository(database.reminderDao())
        val reminder = reminders.create(" : — позвонить мастеру!", null, null)
        assertEquals("Позвонить мастеру!", reminder.text)
        reminders.update(reminder.id, ", уточнить время", null)
        assertEquals("Уточнить время", database.reminderDao().byId(reminder.id)!!.text)
        reminders.saveEditor(reminder.id, " ; забрать заказ.", null, null, false, false)
        assertEquals("Забрать заказ.", database.reminderDao().byId(reminder.id)!!.text)
        reminders.replace(reminder.id, ": проверить номер 10:25", null)
        assertEquals("Проверить номер 10:25", database.reminderDao().byId(reminder.id)!!.text)
    }

    @Test fun punctuationOnlyReminderCannotBecomeAnEmptySavedRecord() = runBlocking {
        val repository = RoomReminderRepository(database.reminderDao())
        val error = runCatching { repository.create(" …,;— ", null, null) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(repository.list().isEmpty())
    }
}

package ru.nezabudka.app.performance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.*
import ru.nezabudka.app.ports.*

@RunWith(AndroidJUnit4::class)
class BackupSnapshotTest {
    @Test fun transactionalSnapshotPreservesEveryFolderLinkAndReminderState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val now = LocalDateTime.of(2026, 10, 23, 12, 0)
            val folderA = db.notesDao().insertFolder(NoteFolderEntity(name = "Первая", createdAt = now))
            val folderB = db.notesDao().insertFolder(NoteFolderEntity(name = "Вторая", createdAt = now))
            val note = db.notesDao().insertNote(NoteEntity(folderId = folderA, title = "Заголовок", text = "Текст",
                createdAt = now, archived = true, color = "#123456"))
            db.notesDao().insertRefs(listOf(NoteFolderRef(note, folderA), NoteFolderRef(note, folderB)))
            db.reminderDao().insert(ReminderEntity(text = "Напоминание", remindAt = now, recurrence = "daily",
                status = "completed", createdAt = now, completedAt = now, favorite = true))
            val model = object : PortableModelSelection {
                override val selectedModelName = MutableStateFlow<String?>(null)
                override fun currentSelectedModelName(): String? = null
                override fun selectIfAvailable(fileName: String) = Unit
            }
            val cloud = object : PortableCloudConfigurationStore {
                override val portableConfiguration = MutableStateFlow(PortableCloudConfiguration())
                override fun currentPortableConfiguration() = portableConfiguration.value
                override fun restorePortableConfiguration(configuration: PortableCloudConfiguration) = Unit
            }
            val manager = UserDataBackupManager(UserModelsStorage(context), db, SettingsStore(context), model, cloud, {})
            val snapshot = manager.databaseSnapshot(PortableAppSettings(theme = "dark"))
            val restored = UserDataBackupCodec.decode(UserDataBackupCodec.encode(snapshot))
            assertEquals(2, restored.folders.size)
            assertEquals(setOf(folderA, folderB), restored.notes.single().folderIds.toSet())
            assertTrue(restored.notes.single().archived)
            assertEquals("#123456", restored.notes.single().color)
            assertEquals("daily", restored.reminders.single().recurrence)
            assertEquals("completed", restored.reminders.single().status)
            assertTrue(restored.reminders.single().favorite)
            assertEquals("dark", restored.settings!!.theme)
            val onlyNotes = manager.databaseSnapshot(content = BackupContent.NOTES)
            assertTrue(onlyNotes.reminders.isEmpty())
            assertNull(onlyNotes.settings)
            assertEquals(restored.notes, onlyNotes.notes)
        } finally { db.close() }
    }
}

package ru.nezabudka.app.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun every_exported_schema_migrates_to_current_version() {
        (1 until CURRENT_DATABASE_VERSION).forEach { startVersion ->
            val name = "migration-$startVersion-to-$CURRENT_DATABASE_VERSION"
            helper.createDatabase(name, startVersion).close()
            helper.runMigrationsAndValidate(name, CURRENT_DATABASE_VERSION, true, *AppDatabase.ALL_MIGRATIONS).close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_from_one_preserves_user_data_and_populates_new_columns() {
        val name = "migration-with-data"
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO reminders(id, text, remind_at, recurrence, status, created_at) " +
                    "VALUES(1, 'Позвонить', '2026-07-27T09:30:00', NULL, 'active', '2026-07-26T08:00:00')",
            )
            execSQL("INSERT INTO note_folders(id, name, created_at) VALUES(7, 'Работа', '2026-07-20T12:00:00')")
            execSQL("INSERT INTO notes(id, folder_id, text, created_at) VALUES(9, 7, 'Отчёт', '2026-07-21T10:00:00')")
            close()
        }

        helper.runMigrationsAndValidate(name, CURRENT_DATABASE_VERSION, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query("SELECT text, favorite, manual_order FROM reminders WHERE id=1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Позвонить", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(1L, cursor.getLong(2))
            }
            db.query("SELECT text, updated_at, title, manual_order FROM notes WHERE id=9").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Отчёт", cursor.getString(0))
                assertEquals("2026-07-21T10:00:00", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals(9L, cursor.getLong(3))
            }
            db.query("SELECT folder_id FROM note_folder_refs WHERE note_id=9").use { cursor ->
                cursor.moveToFirst()
                assertEquals(7L, cursor.getLong(0))
            }
        }
        context.deleteDatabase(name)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private companion object {
        const val CURRENT_DATABASE_VERSION = 8
    }
}

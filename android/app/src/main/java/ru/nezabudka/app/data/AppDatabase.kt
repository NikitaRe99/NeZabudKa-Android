package ru.nezabudka.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

class DateTimeConverters {
    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE status = 'active' AND remind_at < :end ORDER BY remind_at, id LIMIT :limit")
    suspend fun widgetRows(end: LocalDateTime, limit: Int = 500): List<ReminderEntity>
    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'active' AND remind_at < :end")
    suspend fun widgetCount(end: LocalDateTime): Int
    @Query("SELECT * FROM reminders WHERE status = 'completed' AND completed_at >= :start AND completed_at < :end ORDER BY completed_at DESC, id DESC LIMIT :limit")
    suspend fun widgetCompletedRows(start: LocalDateTime, end: LocalDateTime, limit: Int = 500): List<ReminderEntity>
    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'completed' AND completed_at >= :start AND completed_at < :end")
    suspend fun widgetCompletedCount(start: LocalDateTime, end: LocalDateTime): Int
    @Insert suspend fun insert(value: ReminderEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restore(value: ReminderEntity): Long
    @Update suspend fun update(value: ReminderEntity)
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1") suspend fun byId(id: Long): ReminderEntity?
    @Query("SELECT COUNT(*) FROM reminders") suspend fun count(): Int
    @Query("SELECT * FROM reminders ORDER BY manual_order, id") suspend fun allForBackup(): List<ReminderEntity>
    @Query("SELECT * FROM reminders ORDER BY manual_order ASC, id ASC")
    fun observeAll(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ReminderEntity?>
    @Query("SELECT * FROM reminders WHERE status = 'active' AND (:start IS NULL OR remind_at >= :start) AND (:end IS NULL OR remind_at < :end) ORDER BY remind_at IS NULL, remind_at ASC, id ASC")
    suspend fun list(start: LocalDateTime?, end: LocalDateTime?): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id = :id AND status = 'active' LIMIT 1")
    suspend fun findId(id: Long): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE lower(text) LIKE '%' || lower(:text) || '%' AND status = 'active' ORDER BY id DESC LIMIT 1")
    suspend fun findText(text: String): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE status = 'active' ORDER BY id DESC LIMIT 1")
    suspend fun findLast(): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE status = 'active' AND remind_at IS NOT NULL AND remind_at > :now ORDER BY remind_at")
    suspend fun future(now: LocalDateTime): List<ReminderEntity>
    @Query("UPDATE reminders SET favorite = :favorite WHERE id = :id") suspend fun setFavorite(id: Long, favorite: Boolean)
    @Query("UPDATE reminders SET status = :status, completed_at = :completedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, status: String, completedAt: LocalDateTime?)
    @Query("UPDATE reminders SET manual_order = :orderValue WHERE id = :id") suspend fun setOrder(id: Long, orderValue: Long)
    @Transaction
    suspend fun setOrders(updates: List<ManualOrderUpdate>) {
        updates.forEach { update -> setOrder(update.id, update.order) }
    }
    @Query("""
        UPDATE reminders SET
            text = :text,
            remind_at = :remindAt,
            recurrence = :recurrence,
            favorite = :favorite,
            status = CASE WHEN :completed THEN 'completed' ELSE 'active' END,
            completed_at = CASE WHEN :completed THEN :completedAt ELSE NULL END
        WHERE id = :id
    """)
    suspend fun updateFromEditor(id: Long, text: String, remindAt: LocalDateTime?, recurrence: String?, favorite: Boolean, completed: Boolean, completedAt: LocalDateTime?)
    @Transaction
    suspend fun saveFromEditor(id: Long, text: String, remindAt: LocalDateTime?, recurrence: String?, favorite: Boolean, completed: Boolean, completedAt: LocalDateTime?): ReminderEntity {
        updateFromEditor(id, text, remindAt, recurrence, favorite, completed, completedAt)
        return byId(id) ?: throw NoSuchElementException("Напоминание $id не найдено")
    }
    @Query("UPDATE reminders SET recurrence = :recurrence WHERE id = :id")
    suspend fun setRecurrence(id: Long, recurrence: String?)
    @Query("SELECT * FROM reminders WHERE status = 'active' ORDER BY manual_order ASC, id ASC") suspend fun activeInManualOrder(): List<ReminderEntity>
    @Query("DELETE FROM reminders WHERE status = 'completed'") suspend fun deleteCompleted()
}

@Dao
interface NotesDao {
    @Query("""SELECT n.id, n.title, substr(n.text, 1, 500) AS text, n.updated_at, n.pinned FROM notes n
        WHERE n.trashed = 0 AND n.archived = 0
        AND (:folderId IS NULL OR EXISTS (SELECT 1 FROM note_folder_refs r WHERE r.note_id = n.id AND r.folder_id = :folderId))
        ORDER BY n.pinned DESC, n.updated_at DESC, n.id DESC LIMIT :limit""")
    suspend fun widgetRows(folderId: Long?, limit: Int = 500): List<WidgetNoteRow>
    @Query("""SELECT COUNT(*) FROM notes n WHERE n.trashed = 0 AND n.archived = 0
        AND (:folderId IS NULL OR EXISTS (SELECT 1 FROM note_folder_refs r WHERE r.note_id = n.id AND r.folder_id = :folderId))""")
    suspend fun widgetCount(folderId: Long?): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertFolder(value: NoteFolderEntity): Long
    @Query("SELECT note_id FROM note_folder_refs WHERE folder_id=:folderId")
    suspend fun noteIdsForFolder(folderId: Long): List<Long>
    @Query("SELECT * FROM note_folders ORDER BY manual_order, id")
    suspend fun allFolders(): List<NoteFolderEntity>
    @Query("SELECT COUNT(*) FROM note_folders") suspend fun folderCount(): Int
    @Query("SELECT COUNT(*) FROM notes") suspend fun noteCount(): Int
    @Insert suspend fun insertNote(value: NoteEntity): Long
    @Query("SELECT * FROM notes ORDER BY manual_order, id") suspend fun allNotesInManualOrder(): List<NoteEntity>
    @Query("SELECT * FROM note_folder_refs") suspend fun allRefsForBackup(): List<NoteFolderRef>
    @Query("UPDATE notes SET manual_order=:orderValue WHERE id=:id") suspend fun setNoteOrder(id: Long, orderValue: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRefs(values: List<NoteFolderRef>)
    @Query("DELETE FROM note_folder_refs WHERE note_id=:noteId") suspend fun deleteRefs(noteId: Long)
    @Query("SELECT f.id, f.name, f.created_at, f.manual_order, count(DISTINCT r.note_id) AS note_count FROM note_folders f LEFT JOIN note_folder_refs r ON r.folder_id=f.id GROUP BY f.id ORDER BY f.manual_order, f.id")
    fun observeFolders(): Flow<List<NoteFolderRow>>
    @Query("UPDATE note_folders SET manual_order=:orderValue WHERE id=:id") suspend fun setFolderOrder(id: Long, orderValue: Long)
    @Query("UPDATE note_folders SET name=:name WHERE id=:id") suspend fun renameFolder(id: Long, name: String)
    @Query("UPDATE notes SET folder_id=(SELECT MIN(folder_id) FROM note_folder_refs WHERE note_id=notes.id AND folder_id != :folderId) WHERE folder_id=:folderId") suspend fun replacePrimaryFolder(folderId: Long)
    @Query("DELETE FROM note_folders WHERE id=:id") suspend fun deleteFolder(id: Long)
    @Query("SELECT n.id, MIN(f.id) AS folder_id, MIN(f.name) AS folder_name, n.text, n.title, n.created_at, n.updated_at, n.pinned, n.archived, n.trashed, n.color, n.manual_order, GROUP_CONCAT(f.id, '|') AS folder_ids, GROUP_CONCAT(f.name, '|') AS folder_names FROM notes n JOIN note_folder_refs selected ON selected.note_id=n.id AND selected.folder_id=:folderId JOIN note_folder_refs r ON r.note_id=n.id JOIN note_folders f ON f.id=r.folder_id GROUP BY n.id ORDER BY n.created_at DESC, n.id DESC")
    fun observeNotes(folderId: Long): Flow<List<NoteRow>>
    @Query("SELECT n.id, MIN(f.id) AS folder_id, MIN(f.name) AS folder_name, n.text, n.title, n.created_at, n.updated_at, n.pinned, n.archived, n.trashed, n.color, n.manual_order, COALESCE(GROUP_CONCAT(f.id, '|'), '') AS folder_ids, COALESCE(GROUP_CONCAT(f.name, '|'), '') AS folder_names FROM notes n LEFT JOIN note_folder_refs r ON r.note_id=n.id LEFT JOIN note_folders f ON f.id=r.folder_id GROUP BY n.id ORDER BY n.updated_at DESC")
    fun observeAll(): Flow<List<NoteRow>>
    @Query("SELECT n.id, MIN(f.id) AS folder_id, MIN(f.name) AS folder_name, n.text, n.title, n.created_at, n.updated_at, n.pinned, n.archived, n.trashed, n.color, n.manual_order, COALESCE(GROUP_CONCAT(f.id, '|'), '') AS folder_ids, COALESCE(GROUP_CONCAT(f.name, '|'), '') AS folder_names FROM notes n LEFT JOIN note_folder_refs r ON r.note_id=n.id LEFT JOIN note_folders f ON f.id=r.folder_id WHERE n.id=:id GROUP BY n.id")
    suspend fun noteById(id: Long): NoteRow?
    @Update suspend fun updateNote(value: NoteEntity)
    @Query("SELECT * FROM notes WHERE id=:id") suspend fun noteEntity(id: Long): NoteEntity?
    @Query("DELETE FROM notes WHERE id=:id") suspend fun deleteNote(id: Long)
    @Query("DELETE FROM notes WHERE trashed=1") suspend fun deleteTrashed()
    @Query("DELETE FROM notes WHERE trashed=1 AND updated_at <= :cutoff")
    suspend fun deleteTrashedBefore(cutoff: LocalDateTime)
    @Query("UPDATE notes SET pinned=:value, updated_at=:updatedAt WHERE id=:id") suspend fun setPinned(id: Long, value: Boolean, updatedAt: LocalDateTime)
    @Query("UPDATE notes SET archived=:value, updated_at=:updatedAt WHERE id=:id") suspend fun setArchived(id: Long, value: Boolean, updatedAt: LocalDateTime)
    @Query("UPDATE notes SET trashed=:value, updated_at=:updatedAt WHERE id=:id") suspend fun setTrashed(id: Long, value: Boolean, updatedAt: LocalDateTime)
    @Query("UPDATE notes SET pinned=:value, updated_at=:updatedAt WHERE id IN (:ids)") suspend fun setPinned(ids: List<Long>, value: Boolean, updatedAt: LocalDateTime)
    @Query("UPDATE notes SET archived=:value, updated_at=:updatedAt WHERE id IN (:ids)") suspend fun setArchived(ids: List<Long>, value: Boolean, updatedAt: LocalDateTime)
    @Query("UPDATE notes SET trashed=:value, updated_at=:updatedAt WHERE id IN (:ids)") suspend fun setTrashed(ids: List<Long>, value: Boolean, updatedAt: LocalDateTime)
    @Query("UPDATE notes SET color=:color, updated_at=:updatedAt WHERE id IN (:ids)") suspend fun setColor(ids: List<Long>, color: String?, updatedAt: LocalDateTime)
    @Query("DELETE FROM note_folder_refs WHERE note_id IN (:noteIds) AND folder_id=:folderId") suspend fun deleteFolderRefs(noteIds: List<Long>, folderId: Long)
    @Query("UPDATE notes SET folder_id=:folderId WHERE id IN (:noteIds) AND folder_id IS NULL") suspend fun fillPrimaryFolder(noteIds: List<Long>, folderId: Long)
    @Query("UPDATE notes SET folder_id=(SELECT MIN(folder_id) FROM note_folder_refs WHERE note_id=notes.id) WHERE id IN (:noteIds)") suspend fun repairPrimaryFolders(noteIds: List<Long>)
}

@Database(entities = [ReminderEntity::class, NoteFolderEntity::class, NoteEntity::class, NoteFolderRef::class], version = 8, exportSchema = true)
@TypeConverters(DateTimeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun notesDao(): NotesDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "reminders.db")
                .addMigrations(*ALL_MIGRATIONS).build().also { instance = it }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN updated_at TEXT NOT NULL DEFAULT '1970-01-01T00:00:00'")
                db.execSQL("UPDATE notes SET updated_at = created_at")
                db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN trashed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS note_folder_refs (note_id INTEGER NOT NULL, folder_id INTEGER NOT NULL, PRIMARY KEY(note_id, folder_id), FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE, FOREIGN KEY(folder_id) REFERENCES note_folders(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_folder_refs_folder_id ON note_folder_refs(folder_id)")
                db.execSQL("INSERT OR IGNORE INTO note_folder_refs(note_id, folder_id) SELECT id, folder_id FROM notes")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TEMP TABLE note_folder_refs_backup AS SELECT note_id, folder_id FROM note_folder_refs")
                db.execSQL("DROP TABLE note_folder_refs")
                db.execSQL("CREATE TABLE notes_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, folder_id INTEGER, text TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL DEFAULT '1970-01-01T00:00:00', pinned INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, trashed INTEGER NOT NULL DEFAULT 0, color TEXT, FOREIGN KEY(folder_id) REFERENCES note_folders(id) ON DELETE CASCADE)")
                db.execSQL("INSERT INTO notes_new SELECT id, folder_id, text, created_at, updated_at, pinned, archived, trashed, color FROM notes")
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")
                db.execSQL("CREATE INDEX index_notes_folder_id ON notes(folder_id)")
                db.execSQL("CREATE TABLE note_folder_refs (note_id INTEGER NOT NULL, folder_id INTEGER NOT NULL, PRIMARY KEY(note_id, folder_id), FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE, FOREIGN KEY(folder_id) REFERENCES note_folders(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_note_folder_refs_folder_id ON note_folder_refs(folder_id)")
                db.execSQL("INSERT OR IGNORE INTO note_folder_refs SELECT note_id, folder_id FROM note_folder_refs_backup")
                db.execSQL("DROP TABLE note_folder_refs_backup")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_folders ADD COLUMN manual_order INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE note_folders SET manual_order=id")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN manual_order INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE notes SET manual_order=id")
            }
        }
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN manual_order INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN completed_at TEXT")
                db.execSQL("UPDATE reminders SET manual_order = id")
            }
        }

        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
        )
    }
}

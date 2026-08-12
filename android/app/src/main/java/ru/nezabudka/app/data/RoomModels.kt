package ru.nezabudka.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "reminders", indices = [Index("remind_at")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "remind_at") val remindAt: LocalDateTime?,
    val recurrence: String?,
    val status: String = "active",
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
    val favorite: Boolean = false,
    @ColumnInfo(name = "manual_order") val manualOrder: Long = 0,
    @ColumnInfo(name = "completed_at") val completedAt: LocalDateTime? = null,
)

@Entity(
    tableName = "note_folders",
    indices = [Index(value = ["name"], unique = true)],
)
data class NoteFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
    @ColumnInfo(name = "manual_order") val manualOrder: Long = 0,
)

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = NoteFolderEntity::class,
        parentColumns = ["id"],
        childColumns = ["folder_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("folder_id")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "folder_id") val folderId: Long?,
    val text: String,
    @ColumnInfo(defaultValue = "''") val title: String = "",
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
    @ColumnInfo(name = "updated_at", defaultValue = "'1970-01-01T00:00:00'") val updatedAt: LocalDateTime = createdAt,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val color: String? = null,
    @ColumnInfo(name = "manual_order") val manualOrder: Long = 0,
)

@Entity(
    tableName = "note_folder_refs",
    primaryKeys = ["note_id", "folder_id"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = NoteFolderEntity::class, parentColumns = ["id"], childColumns = ["folder_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("folder_id")],
)
data class NoteFolderRef(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "folder_id") val folderId: Long,
)

data class NoteFolderRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
    @ColumnInfo(name = "note_count") val noteCount: Int,
    @ColumnInfo(name = "manual_order") val manualOrder: Long,
)

data class NoteRow(
    val id: Long,
    @ColumnInfo(name = "folder_id") val folderId: Long?,
    @ColumnInfo(name = "folder_name") val folderName: String?,
    val text: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime,
    val pinned: Boolean,
    val archived: Boolean,
    val trashed: Boolean,
    val color: String?,
    @ColumnInfo(name = "manual_order") val manualOrder: Long,
    @ColumnInfo(name = "folder_ids") val folderIds: String,
    @ColumnInfo(name = "folder_names") val folderNames: String,
)

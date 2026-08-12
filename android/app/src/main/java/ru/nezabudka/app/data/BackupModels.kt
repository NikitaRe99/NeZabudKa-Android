package ru.nezabudka.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val CURRENT_BACKUP_VERSION = 2
internal const val MIN_SUPPORTED_BACKUP_VERSION = 1

@Serializable
internal data class UserDataBackupDto(
    val version: Int = CURRENT_BACKUP_VERSION,
    val reminders: List<BackupReminderDto>,
    val folders: List<BackupFolderDto>,
    val notes: List<BackupNoteDto>,
    val settings: PortableAppSettings? = null,
)

@Serializable
internal data class BackupReminderDto(
    val id: Long,
    val text: String,
    val remindAt: String?,
    val recurrence: String?,
    val status: String,
    val createdAt: String,
    val favorite: Boolean,
    val manualOrder: Long,
    val completedAt: String?,
)

@Serializable
internal data class BackupFolderDto(
    val id: Long,
    val name: String,
    val createdAt: String,
    val manualOrder: Long,
)

@Serializable
internal data class BackupNoteDto(
    val id: Long,
    val primaryFolderId: Long?,
    val folderIds: List<Long>,
    val text: String,
    val createdAt: String,
    val updatedAt: String,
    val pinned: Boolean,
    val archived: Boolean,
    val trashed: Boolean,
    val color: String?,
    val title: String = "",
    val manualOrder: Long = id,
)

internal object UserDataBackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: UserDataBackupDto): String = json.encodeToString(backup)

    fun decode(raw: String): UserDataBackupDto = json.decodeFromString<UserDataBackupDto>(raw).also { backup ->
        require(backup.version in MIN_SUPPORTED_BACKUP_VERSION..CURRENT_BACKUP_VERSION) {
            "Неподдерживаемая версия резервной копии: ${backup.version}"
        }
    }
}

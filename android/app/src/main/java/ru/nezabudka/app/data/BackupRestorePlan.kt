package ru.nezabudka.app.data

import java.time.LocalDateTime

internal data class BackupRestorePlan(
    val reminders: List<ReminderEntity>,
    val folders: List<NoteFolderEntity>,
    val notes: List<RestoredNote>,
    val settings: PortableAppSettings?,
) {
    data class RestoredNote(
        val entity: NoteEntity,
        val folderIds: List<Long>,
    ) {
        fun forImport(mapping: Map<Long, Long>): RestoredNote {
            val primary = entity.folderId?.let(mapping::get)
            return RestoredNote(entity.copy(id = 0, folderId = primary),
                (folderIds.mapNotNull(mapping::get) + listOfNotNull(primary)).distinct())
        }
    }

    companion object {
        fun from(backup: UserDataBackupDto): BackupRestorePlan {
            requireUniquePositiveIds("напоминаний", backup.reminders.map(BackupReminderDto::id))
            requireUniquePositiveIds("папок", backup.folders.map(BackupFolderDto::id))
            requireUniquePositiveIds("заметок", backup.notes.map(BackupNoteDto::id))

            val folders = backup.folders.sortedBy(BackupFolderDto::id).map { value ->
                require(value.name.isNotBlank()) { "Пустое название папки в резервной копии" }
                NoteFolderEntity(
                    id = value.id,
                    name = value.name,
                    createdAt = parseDate("дата создания папки ${value.id}", value.createdAt),
                    manualOrder = value.manualOrder,
                )
            }
            val folderIds = folders.mapTo(mutableSetOf(), NoteFolderEntity::id)
            val notes = backup.notes.sortedBy(BackupNoteDto::id).map { value ->
                val validFolderIds = value.folderIds.distinct().filter(folderIds::contains)
                val primary = value.primaryFolderId?.takeIf(folderIds::contains)
                RestoredNote(
                    entity = NoteEntity(
                        id = value.id,
                        folderId = primary,
                        text = value.text,
                        title = value.title,
                        createdAt = parseDate("дата создания заметки ${value.id}", value.createdAt),
                        updatedAt = parseDate("дата изменения заметки ${value.id}", value.updatedAt),
                        pinned = value.pinned,
                        archived = value.archived,
                        trashed = value.trashed,
                        color = value.color,
                        manualOrder = value.manualOrder,
                    ),
                    folderIds = validFolderIds,
                )
            }
            val reminders = backup.reminders.sortedBy(BackupReminderDto::id).map { value ->
                require(value.text.isNotBlank()) { "Пустой текст напоминания ${value.id}" }
                require(value.status == "active" || value.status == "completed") {
                    "Неизвестный статус напоминания ${value.id}"
                }
                ReminderEntity(
                    id = value.id,
                    text = value.text,
                    remindAt = value.remindAt?.let { parseDate("время напоминания ${value.id}", it) },
                    recurrence = value.recurrence,
                    status = value.status,
                    createdAt = parseDate("дата создания напоминания ${value.id}", value.createdAt),
                    favorite = value.favorite,
                    manualOrder = value.manualOrder,
                    completedAt = value.completedAt?.let { parseDate("дата выполнения напоминания ${value.id}", it) },
                )
            }
            return BackupRestorePlan(reminders, folders, notes, backup.settings)
        }

        private fun requireUniquePositiveIds(label: String, ids: List<Long>) {
            require(ids.all { it > 0 }) { "Некорректный идентификатор $label" }
            require(ids.distinct().size == ids.size) { "Повторяющийся идентификатор $label" }
        }

        private fun parseDate(label: String, value: String): LocalDateTime =
            runCatching { LocalDateTime.parse(value) }
                .getOrElse { throw IllegalArgumentException("Некорректная $label", it) }
    }
}

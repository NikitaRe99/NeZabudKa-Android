package ru.nezabudka.app.domain

import java.time.LocalDateTime

enum class CommandIntent(val wireName: String) {
    CREATE_REMINDER("create_reminder"),
    LIST_REMINDERS("list_reminders"),
    DELETE_REMINDER("delete_reminder"),
    CREATE_NOTE("create_note"),
    CREATE_FOLDER("create_folder"),
    DELETE_FOLDER("delete_folder"),
    UNKNOWN("unknown");

    companion object {
        fun fromWireName(value: String): CommandIntent =
            entries.firstOrNull { it.wireName == value }
                ?: throw CommandValidationException("Некорректный intent: $value")
    }
}

enum class CommandScope { ASSISTANT, REMINDERS, NOTES }

data class CommandContext(
    val scope: CommandScope = CommandScope.ASSISTANT,
    val languageTag: String = "ru",
)

class CommandValidationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

data class AssistantCommand(
    val intent: CommandIntent,
    val text: String? = null,
    val remindAt: LocalDateTime? = null,
    val recurrence: String? = null,
    val periodStart: LocalDateTime? = null,
    val periodEnd: LocalDateTime? = null,
    val targetId: Long? = null,
    val targetText: String? = null,
    val targetLast: Boolean = false,
    val timeExpression: String? = null,
    val delayValue: Double? = null,
    val delayUnit: DelayUnit? = null,
    val noteText: String? = null,
    val noteFolder: String? = null,
) {
    fun validate(): AssistantCommand = apply {
        if (intent == CommandIntent.CREATE_REMINDER && text.isNullOrBlank()) {
            throw CommandValidationException("Для создания напоминания нужен text")
        }
        if (intent == CommandIntent.CREATE_NOTE && noteText.isNullOrBlank()) {
            throw CommandValidationException("Для заметки нужен note_text")
        }
        if (intent in setOf(CommandIntent.CREATE_FOLDER, CommandIntent.DELETE_FOLDER) && noteFolder.isNullOrBlank()) {
            throw CommandValidationException("Нужно название папки")
        }
        if (intent == CommandIntent.DELETE_REMINDER &&
            targetId == null && targetText.isNullOrBlank() && !targetLast
        ) throw CommandValidationException("Не указана цель изменения")
        if (periodStart != null && periodEnd != null && !periodStart.isBefore(periodEnd)) {
            throw CommandValidationException("period_start должен быть раньше period_end")
        }
        if ((delayValue == null) != (delayUnit == null)) {
            throw CommandValidationException("delay_value и delay_unit должны быть указаны вместе")
        }
        if (delayValue != null && delayValue <= 0) {
            throw CommandValidationException("delay_value должен быть больше нуля")
        }
    }
}

enum class DelayUnit { MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

data class Reminder(
    val id: Long,
    val text: String,
    val remindAt: LocalDateTime?,
    val recurrence: String?,
    val status: String,
    val createdAt: LocalDateTime,
    val favorite: Boolean = false,
    val manualOrder: Long = id,
    val completedAt: LocalDateTime? = null,
)

data class NoteFolder(val id: Long, val name: String, val createdAt: LocalDateTime, val noteCount: Int = 0, val manualOrder: Long = id)
data class DeletedNoteFolder(val folder: NoteFolder, val noteIds: List<Long>)

data class Note(
    val id: Long,
    val folderId: Long?,
    val folderName: String?,
    val text: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime = createdAt,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val color: String? = null,
    val folderIds: List<Long> = listOfNotNull(folderId),
    val folderNames: List<String> = listOfNotNull(folderName),
    val title: String = "",
    val manualOrder: Long = id,
)

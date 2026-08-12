package ru.nezabudka.app.application

import ru.nezabudka.app.domain.AssistantCommand
import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.domain.DeletedNoteFolder
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.ports.NotesRepository
import ru.nezabudka.app.ports.ReminderRepository
import ru.nezabudka.app.ports.ReminderScheduler
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed interface ActionEffect {
    data class ReminderCreated(val reminder: Reminder) : ActionEffect
    data class ReminderDeleted(val reminder: Reminder) : ActionEffect
    data class NoteCreated(val note: Note) : ActionEffect
    data class FolderCreated(val folder: NoteFolder) : ActionEffect
    data class FolderDeleted(val snapshot: DeletedNoteFolder) : ActionEffect
    data class RemindersListed(val date: java.time.LocalDate?) : ActionEffect
    data class ReminderDateRequested(val clarification: ReminderDateClarification) : ActionEffect
}

data class ActionResult(
    val message: String,
    val reminders: List<Reminder> = emptyList(),
    val notes: List<Note> = emptyList(),
    val effect: ActionEffect? = null,
    val reminderDateClarification: ReminderDateClarification? = null,
)

data class ReminderDateClarification(
    val text: String,
    val recurrence: String? = null,
)

class Dispatcher(
    private val reminders: ReminderRepository,
    private val notes: NotesRepository,
    private val reminderActions: ReminderActions,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    constructor(
        reminders: ReminderRepository,
        notes: NotesRepository,
        scheduler: ReminderScheduler,
        clock: Clock = Clock.systemDefaultZone(),
    ) : this(reminders, notes, ReminderActions(reminders, scheduler), clock)

    suspend fun dispatch(command: AssistantCommand): ActionResult {
        command.validate()
        return when (command.intent) {
            CommandIntent.CREATE_REMINDER -> {
                val value = reminderActions.create(command.text.orEmpty(), command.remindAt, command.recurrence)
                ActionResult("Напоминание создано: ${describe(value)}", listOf(value), effect = ActionEffect.ReminderCreated(value))
            }
            CommandIntent.LIST_REMINDERS -> {
                val now = LocalDateTime.now(clock)
                val values = reminders.list(command.periodStart, command.periodEnd).filter { reminder ->
                    reminder.remindAt?.isBefore(now) != true
                }
                ActionResult(
                    if (values.isEmpty()) "Напоминаний не найдено." else values.joinToString("\n") { "• ${describe(it)}" },
                    values,
                    effect = ActionEffect.RemindersListed(command.periodStart?.toLocalDate()),
                )
            }
            CommandIntent.DELETE_REMINDER -> {
                val target = reminders.find(command.targetId, command.targetText, command.targetLast)
                    ?: return ActionResult("Подходящее напоминание не найдено.")
                reminderActions.delete(target.id)
                ActionResult(
                    "Напоминание удалено: ${describe(target)}",
                    effect = ActionEffect.ReminderDeleted(target),
                )
            }
            CommandIntent.CREATE_NOTE -> {
                val note = notes.create(command.noteFolder, command.noteText.orEmpty())
                ActionResult(note.folderName?.let { "Заметка добавлена в папку «$it»: ${note.text}" } ?: "Заметка добавлена: ${note.text}", notes = listOf(note), effect = ActionEffect.NoteCreated(note))
            }
            CommandIntent.CREATE_FOLDER -> {
                val existing = notes.findFolder(command.noteFolder.orEmpty())
                val folder = existing ?: notes.createFolder(command.noteFolder.orEmpty())
                if (existing == null) ActionResult("Папка «${folder.name}» создана.", effect = ActionEffect.FolderCreated(folder))
                else ActionResult("Папка «${folder.name}» уже существует.")
            }
            CommandIntent.DELETE_FOLDER -> {
                val folder = notes.findFolder(command.noteFolder.orEmpty())
                    ?: return ActionResult("Папка «${command.noteFolder}» не найдена.")
                val snapshot = notes.deleteFolder(folder.id)
                    ?: return ActionResult("Папка «${folder.name}» уже удалена.")
                ActionResult("Папка «${folder.name}» удалена.", effect = ActionEffect.FolderDeleted(snapshot))
            }
            CommandIntent.UNKNOWN -> ActionResult("Не удалось распознать команду.")
        }
    }

    private fun describe(value: Reminder): String = "${value.text} — ${value.remindAt?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) ?: "без даты"}"
}

package ru.nezabudka.app.data

/** Identity and technical creation/order metadata do not identify the user's content. */
internal class ImportDuplicateIndex {
    private data class NoteKey(
        val text: String, val title: String, val primaryFolder: Long?, val folders: Set<Long>,
        val pinned: Boolean, val archived: Boolean, val trashed: Boolean, val color: String?,
    )
    private data class ReminderKey(
        val text: String, val date: java.time.LocalDateTime?, val recurrence: String?,
        val status: String, val favorite: Boolean, val completedAt: java.time.LocalDateTime?,
    )
    private val notes = mutableSetOf<NoteKey>()
    private val reminders = mutableSetOf<ReminderKey>()

    fun addNote(value: NoteEntity, folders: List<Long>): Boolean = notes.add(
        NoteKey(value.text, value.title, value.folderId, folders.toSet() + listOfNotNull(value.folderId),
            value.pinned, value.archived, value.trashed, value.color),
    )
    fun addReminder(value: ReminderEntity): Boolean = reminders.add(
        ReminderKey(value.text, value.remindAt, value.recurrence, value.status, value.favorite, value.completedAt),
    )
}

data class ManualRestoreResult(val added: Int, val skipped: Int, val backgroundReady: Boolean, val foldersAdded: Int = 0)

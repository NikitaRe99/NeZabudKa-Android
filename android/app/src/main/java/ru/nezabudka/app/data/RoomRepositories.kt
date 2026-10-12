package ru.nezabudka.app.data

import androidx.room.withTransaction
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.domain.DeletedNoteFolder
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.domain.normalizeReminderRecurrence
import ru.nezabudka.app.domain.normalizeSavedEntryText
import ru.nezabudka.app.ports.NotesRepository
import ru.nezabudka.app.ports.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.LocalDateTime

class RoomReminderRepository(private val dao: ReminderDao, private val clock: Clock = Clock.systemDefaultZone()) : ReminderRepository {
    override fun observeAll(): Flow<List<Reminder>> = dao.observeAll()
        .map { values -> values.map(ReminderEntity::domain) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
    override fun observe(id: Long): Flow<Reminder?> = dao.observeById(id)
        .map { it?.domain() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
    override suspend fun create(text: String, remindAt: LocalDateTime?, recurrence: String?): Reminder {
        val cleanText = normalizeSavedEntryText(text)
        require(cleanText.isNotBlank()) { "Текст напоминания не может быть пустым" }
        val entity = ReminderEntity(text = cleanText, remindAt = remindAt, recurrence = normalizeReminderRecurrence(recurrence), createdAt = LocalDateTime.now(clock), manualOrder = Long.MAX_VALUE)
        val id = dao.insert(entity)
        dao.setOrder(id, id)
        return entity.copy(id = id, manualOrder = id).domain()
    }
    override suspend fun list(start: LocalDateTime?, end: LocalDateTime?): List<Reminder> = dao.list(start, end).map(ReminderEntity::domain)
    override suspend fun find(id: Long?, text: String?, last: Boolean): Reminder? = when {
        id != null -> dao.findId(id); !text.isNullOrBlank() -> dao.findText(text); last -> dao.findLast(); else -> null
    }?.domain()
    override suspend fun update(id: Long, text: String?, remindAt: LocalDateTime?): Reminder {
        val current = dao.byId(id) ?: throw NoSuchElementException("Напоминание $id не найдено")
        val cleanText = text?.let(::normalizeSavedEntryText) ?: current.text
        require(cleanText.isNotBlank()) { "Текст напоминания не может быть пустым" }
        val updated = current.copy(text = cleanText, remindAt = remindAt ?: current.remindAt)
        dao.update(updated)
        return updated.domain()
    }
    override suspend fun replace(id: Long, text: String, remindAt: LocalDateTime?): Reminder {
        val cleanText = normalizeSavedEntryText(text)
        require(cleanText.isNotBlank()) { "Текст напоминания не может быть пустым" }
        val current = dao.byId(id) ?: throw NoSuchElementException("Напоминание $id не найдено")
        val updated = current.copy(text = cleanText, remindAt = remindAt)
        dao.update(updated)
        return updated.domain()
    }
    override suspend fun setRecurrence(id: Long, recurrence: String?): Reminder {
        dao.setRecurrence(id, normalizeReminderRecurrence(recurrence))
        return (dao.byId(id) ?: throw NoSuchElementException("Напоминание $id не найдено")).domain()
    }
    override suspend fun saveEditor(id: Long, text: String, remindAt: LocalDateTime?, recurrence: String?, favorite: Boolean, completed: Boolean): Reminder {
        val cleanText = normalizeSavedEntryText(text)
        require(cleanText.isNotBlank()) { "Текст напоминания не может быть пустым" }
        return dao.saveFromEditor(
            id = id,
            text = cleanText,
            remindAt = remindAt,
            recurrence = normalizeReminderRecurrence(recurrence),
            favorite = favorite,
            completed = completed,
            completedAt = if (completed) LocalDateTime.now(clock) else null,
        ).domain()
    }
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun restore(reminder: Reminder): Reminder {
        val entity = ReminderEntity(
            id = reminder.id,
            text = reminder.text,
            remindAt = reminder.remindAt,
            recurrence = reminder.recurrence,
            status = reminder.status,
            createdAt = reminder.createdAt,
            favorite = reminder.favorite,
            manualOrder = reminder.manualOrder,
            completedAt = reminder.completedAt,
        )
        dao.restore(entity)
        return entity.domain()
    }
    override suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    override suspend fun setCompleted(id: Long, completed: Boolean): Reminder {
        val current = dao.byId(id) ?: throw NoSuchElementException("Напоминание $id не найдено")
        val completedAt = if (completed) LocalDateTime.now(clock) else null
        val status = if (completed) "completed" else "active"
        dao.setCompleted(id, status, completedAt)
        return current.copy(status = status, completedAt = completedAt).domain()
    }
    override suspend fun move(id: Long, beforeId: Long?) {
        val updates = manualOrderUpdates(
            stored = dao.activeInManualOrder().map { it.id to it.manualOrder },
            movedId = id,
            beforeId = beforeId,
        )
        dao.setOrders(updates)
    }
    override suspend fun deleteCompleted() = dao.deleteCompleted()
}

class RoomNotesRepository(private val database: AppDatabase, private val clock: Clock = Clock.systemDefaultZone()) : NotesRepository {
    suspend fun byId(id: Long): Note? = database.notesDao().noteById(id)?.domain()
    private val dao = database.notesDao()
    override fun observeFolders(): Flow<List<NoteFolder>> = dao.observeFolders()
        .map { rows -> rows.map { NoteFolder(it.id, it.name, it.createdAt, it.noteCount, it.manualOrder) } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
    override fun observeNotes(folderId: Long): Flow<List<Note>> = dao.observeNotes(folderId)
        .map { rows -> rows.map(NoteRow::domain) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
    override fun observeAll(): Flow<List<Note>> = dao.observeAll()
        .map { rows -> rows.map(NoteRow::domain) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
    override suspend fun createFolder(folderName: String): NoteFolder = database.withTransaction {
        val clean = folderName.trim().trim('«', '»', '"').replace(Regex("\\s+"), " ")
        require(clean.isNotBlank()) { "Название папки не может быть пустым" }
        dao.allFolders().firstOrNull { it.name.equals(clean, true) }?.let { return@withTransaction NoteFolder(it.id, it.name, it.createdAt) }
        val entity = NoteFolderEntity(name = clean, createdAt = LocalDateTime.now(clock), manualOrder = Long.MAX_VALUE)
        val id = dao.insertFolder(entity)
        val stored = if (id == -1L) dao.allFolders().first { it.name.equals(clean, true) } else entity.copy(id = id, manualOrder = id).also { dao.setFolderOrder(id, id) }
        NoteFolder(stored.id, stored.name, stored.createdAt, manualOrder = stored.manualOrder)
    }
    override suspend fun findFolder(name: String): NoteFolder? = dao.allFolders()
        .firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        ?.let {
        NoteFolder(it.id, it.name, it.createdAt, manualOrder = it.manualOrder)
    }
    override suspend fun renameFolder(id: Long, name: String) = database.withTransaction {
        val clean = name.trim().trim('«', '»', '"').replace(Regex("\\s+"), " ")
        require(clean.isNotBlank()) { "Название папки не может быть пустым" }
        require(dao.allFolders().none { it.id != id && it.name.equals(clean, true) }) { "Папка с таким названием уже существует" }
        dao.renameFolder(id, clean)
    }
    override suspend fun moveFolder(id: Long, beforeId: Long?) = database.withTransaction {
        manualOrderUpdates(
            stored = dao.allFolders().map { it.id to it.manualOrder },
            movedId = id,
            beforeId = beforeId,
        ).forEach { update -> dao.setFolderOrder(update.id, update.order) }
    }
    override suspend fun moveNote(id: Long, beforeId: Long?) = database.withTransaction {
        manualOrderUpdates(
            stored = dao.allNotesInManualOrder().map { it.id to it.manualOrder },
            movedId = id,
            beforeId = beforeId,
        ).forEach { update -> dao.setNoteOrder(update.id, update.order) }
    }
    override suspend fun deleteFolder(id: Long): DeletedNoteFolder? = database.withTransaction {
        val entity = dao.allFolders().firstOrNull { it.id == id } ?: return@withTransaction null
        val snapshot = DeletedNoteFolder(
            NoteFolder(entity.id, entity.name, entity.createdAt, manualOrder = entity.manualOrder),
            dao.noteIdsForFolder(id),
        )
        dao.replacePrimaryFolder(id)
        dao.deleteFolder(id)
        snapshot
    }
    override suspend fun restoreFolder(snapshot: DeletedNoteFolder): NoteFolder = database.withTransaction {
        val folder = snapshot.folder
        val inserted = dao.insertFolder(NoteFolderEntity(folder.id, folder.name, folder.createdAt, folder.manualOrder))
        check(inserted != -1L) { "Не удалось восстановить папку «${folder.name}»" }
        if (snapshot.noteIds.isNotEmpty()) dao.insertRefs(snapshot.noteIds.map { NoteFolderRef(it, folder.id) })
        folder
    }
    override suspend fun deleteNote(id: Long) = dao.deleteNote(id)
    override suspend fun deleteTrashed() = dao.deleteTrashed()
    override suspend fun deleteTrashedBefore(cutoff: LocalDateTime) = dao.deleteTrashedBefore(cutoff)
    override suspend fun create(folderName: String?, text: String): Note = database.withTransaction {
        val cleanText = normalizeSavedEntryText(text)
        require(cleanText.isNotEmpty()) { "Текст заметки не может быть пустым" }
        val folder = folderName?.trim()?.takeIf { it.isNotEmpty() }?.let { createFolder(it) }
        val now = LocalDateTime.now(clock)
        val noteId = dao.insertNote(NoteEntity(folderId = folder?.id, text = cleanText, createdAt = now, updatedAt = now, manualOrder = Long.MAX_VALUE))
        dao.setNoteOrder(noteId, noteId)
        if (folder != null) dao.insertRefs(listOf(NoteFolderRef(noteId, folder.id)))
        checkNotNull(dao.noteById(noteId)).domain()
    }
    override suspend fun save(id: Long?, folderNames: List<String>, title: String, text: String, pinned: Boolean, archived: Boolean, color: String?): Note = database.withTransaction {
        val cleanTitle = normalizeSavedEntryText(title)
        val cleanText = normalizeSavedEntryText(text)
        if (id == null) require(cleanTitle.isNotEmpty() || cleanText.isNotEmpty()) { "Заметка не может быть пустой" }
        val cleanNames = folderNames.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val selectedFolders = cleanNames.map { createFolder(it) }
        val primaryFolder = selectedFolders.firstOrNull()
        val now = LocalDateTime.now(clock)
        val noteId = if (id == null) {
            dao.insertNote(NoteEntity(folderId = primaryFolder?.id, text = cleanText, title = cleanTitle, createdAt = now, updatedAt = now, pinned = pinned, archived = archived, color = color, manualOrder = Long.MAX_VALUE)).also { noteId ->
                dao.setNoteOrder(noteId, noteId)
            }
        } else {
            val old = dao.noteEntity(id) ?: throw NoSuchElementException("Заметка не найдена")
            dao.updateNote(old.copy(folderId = primaryFolder?.id, text = cleanText, title = cleanTitle, updatedAt = now, pinned = pinned, archived = archived, color = color)); id
        }
        dao.deleteRefs(noteId)
        if (selectedFolders.isNotEmpty()) dao.insertRefs(selectedFolders.map { NoteFolderRef(noteId, it.id) })
        checkNotNull(dao.noteById(noteId)).domain()
    }
    override suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned, LocalDateTime.now(clock))
    override suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived, LocalDateTime.now(clock))
    override suspend fun setTrashed(id: Long, trashed: Boolean) = dao.setTrashed(id, trashed, LocalDateTime.now(clock))
    override suspend fun setPinned(ids: List<Long>, pinned: Boolean) { if (ids.isNotEmpty()) dao.setPinned(ids, pinned, LocalDateTime.now(clock)) }
    override suspend fun setArchived(ids: List<Long>, archived: Boolean) { if (ids.isNotEmpty()) dao.setArchived(ids, archived, LocalDateTime.now(clock)) }
    override suspend fun setTrashed(ids: List<Long>, trashed: Boolean) { if (ids.isNotEmpty()) dao.setTrashed(ids, trashed, LocalDateTime.now(clock)) }
    override suspend fun setColor(ids: List<Long>, color: String?) { if (ids.isNotEmpty()) dao.setColor(ids, color, LocalDateTime.now(clock)) }
    override suspend fun setFolderMembership(ids: List<Long>, folderName: String, included: Boolean) = database.withTransaction {
        if (ids.isEmpty()) return@withTransaction
        val folder = findFolder(folderName) ?: if (included) createFolder(folderName) else return@withTransaction
        if (included) {
            dao.insertRefs(ids.map { NoteFolderRef(it, folder.id) })
            dao.fillPrimaryFolder(ids, folder.id)
        } else {
            dao.deleteFolderRefs(ids, folder.id)
            dao.repairPrimaryFolders(ids)
        }
    }
}

private fun ReminderEntity.domain() = Reminder(id, text, remindAt, recurrence, status, createdAt, favorite, manualOrder, completedAt)
private fun NoteRow.domain() = Note(
    id = id,
    folderId = folderId,
    folderName = folderName,
    text = text,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinned = pinned,
    archived = archived,
    trashed = trashed,
    color = color,
    folderIds = folderIds.split('|').mapNotNull(String::toLongOrNull),
    folderNames = folderNames.split('|').filter(String::isNotBlank),
    title = title,
    manualOrder = manualOrder,
)

package ru.nezabudka.app.ui

import ru.nezabudka.app.application.ActionEffect
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.ports.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val folders: List<NoteFolder> = emptyList(),
    val allNotes: List<Note> = emptyList(),
    val selectedFolderId: Long? = null,
)

sealed interface NoteUiEvent {
    data class SelectFolder(val id: Long?) : NoteUiEvent
    data class TogglePinned(val notes: List<Note>) : NoteUiEvent
    data class ToggleArchived(val notes: List<Note>) : NoteUiEvent
    data class ToggleTrashed(val note: Note) : NoteUiEvent
    data class MoveToTrash(val notes: List<Note>) : NoteUiEvent
    data class SetColor(val notes: List<Note>, val color: String?) : NoteUiEvent
    data class SetFolder(val notes: List<Note>, val folderName: String, val included: Boolean) : NoteUiEvent
    data object EmptyTrash : NoteUiEvent
}

internal class NotesFeature(
    private val repository: NotesRepository,
    private val scope: CoroutineScope,
    private val onNotice: (String, ActionEffect?) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    val folders: StateFlow<List<NoteFolder>> = repository.observeFolders()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allNotes: StateFlow<List<Note>> = repository.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selectedFolder = MutableStateFlow<Long?>(null)
    val state: StateFlow<NotesUiState> = combine(folders, allNotes, selectedFolder) {
            folderValues, noteValues, selectedId ->
        NotesUiState(folderValues, noteValues, selectedId)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun dispatch(event: NoteUiEvent): Job? = when (event) {
        is NoteUiEvent.SelectFolder -> {
            selectedFolder.value = event.id
            null
        }
        is NoteUiEvent.TogglePinned -> mutate {
            repository.setPinned(event.notes.map(Note::id), !event.notes.all(Note::pinned))
        }
        is NoteUiEvent.ToggleArchived -> mutate {
            repository.setArchived(event.notes.map(Note::id), !event.notes.all(Note::archived))
        }
        is NoteUiEvent.ToggleTrashed -> mutate {
            repository.setTrashed(event.note.id, !event.note.trashed)
        }
        is NoteUiEvent.MoveToTrash -> mutate { repository.setTrashed(event.notes.map(Note::id), true) }
        is NoteUiEvent.SetColor -> mutate { repository.setColor(event.notes.map(Note::id), event.color) }
        is NoteUiEvent.SetFolder -> mutate {
            repository.setFolderMembership(event.notes.map(Note::id), event.folderName, event.included)
        }
        NoteUiEvent.EmptyTrash -> mutate { repository.deleteTrashed() }
    }

    fun createFolder(name: String, publish: Boolean, onCreated: (NoteFolder) -> Unit): Job = scope.launch {
        runCatching {
            val existing = repository.findFolder(name)
            (existing ?: repository.createFolder(name)) to (existing == null)
        }.onSuccess { (folder, created) ->
            onCreated(folder)
            if (publish) {
                onNotice(
                    if (created) "Папка «${folder.name}» создана." else "Папка «${folder.name}» уже существует.",
                    if (created) ActionEffect.FolderCreated(folder) else null,
                )
            }
        }.onFailure(onError)
    }

    fun renameFolder(folder: NoteFolder, name: String): Job = mutate {
        repository.renameFolder(folder.id, name)
    }

    fun moveFolder(folder: NoteFolder, direction: Int): Job = mutate {
        val values = folders.value.sortedBy(NoteFolder::manualOrder)
        val from = values.indexOfFirst { it.id == folder.id }
        val to = (from + direction).coerceIn(0, values.lastIndex)
        if (from >= 0 && from != to) {
            val reordered = values.toMutableList().apply { add(to, removeAt(from)) }
            val newIndex = reordered.indexOfFirst { it.id == folder.id }
            repository.moveFolder(folder.id, reordered.getOrNull(newIndex + 1)?.id)
        }
    }

    fun moveNote(id: Long, beforeId: Long?): Job = mutate {
        repository.moveNote(id, beforeId)
    }

    fun deleteFolder(folder: NoteFolder): Job = scope.launch {
        runCatching { repository.deleteFolder(folder.id) }
            .onSuccess { snapshot ->
                if (snapshot != null) {
                    onNotice("Папка «${folder.name}» удалена.", ActionEffect.FolderDeleted(snapshot))
                }
            }
            .onFailure(onError)
    }

    fun save(
        id: Long?,
        folderNames: List<String>,
        title: String,
        text: String,
        pinned: Boolean,
        archived: Boolean,
        color: String?,
        onSaved: () -> Unit,
    ): Job = scope.launch {
        runCatching { repository.save(id, folderNames, title, text, pinned, archived, color) }
            .onSuccess { onSaved() }
            .onFailure(onError)
    }

    private fun mutate(block: suspend () -> Unit): Job = scope.launch {
        runCatching { block() }.onFailure(onError)
    }
}

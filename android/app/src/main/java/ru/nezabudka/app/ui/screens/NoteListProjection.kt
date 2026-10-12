package ru.nezabudka.app.ui.screens

import ru.nezabudka.app.domain.Note

internal fun sortNotesForDisplay(notes: List<Note>, sort: NoteSort): List<Note> =
    notes.sortedWith(
        when (sort) {
            NoteSort.MANUAL -> compareBy<Note> { it.manualOrder }.thenBy { it.id }
            NoteSort.CREATED -> compareByDescending<Note> { it.createdAt }
            NoteSort.UPDATED -> compareByDescending<Note> { it.updatedAt }
        },
    )

internal fun filterNotesForDisplay(
    sortedNotes: List<Note>,
    selectedFolder: Long?,
    section: NoteSection,
    query: String,
): List<Note> {
    val searching = query.isNotBlank()
    return sortedNotes.filter { note ->
        (section != NoteSection.ACTIVE || selectedFolder == null || selectedFolder in note.folderIds) &&
            when (section) {
                NoteSection.ACTIVE -> !note.archived && !note.trashed
                NoteSection.ARCHIVE -> note.archived && !note.trashed
                NoteSection.TRASH -> note.trashed
            } && (!searching || note.title.contains(query, true) || note.text.contains(query, true) ||
                note.folderNames.any { it.contains(query, true) })
    }
}

/** Bound text shaping in a scrolling card; the editor always receives the full note. */
internal fun noteCardPreview(text: String): String {
    val limit = 2_000
    if (text.length <= limit) return text
    val end = if (text[limit - 1].isHighSurrogate() && text[limit].isLowSurrogate()) limit - 1 else limit
    return text.substring(0, end) + "…"
}

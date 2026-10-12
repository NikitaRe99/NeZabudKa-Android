package ru.nezabudka.app.widget

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class WidgetPreferences(context: Context, preferenceName: String = "home_widgets") {
    private val prefs = context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    companion object { private val completedLock = Any() }
    private val json = Json { ignoreUnknownKeys = true }
    fun folder(id: Int): Long? = prefs.getLong("folder_$id", 0).takeIf { it > 0 }
    fun setFolder(id: Int, folderId: Long?) { prefs.edit().putLong("folder_$id", folderId ?: 0).commit() }
    internal fun undo(id: Int, includeExpired: Boolean = false): WidgetUndo? = prefs.getString("undo_$id", null)
        ?.let { runCatching { json.decodeFromString<WidgetUndo>(it) }.getOrNull() }
        ?.takeIf { includeExpired || it.expiresAt > System.currentTimeMillis() }
    internal fun setUndo(id: Int, undo: WidgetUndo?) {
        prefs.edit().apply { if (undo == null) remove("undo_$id") else putString("undo_$id", json.encodeToString(undo)) }.commit()
    }
    // Only repeat occurrences completed from a widget need a snapshot: the
    // database keeps the active next occurrence, not the completed previous one.
    internal fun completedOccurrences(today: java.time.LocalDate = java.time.LocalDate.now()): List<WidgetCompletedOccurrence> = synchronized(completedLock) {
        val stored = prefs.getString("completed_occurrences", null)?.let {
            runCatching { json.decodeFromString<List<WidgetCompletedOccurrence>>(it) }.getOrNull()
        }.orEmpty()
        val current = stored.filter { it.completedAt.startsWith(today.toString() + "T") }
        if (current.size != stored.size) saveCompleted(current)
        current
    }
    internal fun recordCompletion(undo: WidgetUndo) = synchronized(completedLock) {
        if (undo.afterStatus != "active" || undo.recurrence == null) return@synchronized
        saveCompleted((completedOccurrences() + WidgetCompletedOccurrence(undo, java.time.LocalDateTime.now().toString())).takeLast(500))
    }
    internal fun removeCompletion(token: String) = synchronized(completedLock) { saveCompleted(completedOccurrences().filterNot { it.undo.token == token }) }
    private fun saveCompleted(items: List<WidgetCompletedOccurrence>) {
        prefs.edit().putString("completed_occurrences", json.encodeToString(items)).commit()
    }
    fun delete(ids: IntArray) { prefs.edit().apply { ids.forEach { remove("folder_$it"); remove("undo_$it") } }.apply() }
    fun restore(oldIds: IntArray, newIds: IntArray) {
        val folders = oldIds.map(::folder)
        newIds.forEachIndexed { index, new -> setFolder(new, folders.getOrNull(index)) }
        delete(oldIds.filterNot { it in newIds }.toIntArray())
    }
}

@kotlinx.serialization.Serializable
internal data class WidgetCompletedOccurrence(val undo: WidgetUndo, val completedAt: String)

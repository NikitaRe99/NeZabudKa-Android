package ru.nezabudka.app.widget

import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.ReminderEntity
import java.time.LocalDate
import java.time.LocalDateTime

internal data class WidgetReminderRow(val item: ReminderEntity, val completed: Boolean = false, val token: String? = null) {
    val stableId: Long get() = if (!completed) item.id else if (token == null) -item.id else Long.MIN_VALUE + (token.hashCode().toLong() and 0xffffffffL)
}
internal data class WidgetReminderRows(val rows: List<WidgetReminderRow>, val activeCount: Int, val completedCount: Int)

internal suspend fun widgetReminderRows(database: AppDatabase, preferences: WidgetPreferences, today: LocalDate): WidgetReminderRows {
    val dao = database.reminderDao()
    val end = today.plusDays(1).atStartOfDay()
    val active = dao.widgetRows(end).map { WidgetReminderRow(it) }
    val completed = dao.widgetCompletedRows(today.atStartOfDay(), end).map { WidgetReminderRow(it, completed = true) }
    val repeats = preferences.completedOccurrences(today).mapNotNull { snapshot ->
        val undo = snapshot.undo
        val current = dao.byId(undo.id)?.takeIf { it.createdAt.toString() == undo.createdAt } ?: return@mapNotNull null
        val before = undo.beforeAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return@mapNotNull null
        val completedAt = runCatching { LocalDateTime.parse(snapshot.completedAt) }.getOrNull() ?: return@mapNotNull null
        WidgetReminderRow(current.copy(text = undo.text, remindAt = before, recurrence = undo.recurrence,
            status = "completed", completedAt = completedAt), true, undo.token)
    }
    val finished = (completed + repeats).sortedByDescending { it.item.completedAt }
    return WidgetReminderRows((active + finished).take(500), dao.widgetCount(end), dao.widgetCompletedCount(today.atStartOfDay(), end) + repeats.size)
}

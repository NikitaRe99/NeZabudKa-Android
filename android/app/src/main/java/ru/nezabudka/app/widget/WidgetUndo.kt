package ru.nezabudka.app.widget

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import ru.nezabudka.app.application.ReminderActions
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.RoomReminderRepository
import java.time.LocalDateTime
import java.util.UUID

@Serializable
internal data class WidgetUndo(
    val id: Long, val beforeAt: String?, val afterAt: String?, val afterStatus: String,
    val createdAt: String, val text: String, val recurrence: String?, val expiresAt: Long,
    val token: String,
)

internal suspend fun completeWidgetReminderWithUndo(database: AppDatabase, actions: ReminderActions, id: Long,
    expectedAt: String?, expectedCreatedAt: String, onCompleted: (WidgetUndo) -> Unit = {}): WidgetUndo? = database.withTransaction {
    val current = RoomReminderRepository(database.reminderDao()).find(id, null, false) ?: return@withTransaction null
    if (!widgetCompletionMatches(current, expectedAt, expectedCreatedAt)) return@withTransaction null
    val updated = actions.setCompleted(id, true)
    WidgetUndo(id, current.remindAt?.toString(), updated.remindAt?.toString(), updated.status,
        current.createdAt.toString(), current.text, current.recurrence, System.currentTimeMillis() + 10_000, UUID.randomUUID().toString()).also(onCompleted)
}

internal suspend fun undoWidgetReminder(database: AppDatabase, actions: ReminderActions, undo: WidgetUndo): Boolean = database.withTransaction {
    if (undo.expiresAt <= System.currentTimeMillis()) return@withTransaction false
    val entity = database.reminderDao().byId(undo.id) ?: return@withTransaction false
    if (entity.remindAt?.toString() != undo.afterAt || entity.status != undo.afterStatus ||
        entity.createdAt.toString() != undo.createdAt || entity.text != undo.text || entity.recurrence != undo.recurrence) return@withTransaction false
    if (entity.status == "completed") actions.setCompleted(undo.id, false)
    else {
        val current = RoomReminderRepository(database.reminderDao()).find(undo.id, null, false) ?: return@withTransaction false
        actions.reschedule(current, undo.beforeAt?.let(LocalDateTime::parse))
    }
    true
}

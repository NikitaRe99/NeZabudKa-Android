package ru.nezabudka.app.ui.screens

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.ui.ReminderSort

class ReminderListProjectionTest {
    private val now = LocalDateTime.of(2026, 10, 23, 12, 0)
    private fun item(id: Long, text: String, at: LocalDateTime?) = Reminder(id, text, at, null, "active", now, manualOrder = id)

    @Test fun cachedProjectionPreservesOverdueCompletionSearchAndDismissalRules() {
        val projector = ReminderListProjector()
        val values = listOf(item(1, "Вода", now.minusMinutes(1)), item(2, "Звонок", now.plusMinutes(1)),
            item(3, "Без даты", null), item(4, "Вода куплена", now).copy(status = "completed", completedAt = now))
        val first = projector.project(values, ReminderSort.DATE, emptyList(), "", now)
        assertEquals(listOf(1L), first.overdue.map { it.id })
        assertEquals(listOf(2L, 3L), first.active.map { it.id })
        assertEquals(listOf(4L), first.completed.map { it.id })
        val later = projector.project(values, ReminderSort.DATE, emptyList(), "", now.plusMinutes(2))
        assertEquals(listOf(1L, 2L), later.overdue.map { it.id })
        val searched = projector.project(values, ReminderSort.DATE, listOf(1), "ВОДА", now)
        assertTrue(searched.filteredActive.isEmpty())
        assertEquals(listOf(4L), searched.completed.map { it.id })
        val changed = projector.project(values.map { it.copy(text = "Новое") }, ReminderSort.TITLE, emptyList(), "ВОДА", now)
        assertTrue(changed.filteredActive.isEmpty())
        assertTrue(changed.completed.isEmpty())
    }
}

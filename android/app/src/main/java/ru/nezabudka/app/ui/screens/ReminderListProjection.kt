package ru.nezabudka.app.ui.screens

import java.time.LocalDateTime
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.ui.ReminderSort
import ru.nezabudka.app.ui.isOverdueAt

internal data class ReminderListProjection(
    val allActive: List<Reminder> = emptyList(), val allCompleted: List<Reminder> = emptyList(),
    val filteredActive: List<Reminder> = emptyList(), val overdue: List<Reminder> = emptyList(),
    val active: List<Reminder> = emptyList(), val completed: List<Reminder> = emptyList(),
)

internal class ReminderListProjector {
    private var source: List<Reminder>? = null
    private var order: ReminderSort? = null
    private var dismissedBefore: Set<Long> = emptySet()
    private var allActive = emptyList<Reminder>()
    private var allCompleted = emptyList<Reminder>()

    @Synchronized fun project(reminders: List<Reminder>, sort: ReminderSort, dismissed: Collection<Long>,
                              query: String, now: LocalDateTime): ReminderListProjection {
        val dismissedIds = dismissed.toHashSet()
        if (source !== reminders || order != sort || dismissedBefore != dismissedIds) {
            val comparator = when (sort) {
                ReminderSort.MY_ORDER -> compareBy<Reminder> { it.manualOrder }
                ReminderSort.DATE -> compareBy<Reminder> { it.remindAt == null }.thenBy { it.remindAt }
                ReminderSort.RECENTLY_MARKED -> compareByDescending<Reminder> { it.favorite }.thenBy { it.manualOrder }
                ReminderSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.text }
            }
            allActive = reminders.filter { it.status != "completed" && it.id !in dismissedIds }.sortedWith(comparator)
            allCompleted = reminders.filter { it.status == "completed" && it.id !in dismissedIds }.sortedByDescending { it.completedAt }
            source = reminders
            order = sort
            dismissedBefore = dismissedIds
        }
        val filtered = allActive.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }
        val (overdue, active) = filtered.partition { it.isOverdueAt(now) }
        return ReminderListProjection(allActive, allCompleted, filtered, overdue, active,
            allCompleted.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) })
    }
}

package ru.nezabudka.app.application

import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.domain.alignReminderOccurrence
import ru.nezabudka.app.domain.nextReminderOccurrence
import ru.nezabudka.app.domain.normalizeReminderRecurrence
import ru.nezabudka.app.ports.ReminderRepository
import ru.nezabudka.app.ports.ReminderScheduler
import java.time.Clock
import java.time.LocalDateTime

/** Keeps persistent reminder state and the matching system alarm in sync. */
class ReminderActions(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun create(
        text: String,
        remindAt: LocalDateTime?,
        recurrence: String? = null,
        favorite: Boolean = false,
    ): Reminder {
        val normalized = normalizeReminderRecurrence(recurrence)
        var created = reminders.create(text, remindAt?.let { alignReminderOccurrence(it, normalized) }, normalized)
        if (favorite) {
            reminders.setFavorite(created.id, true)
            created = created.copy(favorite = true)
        }
        scheduler.schedule(created)
        return created
    }

    suspend fun update(id: Long, text: String?, remindAt: LocalDateTime?): Reminder {
        val updated = reminders.update(id, text, remindAt)
        scheduler.cancel(id)
        scheduler.schedule(updated)
        return updated
    }

    suspend fun saveEditor(
        original: Reminder,
        text: String,
        remindAt: LocalDateTime?,
        recurrence: String?,
        favorite: Boolean,
        completed: Boolean,
    ): Reminder {
        val normalizedRecurrence = normalizeReminderRecurrence(recurrence)
        val now = LocalDateTime.now(clock)
        val alignedDate = remindAt?.let { alignReminderOccurrence(it, normalizedRecurrence) }
        val effectiveRemindAt = if (
            normalizedRecurrence != null &&
            normalizedRecurrence != normalizeReminderRecurrence(original.recurrence) &&
            remindAt != null &&
            !remindAt.isAfter(now)
        ) {
            nextReminderOccurrence(remindAt, normalizedRecurrence, now) ?: remindAt
        } else {
            alignedDate
        }
        val updated = reminders.saveEditor(
            original.id,
            text,
            effectiveRemindAt,
            normalizedRecurrence,
            favorite,
            completed && !(normalizedRecurrence != null && effectiveRemindAt != null),
        )
        if (completed) {
            return setCompleted(updated.id, true)
        } else if (
            original.status == "completed" ||
            original.remindAt != updated.remindAt ||
            original.recurrence != updated.recurrence
        ) {
            scheduler.cancel(updated.id)
            scheduler.schedule(updated)
        }
        return updated
    }

    suspend fun reschedule(reminder: Reminder, remindAt: LocalDateTime?): Reminder {
        scheduler.cancel(reminder.id)
        var updated = reminders.reschedule(reminder.id, reminder.text, remindAt)
        if (remindAt == null && updated.recurrence != null) {
            updated = reminders.setRecurrence(updated.id, null)
        }
        scheduler.schedule(updated)
        return updated
    }

    suspend fun setCompleted(id: Long, completed: Boolean): Reminder {
        if (completed) {
            val current = reminders.find(id, null, false) ?: throw NoSuchElementException("Напоминание $id не найдено")
            val date = current.remindAt
            if (current.status != "completed" && date != null) {
                nextReminderOccurrence(date, current.recurrence, maxOf(date, LocalDateTime.now(clock)))?.let { next ->
                    scheduler.cancel(id)
                    return reminders.reschedule(id, current.text, next).also(scheduler::schedule)
                }
            }
        }
        var updated = reminders.setCompleted(id, completed)
        if (completed) {
            scheduler.cancel(id)
        } else {
            val date = updated.remindAt
            if (date != null && !date.isAfter(LocalDateTime.now(clock))) {
                nextReminderOccurrence(date, updated.recurrence, LocalDateTime.now(clock))?.let { next ->
                    updated = reminders.reschedule(updated.id, updated.text, next)
                }
            }
            scheduler.schedule(updated)
        }
        return updated
    }

    /** Alarm delivery may already have advanced the row. Completing that notification is idempotent. */
    suspend fun completeFromNotification(id: Long): Reminder? {
        val current = reminders.find(id, null, false) ?: return null
        if (normalizeReminderRecurrence(current.recurrence) != null &&
            current.remindAt?.isAfter(LocalDateTime.now(clock).plusMinutes(1)) == true) return current
        return setCompleted(id, true)
    }

    suspend fun rescheduleWithRecurrence(reminder: Reminder, date: LocalDateTime?, recurrence: String?): Reminder {
        scheduler.cancel(reminder.id)
        val normalized = if (date == null) null else normalizeReminderRecurrence(recurrence)
        var updated = reminders.reschedule(reminder.id, reminder.text, date?.let { alignReminderOccurrence(it, normalized) })
        if (updated.recurrence != normalized) updated = reminders.setRecurrence(updated.id, normalized)
        scheduler.schedule(updated)
        return updated
    }

    suspend fun advanceRecurring(id: Long, now: LocalDateTime = LocalDateTime.now(clock)): Reminder? {
        val current = reminders.find(id, null, false) ?: return null
        val date = current.remindAt ?: return null
        if (date.isAfter(now.plusMinutes(1))) return current
        val next = nextReminderOccurrence(date, current.recurrence, now) ?: return null
        scheduler.cancel(id)
        return reminders.reschedule(id, current.text, next).also(scheduler::schedule)
    }

    suspend fun delete(id: Long) {
        reminders.delete(id)
        scheduler.cancel(id)
    }

    suspend fun deleteAll(ids: Iterable<Long>) {
        ids.distinct().forEach { id -> delete(id) }
    }

    suspend fun restore(reminder: Reminder): Reminder = reminders.restore(reminder).also { restored ->
        if (restored.status == "active") scheduler.schedule(restored)
    }
}

class RestoreReminderSchedules(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend operator fun invoke(skipIfSchedulesIntact: Boolean = false) {
        val now = LocalDateTime.now(clock)
        val activeReminders = reminders.list()

        activeReminders.forEach { reminder ->
            val date = reminder.remindAt ?: return@forEach
            if (!date.isAfter(now)) {
                nextReminderOccurrence(date, reminder.recurrence, now)?.let { next ->
                    reminders.reschedule(reminder.id, reminder.text, next).also(scheduler::schedule)
                }
            }
        }

        val futureReminders = activeReminders.filter { reminder -> reminder.remindAt?.isAfter(now) == true }
        if (skipIfSchedulesIntact && futureReminders.firstOrNull()?.let { scheduler.isScheduled(it.id) } == true) {
            return
        }
        futureReminders.forEach(scheduler::schedule)
    }
}

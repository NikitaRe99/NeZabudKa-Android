package ru.nezabudka.app.ui

import ru.nezabudka.app.application.ReminderActions
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.ports.ReminderRepository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ReminderUiEvent {
    data class Delete(val id: Long) : ReminderUiEvent
    data class Restore(val reminder: Reminder) : ReminderUiEvent
    data class ToggleFavorite(val reminder: Reminder) : ReminderUiEvent
    data class ToggleCompleted(val reminder: Reminder) : ReminderUiEvent
    data class SetCompleted(val id: Long, val completed: Boolean) : ReminderUiEvent
    data class DeleteOverdue(val now: LocalDateTime) : ReminderUiEvent
    data object DeleteCompleted : ReminderUiEvent
}

/** Owns reminder-specific concurrency so editor saves cannot overwrite newer actions. */
internal class ReminderFeature(
    repository: ReminderRepository,
    private val actions: ReminderActions,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit,
    private val onReminderCreated: (Reminder) -> Unit = {},
) {
    val reminders: StateFlow<List<Reminder>> = repository.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val reminderRepository = repository
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val revisions = ConcurrentHashMap<Long, AtomicLong>()

    fun observe(id: Long) = reminderRepository.observe(id)

    fun dispatch(event: ReminderUiEvent): Job = when (event) {
        is ReminderUiEvent.Delete -> mutate(event.id) { actions.delete(event.id) }
        is ReminderUiEvent.Restore -> mutate(event.reminder.id) { actions.restore(event.reminder) }
        is ReminderUiEvent.ToggleFavorite -> mutate(event.reminder.id) {
            reminderRepository.setFavorite(event.reminder.id, !event.reminder.favorite)
        }
        is ReminderUiEvent.ToggleCompleted -> mutate(event.reminder.id) {
            actions.setCompleted(event.reminder.id, event.reminder.status != "completed")
        }
        is ReminderUiEvent.SetCompleted -> mutate(event.id) {
            actions.setCompleted(event.id, event.completed)
        }
        is ReminderUiEvent.DeleteOverdue -> deleteAll(
            reminders.value.filter { it.isOverdueAt(event.now) }.map(Reminder::id),
        )
        ReminderUiEvent.DeleteCompleted -> scope.launch {
            runCatching { reminderRepository.deleteCompleted() }.onFailure(onError)
        }
    }

    fun create(
        text: String,
        remindAt: LocalDateTime?,
        recurrence: String?,
        favorite: Boolean,
        onCreated: () -> Unit,
    ): Job = scope.launch {
        runCatching { actions.create(text, remindAt, recurrence, favorite) }
            .onSuccess { reminder -> onReminderCreated(reminder); onCreated() }
            .onFailure(onError)
    }

    fun save(
        reminder: Reminder,
        text: String,
        remindAt: LocalDateTime?,
        recurrence: String?,
        favorite: Boolean,
        completed: Boolean,
        onSaved: () -> Unit,
    ): Job {
        val revision = nextRevision(reminder.id)
        return scope.launch {
            var applied = false
            runCatching {
                lock(reminder.id).withLock {
                    if (revision != currentRevision(reminder.id)) return@withLock
                    actions.saveEditor(reminder, text, remindAt, recurrence, favorite, completed)
                    applied = true
                }
            }.onSuccess {
                if (applied && revision == currentRevision(reminder.id)) onSaved()
            }.onFailure(onError)
        }
    }

    fun reschedule(reminder: Reminder, remindAt: LocalDateTime?, onSaved: () -> Unit): Job =
        mutate(reminder.id, onSuccess = onSaved) { actions.reschedule(reminder, remindAt) }

    fun rescheduleWithRecurrence(reminder: Reminder, remindAt: LocalDateTime?, recurrence: String?, onSaved: () -> Unit): Job =
        mutate(reminder.id, onSuccess = onSaved) { actions.rescheduleWithRecurrence(reminder, remindAt, recurrence) }

    fun move(id: Long, beforeId: Long?): Job =
        mutate(id) { reminderRepository.move(id, beforeId) }

    private fun mutate(id: Long, onSuccess: () -> Unit = {}, block: suspend () -> Unit): Job {
        nextRevision(id)
        return scope.launch {
            runCatching { lock(id).withLock { block() } }
                .onSuccess { onSuccess() }
                .onFailure(onError)
        }
    }

    private fun deleteAll(ids: List<Long>): Job {
        ids.forEach(::nextRevision)
        return scope.launch {
            runCatching {
                ids.forEach { id -> lock(id).withLock { actions.delete(id) } }
            }.onFailure(onError)
        }
    }

    private fun lock(id: Long) = locks.computeIfAbsent(id) { Mutex() }
    private fun nextRevision(id: Long) = revisions.computeIfAbsent(id) { AtomicLong() }.incrementAndGet()
    private fun currentRevision(id: Long) = revisions[id]?.get() ?: 0L
}

package ru.nezabudka.app.application

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ReminderActionsTest {
    @Test fun `completing a future monthly occurrence keeps the reminder active`() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC)
        val actions = ReminderActions(repository, scheduler, clock)
        val date = LocalDateTime.of(2026, 10, 12, 9, 0)
        val reminder = repository.create("поливать цветы", date, "monthly")
        val updated = actions.setCompleted(reminder.id, true)
        assertEquals("active", updated.status)
        assertEquals(date.plusMonths(1), updated.remindAt)
        assertEquals("monthly", updated.recurrence)
        assertEquals(listOf(reminder.id), scheduler.scheduled)
    }

    @Test fun `notification completion does not advance an already advanced occurrence twice`() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneOffset.UTC)
        val actions = ReminderActions(repository, scheduler, clock)
        val date = LocalDateTime.of(2026, 10, 12, 9, 0)
        val reminder = repository.create("поливать цветы", date, "monthly")
        val first = actions.completeFromNotification(reminder.id)!!
        assertEquals(date.plusMonths(1), first.remindAt)
        assertEquals(first, actions.completeFromNotification(reminder.id))
        assertEquals("active", repository.values.single().status)
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test fun `last finite occurrence completes instead of disappearing without a schedule`() = runBlocking {
        val date = LocalDateTime.of(2026, 10, 12, 9, 0)
        val clock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneOffset.UTC)
        val actions = ReminderActions(repository, scheduler, clock)
        val rule = ru.nezabudka.app.domain.CustomReminderRecurrence(1, ru.nezabudka.app.domain.ReminderRecurrenceUnit.DAY, emptySet(), date, repeatCount = 1).storedValue()
        val reminder = repository.create("проверить фильтр", date, rule)
        assertEquals(date.plusDays(1), actions.setCompleted(reminder.id, true).remindAt)
        assertEquals("completed", actions.setCompleted(reminder.id, true).status)
    }

    @Test fun `editor completion and changed schedule preserve the repeat rule`() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC)
        val actions = ReminderActions(repository, scheduler, clock)
        val date = LocalDateTime.of(2026, 10, 12, 9, 0)
        val reminder = repository.create("поливать цветы", date, "monthly")
        val updated = actions.saveEditor(reminder, reminder.text, date, "monthly", false, true)
        assertEquals("active", updated.status)
        assertEquals(date.plusMonths(1), updated.remindAt)
        val changed = actions.rescheduleWithRecurrence(updated, date.plusDays(1), "weekly")
        assertEquals("weekly", changed.recurrence)
        assertEquals(date.plusDays(1), changed.remindAt)
    }
    private val repository = FakeReminderRepository()
    private val scheduler = FakeScheduler()
    private val actions = ReminderActions(repository, scheduler)

    @Test
    fun `create update completion and delete keep alarm in sync`() = runBlocking {
        val initialTime = LocalDateTime.of(2026, 7, 27, 9, 0)
        val created = actions.create("Позвонить", initialTime, favorite = true)
        assertTrue(created.favorite)
        assertEquals(listOf(created.id), scheduler.scheduled)

        val changedTime = initialTime.plusHours(2)
        val updated = actions.update(created.id, "Позвонить Кате", changedTime)
        assertEquals("Позвонить Кате", updated.text)
        assertEquals(listOf(created.id), scheduler.cancelled)

        actions.setCompleted(created.id, true)
        assertEquals("completed", repository.values.single().status)
        actions.setCompleted(created.id, false)
        assertEquals("active", repository.values.single().status)

        actions.delete(created.id)
        assertTrue(repository.values.isEmpty())
        assertTrue(scheduler.cancelled.count { it == created.id } >= 3)
    }

    @Test
    fun `restored active reminder is scheduled and completed reminder is not`() = runBlocking {
        val active = repository.create("Активное", LocalDateTime.of(2026, 7, 28, 10, 0), null)
        repository.delete(active.id)
        val restoredActive = actions.restore(active)
        assertTrue(restoredActive.id in scheduler.scheduled)

        val completed = active.copy(id = 20, status = "completed")
        val restoredCompleted = actions.restore(completed)
        assertFalse(restoredCompleted.id in scheduler.scheduled)
    }

    @Test
    fun `snooze replaces alarm with the selected time`() = runBlocking {
        val reminder = repository.create("Позвонить", LocalDateTime.of(2026, 8, 9, 9, 0), null)
        val snoozedUntil = LocalDateTime.of(2026, 8, 9, 9, 30)

        val snoozed = actions.reschedule(reminder, snoozedUntil)

        assertEquals(snoozedUntil, snoozed.remindAt)
        assertEquals(snoozedUntil, repository.values.single().remindAt)
        assertEquals(listOf(reminder.id), scheduler.cancelled)
        assertEquals(listOf(reminder.id), scheduler.scheduled)
    }

    @Test
    fun `bulk delete removes every reminder and cancels its alarm`() = runBlocking {
        val first = repository.create("Первое", LocalDateTime.of(2026, 7, 25, 10, 0), null)
        val second = repository.create("Второе", LocalDateTime.of(2026, 7, 25, 11, 0), null)

        actions.deleteAll(listOf(first.id, second.id, first.id))

        assertTrue(repository.values.isEmpty())
        assertEquals(listOf(first.id, second.id), scheduler.cancelled)
    }

    @Test
    fun `schedule restoration includes only future dated reminders`() = runBlocking {
        repository.create("Прошлое", LocalDateTime.of(2026, 7, 25, 10, 0), null)
        val repeating = repository.create("Ежедневное", LocalDateTime.of(2026, 7, 25, 8, 0), "daily")
        val future = repository.create("Будущее", LocalDateTime.of(2026, 7, 27, 10, 0), null)
        repository.create("Без даты", null, null)
        val clock = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC)

        RestoreReminderSchedules(repository, scheduler, clock)()

        assertEquals(listOf(repeating.id, future.id), scheduler.scheduled)
        assertEquals(LocalDateTime.of(2026, 7, 27, 8, 0), repository.values.first { it.id == repeating.id }.remindAt)
    }

    @Test
    fun `startup restoration skips alarm manager writes when schedules are intact`() = runBlocking {
        val future = repository.create("Будущее", LocalDateTime.of(2026, 7, 27, 10, 0), null)
        val clock = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC)
        scheduler.schedule(future)

        RestoreReminderSchedules(repository, scheduler, clock)(skipIfSchedulesIntact = true)

        assertEquals(listOf(future.id), scheduler.scheduled)
    }

    @Test
    fun `trigger advances recurring reminder and keeps it active`() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC)
        val recurringActions = ReminderActions(repository, scheduler, clock)
        val reminder = repository.create("Лекарство", LocalDateTime.of(2026, 7, 29, 9, 0), "weekdays")

        val advanced = recurringActions.advanceRecurring(reminder.id)

        assertEquals(LocalDateTime.of(2026, 7, 30, 9, 0), advanced?.remindAt)
        assertEquals("active", advanced?.status)
        assertEquals(listOf(reminder.id), scheduler.scheduled)
    }

    @Test
    fun `adding recurrence to overdue reminder moves it to next occurrence`() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
        val recurringActions = ReminderActions(repository, scheduler, clock)
        val reminder = repository.create("Лекарство", LocalDateTime.of(2026, 7, 28, 9, 0), null)

        val updated = recurringActions.saveEditor(
            original = reminder,
            text = reminder.text,
            remindAt = reminder.remindAt,
            recurrence = "daily",
            favorite = false,
            completed = false,
        )

        assertEquals(LocalDateTime.of(2026, 7, 31, 9, 0), updated.remindAt)
        assertEquals("daily", updated.recurrence)
        assertEquals(listOf(reminder.id), scheduler.scheduled)
    }
}

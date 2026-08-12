package ru.nezabudka.app.notifications

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ru.nezabudka.app.application.ReminderActions
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.RoomReminderRepository
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.domain.ALARM_PRESENTATION_NOTIFICATION
import ru.nezabudka.app.domain.ALARM_PRESENTATION_SCREEN
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** Small entry points used by the real-device verification script. */
@RunWith(AndroidJUnit4::class)
class ReminderDeviceScenarioInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = RoomReminderRepository(AppDatabase.get(context).reminderDao())
    private val scheduler = AndroidReminderScheduler(context)
    private val actions = ReminderActions(repository, scheduler)
    private val settings = SettingsStore(context)

    @Before
    fun requireExplicitDeviceScenarioRun() {
        assumeTrue(
            "Device scenarios create real reminders and must be requested explicitly",
            InstrumentationRegistry.getArguments().getString(DEVICE_SCENARIOS_ENABLED) == "true",
        )
    }

    @Test
    fun scheduleOrdinaryNotification() {
        schedule(
            label = "ordinary",
            delaySeconds = 20,
            alarmMode = false,
            presentation = ALARM_PRESENTATION_NOTIFICATION,
        )
        holdForDeviceVerificationIfRequested()
    }

    @Test
    fun scheduleLockedAlarmScreen() {
        schedule(
            label = "locked-screen",
            delaySeconds = 25,
            alarmMode = true,
            presentation = ALARM_PRESENTATION_SCREEN,
        )
        holdForDeviceVerificationIfRequested()
    }

    @Test
    fun scheduleReminderAcrossReboot() {
        schedule(
            label = "reboot",
            delaySeconds = 150,
            alarmMode = false,
            presentation = ALARM_PRESENTATION_NOTIFICATION,
        )
        holdForDeviceVerificationIfRequested()
    }

    @Test
    fun cleanupDeviceScenarios() = runBlocking {
        repository.list().filter { it.text.startsWith(MARKER) }.forEach { actions.delete(it.id) }
        settings.setReminderAlarmMode(false)
        settings.setReminderAlarmPresentation(ALARM_PRESENTATION_SCREEN)
    }

    @Test
    fun resetPermissionPromptHistory() {
        assertTrue(context.getSharedPreferences("permission_prompts", Context.MODE_PRIVATE).edit().clear().commit())
    }

    private fun schedule(
        label: String,
        delaySeconds: Long,
        alarmMode: Boolean,
        presentation: String,
    ) = runBlocking {
        repository.list().filter { it.text.startsWith(MARKER) }.forEach { actions.delete(it.id) }
        settings.setReminderAlarmMode(alarmMode)
        settings.setReminderAlarmPresentation(presentation)
        val reminder = actions.create(
            text = "$MARKER $label",
            remindAt = LocalDateTime.now().plusSeconds(delaySeconds),
        )
        assertTrue(scheduler.isScheduled(reminder.id))
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2,
            Bundle().apply {
                putString("scenario", label)
                putLong("reminder_id", reminder.id)
                putString("remind_at", reminder.remindAt.toString())
            },
        )
    }

    private fun holdForDeviceVerificationIfRequested() {
        val seconds = InstrumentationRegistry.getArguments()
            .getString("device_verification_hold_seconds")
            ?.toLongOrNull()
            ?.coerceIn(0L, 180L)
            ?: return
        SystemClock.sleep(seconds * 1_000L)
    }

    private companion object {
        const val DEVICE_SCENARIOS_ENABLED = "run_device_scenarios"
        const val MARKER = "[DEVICE-SCENARIO]"
    }
}

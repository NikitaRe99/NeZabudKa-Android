package ru.nezabudka.app.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ru.nezabudka.app.domain.UI_PERFORMANCE_AUTO
import ru.nezabudka.app.domain.UI_PERFORMANCE_REDUCED
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreSttTest {
    @Test
    fun ui_performance_mode_is_persisted_and_restored() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedPreferencesContext(base)
        context.clear()
        try {
            val store = SettingsStore(context)
            assertEquals(UI_PERFORMANCE_AUTO, store.state.value.uiPerformanceMode)

            store.setUiPerformanceMode(UI_PERFORMANCE_REDUCED)
            assertEquals(UI_PERFORMANCE_REDUCED, SettingsStore(context).state.value.uiPerformanceMode)

            store.restorePortableSettings(PortableAppSettings())
            assertEquals(UI_PERFORMANCE_AUTO, store.state.value.uiPerformanceMode)
        } finally {
            context.clear()
        }
    }

    @Test
    fun whisper_model_retention_is_optional_and_persisted() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedPreferencesContext(base)
        context.clear()
        try {
            val store = SettingsStore(context)
            assertFalse(store.state.value.keepWhisperModelLoaded)

            store.setKeepWhisperModelLoaded(true)
            assertTrue(store.state.value.keepWhisperModelLoaded)
            assertTrue(SettingsStore(context).state.value.keepWhisperModelLoaded)

            store.restorePortableSettings(
                PortableAppSettings(
                    keepWhisperModelLoaded = false,
                ),
            )
            assertFalse(store.state.value.keepWhisperModelLoaded)
        } finally {
            context.clear()
        }
    }

    @Test
    fun technical_log_visibility_is_persisted_and_restored() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedPreferencesContext(base)
        context.clear()
        try {
            val store = SettingsStore(context)
            assertFalse(store.state.value.showTechnicalLog)

            store.setShowTechnicalLog(true)
            assertTrue(store.state.value.showTechnicalLog)
            assertTrue(SettingsStore(context).state.value.showTechnicalLog)

            store.restorePortableSettings(PortableAppSettings(showTechnicalLog = false))
            assertFalse(store.state.value.showTechnicalLog)
        } finally {
            context.clear()
        }
    }

    @Test
    fun reminder_alarm_cycle_and_wake_settings_are_persisted_and_restored() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedPreferencesContext(base)
        context.clear()
        try {
            val store = SettingsStore(context)
            assertFalse(store.state.value.notificationWakeScreen)

            store.setNotificationWakeScreen(true)
            store.setReminderAlarmRingSeconds(30)
            store.setReminderAlarmPauseMinutes(3)
            store.setReminderAlarmRepeatCount(5)

            val persisted = SettingsStore(context).state.value
            assertTrue(persisted.notificationWakeScreen)
            assertEquals(30, persisted.reminderAlarmRingSeconds)
            assertEquals(3, persisted.reminderAlarmPauseMinutes)
            assertEquals(5, persisted.reminderAlarmRepeatCount)

            store.restorePortableSettings(PortableAppSettings())
            assertFalse(store.state.value.notificationWakeScreen)
            assertEquals(60, store.state.value.reminderAlarmRingSeconds)
            assertEquals(5, store.state.value.reminderAlarmPauseMinutes)
            assertEquals(3, store.state.value.reminderAlarmRepeatCount)
        } finally {
            context.clear()
        }
    }

    private class IsolatedPreferencesContext(base: Context) : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(TEST_PREFERENCES, mode)

        fun clear() {
            super.getSharedPreferences(TEST_PREFERENCES, MODE_PRIVATE).edit().clear().commit()
        }
    }

    private companion object {
        const val TEST_PREFERENCES = "settings_store_stt_test"
    }
}

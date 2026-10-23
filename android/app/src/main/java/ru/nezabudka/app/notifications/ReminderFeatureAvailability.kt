package ru.nezabudka.app.notifications

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nezabudka.app.application.RestoreReminderSchedules
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.RoomReminderRepository
import ru.nezabudka.app.data.SettingsStore

/** Serialize rapid toggles and read the latest persisted choice inside the lock. */
internal object ReminderFeatureAvailability {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            lock.withLock {
                runCatching {
                    val scheduler = AndroidReminderScheduler(app)
                    if (!SettingsStore.remindersEnabled(app)) {
                        NotificationChannels.cancelAllReminders(app)
                        scheduler.cancel(ReminderDisplayTest.ID)
                    }
                    RestoreReminderSchedules(RoomReminderRepository(AppDatabase.get(app).reminderDao()), scheduler)()
                }.onFailure { Log.e("ReminderAvailability", "Не удалось обновить расписание напоминаний", it) }
            }
        }
    }
}

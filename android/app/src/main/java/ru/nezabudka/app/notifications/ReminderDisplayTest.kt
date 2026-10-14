package ru.nezabudka.app.notifications

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import ru.nezabudka.app.domain.Reminder
import java.time.LocalDateTime
import ru.nezabudka.app.R

/** A reserved synthetic reminder: never inserted into Room or repeated. */
object ReminderDisplayTest {
    const val ID = Long.MAX_VALUE - 65L
    const val LOCK_SCREEN_DELAY_SECONDS = 5
    fun start(context: Context, delaySeconds: Int) {
        require(delaySeconds == 0 || delaySeconds == LOCK_SCREEN_DELAY_SECONDS)
        require(NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            "Разрешите уведомления в настройках напоминаний"
        }
        cancel(context)
        if (delaySeconds == 0) {
            context.sendBroadcast(Intent(context, ReminderAlarmReceiver::class.java)
                .putExtra(EXTRA_ID, ID).putExtra(EXTRA_TEXT, context.getString(R.string.reminder_display_test_text)))
        } else {
            val alarms = context.getSystemService(android.app.AlarmManager::class.java)
            require(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                "Для проверки на экране блокировки разрешите точное время срабатывания"
            }
            val now = LocalDateTime.now()
            AndroidReminderScheduler(context).schedule(Reminder(ID, context.getString(R.string.reminder_display_test_text),
                now.plusSeconds(delaySeconds.toLong()), null, "active", now))
        }
    }
    fun cancel(context: Context) {
        if (AlarmSessionCoordinator.isActive(context, ID)) ReminderOverlayService.dismiss(context)
        AndroidReminderScheduler(context).cancel(ID)
    }
}

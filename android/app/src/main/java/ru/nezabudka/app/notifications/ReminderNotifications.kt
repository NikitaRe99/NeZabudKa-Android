package ru.nezabudka.app.notifications

import android.Manifest
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.media.AudioAttributes
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.RoomReminderRepository
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.domain.normalizeReminderRecurrence
import ru.nezabudka.app.application.ReminderActions
import ru.nezabudka.app.application.RestoreReminderSchedules
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.domain.ALARM_PRESENTATION_SCREEN
import ru.nezabudka.app.ports.ReminderScheduler
import ru.nezabudka.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private const val REMINDER_CHANNEL_PREFIX = "scheduled_reminders"
internal const val EXTRA_ID = "reminder_id"
internal const val EXTRA_TEXT = "reminder_text"
private const val EXTRA_RECURRENCE = "reminder_recurrence"
internal const val EXTRA_SNOOZE_AT = "snooze_at"
const val EXTRA_OPEN_REMINDER_ID = "open_reminder_id"
const val EXTRA_SNOOZE_REMINDER_ID = "snooze_reminder_id"
internal const val ACTION_COMPLETE = "ru.nezabudka.app.action.COMPLETE_REMINDER"
internal const val ACTION_SNOOZE = "ru.nezabudka.app.action.SNOOZE_REMINDER"
private const val ACTION_OPEN_ALARM = "ru.nezabudka.app.action.OPEN_ALARM"
private const val ACTION_ALARM_RETRY = "ru.nezabudka.app.action.ALARM_RETRY"
private const val EXTRA_ALARM_ATTEMPT = "alarm_attempt"
private const val NOTIFICATION_CHANNEL_VERSION = 7
private val NOTIFICATION_VIBRATION = longArrayOf(0L, 250L, 150L, 250L)
internal val ALARM_VIBRATION = longArrayOf(0L, 800L, 400L, 800L, 400L, 800L)

object NotificationChannels {
    fun create(context: Context) {
        val settings = SettingsStore.currentReminderNotificationSettings(context)
        val currentChannelIds = setOf(
            ensure(
                context = context,
                alarmMode = false,
                sound = settings.notificationSound,
                vibrationEnabled = settings.notificationVibrationEnabled,
            ),
            ensure(
                context = context,
                alarmMode = true,
                sound = settings.reminderAlarmSound.ifBlank { "content://settings/system/alarm_alert" },
                vibrationEnabled = settings.notificationVibrationEnabled,
            ),
            ensure(
                context = context,
                alarmMode = true,
                headsUpMode = true,
                sound = settings.reminderAlarmSound.ifBlank { "content://settings/system/alarm_alert" },
                vibrationEnabled = settings.notificationVibrationEnabled,
            ),
            ensure(
                context = context,
                alarmMode = true,
                overlayMode = true,
                sound = settings.reminderAlarmSound.ifBlank { "content://settings/system/alarm_alert" },
                vibrationEnabled = settings.notificationVibrationEnabled,
            ),
        )
        removeObsoleteChannels(context, currentChannelIds)
        if (!settings.reminderAlarmMode) cancelActiveAlarms(context)
    }

    internal fun ensure(
        context: Context,
        alarmMode: Boolean,
        headsUpMode: Boolean = false,
        overlayMode: Boolean = false,
        sound: String,
        vibrationEnabled: Boolean,
    ): String {
        val vibrationPattern = if (alarmMode) ALARM_VIBRATION else NOTIFICATION_VIBRATION
        val channelMode = when {
            overlayMode -> "alarm_overlay_service_silent"
            headsUpMode -> "alarm_heads_up"
            alarmMode -> "alarm"
            else -> "notification"
        }
        val soundKey = if (overlayMode) 0 else sound.hashCode()
        val channelId = "${REMINDER_CHANNEL_PREFIX}_${channelMode}_v${NOTIFICATION_CHANNEL_VERSION}_${soundKey}_${vibrationEnabled}"
        val channel = NotificationChannel(
                channelId,
                when {
                    overlayMode -> "Сигнал компактного окна"
                    headsUpMode -> "Будильники поверх приложений"
                    alarmMode -> "Будильники"
                    else -> "Напоминания"
                },
                if (overlayMode) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = when {
                    overlayMode -> "Служебное уведомление компактного окна; звук воспроизводит сам будильник"
                    headsUpMode -> "Всплывающие напоминания с мелодией будильника"
                    alarmMode -> "Напоминания в режиме будильника"
                    else -> "Запланированные локальные напоминания"
                }
                if (overlayMode) {
                    setSound(null, null)
                } else {
                    setSound(
                        sound.takeIf(String::isNotBlank)?.let(Uri::parse),
                        AudioAttributes.Builder()
                            .setUsage(
                                if (alarmMode) {
                                    AudioAttributes.USAGE_ALARM
                                } else {
                                    AudioAttributes.USAGE_NOTIFICATION
                                },
                            )
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                }
                val channelVibrationEnabled = vibrationEnabled && !overlayMode
                enableVibration(channelVibrationEnabled)
                if (channelVibrationEnabled) setVibrationPattern(vibrationPattern)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return channelId
    }

    fun recreate(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notificationChannels.filter { it.id.startsWith(REMINDER_CHANNEL_PREFIX) }.forEach { manager.deleteNotificationChannel(it.id) }
        create(context)
    }

    fun cancelActiveAlarms(context: Context) {
        ReminderOverlayService.dismiss(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { status ->
                status.notification.category == Notification.CATEGORY_ALARM ||
                    status.notification.channelId.startsWith("${REMINDER_CHANNEL_PREFIX}_alarm_")
            }
            .forEach { status -> manager.cancel(status.tag, status.id) }
    }

    fun alarmChannelId(context: Context): String {
        val settings = SettingsStore.currentReminderNotificationSettings(context)
        return ensure(
            context = context,
            alarmMode = true,
            headsUpMode = true,
            sound = settings.reminderAlarmSound.ifBlank { "content://settings/system/alarm_alert" },
            vibrationEnabled = settings.notificationVibrationEnabled,
        )
    }

    private fun removeObsoleteChannels(context: Context, currentChannelIds: Set<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notificationChannels
            .filter { it.id.startsWith(REMINDER_CHANNEL_PREFIX) && it.id !in currentChannelIds }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }
}

class AndroidReminderScheduler(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: Reminder) {
        val date = reminder.remindAt ?: return
        cancel(reminder.id)
        val trigger = date.atZone(zoneId).toInstant().toEpochMilli()
        if (trigger <= System.currentTimeMillis()) return
        val operation = requireNotNull(
            pendingIntent(
                reminder.id,
                reminder.text,
                reminder.recurrence,
                PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val alarmMode = SettingsStore.currentReminderAlarmMode(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            if (!alarmMode) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
                return
            }
            val showReminder = PendingIntent.getActivity(
                context,
                notificationId(reminder.id) xor 0x20000000,
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_OPEN_REMINDER_ID, reminder.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showReminder), operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
        }
    }

    override fun cancel(reminderId: Long) {
        pendingIntent(reminderId, "", null, PendingIntent.FLAG_NO_CREATE)?.let { operation ->
            alarms.cancel(operation)
            operation.cancel()
        }
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
        AlarmRetryScheduler.cancel(context, reminderId)
        AlarmSessionCoordinator.release(context, reminderId)
    }

    override fun isScheduled(reminderId: Long): Boolean =
        pendingIntent(reminderId, "", null, PendingIntent.FLAG_NO_CREATE) != null

    private fun pendingIntent(id: Long, text: String, recurrence: String?, lookupFlag: Int): PendingIntent? {
        val receiver = PendingIntent.getBroadcast(
            context,
            notificationId(id),
            Intent(context, ReminderAlarmReceiver::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_RECURRENCE, recurrence),
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )
        return receiver
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_ID, -1L)
        val settings = SettingsStore.currentReminderNotificationSettings(context)
        if (settings.reminderAlarmMode) {
            val attempt = intent.getIntExtra(EXTRA_ALARM_ATTEMPT, 0).coerceAtLeast(0)
            val ringMillis = settings.reminderAlarmRingSeconds * 1_000L
            val busyUntil = AlarmSessionCoordinator.claim(context, reminderId, ringMillis)
            if (busyUntil != null) {
                AlarmRetryScheduler.scheduleAt(context, intent, busyUntil + 1_000L, attempt)
                return
            }
        }
        val built = ReminderNotificationFactory.build(context, intent) ?: return
        maybeWakeScreen(context, settings.notificationWakeScreen)
        if (
            intent.action == ACTION_ALARM_RETRY ||
            normalizeReminderRecurrence(intent.getStringExtra(EXTRA_RECURRENCE)) == null
        ) {
            if (settings.reminderAlarmMode) {
                AlarmRetryScheduler.scheduleNext(
                    context,
                    intent,
                    intent.getIntExtra(EXTRA_ALARM_ATTEMPT, 0).coerceAtLeast(0),
                    settings,
                )
            }
            presentReminder(context, intent, built, settings)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = RoomReminderRepository(AppDatabase.get(context).reminderDao())
                runCatching {
                    ReminderActions(repository, AndroidReminderScheduler(context)).advanceRecurring(reminderId)
                }
                if (settings.reminderAlarmMode) {
                    val busyUntil = AlarmSessionCoordinator.claim(
                        context,
                        reminderId,
                        settings.reminderAlarmRingSeconds * 1_000L,
                    )
                    if (busyUntil != null) {
                        AlarmRetryScheduler.scheduleAt(context, intent, busyUntil + 1_000L, 0)
                        return@launch
                    }
                    AlarmRetryScheduler.scheduleNext(context, intent, 0, settings)
                }
                presentReminder(context, intent, built, settings)
            } finally {
                pending.finish()
            }
        }
    }
}

private fun presentReminder(
    context: Context,
    source: Intent,
    built: Pair<Int, Notification>,
    settings: ru.nezabudka.app.data.ReminderNotificationSettingsSnapshot,
) {
    // Every alarm has a notification. On an unlocked device the optional compact
    // overlay is added on top, using capability checks rather than an OEM name.
    notifyIfAllowed(context, built)
    val compactOverlayRequested =
        settings.reminderAlarmMode &&
            settings.reminderAlarmPresentation == ALARM_PRESENTATION_SCREEN &&
            ReminderOverlayService.canShowOverlay(context)
    if (compactOverlayRequested && !ReminderOverlayService.show(context, source)) {
        // If the foreground service could not start, replace its silent service
        // notification with the normal sounding heads-up alarm.
        val deviceLocked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        ReminderNotificationFactory.build(
            context = context,
            intent = source,
            deviceLocked = deviceLocked,
            overlayWindowAllowed = false,
        )?.let { fallback -> notifyIfAllowed(context, fallback) }
    }
}

private fun notifyIfAllowed(context: Context, built: Pair<Int, Notification>) {
    if (
        Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
        runCatching { NotificationManagerCompat.from(context).notify(built.first, built.second) }
    }
}

private fun alarmScreenPendingIntent(
    context: Context,
    id: Long,
    text: String,
    compact: Boolean,
): PendingIntent {
    val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic().apply {
            pendingIntentCreatorBackgroundActivityStartMode =
                if (Build.VERSION.SDK_INT >= 36) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                } else {
                    @Suppress("DEPRECATION")
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
        }.toBundle()
    } else {
        null
    }
    val activityClass = if (compact) ReminderCompactAlertActivity::class.java else ReminderAlertActivity::class.java
    return PendingIntent.getActivity(
        context,
        notificationId(id) xor if (compact) 0x20000000 else 0x60000000,
        Intent(context, activityClass)
            .setAction(ACTION_OPEN_ALARM)
            .setData(Uri.parse("nezabudka://alarm/$id?compact=$compact"))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        options,
    )
}

internal object ReminderNotificationFactory {
    fun build(
        context: Context,
        intent: Intent,
        deviceLocked: Boolean = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        overlayWindowAllowed: Boolean = !deviceLocked && Settings.canDrawOverlays(context),
    ): Pair<Int, Notification>? {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id <= 0L) return null
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Пора выполнить напоминание" }
        val settings = SettingsStore.currentReminderNotificationSettings(context)
        val alarmMode = settings.reminderAlarmMode
        val alarmScreenRequested = alarmMode &&
            settings.reminderAlarmPresentation == ALARM_PRESENTATION_SCREEN
        val showAlarmScreen = alarmScreenRequested && deviceLocked
        val showOverlayAlarm = alarmScreenRequested && !deviceLocked && overlayWindowAllowed
        val showHeadsUpAlarm = alarmMode && !showAlarmScreen && !showOverlayAlarm
        val sound = if (alarmMode) {
            settings.reminderAlarmSound.ifBlank { "content://settings/system/alarm_alert" }
        } else {
            settings.notificationSound
        }
        val vibrationEnabled = settings.notificationVibrationEnabled
        val channelId = NotificationChannels.ensure(
            context = context,
            alarmMode = alarmMode,
            headsUpMode = showHeadsUpAlarm,
            overlayMode = showOverlayAlarm,
            sound = sound,
            vibrationEnabled = vibrationEnabled,
        )
        val openReminder = PendingIntent.getActivity(
            context,
            notificationId(id),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_REMINDER_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val complete = PendingIntent.getBroadcast(
            context,
            notificationId(id),
            Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_COMPLETE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getActivity(
            context,
            notificationId(id) xor 0x40000000,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_SNOOZE_REMINDER_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmScreen = alarmScreenPendingIntent(
            context = context,
            id = id,
            text = text,
            compact = alarmScreenRequested && !deviceLocked,
        )
        val overlayContent = ReminderOverlayService.contentIntent(context, id, text)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Напоминание")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(
                when {
                    showHeadsUpAlarm -> NotificationCompat.PRIORITY_MAX
                    showOverlayAlarm -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_HIGH
                },
            )
            .setCategory(if (alarmMode) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(
                when {
                    showOverlayAlarm -> overlayContent
                    alarmScreenRequested -> alarmScreen
                    else -> openReminder
                },
            )
            .setAutoCancel(!alarmMode)
            .addAction(android.R.drawable.checkbox_on_background, "Выполнено", complete)
            .addAction(android.R.drawable.ic_menu_recent_history, "Перенести", snooze)
        if (vibrationEnabled && !showOverlayAlarm) {
            builder.setVibrate(if (alarmMode) ALARM_VIBRATION else NOTIFICATION_VIBRATION)
        }
        // On Android 16 timeoutAfter also sends deleteIntent. Alarm notifications keep
        // their independent timeout/retry lifecycle; ordinary reminder notifications
        // use the delete action to interpret an explicit shade swipe as completion.
        if (!alarmMode) builder.setDeleteIntent(complete)
        if (alarmMode) {
            builder.setTimeoutAfter(settings.reminderAlarmRingSeconds * 1_000L)
            if (alarmScreenRequested) {
                builder.setOngoing(true)
            }
            if (showAlarmScreen) {
                builder.setFullScreenIntent(alarmScreen, true)
            }
        }
        val notification = builder.build().apply {
            if (alarmMode) flags = flags or Notification.FLAG_INSISTENT
        }
        return notificationId(id) to notification
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_COMPLETE, ACTION_SNOOZE)) return
        val id = intent.getLongExtra(EXTRA_ID, -1L).takeIf { it > 0L } ?: return
        // Silence the active alarm immediately. Persistence remains asynchronous so
        // a slow Room write can never leave sound, vibration or the overlay running.
        dismissReminderNotification(context, id)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = RoomReminderRepository(AppDatabase.get(context).reminderDao())
                val actions = ReminderActions(repository, AndroidReminderScheduler(context))
                runCatching {
                    val reminder = repository.find(id, null, false)
                    when (intent.action) {
                        ACTION_COMPLETE -> {
                            actions.completeFromNotification(id)
                        }
                        ACTION_SNOOZE -> {
                            val snoozeAt = intent.getLongExtra(EXTRA_SNOOZE_AT, -1L)
                                .takeIf { it > System.currentTimeMillis() }
                                ?.let { epoch -> LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault()) }
                            if (reminder != null && snoozeAt != null) {
                                actions.reschedule(reminder, snoozeAt)
                            }
                        }
                    }
                }
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            )
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = RoomReminderRepository(AppDatabase.get(context).reminderDao())
                val scheduler = AndroidReminderScheduler(context)
                RestoreReminderSchedules(repository, scheduler)()
            } finally { pending.finish() }
        }
    }
}

internal fun notificationId(id: Long): Int = (id xor (id ushr 32)).toInt()

fun dismissReminderNotification(context: Context, reminderId: Long) {
    ReminderOverlayService.dismiss(context)
    NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    AlarmRetryScheduler.cancel(context, reminderId)
    AlarmSessionCoordinator.release(context, reminderId)
}

internal fun expireAlarmSignal(context: Context, reminderId: Long) {
    ReminderOverlayService.dismiss(context)
    NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    AlarmSessionCoordinator.release(context, reminderId)
}

private object AlarmRetryScheduler {
    fun scheduleNext(
        context: Context,
        source: Intent,
        attempt: Int,
        settings: ru.nezabudka.app.data.ReminderNotificationSettingsSnapshot,
    ) {
        if (attempt + 1 >= settings.reminderAlarmRepeatCount) return
        val delayMillis =
            settings.reminderAlarmRingSeconds * 1_000L + settings.reminderAlarmPauseMinutes * 60_000L
        scheduleAt(context, source, System.currentTimeMillis() + delayMillis, attempt + 1)
    }

    fun scheduleAt(context: Context, source: Intent, triggerAtMillis: Long, attempt: Int) {
        val id = source.getLongExtra(EXTRA_ID, -1L).takeIf { it > 0L } ?: return
        val operation = PendingIntent.getBroadcast(
            context,
            notificationId(id) xor RETRY_REQUEST_MASK,
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ACTION_ALARM_RETRY)
                .setData(Uri.parse("nezabudka://alarm-retry/$id"))
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TEXT, source.getStringExtra(EXTRA_TEXT))
                .putExtra(EXTRA_RECURRENCE, source.getStringExtra(EXTRA_RECURRENCE))
                .putExtra(EXTRA_ALARM_ATTEMPT, attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            val showReminder = PendingIntent.getActivity(
                context,
                notificationId(id) xor 0x20000000,
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_OPEN_REMINDER_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showReminder), operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val operation = PendingIntent.getBroadcast(
            context,
            notificationId(reminderId) xor RETRY_REQUEST_MASK,
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ACTION_ALARM_RETRY)
                .setData(Uri.parse("nezabudka://alarm-retry/$reminderId")),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(operation)
        operation.cancel()
    }

    private const val RETRY_REQUEST_MASK = 0x10000000
}

/** Serializes simultaneous alarm-mode reminders without keeping a process or service alive. */
private object AlarmSessionCoordinator {
    private const val PREFS = "alarm_session"
    private const val ACTIVE_ID = "active_id"
    private const val ACTIVE_UNTIL = "active_until"

    @Synchronized
    fun claim(context: Context, reminderId: Long, durationMillis: Long): Long? {
        if (reminderId <= 0L) return null
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val activeId = prefs.getLong(ACTIVE_ID, -1L)
        val activeUntil = prefs.getLong(ACTIVE_UNTIL, 0L)
        if (activeId != reminderId && activeUntil > now) return activeUntil
        prefs.edit()
            .putLong(ACTIVE_ID, reminderId)
            .putLong(ACTIVE_UNTIL, now + durationMillis)
            .apply()
        return null
    }

    @Synchronized
    fun release(context: Context, reminderId: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(ACTIVE_ID, -1L) == reminderId) prefs.edit().clear().apply()
    }
}

@Suppress("DEPRECATION")
private fun maybeWakeScreen(context: Context, enabled: Boolean) {
    if (!enabled) return
    val power = context.getSystemService(PowerManager::class.java)
    if (power.isInteractive) return
    runCatching {
        power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "${context.packageName}:reminderWake",
        ).acquire(3_000L)
    }
}

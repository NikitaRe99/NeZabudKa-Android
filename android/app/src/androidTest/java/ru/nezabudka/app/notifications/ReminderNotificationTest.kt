package ru.nezabudka.app.notifications

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.ReminderEntity
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.domain.ALARM_PRESENTATION_NOTIFICATION
import ru.nezabudka.app.domain.ALARM_PRESENTATION_SCREEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun notificationAndAlarmModesUseIndependentChannelsAndBehavior() {
        val settings = SettingsStore(context)
        val previousAlarmMode = settings.state.value.reminderAlarmMode
        val previousAlarmPresentation = settings.state.value.reminderAlarmPresentation
        val previousNotificationVibration = settings.state.value.notificationVibrationEnabled
        val previousNotificationSound = settings.state.value.notificationSound
        val previousAlarmSound = settings.state.value.reminderAlarmSound
        val previousRingSeconds = settings.state.value.reminderAlarmRingSeconds
        try {
            settings.setNotificationVibrationEnabled(true)
            settings.setNotificationSound("content://settings/system/notification_sound")
            settings.setReminderAlarmMode(false)
            val reminder = buildNotification(701L, deviceLocked = false)
            assertNull(reminder.fullScreenIntent)
            assertEquals(Notification.CATEGORY_REMINDER, reminder.category)
            assertFalse(reminder.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNotNull(reminder.deleteIntent)
            assertNull(reminder.contentView)
            assertNull(reminder.headsUpContentView)
            assertNull(reminder.bigContentView)
            assertEquals("Проверить будильник", reminder.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            assertEquals(listOf("Выполнено", "Перенести"), reminder.actions.map { it.title.toString() })
            val reminderChannel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(reminder.channelId)
            assertTrue(reminder.channelId.startsWith("scheduled_reminders_notification_v7_"))
            assertEquals(AudioAttributes.USAGE_NOTIFICATION, reminderChannel.audioAttributes.usage)
            assertNotNull(reminderChannel.sound)
            assertTrue(reminderChannel.shouldVibrate())
            settings.setReminderAlarmSound("")
            settings.setReminderAlarmMode(true)
            settings.setReminderAlarmRingSeconds(30)
            settings.setReminderAlarmPresentation(ALARM_PRESENTATION_SCREEN)
            val alarm = buildNotification(702L, deviceLocked = true)
            assertNotNull(alarm.fullScreenIntent)
            assertEquals(Notification.CATEGORY_ALARM, alarm.category)
            assertTrue(alarm.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNull(alarm.contentView)
            assertNull(alarm.headsUpContentView)
            assertNull(alarm.bigContentView)
            assertEquals(listOf("Выполнено", "Перенести"), alarm.actions.map { it.title.toString() })
            assertEquals(30_000L, alarm.timeoutAfter)
            val alarmChannel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(alarm.channelId)
            assertTrue(alarm.channelId.startsWith("scheduled_reminders_alarm_v7_"))
            assertNotEquals(reminder.channelId, alarm.channelId)
            assertNotNull(alarmChannel.sound)
            assertEquals(AudioAttributes.USAGE_ALARM, alarmChannel.audioAttributes.usage)
            assertTrue(alarmChannel.shouldVibrate())

            val unlockedAlarm = buildNotification(703L, deviceLocked = false)
            assertNull(unlockedAlarm.fullScreenIntent)
            assertEquals(Notification.CATEGORY_ALARM, unlockedAlarm.category)
            assertTrue(unlockedAlarm.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(unlockedAlarm.flags and Notification.FLAG_INSISTENT != 0)
            assertNull(unlockedAlarm.deleteIntent)
            assertEquals(listOf("Выполнено", "Перенести"), unlockedAlarm.actions.map { it.title.toString() })
            val unlockedAlarmChannel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(unlockedAlarm.channelId)
            assertTrue(unlockedAlarm.channelId.startsWith("scheduled_reminders_alarm_heads_up_v7_"))
            assertNotEquals(alarm.channelId, unlockedAlarm.channelId)
            assertEquals(AudioAttributes.USAGE_ALARM, unlockedAlarmChannel.audioAttributes.usage)
            assertNotNull(unlockedAlarmChannel.sound)

            val overlayAlarm = buildNotification(705L, deviceLocked = false, overlayWindowAllowed = true)
            assertNull(overlayAlarm.fullScreenIntent)
            assertTrue(overlayAlarm.flags and Notification.FLAG_ONGOING_EVENT != 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                assertTrue(overlayAlarm.contentIntent.isService)
            }
            assertTrue(overlayAlarm.channelId.startsWith("scheduled_reminders_alarm_overlay_service_silent_v7_"))
            assertNotEquals(unlockedAlarm.channelId, overlayAlarm.channelId)
            val overlayChannel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(overlayAlarm.channelId)
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, overlayChannel.importance)
            assertNull(overlayChannel.sound)
            assertFalse(overlayChannel.shouldVibrate())

            settings.setReminderAlarmPresentation(ALARM_PRESENTATION_NOTIFICATION)
            val alarmNotification = buildNotification(704L, deviceLocked = true)
            assertNull(alarmNotification.fullScreenIntent)
            assertEquals(Notification.CATEGORY_ALARM, alarmNotification.category)
            assertFalse(alarmNotification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(alarmNotification.flags and Notification.FLAG_INSISTENT != 0)
            assertEquals(30_000L, alarmNotification.timeoutAfter)
            assertTrue(alarmNotification.channelId.startsWith("scheduled_reminders_alarm_heads_up_v7_"))
            assertEquals(unlockedAlarm.channelId, alarmNotification.channelId)
            assertNull(alarmNotification.deleteIntent)

            val permissions = context.packageManager
                .getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
                .requestedPermissions.orEmpty()
            assertTrue(Manifest.permission.USE_FULL_SCREEN_INTENT in permissions)
            assertTrue(Manifest.permission.WAKE_LOCK in permissions)
            assertTrue(Manifest.permission.SYSTEM_ALERT_WINDOW in permissions)
            assertTrue(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE in permissions)

            val alarmActivity = context.packageManager.getActivityInfo(
                ComponentName(context, ReminderAlertActivity::class.java),
                0,
            )
            assertFalse(alarmActivity.exported)
            val compactAlarmActivity = context.packageManager.getActivityInfo(
                ComponentName(context, ReminderCompactAlertActivity::class.java),
                0,
            )
            assertFalse(compactAlarmActivity.exported)
            assertNotEquals(alarmActivity.themeResource, compactAlarmActivity.themeResource)
            val alarmReceiver = context.packageManager.getReceiverInfo(
                ComponentName(context, ReminderAlarmReceiver::class.java),
                0,
            )
            assertEquals(context.packageName, alarmReceiver.processName)
            val overlayService = context.packageManager.getServiceInfo(
                ComponentName(context, ReminderOverlayService::class.java),
                0,
            )
            assertFalse(overlayService.exported)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                assertTrue(
                    overlayService.foregroundServiceType and
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0,
                )
            }
        } finally {
            settings.setReminderAlarmMode(previousAlarmMode)
            settings.setReminderAlarmPresentation(previousAlarmPresentation)
            settings.setNotificationVibrationEnabled(previousNotificationVibration)
            settings.setNotificationSound(previousNotificationSound)
            settings.setReminderAlarmSound(previousAlarmSound)
            settings.setReminderAlarmRingSeconds(previousRingSeconds)
            NotificationChannels.recreate(context)
        }
    }

    @Test
    fun compactAlarmUsesATopWrapContentWindow() {
        val attributes = compactAlarmWindowAttributes(WindowManager.LayoutParams())

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attributes.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, attributes.height)
        assertEquals(Gravity.TOP, attributes.gravity and Gravity.VERTICAL_GRAVITY_MASK)
        assertTrue(attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertTrue(attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        assertEquals(0f, attributes.dimAmount)
    }

    @Test
    fun permittedOverlayServiceStartsAndStopsCleanly() {
        assumeTrue(Settings.canDrawOverlays(context))
        val settings = SettingsStore(context)
        val previousAlarmMode = settings.state.value.reminderAlarmMode
        val previousPresentation = settings.state.value.reminderAlarmPresentation
        try {
            settings.setReminderAlarmMode(true)
            settings.setReminderAlarmPresentation(ALARM_PRESENTATION_SCREEN)
            assertTrue(
                ReminderOverlayService.show(
                    context,
                    Intent(context, ReminderAlarmReceiver::class.java)
                        .putExtra(EXTRA_ID, 797L)
                        .putExtra(EXTRA_TEXT, "Проверка окна поверх приложений"),
                ),
            )
            val manager = context.getSystemService(ActivityManager::class.java)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val startedDeadline = SystemClock.elapsedRealtime() + 3_000L
            while (
                notificationManager.activeNotifications.none { it.id == notificationId(797L) } &&
                SystemClock.elapsedRealtime() < startedDeadline
            ) {
                SystemClock.sleep(50L)
            }
            assertTrue(notificationManager.activeNotifications.any { it.id == notificationId(797L) })
            SystemClock.sleep(300L)
            assertTrue(isOverlayServiceRunning(manager))
            val audioManager = context.getSystemService(AudioManager::class.java)
            assertTrue(
                audioManager.activePlaybackConfigurations.any {
                    it.audioAttributes.usage == AudioAttributes.USAGE_ALARM
                },
            )
            val alarmNotification = notificationManager.activeNotifications
                .first { it.id == notificationId(797L) }
                .notification
            alarmNotification.actions.first { it.title.toString() == "Выполнено" }.actionIntent.send()
            val stoppedDeadline = SystemClock.elapsedRealtime() + 3_000L
            while (isOverlayServiceRunning(manager) && SystemClock.elapsedRealtime() < stoppedDeadline) {
                SystemClock.sleep(50L)
            }
            assertFalse(isOverlayServiceRunning(manager))
        } finally {
            ReminderOverlayService.dismiss(context)
            settings.setReminderAlarmMode(previousAlarmMode)
            settings.setReminderAlarmPresentation(previousPresentation)
            NotificationManagerCompat.from(context).cancel(notificationId(797L))
        }
    }

    @Test
    fun vivoAlarmSettingsOpenThePerAppPermissionPage() {
        val vendor = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        assumeTrue(vendor.contains("vivo") || vendor.contains("iqoo"))

        val destination = requireNotNull(DeviceAutoStartSettings.alarmPermissionDestination(context))
        assertEquals("permission.intent.action.softPermissionDetail", destination.intent.action)
        assertEquals("com.vivo.permissionmanager", destination.intent.component?.packageName)
        assertEquals(context.packageName, destination.intent.getStringExtra("packagename"))
    }

    @Test
    fun vivoAutoStartSettingsOpenTheSamePerAppPermissionPage() {
        val vendor = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        assumeTrue(vendor.contains("vivo") || vendor.contains("iqoo"))

        val destination = requireNotNull(DeviceAutoStartSettings.destination(context))
        assertEquals("permission.intent.action.softPermissionDetail", destination.intent.action)
        assertEquals("com.vivo.permissionmanager", destination.intent.component?.packageName)
        assertEquals(context.packageName, destination.intent.getStringExtra("packagename"))
        assertEquals("package:${context.packageName}", DeviceAutoStartSettings.backgroundDestination(context).intent.dataString)
    }

    @Test
    fun disablingAlarmModeCanCancelAnAlreadyActiveAlarm() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "scheduled_reminders_alarm_test_silent"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Alarm test", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        try {
            manager.notify(
                TEST_ALARM_NOTIFICATION_ID,
                Notification.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Alarm test")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .build(),
            )
            val postedDeadline = SystemClock.elapsedRealtime() + 2_000L
            while (
                manager.activeNotifications.none { it.id == TEST_ALARM_NOTIFICATION_ID } &&
                SystemClock.elapsedRealtime() < postedDeadline
            ) {
                SystemClock.sleep(50L)
            }
            assertTrue(manager.activeNotifications.any { it.id == TEST_ALARM_NOTIFICATION_ID })

            NotificationChannels.cancelActiveAlarms(context)

            val deadline = SystemClock.elapsedRealtime() + 2_000L
            while (
                manager.activeNotifications.any { it.id == TEST_ALARM_NOTIFICATION_ID } &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(50L)
            }
            assertFalse(manager.activeNotifications.any { it.id == TEST_ALARM_NOTIFICATION_ID })
        } finally {
            manager.cancel(TEST_ALARM_NOTIFICATION_ID)
            manager.deleteNotificationChannel(channelId)
        }
    }

    @Test
    fun dismissingOrdinaryNotificationCompletesReminderAndInvalidatesRoomFlow() = runBlocking {
        val settings = SettingsStore(context)
        val previousAlarmMode = settings.state.value.reminderAlarmMode
        val dao = AppDatabase.get(context).reminderDao()
        val id = dao.insert(
            ReminderEntity(
                text = "Notification dismiss test ${System.nanoTime()}",
                remindAt = LocalDateTime.now().plusHours(1),
                recurrence = null,
                createdAt = LocalDateTime.now(),
            ),
        )
        try {
            settings.setReminderAlarmMode(false)
            val notification = buildNotification(id, deviceLocked = false)
            val completed = async {
                withTimeout(3_000L) {
                    dao.observeById(id).filterNotNull().first { it.status == "completed" }
                }
            }

            requireNotNull(notification.deleteIntent).send()

            assertEquals("completed", completed.await().status)
        } finally {
            settings.setReminderAlarmMode(previousAlarmMode)
            dao.delete(id)
            dismissReminderNotification(context, id)
        }
    }

    @Test
    fun notificationTimeoutInvokesDeleteActionSoAlarmsMustNotAttachIt() = runBlocking {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val dao = AppDatabase.get(context).reminderDao()
        val id = dao.insert(
            ReminderEntity(
                text = "Notification timeout test ${System.nanoTime()}",
                remindAt = LocalDateTime.now().plusHours(1),
                recurrence = null,
                createdAt = LocalDateTime.now(),
            ),
        )
        val channelId = "scheduled_reminders_timeout_test"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Timeout test", NotificationManager.IMPORTANCE_LOW),
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            TEST_TIMEOUT_NOTIFICATION_ID,
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(ACTION_COMPLETE)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            manager.notify(
                TEST_TIMEOUT_NOTIFICATION_ID,
                Notification.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Timeout test")
                    .setDeleteIntent(deleteIntent)
                    .setTimeoutAfter(250L)
                    .build(),
            )
            SystemClock.sleep(1_000L)

            val deadline = SystemClock.elapsedRealtime() + 2_000L
            while (dao.byId(id)?.status != "completed" && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50L)
            }
            assertEquals("completed", dao.byId(id)?.status)
        } finally {
            manager.cancel(TEST_TIMEOUT_NOTIFICATION_ID)
            manager.deleteNotificationChannel(channelId)
            dao.delete(id)
        }
    }

    private fun buildNotification(
        id: Long,
        deviceLocked: Boolean,
        overlayWindowAllowed: Boolean = false,
    ): Notification = requireNotNull(
        ReminderNotificationFactory.build(
            context,
            Intent(context, ReminderAlarmReceiver::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TEXT, "Проверить будильник"),
            deviceLocked = deviceLocked,
            overlayWindowAllowed = overlayWindowAllowed,
        ),
    ).second

    @Suppress("DEPRECATION")
    private fun isOverlayServiceRunning(manager: ActivityManager): Boolean =
        manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == ReminderOverlayService::class.java.name
        }

    private companion object {
        const val TEST_ALARM_NOTIFICATION_ID = 91_705
        const val TEST_TIMEOUT_NOTIFICATION_ID = 91_706
    }
}

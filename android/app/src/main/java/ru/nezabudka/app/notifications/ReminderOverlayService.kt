package ru.nezabudka.app.notifications

import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.Service
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.ui.LocalAssistantTheme
import java.time.LocalDateTime
import java.time.ZoneId

/** Short-lived overlay used only for the explicitly selected alarm-window mode. */
class ReminderOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    private val handler = Handler(Looper.getMainLooper())
    private val savedStateController = SavedStateRegistryController.create(this)
    private val serviceViewModelStore = ViewModelStore()
    private var overlayView: ComposeView? = null
    private var currentReminderId: Long? = null
    private var alarmVibrator: Vibrator? = null
    private var alarmRingtone: Ringtone? = null

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = serviceViewModelStore

    override fun onCreate() {
        savedStateController.performAttach()
        super.onCreate()
        savedStateController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!SettingsStore.remindersEnabled(this)) {
            stopSelf()
            return Service.START_NOT_STICKY
        }
        val reminderId = intent?.getLongExtra(EXTRA_ID, -1L)?.takeIf { it > 0L }
        if (
            intent?.action == ACTION_REVEAL_EXISTING_OVERLAY &&
            reminderId == currentReminderId &&
            overlayView != null
        ) {
            return Service.START_NOT_STICKY
        }
        if (reminderId == null || !canShowOverlay(this)) {
            stopSelf()
            return Service.START_NOT_STICKY
        }
        val notification = ReminderNotificationFactory.build(this, intent, deviceLocked = false)?.second
        if (notification == null) {
            stopSelf()
            return Service.START_NOT_STICKY
        }
        val foregroundId = notificationId(reminderId).takeUnless { it == 0 } ?: 1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(foregroundId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(foregroundId, notification)
        }
        currentReminderId = reminderId
        val reminderText = intent.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Пора выполнить напоминание" }
        val notificationSettings = SettingsStore.currentReminderNotificationSettings(this)
        startAlarmSound(notificationSettings.reminderAlarmSound)
        if (notificationSettings.notificationVibrationEnabled) startAlarmVibration()
        showOverlay(reminderId, reminderText)
        handler.removeCallbacksAndMessages(null)
        val ringMillis = notificationSettings.reminderAlarmRingSeconds * 1_000L
        handler.postDelayed({
            expireAlarmSignal(this, reminderId)
            stopSelf()
        }, ringMillis)
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { view ->
            runCatching { getSystemService(WindowManager::class.java).removeView(view) }
        }
        overlayView = null
        currentReminderId = null
        alarmVibrator?.cancel()
        alarmVibrator = null
        alarmRingtone?.stop()
        alarmRingtone = null
        serviceViewModelStore.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun showOverlay(reminderId: Long, reminderText: String) {
        val windowManager = getSystemService(WindowManager::class.java)
        overlayView?.let { previous -> runCatching { windowManager.removeView(previous) } }
        val settings = SettingsStore(this).state.value
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ReminderOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ReminderOverlayService)
            setViewTreeViewModelStoreOwner(this@ReminderOverlayService)
            setContent {
                LocalAssistantTheme(settings) {
                    AlarmSurface(
                        reminderText = reminderText,
                        compact = true,
                        applyStatusBarPadding = false,
                        complete = { performAction(reminderId, ACTION_COMPLETE) },
                        snooze = { delay ->
                            performAction(reminderId, ACTION_SNOOZE, System.currentTimeMillis() + delay)
                        },
                        snoozeAt = { dateTime ->
                            performAction(
                                reminderId,
                                ACTION_SNOOZE,
                                dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            )
                        },
                    )
                }
            }
        }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = overlayTopInset(windowManager)
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                val top = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
                ).top
                if (top > 0 && layout.y != top) {
                    layout.y = top
                    overlayView?.let { attached ->
                        runCatching { windowManager.updateViewLayout(attached, layout) }
                    }
                }
                insets
            }
        }
        if (runCatching { windowManager.addView(view, layout) }.isSuccess) {
            overlayView = view
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) ViewCompat.requestApplyInsets(view)
        } else {
            stopSelf()
        }
    }

    private fun startAlarmVibration() {
        val vibrator = getSystemService(Vibrator::class.java).takeIf(Vibrator::hasVibrator) ?: return
        val effect = VibrationEffect.createWaveform(ALARM_VIBRATION, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrateAlarmCompat(vibrator, effect)
        }
        alarmVibrator = vibrator
    }

    @Suppress("DEPRECATION")
    private fun vibrateAlarmCompat(vibrator: Vibrator, effect: VibrationEffect) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        vibrator.vibrate(effect, attributes)
    }

    private fun overlayTopInset(windowManager: WindowManager): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout(),
                )
                .top
        }
        return 0
    }

    private fun startAlarmSound(configuredSound: String) {
        val defaultAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Uri.parse("content://settings/system/alarm_alert")
        val selected = configuredSound.takeIf(String::isNotBlank)?.let(Uri::parse) ?: defaultAlarm
        val ringtone = RingtoneManager.getRingtone(this, selected)
            ?: RingtoneManager.getRingtone(this, defaultAlarm)
            ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.isLooping = true
        if (runCatching { ringtone.play() }.isSuccess) alarmRingtone = ringtone
    }

    private fun performAction(reminderId: Long, action: String, snoozeAtMillis: Long? = null) {
        sendBroadcast(
            Intent(this, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ID, reminderId)
                .apply { snoozeAtMillis?.let { putExtra(EXTRA_SNOOZE_AT, it) } },
        )
        dismissReminderNotification(this, reminderId)
        stopSelf()
    }

    companion object {
        private const val ACTION_REVEAL_EXISTING_OVERLAY =
            "ru.nezabudka.app.action.REVEAL_EXISTING_ALARM_OVERLAY"

        fun contentIntent(context: Context, reminderId: Long, reminderText: String): PendingIntent =
            PendingIntent.getService(
                context,
                notificationId(reminderId) xor 0x30000000,
                Intent(context, ReminderOverlayService::class.java)
                    .setAction(ACTION_REVEAL_EXISTING_OVERLAY)
                    .setData(Uri.parse("nezabudka://alarm-overlay/$reminderId"))
                    .putExtra(EXTRA_ID, reminderId)
                    .putExtra(EXTRA_TEXT, reminderText),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun show(context: Context, source: Intent): Boolean {
            if (!canShowOverlay(context)) return false
            val intent = Intent(context, ReminderOverlayService::class.java)
                .putExtra(EXTRA_ID, source.getLongExtra(EXTRA_ID, -1L))
                .putExtra(EXTRA_TEXT, source.getStringExtra(EXTRA_TEXT))
            return runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, ReminderOverlayService::class.java))
        }

        fun canShowOverlay(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            val power = context.getSystemService(PowerManager::class.java)
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            return power.isInteractive && !keyguard.isKeyguardLocked
        }
    }
}

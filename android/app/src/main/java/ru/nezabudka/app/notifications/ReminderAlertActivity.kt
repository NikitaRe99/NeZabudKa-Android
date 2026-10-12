package ru.nezabudka.app.notifications

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.LocalAssistantTheme
import ru.nezabudka.app.ui.applyLegacySystemBarColors
import ru.nezabudka.app.ui.appShadowElevation
import ru.nezabudka.app.ui.screens.ReminderDateTimeDialog
import ru.nezabudka.app.ui.screens.AppAlertDialog
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Alarm surface shown above the keyguard until the user chooses an action. */
open class ReminderAlertActivity : ComponentActivity() {
    protected open val compactPresentation: Boolean = false
    private var reminderId: Long? = null
    private var reminderText: String = ""
    private var keyguardDismissRequested = false
    private var closing = false
    private var screenOffReceiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) dismissToOverdue()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val compact = compactPresentation &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (compact) {
            configureCompactWindow()
        } else {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        reminderId = intent.getLongExtra(EXTRA_ID, -1L)
            .takeIf { it > 0L }
            ?: intent.data?.lastPathSegment?.toLongOrNull()?.takeIf { it > 0L }
        reminderText = intent.getStringExtra(EXTRA_TEXT).orEmpty()
            .ifBlank { "Пора выполнить напоминание" }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = dismissToOverdue()
        })
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceiverRegistered = true

        val settings = SettingsStore(this).state.value
        lifecycleScope.launch {
            delay(settings.reminderAlarmRingSeconds * 1_000L)
            expireCurrentSignal()
        }
        setContent {
            LocalAssistantTheme(settings) {
                val colorScheme = MaterialTheme.colorScheme
                SideEffect {
                    if (!compact) {
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = colorScheme.background.luminance() > 0.5f
                            isAppearanceLightNavigationBars = colorScheme.surfaceContainer.luminance() > 0.5f
                        }
                        applyLegacySystemBarColors(
                            window,
                            colorScheme.background.toArgb(),
                            colorScheme.surfaceContainer.toArgb(),
                        )
                    }
                }
                AlarmSurface(
                    reminderText = reminderText,
                    compact = compact,
                    complete = { performActionOrDismiss(ACTION_COMPLETE) },
                    snooze = { delay -> performActionOrDismiss(ACTION_SNOOZE, System.currentTimeMillis() + delay) },
                    snoozeAt = { time ->
                        if (time.isAfter(LocalDateTime.now())) {
                            performActionOrDismiss(
                                ACTION_SNOOZE,
                                time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            )
                        } else {
                            Toast.makeText(this, "Выберите будущее время", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!compactPresentation && !keyguardDismissRequested) {
            keyguardDismissRequested = true
            requestKeyguardDismissal()
        }
    }

    override fun onDestroy() {
        if (screenOffReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            screenOffReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun requestKeyguardDismissal() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) keyguard.requestDismissKeyguard(this, null)
    }

    private fun configureCompactWindow() {
        setShowWhenLocked(false)
        setTurnScreenOn(false)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = compactAlarmWindowAttributes(window.attributes)
        window.decorView.post {
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun performActionOrDismiss(action: String, snoozeAtMillis: Long? = null) {
        if (closing) return
        closing = true
        val id = reminderId
        if (id == null) {
            NotificationChannels.cancelActiveAlarms(this)
            finishAndRemoveTask()
            return
        }
        sendBroadcast(
            Intent(this, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ID, id)
                .apply { snoozeAtMillis?.let { putExtra(EXTRA_SNOOZE_AT, it) } },
        )
        dismissReminderNotification(this, id)
        finishAndRemoveTask()
    }

    private fun dismissToOverdue() {
        if (closing) return
        closing = true
        reminderId?.let { dismissReminderNotification(this, it) }
            ?: NotificationChannels.cancelActiveAlarms(this)
        finishAndRemoveTask()
    }

    private fun expireCurrentSignal() {
        if (closing || isFinishing) return
        closing = true
        reminderId?.let { expireAlarmSignal(this, it) }
        finishAndRemoveTask()
    }
}

internal fun compactAlarmWindowAttributes(
    attributes: WindowManager.LayoutParams,
): WindowManager.LayoutParams = attributes.apply {
    flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    gravity = Gravity.TOP
    width = WindowManager.LayoutParams.MATCH_PARENT
    height = WindowManager.LayoutParams.WRAP_CONTENT
    dimAmount = 0f
}

class ReminderCompactAlertActivity : ReminderAlertActivity() {
    override val compactPresentation: Boolean = true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmSurface(
    reminderText: String,
    compact: Boolean,
    applyStatusBarPadding: Boolean = true,
    complete: () -> Unit,
    snooze: (Long) -> Unit,
    snoozeAt: (LocalDateTime) -> Unit,
) {
    val theme = AppTheme.tokens
    var showSnoozeOptions by remember { mutableStateOf(false) }
    var showCustomTime by remember { mutableStateOf(false) }
    val customInitial = remember { LocalDateTime.now().plusHours(1) }

    if (compact) {
        val compactModifier = if (applyStatusBarPadding) {
            Modifier.fillMaxWidth().statusBarsPadding()
        } else {
            Modifier.fillMaxWidth()
        }
        Box(
            compactModifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(theme.shapes.card),
                colors = CardDefaults.cardColors(
                    containerColor = theme.colors.card,
                    contentColor = theme.colors.textPrimary,
                ),
                elevation = CardDefaults.cardElevation(appShadowElevation(8.dp)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("НеЗабудьКа", style = MaterialTheme.typography.titleMedium)
                    Text(reminderText, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                    if (!showSnoozeOptions) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = complete,
                                modifier = Modifier.widthIn(min = 120.dp).heightIn(min = 48.dp),
                                shape = RoundedCornerShape(theme.shapes.action),
                            ) {
                                Text("Выполнено", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = { showSnoozeOptions = true },
                                modifier = Modifier.widthIn(min = 120.dp).heightIn(min = 48.dp),
                                shape = RoundedCornerShape(theme.shapes.action),
                            ) {
                                Text("Отложить", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        Text("На сколько отложить?", style = MaterialTheme.typography.titleSmall)
                        listOf(
                            listOf("10 минут" to 10 * MINUTE_MS, "30 минут" to 30 * MINUTE_MS),
                            listOf("1 час" to HOUR_MS, "1 день" to DAY_MS),
                        ).forEach { options ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                options.forEach { (label, delay) ->
                                    OutlinedButton(
                                        onClick = { snooze(delay) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(theme.shapes.action),
                                    ) { Text(label) }
                                }
                            }
                        }
                        TextButton(
                            onClick = { showSnoozeOptions = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Назад") }
                    }
                }
            }
        }
    } else Box(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(theme.colors.selected, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AccessAlarm,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = theme.colors.accent,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Будильник",
                color = theme.colors.textPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "НеЗабудьКа",
                color = theme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(theme.shapes.card),
                colors = CardDefaults.cardColors(
                    containerColor = theme.colors.card,
                    contentColor = theme.colors.textPrimary,
                ),
                elevation = CardDefaults.cardElevation(appShadowElevation(4.dp)),
            ) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        reminderText,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            SwipeToCompleteButton(complete = complete)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showSnoozeOptions = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(theme.shapes.action),
            ) {
                Text("Отложить", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showSnoozeOptions && !compact) {
        AppAlertDialog(
            onDismissRequest = { showSnoozeOptions = false },
            title = { Text("На сколько отложить?") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(
                        "На 10 минут" to 10 * MINUTE_MS,
                        "На 30 минут" to 30 * MINUTE_MS,
                        "На 1 час" to HOUR_MS,
                        "На 1 день" to DAY_MS,
                    ).forEach { (label, delay) ->
                        TextButton(
                            onClick = { snooze(delay) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, Modifier.weight(1f), textAlign = TextAlign.Start)
                        }
                    }
                    TextButton(
                        onClick = {
                            showSnoozeOptions = false
                            showCustomTime = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Schedule, null)
                        Text("Своё время", Modifier.weight(1f).padding(start = 12.dp), textAlign = TextAlign.Start)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSnoozeOptions = false }) { Text("Отмена") }
            },
        )
    }

    if (showCustomTime && !compact) {
        ReminderDateTimeDialog(
            initialDate = customInitial.toLocalDate(),
            initialTime = customInitial.toLocalTime(),
            title = "Отложить до",
            dismiss = { showCustomTime = false },
            clear = { showCustomTime = false },
            confirm = { date, time ->
                val selected = date.atTime(time ?: customInitial.toLocalTime())
                if (selected.isAfter(LocalDateTime.now())) showCustomTime = false
                snoozeAt(selected)
            },
        )
    }
}

@Composable
private fun SwipeToCompleteButton(complete: () -> Unit) {
    val theme = AppTheme.tokens
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    fun confirmCompletion() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        complete()
    }
    val thumbSize = 52.dp
    val horizontalPadding = 6.dp
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val maxOffsetPx = (trackWidthPx - thumbSizePx - horizontalPaddingPx * 2f).coerceAtLeast(0f)
    val displayedOffsetPx by animateFloatAsState(
        targetValue = if (settling) 0f else dragOffsetPx,
        animationSpec = if (settling) androidx.compose.animation.core.spring() else snap(),
        label = "swipe-to-complete",
        finishedListener = {
            if (settling) {
                dragOffsetPx = 0f
                settling = false
            }
        },
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .background(theme.colors.selected, RoundedCornerShape(theme.shapes.action))
            .border(1.dp, theme.colors.accent, RoundedCornerShape(theme.shapes.action))
            .semantics {
                contentDescription = "Проведите вправо, чтобы выполнить"
                onClick(label = "Выполнить напоминание") {
                    confirmCompletion()
                    true
                }
            }
            .pointerInput(maxOffsetPx) {
                detectHorizontalDragGestures(
                    onDragStart = { settling = false },
                    onDragCancel = { settling = true },
                    onDragEnd = {
                        if (maxOffsetPx > 0f && dragOffsetPx >= maxOffsetPx * 0.9f) {
                            confirmCompletion()
                        } else {
                            settling = true
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    dragOffsetPx = (dragOffsetPx + amount).coerceIn(0f, maxOffsetPx)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Проведите, чтобы выполнить",
            color = theme.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
        Box(
            Modifier
                .padding(start = horizontalPadding)
                .align(Alignment.CenterStart)
                .graphicsLayer(translationX = displayedOffsetPx)
                .size(thumbSize)
                .background(theme.colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = theme.colors.onAccent,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

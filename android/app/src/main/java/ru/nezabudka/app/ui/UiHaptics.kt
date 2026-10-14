package ru.nezabudka.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants

/** Delegates to the platform so system haptic settings remain authoritative. */
internal class GatedHapticFeedback(
    private val platform: HapticFeedback, private val enabled: Boolean,
    private val clock: () -> Long = System::nanoTime,
) : HapticFeedback {
    private var lastPulse: Long? = null
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val now = clock()
        val previous = lastPulse
        if (enabled && (previous == null || now - previous >= 40_000_000L)) {
            lastPulse = now
            platform.performHapticFeedback(hapticFeedbackType)
        }
    }
}

@Composable
internal fun rememberUiHapticFeedback(enabled: Boolean): HapticFeedback {
    val platform = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(platform, view, enabled) {
        val feedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackType == HapticFeedbackType.TextHandleMove) {
                    // Text-selection feedback can be disabled separately by the phone.
                    // Picker detents use the system clock tick and still respect haptic settings.
                    if (!view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                } else {
                    platform.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
        GatedHapticFeedback(feedback, enabled)
    }
}

@Composable
internal fun RecordingHapticEffect(recording: Boolean) {
    val haptic by rememberUpdatedState(LocalHapticFeedback.current)
    var previous by remember { mutableStateOf(recording) }
    LaunchedEffect(recording) {
        if (recording != previous) {
            haptic.performHapticFeedback(if (recording) HapticFeedbackType.LongPress else HapticFeedbackType.GestureEnd)
            previous = recording
        }
    }
}

@Composable
internal fun SelectionHapticEffect(selection: Any?) {
    val haptic by rememberUpdatedState(LocalHapticFeedback.current)
    var previous by remember { mutableStateOf(selection) }
    LaunchedEffect(selection) {
        if (selection != previous) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            previous = selection
        }
    }
}

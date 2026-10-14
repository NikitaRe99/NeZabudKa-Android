package ru.nezabudka.app.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.*
import org.junit.Test

class UiHapticsTest {
    @Test fun `disabled UI feedback never reaches the system`() {
        var count = 0
        val system = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { count++ }
        }
        GatedHapticFeedback(system, false).performHapticFeedback(HapticFeedbackType.LongPress)
        assertEquals(0, count)
    }
    @Test fun `near simultaneous picker pulses merge without suppressing later selections`() {
        val pulses = mutableListOf<HapticFeedbackType>()
        val system = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses += hapticFeedbackType }
        }
        var now = 0L
        val gated = GatedHapticFeedback(system, true) { now }
        gated.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        now = 1_000_000L
        gated.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        now = 50_000_000L
        gated.performHapticFeedback(HapticFeedbackType.Confirm)
        assertEquals(listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.Confirm), pulses)
    }
}

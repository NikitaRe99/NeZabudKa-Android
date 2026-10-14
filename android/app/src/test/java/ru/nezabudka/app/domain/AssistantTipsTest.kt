package ru.nezabudka.app.domain

import org.junit.Assert.*
import org.junit.Test

class AssistantTipsTest {
    @Test fun `immediate execution hint waits for three commands across restarts`() {
        val store = MemoryStore()
        AssistantTipController(store).assistantVoiceCommandCompleted(false)
        val second = AssistantTipController(store)
        second.assistantVoiceCommandCompleted(false)
        assertTrue(second.pending.value.isEmpty())
        val third = AssistantTipController(store)
        third.assistantVoiceCommandCompleted(false)
        assertEquals(listOf(AssistantTipKind.IMMEDIATE_COMMANDS), third.pending.value)
        third.acknowledge(AssistantTipKind.IMMEDIATE_COMMANDS)
        third.assistantVoiceCommandCompleted(false)
        assertTrue(third.pending.value.isEmpty())
        third.reset()
        third.assistantVoiceCommandCompleted(false)
        assertTrue(third.pending.value.isEmpty())
    }
    @Test fun `enabled immediate execution does not offer a redundant hint`() {
        val tips = AssistantTipController(MemoryStore())
        repeat(5) { tips.assistantVoiceCommandCompleted(true) }
        assertTrue(tips.pending.value.isEmpty())
    }
    private class MemoryStore : AssistantTipStore {
        var history = AssistantTipHistory()
        override fun read() = history
        override fun write(history: AssistantTipHistory) { this.history = history }
    }

    @Test fun `pending cards survive controller recreation without duplicating an event`() {
        val store = MemoryStore()
        val first = AssistantTipController(store)
        first.offer(AssistantTipKind.WHISPER_SPEED)
        first.offer(AssistantTipKind.WHISPER_SPEED)
        first.offer(AssistantTipKind.PUNCTUATION)
        val reopened = AssistantTipController(store)
        reopened.offer(AssistantTipKind.WHISPER_SPEED)
        assertEquals(listOf(AssistantTipKind.WHISPER_SPEED, AssistantTipKind.PUNCTUATION), reopened.pending.value)
    }

    @Test fun `acknowledging one card persists and does not remove other pending cards`() {
        val store = MemoryStore()
        val tips = AssistantTipController(store)
        tips.offer(AssistantTipKind.REMINDER_MODES)
        tips.offer(AssistantTipKind.REMINDER_DELIVERY)
        tips.acknowledge(AssistantTipKind.REMINDER_MODES)
        val reopened = AssistantTipController(store)
        reopened.offer(AssistantTipKind.REMINDER_MODES)
        assertEquals(listOf(AssistantTipKind.REMINDER_DELIVERY), reopened.pending.value)
    }

    @Test fun `reset clears current cards and allows feature events to offer them again`() {
        val store = MemoryStore()
        val tips = AssistantTipController(store)
        tips.offer(AssistantTipKind.FOLDERS)
        tips.acknowledge(AssistantTipKind.FOLDERS)
        tips.offer(AssistantTipKind.NLU_ENABLE)
        tips.reset()
        assertTrue(tips.pending.value.isEmpty())
        val reopened = AssistantTipController(store)
        assertTrue(reopened.pending.value.isEmpty())
        reopened.offer(AssistantTipKind.FOLDERS)
        assertEquals(listOf(AssistantTipKind.FOLDERS), reopened.pending.value)
    }

    @Test fun `new controller does not create introductory cards before a feature event`() {
        assertTrue(AssistantTipController(MemoryStore()).pending.value.isEmpty())
    }

    @Test fun `unknown saved identifiers do not break the assistant`() {
        val store = MemoryStore()
        store.history = AssistantTipHistory(setOf("future-tip"), listOf("future-tip", "TIME_OF_DAY", "TIME_OF_DAY"))
        assertEquals(listOf(AssistantTipKind.TIME_OF_DAY), AssistantTipController(store).pending.value)
    }

    @Test fun `permission cards are first and equal priorities preserve event order`() {
        assertEquals(listOf(AssistantTipKind.REMINDER_DELIVERY, AssistantTipKind.ALARM_WINDOW,
            AssistantTipKind.RECOGNITION, AssistantTipKind.FOLDERS), prioritizedAssistantTips(listOf(
            AssistantTipKind.FOLDERS, AssistantTipKind.REMINDER_DELIVERY, AssistantTipKind.RECOGNITION,
            AssistantTipKind.ALARM_WINDOW)))
    }
    @Test fun `ordinary notification does not require window permissions`() {
        val allowed = ReminderPermissionStatus(true, true, true, false, false)
        assertTrue(allowed.deliveryReady())
        assertFalse(allowed.alarmWindowReady())
        assertFalse(allowed.copy(notifications = false).deliveryReady())
        assertFalse(allowed.copy(exactTime = false).deliveryReady())
        assertFalse(allowed.copy(background = false).deliveryReady())
        assertTrue(allowed.copy(fullScreen = true, overlay = true).alarmWindowReady())
    }

    @Test fun `old NLU and background hints migrate into one pending card each`() {
        val store = MemoryStore()
        store.history = AssistantTipHistory(setOf("NLU_USAGE", "NLU_ENABLE", "DEVICE_BACKGROUND", "REMINDER_DELIVERY"),
            listOf("NLU_USAGE", "NLU_ENABLE", "DEVICE_BACKGROUND", "REMINDER_DELIVERY"))
        val controller = AssistantTipController(store)
        assertEquals(listOf(AssistantTipKind.NLU_ENABLE, AssistantTipKind.REMINDER_DELIVERY), controller.pending.value)
        controller.acknowledge(AssistantTipKind.NLU_ENABLE)
        assertEquals(listOf(AssistantTipKind.REMINDER_DELIVERY), AssistantTipController(store).pending.value)
        assertFalse(store.history.pending.contains("NLU_USAGE"))
        assertFalse(store.history.pending.contains("DEVICE_BACKGROUND"))
    }
    @Test fun `acknowledged old explanations are not shown again after merge`() {
        val store = MemoryStore()
        store.history = AssistantTipHistory(setOf("NLU_USAGE", "DEVICE_BACKGROUND"))
        val controller = AssistantTipController(store)
        controller.offer(AssistantTipKind.NLU_ENABLE)
        controller.offer(AssistantTipKind.REMINDER_DELIVERY)
        assertTrue(controller.pending.value.isEmpty())
    }
}

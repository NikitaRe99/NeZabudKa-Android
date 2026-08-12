package ru.nezabudka.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AssistantUiProjectionTest {
    @Test
    fun `list projection ignores logs results and audio changes`() {
        val initial = AssistantUiState(recognizedText = "текст")
        val unrelated = initial.copy(
            log = List(100) { "строка $it" },
            result = "готово",
            error = "диагностика",
        )

        assertEquals(initial.voiceActionState(), unrelated.voiceActionState())
        assertNotEquals(
            initial.voiceActionState(),
            unrelated.copy(isProcessing = true).voiceActionState(),
        )
    }

    @Test
    fun `editor projection ignores command recognition changes`() {
        val initial = AssistantUiState(noteDictationActive = true)

        assertEquals(
            initial.noteDictationState(),
            initial.copy(recognizedText = "команда", log = listOf("шум")).noteDictationState(),
        )
    }
}

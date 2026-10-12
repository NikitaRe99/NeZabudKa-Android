package ru.nezabudka.app.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nezabudka.app.application.ActionResult

class CommandResultUiTest {
    @Test fun `unknown auto executed speech remains editable and can be retried`() {
        val state = AssistantUiState(recognizedText = "неясная фраза", recognizedAlternatives = listOf("другой вариант"), isProcessing = true, awaitingSttConfirmation = false)
        val result = state.afterCommandResult(ActionResult("Не удалось распознать команду.", commandRecognized = false))
        assertEquals(state.recognizedText, result.recognizedText)
        assertEquals(state.recognizedAlternatives, result.recognizedAlternatives)
        assertTrue(result.awaitingSttConfirmation)
        assertFalse(result.isProcessing)
        assertEquals("", result.afterCommandResult(ActionResult("Заметка добавлена")).recognizedText)
    }
}

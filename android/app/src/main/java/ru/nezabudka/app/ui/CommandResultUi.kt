package ru.nezabudka.app.ui

import ru.nezabudka.app.application.ActionResult

internal fun AssistantUiState.afterCommandResult(result: ActionResult): AssistantUiState = copy(
    isProcessing = false,
    result = result.message,
    recognizedText = if (result.commandRecognized) "" else recognizedText,
    recognizedAlternatives = if (result.commandRecognized) emptyList() else recognizedAlternatives,
    awaitingSttConfirmation = !result.commandRecognized && recognizedText.isNotBlank(),
)

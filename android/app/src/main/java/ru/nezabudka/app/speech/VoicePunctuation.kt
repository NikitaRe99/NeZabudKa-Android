package ru.nezabudka.app.speech

import kotlinx.coroutines.CancellationException
import ru.nezabudka.app.ports.SpeechRecognition

/** Punctuation changes text, never the recognizer's confidence or confirmation requirement. */
internal suspend fun restoreVoicePunctuation(
    recognition: SpeechRecognition,
    model: String,
    restore: suspend (String, String) -> String,
    onFailure: (Exception) -> Unit,
): SpeechRecognition = try {
    val text = restore(recognition.text, model)
    check(text.isNotBlank() || recognition.text.isBlank()) { "Модель пунктуации вернула пустой текст" }
    recognition.copy(text = text)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    onFailure(error)
    recognition
}

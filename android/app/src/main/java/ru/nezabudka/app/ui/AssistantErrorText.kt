package ru.nezabudka.app.ui

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import ru.nezabudka.app.domain.CommandValidationException
import ru.nezabudka.app.speech.WhisperModelMissingException
import ru.nezabudka.app.speech.RupunctModelMissingException
import ru.nezabudka.app.ports.SpeechRecognition

/** A decoder can return only CTC blanks without throwing; this is not a successful dictation. */
internal fun SpeechRecognition.requireRecognizedSpeech(): SpeechRecognition = apply {
    check(text.isNotBlank()) { "Речь не обнаружена. Попробуйте произнести фразу ближе к микрофону." }
}

/** User-facing text only. Raw exception details are retained by the caller in its log. */
internal fun assistantErrorNoticeText(error: Throwable): String? {
    val full = assistantErrorText(error) ?: return null
    // Keep advice on the assistant card; popups contain just the short result.
    return full.substringBefore(". ").trimEnd('.') + "."
}

internal fun assistantErrorText(error: Throwable): String? {
    val message = error.message.orEmpty()
    val lower = message.lowercase()
    if (error is SecurityException && "микрофон" in lower) return null
    if (error is TimeoutCancellationException || error is TimeoutException)
        return "Распознавание заняло слишком много времени. Попробуйте более короткую фразу или меньшую модель."
    if (error is CancellationException) return null
    if (error is WhisperModelMissingException) return "Модель Whisper не установлена."
    if (error is ru.nezabudka.app.speech.GigaamModelMissingException) return "Модель GigaAM не установлена."
    if (error is RupunctModelMissingException) return "Модель пунктуации не установлена. Сохранён исходный текст."
    if ("речь не обнаружена" in lower || "запись пуста" in lower)
        return "Речь не обнаружена. Попробуйте произнести фразу ближе к микрофону."
    if ("не распознал речь" in lower || "речь не распознана" in lower)
        return "Не удалось распознать речь. Попробуйте повторить фразу."
    if ("недостаточно" in lower && "памяти" in lower)
        return "Недостаточно свободной памяти для распознавания. Закройте другие приложения или выберите меньшую модель."
    if (lower.startsWith("встроенный микрофон") || "не удалось открыть микрофон" in lower)
        return "Не удалось записать звук. Возможно, микрофон используется другим приложением."
    if (error is UnknownHostException || error is ConnectException || "без сети" in lower)
        return "Нет соединения с сервисом распознавания. Проверьте подключение к интернету."
    if (error is SocketTimeoutException)
        return "Сервис распознавания не ответил вовремя. Попробуйте ещё раз."
    val httpCode = Regex("HTTP\\s+(\\d{3})").find(message)?.groupValues?.get(1)?.toIntOrNull()
    if (httpCode != null) return when (httpCode) {
        401, 403 -> "Сервис отклонил доступ. Проверьте ключ или токен распознавания."
        429 -> "Сервис временно ограничил число запросов. Попробуйте позже."
        in 500..599 -> "Сервис распознавания временно недоступен. Попробуйте позже."
        else -> "Сервис не смог обработать запись. Попробуйте повторить фразу."
    }
    if ("токен" in lower || "api-ключ" in lower || "endpoint" in lower)
        return "Не заполнены данные выбранного сервиса распознавания."
    if ("speechrecognizer" in lower || "системный сервис распознавания" in lower || "модель распознавания не установлена" in lower)
        return "Системное распознавание недоступно. Проверьте службу распознавания речи на телефоне."
    if ("файл слишком мал" in lower || "ggml" in lower || "onnx" in lower)
        return "Не удалось загрузить модель. Проверьте файл модели."
    if (error is CommandValidationException) return message
    return "Не удалось выполнить действие. Попробуйте ещё раз."
}

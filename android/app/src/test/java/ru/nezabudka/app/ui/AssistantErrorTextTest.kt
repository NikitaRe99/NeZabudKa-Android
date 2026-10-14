package ru.nezabudka.app.ui

import java.net.UnknownHostException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test
import ru.nezabudka.app.speech.WhisperModelMissingException

class AssistantErrorTextTest {
    @Test fun `empty decoder results produce the speech error instead of successful recognition`() {
        for (text in listOf("", " ", "\n\t")) {
            val failure = runCatching { ru.nezabudka.app.ports.SpeechRecognition(text).requireRecognizedSpeech() }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals("Речь не обнаружена.", assistantErrorNoticeText(requireNotNull(failure)))
            assertEquals("Речь не обнаружена. Попробуйте произнести фразу ближе к микрофону.", assistantErrorText(failure))
        }
        val speech = ru.nezabudka.app.ports.SpeechRecognition("Покажи заметки")
        assertSame(speech, speech.requireRecognizedSpeech())
    }
    @Test fun `popup errors are short and never contain raw diagnostics`() {
        assertEquals("Речь не обнаружена.", assistantErrorNoticeText(IllegalStateException("Речь не обнаружена")))
        assertEquals("Сервис отклонил доступ.", assistantErrorNoticeText(IllegalStateException("HTTP 403: private-token")))
        assertNull(assistantErrorNoticeText(SecurityException("Разрешите доступ к микрофону")))
    }
    @Test fun `no speech does not become a microphone permission failure`() {
        assertEquals("Речь не обнаружена. Попробуйте произнести фразу ближе к микрофону.",
            assistantErrorText(IllegalStateException("Речь не обнаружена. Попробуйте произнести фразу ближе к микрофону.")))
        assertNull(assistantErrorText(SecurityException("Разрешите доступ к микрофону")))
        assertEquals("Модель Whisper не установлена.", assistantErrorText(WhisperModelMissingException()))
    }
    @Test fun `cloud details and arbitrary exception text are not exposed`() {
        assertEquals("Сервис отклонил доступ. Проверьте ключ или токен распознавания.",
            assistantErrorText(IllegalStateException("Yandex SpeechKit: HTTP 403: internal response with secret")))
        assertEquals("Сервис временно ограничил число запросов. Попробуйте позже.",
            assistantErrorText(IllegalStateException("VK Cloud Speech: HTTP 429: internal response")))
        assertFalse(assistantErrorText(UnknownHostException("private-host"))!!.contains("private-host"))
        assertFalse(assistantErrorText(IllegalStateException("native address 0xFF"))!!.contains("0xFF"))
        assertEquals("Сервис распознавания не ответил вовремя. Попробуйте ещё раз.",
            assistantErrorText(SocketTimeoutException("timeout")))
    }
}

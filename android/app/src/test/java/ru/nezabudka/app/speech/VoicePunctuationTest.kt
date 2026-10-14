package ru.nezabudka.app.speech

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.nezabudka.app.ports.SpeechRecognition

class VoicePunctuationTest {
    @Test fun `punctuation preserves confidence alternatives and mandatory confirmation`() = runBlocking {
        val original = SpeechRecognition("Если пойдёт дождь останусь дома", 0.41f,
            listOf("Если пойдет дождь останусь дома"), "Проверьте текст", true)
        var calls = 0
        val result = restoreVoicePunctuation(original, NOTE_PUNCTUATION_RUPUNCT, { text, model ->
            calls++
            assertEquals(original.text, text)
            assertEquals(NOTE_PUNCTUATION_RUPUNCT, model)
            "Если пойдёт дождь, останусь дома"
        }, { throw AssertionError(it) })
        assertEquals(1, calls)
        assertEquals(original.copy(text = "Если пойдёт дождь, останусь дома"), result)
    }

    @Test fun `failure or empty punctuation result preserves original voice text`() = runBlocking {
        val original = SpeechRecognition("Напомни полить цветы", 0.8f)
        for (blank in listOf(false, true)) {
            var error: Exception? = null
            val result = restoreVoicePunctuation(original, NOTE_PUNCTUATION_RUPUNCT,
                { _, _ -> if (blank) "" else throw IllegalStateException("Модель недоступна") }, { error = it })
            assertSame(original, result)
            assertNotNull(error)
        }
    }

    @Test fun `cancellation is not swallowed or treated as successful speech`() = runBlocking {
        var cancelled = false
        try {
            restoreVoicePunctuation(SpeechRecognition("Тест"), NOTE_PUNCTUATION_RUPUNCT,
                { _, _ -> throw CancellationException() }, { fail("Cancellation must propagate") })
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun `recognizer punctuation mode leaves its text intact`() = runBlocking {
        val original = SpeechRecognition("В десять двадцать пять, завтра.")
        val result = restoreVoicePunctuation(original, NOTE_PUNCTUATION_WHISPER,
            { text, model -> assertEquals(NOTE_PUNCTUATION_WHISPER, model); text }, { throw AssertionError(it) })
        assertEquals(original, result)
    }
}

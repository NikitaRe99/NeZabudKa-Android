package ru.nezabudka.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperTranscriptTest {
    @Test fun `напомню at command start is corrected without changing name`() {
        assertEquals(
            "Напомни позвонить Иннокентию",
            "Напомню, позвонить Иннокентию".normalizeWhisperTranscript(),
        )
    }

    @Test fun `punctuation after correct command word is removed`() {
        assertEquals("Напомни позвонить Кате", "Напомни, позвонить Кате".normalizeWhisperTranscript())
    }

    @Test fun `split and inflected reminder command variants are corrected at sentence start`() {
        val suffix = " купить молоко завтра"
        listOf("На помни", "Напом ни", "Напомню", "Напомня", "Напом не", "Напом ните").forEach { variant ->
            assertEquals("Напомни$suffix", "$variant$suffix".normalizeWhisperTranscript())
        }
        assertEquals("Пожалуйста, напомни$suffix", "Пожалуйста, напомня,$suffix".normalizeWhisperTranscript())
    }

    @Test fun `common nominative name errors are corrected only in dative context`() {
        assertEquals("Напомни Кате позвонить", "Напомни Катя позвонить".normalizeWhisperTranscript())
        assertEquals("Напомни позвонить Ване", "Напомни позвонить Ваня".normalizeWhisperTranscript())
        assertEquals("Напомни встретить Катю", "Напомни встретить Катю".normalizeWhisperTranscript())
    }

    @Test fun `folder names are restored to expected nominative form`() {
        assertEquals("Создай папку личное", "Создай папку личную".normalizeWhisperTranscript())
        assertEquals("Создай папку Развлечение", "Создай папку Развлечения".normalizeWhisperTranscript())
        assertEquals("Создай папку рабочая", "Создай папку рабочую".normalizeWhisperTranscript())
        assertEquals("Создай папку новая работа", "Создай папку новую работу".normalizeWhisperTranscript())
        assertEquals("Создай папку семья", "Создай папку семью".normalizeWhisperTranscript())
        assertEquals("Создай папку меню", "Создай папку меню".normalizeWhisperTranscript())
        assertEquals("Создай заметку про развлечения", "Создай заметку про развлечения".normalizeWhisperTranscript())
    }

    @Test fun `напомню inside ordinary sentence is not changed`() {
        assertEquals(
            "Я напомню Иннокентию",
            "Я напомню Иннокентию".normalizeWhisperTranscript(),
        )
        assertEquals("Я напомня ему завтра", "Я напомня ему завтра".normalizeWhisperTranscript())
        assertEquals("Напомнить ему завтра", "Напомнить ему завтра".normalizeWhisperTranscript())
    }

    @Test fun `dative name is corrected inside a free reminder phrase`() {
        assertEquals(
            "Надо не забыть позвонить Кате через три дня",
            "Надо не забыть позвонить Катя через три дня".normalizeWhisperTranscript(),
        )
    }

    @Test fun `relative date punctuation and missing time preposition are corrected`() {
        assertEquals(
            "Надо не забыть позвонить Кате через три дня в восемь утра.",
            "Надо не забыть позвонить Катя, через три дня, восемь утра.".normalizeWhisperTranscript(),
        )
    }

    @Test fun `streaming chunks are joined by their longest word overlap`() {
        assertEquals(
            "Напомни позвонить Кате завтра утром",
            mergeTranscripts("Напомни позвонить Кате.", "позвонить Кате завтра утром"),
        )
    }

    @Test fun `streaming chunks without overlap are appended`() {
        assertEquals("Создай заметку купить молоко", mergeTranscripts("Создай заметку", "купить молоко"))
    }

    @Test fun `decoder loop repeating a phrase many times is collapsed`() {
        assertEquals(
            "Напомни купить молоко.",
            "Напомни купить молоко. Напомни купить молоко. Напомни купить молоко. Напомни купить молоко."
                .collapseWhisperRepetition(),
        )
    }

    @Test fun `intentional short repetitions are preserved`() {
        assertEquals(
            "очень очень важно",
            "очень очень важно".collapseWhisperRepetition(),
        )
        assertEquals(
            "Повтори эту фразу. Повтори эту фразу.",
            "Повтори эту фразу. Повтори эту фразу.".collapseWhisperRepetition(),
        )
    }

    @Test fun `compact model filter removes a duplicated multi word phrase`() {
        assertEquals(
            "Купить молоко завтра.",
            "Купить молоко завтра. Купить молоко завтра.".collapseWhisperRepetition(aggressive = true),
        )
        assertEquals("очень очень важно", "очень очень важно".collapseWhisperRepetition(aggressive = true))
    }

    @Test fun `trailing partial decoder restart is removed without another inference`() {
        assertEquals(
            "Напомни выбросить мусор.",
            "Напомни выбросить мусор. нап".collapseWhisperRepetition(),
        )
        assertEquals(
            "Создай заметку купить молоко",
            "Создай заметку купить молоко созд".collapseWhisperRepetition(),
        )
    }

    @Test fun `ordinary phrase ending is not mistaken for partial restart`() {
        assertEquals(
            "Напомни выбросить мусор завтра",
            "Напомни выбросить мусор завтра".collapseWhisperRepetition(),
        )
    }

    @Test fun `split command words provide a corrected alternative`() {
        assertEquals("удали напоминание про мусор", speechCommandAlternatives("У дали напоминание про мусор").first())
        assertEquals("покажи напоминания", speechCommandAlternatives("По кажи напоминания").first())
        assertEquals("Создай заметку купить хлеб", speechCommandAlternatives("Создай за метку купить хлеб").first())
    }

    @Test fun `ordinary transcript is not duplicated as an alternative`() {
        assertEquals(listOf("Какие у меня планы"), speechCommandAlternatives("Какие у меня планы"))
    }

    @Test fun `context prompt is bounded and strips unsafe punctuation`() {
        val prompt = contextualWhisperPrompt((1..20).map { "Папка/$it!" })

        assertTrue(prompt.contains("Папка 1"))
        assertTrue(prompt.contains("Папка 16"))
        assertFalse(prompt.contains("Папка 17"))
        assertFalse(prompt.contains('/'))
        assertFalse(prompt.contains('!'))
        assertTrue(RUSSIAN_WHISPER_PROMPT.contains("Напомни"))
        assertTrue(RUSSIAN_WHISPER_PROMPT.contains("через три дня"))
        assertTrue(RUSSIAN_WHISPER_PROMPT.contains("о встрече"))
        assertTrue(RUSSIAN_WHISPER_PROMPT.contains("для работы"))
        assertTrue(RUSSIAN_WHISPER_PROMPT.contains("на следующую неделю"))
    }

}

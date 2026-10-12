package ru.nezabudka.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class PunctuationTextTest {
    @Test fun `only predicted commas change while original words and terminal signs remain`() {
        val text = "Если завтра будет дождь я останусь дома."
        val words = punctuationWords(text)
        val commas = words.map { it.value == "дождь" }
        assertEquals("Если завтра будет дождь, я останусь дома.", applyPredictedCommas(text, words, commas))
    }

    @Test fun `incorrect comma can be removed without changing whitespace or case`() {
        val text = "Напомни, завтра утром."
        assertEquals("Напомни завтра утром.", applyPredictedCommas(text, punctuationWords(text), List(3) { false }))
    }

    @Test fun `decimal numbers URLs email quotes and newlines remain intact`() {
        val text = "Цена 3,5 рубля. Сайт https://example.ru/a,b и почта a,b@example.ru.\n«Да», — ответил я!"
        val words = punctuationWords(text)
        val output = applyPredictedCommas(text, words, List(words.size) { null })
        assertEquals(text, output)
        val decimal = "Цена 3,5 рубля.\nНовая строка."
        val changed = applyPredictedCommas(decimal, punctuationWords(decimal), List(punctuationWords(decimal).size) { false })
        assertEquals(decimal, changed)
    }

    @Test fun `empty input and unknown model preserve default behavior`() {
        assertEquals("", applyPredictedCommas("", emptyList(), emptyList()))
        assertEquals("whisper", normalizeNotePunctuationModel("unknown"))
        assertEquals("rupunct_small", normalizeNotePunctuationModel("rupunct_small"))
    }
}

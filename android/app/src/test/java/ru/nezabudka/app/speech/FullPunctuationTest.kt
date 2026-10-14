package ru.nezabudka.app.speech

import org.junit.Assert.*
import org.junit.Test

class FullPunctuationTest {
    @Test fun combinesCaseVariantsWithoutTurningUnknownMarkIntoComma() {
        val labels = listOf("UPPER_PERIOD", "LOWER_PERIOD", "LOWER_O", "LOWER_DEFIS")
        assertEquals(".", fullPunctuationPrediction(floatArrayOf(4f, 4f, 0f, 0f), labels).mark)
        assertNull(fullPunctuationPrediction(floatArrayOf(0f, 0f, 0f, 8f), labels).mark)
        assertNull(fullPunctuationPrediction(floatArrayOf(0f, 0f, 8f, 0f), labels).mark)
    }
    @Test fun restoresSentenceBoundariesAndCaseWithoutReplacingWords() {
        val text = "ты дома когда вернёшься"
        val result = applyFullPunctuation(text, punctuationWords(text), listOf(
            null, FullPunctuationPrediction("?"), null, FullPunctuationPrediction("?")))
        assertEquals("Ты дома? Когда вернёшься?", result)
    }
    @Test fun preservesNumbersLinksQuotesAndExistingPunctuation() {
        val text = "размер 3.14 время 10:25 адрес team@example.ru сайт https://example.org/a\n«да!»"
        val words = punctuationWords(text)
        val result = applyFullPunctuation(text, words, List(words.size) { FullPunctuationPrediction(",") })
        assertTrue(result.contains("3.14")); assertTrue(result.contains("10:25"))
        assertTrue(result.contains("team@example.ru")); assertTrue(result.contains("https://example.org/a\n«"))
        assertTrue(result.endsWith("Да!»"))
    }
    @Test fun doesNotCreateTrailingCommaOrDuplicateFullStop() {
        assertEquals("Привет", applyFullPunctuation("привет", punctuationWords("привет"), listOf(FullPunctuationPrediction(","))))
        assertEquals("Привет!", applyFullPunctuation("привет!", punctuationWords("привет!"), listOf(FullPunctuationPrediction("."))))
    }
    @Test fun numericSequenceRetainsItsOriginalGap() {
        assertEquals("10 25", applyFullPunctuation("10 25", punctuationWords("10 25"), listOf(FullPunctuationPrediction("."), null)))
    }
    @Test fun preservesExistingMixedCaseNames() {
        assertEquals("iPhone работает.", applyFullPunctuation("iPhone работает", punctuationWords("iPhone работает"), listOf(null, FullPunctuationPrediction("."))))
    }
}

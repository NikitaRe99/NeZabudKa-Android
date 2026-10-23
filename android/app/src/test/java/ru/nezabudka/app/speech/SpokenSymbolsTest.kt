package ru.nezabudka.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nezabudka.app.domain.DEFAULT_SPOKEN_SYMBOLS
import ru.nezabudka.app.domain.SpokenSymbol

class SpokenSymbolsTest {
    @Test fun `punctuation phrases respect word boundaries and longest match`() {
        assertEquals("Купить: хлеб, молоко; чай", replaceSpokenSymbols("Купить двоеточие хлеб запятая молоко точка с запятой чай", DEFAULT_SPOKEN_SYMBOLS))
        assertEquals("точка зрения и точками", replaceSpokenSymbols("точка зрения и точками", listOf(SpokenSymbol("точками", "точками"))))
        assertEquals("двоеточиями", replaceSpokenSymbols("двоеточиями", DEFAULT_SPOKEN_SYMBOLS))
    }
    @Test fun `newlines and custom replacements are applied only once`() {
        assertEquals("Готово.", replaceSpokenSymbols("Готово точка.", DEFAULT_SPOKEN_SYMBOLS))
        assertEquals("Серьёзно?", replaceSpokenSymbols("Серьёзно вопросительный знак?", DEFAULT_SPOKEN_SYMBOLS))
        assertEquals("Первая\nВторая", replaceSpokenSymbols("Первая новая строка Вторая", DEFAULT_SPOKEN_SYMBOLS))
        assertEquals("тире", replaceSpokenSymbols("знак", listOf(SpokenSymbol("знак", "тире"), SpokenSymbol("тире", "—"))))
        assertEquals("План: дальше", replaceSpokenSymbols("План двоеточие, дальше", DEFAULT_SPOKEN_SYMBOLS))
    }
}

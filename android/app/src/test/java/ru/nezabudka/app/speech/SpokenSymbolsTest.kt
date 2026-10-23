package ru.nezabudka.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nezabudka.app.domain.DEFAULT_SPOKEN_SYMBOLS
import ru.nezabudka.app.domain.SpokenSymbol

class SpokenSymbolsTest {
    private val rules = DEFAULT_SPOKEN_SYMBOLS + listOf(
        SpokenSymbol("запятая", ","), SpokenSymbol("точка с запятой", ";"),
        SpokenSymbol("точка", "."), SpokenSymbol("вопросительный знак", "?"),
        SpokenSymbol("новая строка", "\n"),
    )
    @Test fun `punctuation phrases respect word boundaries and longest match`() {
        assertEquals("Купить: хлеб, молоко; чай", replaceSpokenSymbols("Купить двоеточие хлеб запятая молоко точка с запятой чай", rules))
        assertEquals("точка зрения и точками", replaceSpokenSymbols("точка зрения и точками", listOf(SpokenSymbol("точками", "точками"))))
        assertEquals("двоеточиями", replaceSpokenSymbols("двоеточиями", rules))
    }
    @Test fun `newlines and custom replacements are applied only once`() {
        assertEquals("Готово.", replaceSpokenSymbols("Готово точка.", rules))
        assertEquals("Серьёзно?", replaceSpokenSymbols("Серьёзно вопросительный знак?", rules))
        assertEquals("Первая\nВторая", replaceSpokenSymbols("Первая новая строка Вторая", rules))
        assertEquals("тире", replaceSpokenSymbols("знак", listOf(SpokenSymbol("знак", "тире"), SpokenSymbol("тире", "—"))))
        assertEquals("План: дальше", replaceSpokenSymbols("План двоеточие, дальше", rules))
    }
}

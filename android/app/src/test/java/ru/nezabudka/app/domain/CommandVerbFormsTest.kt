package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandVerbFormsTest {
    @Test fun `request morphology changes only the leading verb`() {
        val cases = mapOf(
            "Отобразить дела на следующую пятницу" to "покажи дела на следующую пятницу",
            "Пожалуйста, показать мои напоминания на завтра" to "Пожалуйста, покажи мои напоминания на завтра",
            "Вывести список задач на месяц" to "выведи список задач на месяц",
            "Зафиксируем мысль: завтра зафиксируем результат" to "зафиксируй мысль: завтра зафиксируем результат",
            "Зафиксировать запись в папке Опыт о том, что часы остановились" to "зафиксируй запись в папке Опыт о том, что часы остановились",
            "Запишем следующую мысль о ремонте" to "запиши следующую мысль о ремонте",
            "Сохраним заметку заменить крепёж" to "сохрани заметку заменить крепёж",
        )
        cases.forEach { (input, expected) -> assertEquals(expected, normalizeCommandVerbForm(input)) }
    }

    @Test fun `statements negation and unrelated objects are unchanged`() {
        listOf(
            "Завтра зафиксируем результат", "Мы зафиксируем мысль позже", "Не отобразить дела",
            "Как отобразить дела на завтра", "Можно ли зафиксировать мысль",
            "Сохраним отношения", "Зафиксируем давление",
            "Зафиксируй мысль написать слово зафиксируем", "Создай заметку отобразить дела",
        ).forEach { input ->
            assertEquals(input, normalizeCommandVerbForm(input))
        }
    }
}

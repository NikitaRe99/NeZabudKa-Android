package ru.nezabudka.app.llm

import ru.nezabudka.app.domain.CommandIntent
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicNluCommandMapperTest {
    @Test fun `an explicit note keeps acronyms even if the model drops them`() {
        assertEquals("проверить API", mapper.map(CommandIntent.CREATE_NOTE, mapOf("TEXT" to "проверить"), "Создай заметку проверить API", now).noteText)
        val withoutFolder = mapper.map(CommandIntent.CREATE_NOTE, mapOf("TEXT" to "позвонить поставщику"), "Сохрани заметку без папки позвонить поставщику", now)
        assertEquals("позвонить поставщику", withoutFolder.noteText)
        assertNull(withoutFolder.noteFolder)
    }

    @Test fun `folder insertion carrier separates a multiword unquoted folder`() {
        val result = mapper.map(CommandIntent.CREATE_NOTE, mapOf("TEXT" to "в сером пенале"), "Закинь в Тестовый блокнот запись о том что запасные насадки лежат в сером пенале", now)
        assertEquals("Тестовый блокнот", result.noteFolder)
        assertEquals("запасные насадки лежат в сером пенале", result.noteText)
    }
    @Test fun `speech carriers preserve the full body without punctuation`() {
        val first = mapper.map(CommandIntent.CREATE_NOTE, mapOf("TEXT" to "семь килограммов"), "Для папки Тестовый блокнот сохрани следующую мысль верхняя полка выдерживает семь килограммов", now)
        assertEquals("Тестовый блокнот", first.noteFolder)
        assertEquals("верхняя полка выдерживает семь килограммов", first.noteText)
        val second = mapper.map(CommandIntent.CREATE_NOTE, mapOf("TEXT" to "миллиметра"), "Хочу оставить себе такую запись размер переходника двадцать два миллиметра", now)
        assertEquals("размер переходника двадцать два миллиметра", second.noteText)
    }

    @Test fun `spoken repeat is attached to a reminder`() {
        val command = mapper.map(CommandIntent.CREATE_REMINDER, mapOf("TEXT" to "полить рассаду"), "Напомни каждый день в десять двадцать пять полить рассаду", now)
        assertEquals("daily", command.recurrence)
        assertEquals("полить рассаду", command.text)
    }
    private val mapper = ClassicNluCommandMapper()
    private val now = LocalDateTime.of(2026, 8, 8, 12, 0)

    @Test fun `folder preceding the action excludes the action verb from its name`() {
        val command = mapper.map(CommandIntent.CREATE_NOTE, mapOf("FOLDER" to "Рецепты добавь", "TEXT" to "соус из томатов"),
            "В папку Рецепты добавь заметку соус из томатов", now)
        assertEquals("Рецепты", command.noteFolder)
        assertEquals("соус из томатов", command.noteText)
    }

    @Test fun `recognized reminder carrier keeps a complete verb even when BIO drops it`() {
        val phrases = listOf("Подай мне сигнал перед тем, как продлить абонемент на парковку", "Мне потребуется вовремя продлить абонемент на парковку")
        phrases.forEach { phrase ->
            assertEquals("продлить абонемент на парковку", mapper.map(CommandIntent.CREATE_REMINDER,
                mapOf("TEXT" to "абонемент на парковку"), phrase, now).text)
        }
    }

    @Test fun `an unfamiliar noun after a generic creation verb is not copied into the payload`() {
        assertEquals("купить страховку", mapper.map(CommandIntent.CREATE_REMINDER,
            mapOf("TEXT" to "купить страховку"), "Оставь контрольную точку: купить страховку", now).text)
        assertEquals("проверить водяной насос", mapper.map(CommandIntent.CREATE_REMINDER,
            mapOf("TEXT" to "проверить водяной насос"), "Создай контрольный пункт проверить водяной насос", now).text)
    }

    @Test fun `note carrier keeps every original word instead of an incomplete slot`() {
        assertEquals("контакт мастера 89201234567", mapper.map(CommandIntent.CREATE_NOTE,
            mapOf("TEXT" to "мастера 89201234567"), "Оформи карточку с содержимым контакт мастера 89201234567", now).noteText)
        assertEquals("номер счётчика 547812", mapper.map(CommandIntent.CREATE_NOTE,
            mapOf("TEXT" to "номер счётчика 547812"), "Запиши в личную памятку номер счётчика 547812", now).noteText)
    }

    @Test fun `stop tracking extracts the whole reminder target`() {
        assertEquals("проверку нового водяного фильтра", mapper.map(CommandIntent.DELETE_REMINDER,
            mapOf("TARGET" to "фильтра"), "Не отслеживай больше задачу проверку нового водяного фильтра", now).targetText)
    }

    @Test fun `reminder date always comes from original phrase`() {
        val command = mapper.map(
            CommandIntent.CREATE_REMINDER,
            mapOf("TEXT" to "проверить давление"),
            "Главное не забыть завтра в девять проверить давление",
            now,
        )

        assertEquals("проверить давление", command.text)
        assertEquals(LocalDateTime.of(2026, 8, 9, 9, 0), command.remindAt)
    }

    @Test fun `undated reminder remains undated for clarification`() {
        val command = mapper.map(
            CommandIntent.CREATE_REMINDER,
            mapOf("TEXT" to "выбросить мусор"),
            "Надо не забыть выбросить мусор",
            now,
        )

        assertEquals("выбросить мусор", command.text)
        assertNull(command.remindAt)
    }

    @Test fun `complete BIO reminder text wins over an unfamiliar command prefix`() {
        val command = mapper.map(
            CommandIntent.CREATE_REMINDER,
            mapOf("TEXT" to "передать ключи управляющему"),
            "Возьми под контроль передать ключи управляющему",
            now,
        )

        assertEquals("передать ключи управляющему", command.text)
    }

    @Test fun `delimiter extracts reminder payload independently of command wording`() {
        val command = mapper.map(
            CommandIntent.CREATE_REMINDER,
            emptyMap(),
            "Поставь отметку на завтра: проверить крепление кресла",
            now,
        )

        assertEquals("проверить крепление кресла", command.text)
        assertEquals(now.toLocalDate().plusDays(1), command.remindAt?.toLocalDate())
    }

    @Test fun `list period is parsed by Kotlin`() {
        val command = mapper.map(CommandIntent.LIST_REMINDERS, emptyMap(), "Что у меня завтра", now)

        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(), command.periodStart)
        assertEquals(now.toLocalDate().plusDays(2).atStartOfDay(), command.periodEnd)
    }

    @Test fun `last reminder deletion needs no generated target`() {
        val command = mapper.map(CommandIntent.DELETE_REMINDER, emptyMap(), "Удали последнее", now)

        assertTrue(command.targetLast)
        assertNull(command.targetText)
    }

    @Test fun `note folder is accepted only from the original phrase`() {
        val command = mapper.map(
            CommandIntent.CREATE_NOTE,
            mapOf("TEXT" to "неверный текст", "FOLDER" to "Выдуманная"),
            "Создай заметку купить фильтр в папке Покупки",
            now,
        )

        assertEquals("купить фильтр", command.noteText)
        assertEquals("Покупки", command.noteFolder)
    }

    @Test fun `date and free reminder prefix are removed without trusting truncated BIO slot`() {
        val command = mapper.map(
            CommandIntent.CREATE_REMINDER,
            mapOf("TEXT" to "ить мясо"),
            "Маякни мне через час разморозить мясо",
            now,
        )

        assertEquals("разморозить мясо", command.text)
        assertEquals(now.plusHours(1), command.remindAt)
    }

    @Test fun `note text preserves leading noun and full number`() {
        val command = mapper.map(
            CommandIntent.CREATE_NOTE,
            mapOf("TEXT" to "калитки 246"),
            "Запомни код калитки 2468",
            now,
        )

        assertEquals("код калитки 2468", command.noteText)
    }

    @Test fun `complete BIO note text wins over an unfamiliar command prefix`() {
        val command = mapper.map(
            CommandIntent.CREATE_NOTE,
            mapOf("TEXT" to "номер заявления 203817"),
            "Внеси сведения в память: номер заявления 203817",
            now,
        )

        assertEquals("номер заявления 203817", command.noteText)
    }

    @Test fun `delimiter extracts note text but does not split a URL`() {
        val delimited = mapper.map(
            CommandIntent.CREATE_NOTE,
            emptyMap(),
            "Оставь для справки — номер пломбы 843019",
            now,
        )
        val url = mapper.map(
            CommandIntent.CREATE_NOTE,
            mapOf("TEXT" to "https://example.org/help"),
            "Запомни https://example.org/help",
            now,
        )

        assertEquals("номер пломбы 843019", delimited.noteText)
        assertEquals("https://example.org/help", url.noteText)
    }

    @Test fun `multiword folder is extracted from original phrase`() {
        val command = mapper.map(
            CommandIntent.CREATE_FOLDER,
            mapOf("FOLDER" to "Домашний"),
            "Организуй папку Домашний ремонт",
            now,
        )

        assertEquals("Домашний ремонт", command.noteFolder)
    }

    @Test fun `deletion target is extracted for prefix and suffix formulations`() {
        val prefix = mapper.map(
            CommandIntent.DELETE_REMINDER,
            mapOf("TARGET" to "поликлинику"),
            "Отмени визит в поликлинику",
            now,
        )
        val suffix = mapper.map(
            CommandIntent.DELETE_REMINDER,
            mapOf("TARGET" to "поликлинику"),
            "Задачу позвонить в поликлинику можно убрать",
            now,
        )

        assertEquals("визит в поликлинику", prefix.targetText)
        assertEquals("позвонить в поликлинику", suffix.targetText)
    }

    @Test fun `polite and negative deletion formulations preserve target`() {
        val polite = mapper.map(
            CommandIntent.DELETE_REMINDER,
            emptyMap(),
            "Можно отменить оплату страховки",
            now,
        )
        val negative = mapper.map(
            CommandIntent.DELETE_REMINDER,
            emptyMap(),
            "Больше не напоминай про оплату парковки",
            now,
        )

        assertEquals("оплату страховки", polite.targetText)
        assertEquals("оплату парковки", negative.targetText)
    }

    @Test fun `model folder is accepted only when it occurs in original note phrase`() {
        val command = mapper.map(
            CommandIntent.CREATE_NOTE,
            mapOf("TEXT" to "давление шин 2.3", "FOLDER" to "Автомобиль"),
            "Запиши в Автомобиль давление шин 2.3",
            now,
        )

        assertEquals("давление шин 2.3", command.noteText)
        assertEquals("Автомобиль", command.noteFolder)
    }
}

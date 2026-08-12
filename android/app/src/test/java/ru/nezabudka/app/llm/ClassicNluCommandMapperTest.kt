package ru.nezabudka.app.llm

import ru.nezabudka.app.domain.CommandIntent
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicNluCommandMapperTest {
    private val mapper = ClassicNluCommandMapper()
    private val now = LocalDateTime.of(2026, 8, 8, 12, 0)

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

package ru.nezabudka.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperTranscriptTest {
    @Test fun `an implausible decoder cycle triggers verification without treating spoken repeats as a loop`() {
        val loop = List(14) { "После, завтра, восемь вечера" }.joinToString(", ") + ", завтра, вос"
        assertTrue(loop.hasWhisperDecoderLoop(2.98))
        assertFalse("Привет привет, очень очень важно".hasWhisperDecoderLoop(2.5))
        assertFalse("Купить билеты. Купить билеты. Купить билеты.".hasWhisperDecoderLoop(8.0))
    }
    @Test fun `split day after tomorrow is joined only in reminder commands or date replies`() {
        listOf("после завтра", "после, завтра", "после,завтра").forEach { day ->
            assertEquals("Напомни послезавтра проверить фильтр", "Напомни $day проверить фильтр".normalizeWhisperTranscript())
            assertEquals("Покажи напоминания послезавтра", "Покажи напоминания $day".normalizeWhisperTranscript())
            assertEquals("послезавтра в десять двадцать пять", normalizeExpectedDateReply("$day десять двадцать пять"))
            val note = "Создай заметку фраза $day будет другой"
            assertEquals(note, note.normalizeWhisperTranscript())
            val dictation = "Напомни $day проверить фильтр"
            assertEquals(dictation, dictation.normalizeWhisperTranscript(commandMode = false))
        }
        assertEquals("Послезавтра утром", normalizeExpectedDateReply("После, завтра утром"))
        assertEquals("Напомни после завтрака позвонить", "Напомни после завтрака позвонить".normalizeWhisperTranscript())
    }

    @Test fun `command date pauses do not become commas in a reminder`() {
        assertEquals(
            "Напомни завтра выбросить мусор и сходить в магазин за колбасой.",
            "Напомни завтра, выбросить мусор и сходить в магазин за колбасой.".normalizeWhisperTranscript(),
        )
        assertEquals("Послезавтра в восемь вечера.", "После, завтра в восемь вечера.".normalizeWhisperTranscript())
        assertEquals("Напомни послезавтра купить хлеб, молоко и сыр", "Напомни после, завтра, купить хлеб, молоко и сыр".normalizeWhisperTranscript())
        assertEquals("Напомни завтра в восемь вечера купить хлеб", "Напомни завтра, в восемь вечера купить хлеб".normalizeWhisperTranscript())
        assertEquals("Послезавтра в восемь вечера.", normalizeExpectedDateReply("Послезавтра, в восемь вечера."))
    }

    @Test fun `date comma cleanup keeps punctuation in note text and dictation`() {
        val note = "Создай заметку завтра, купить хлеб, молоко и сыр"
        assertEquals(note, note.normalizeWhisperTranscript())
        assertEquals(note, note.normalizeWhisperTranscript(commandMode = false))
        val reminder = "Напомни купить хлеб, молоко и сыр завтра"
        assertEquals(reminder, reminder.normalizeWhisperTranscript())
    }
    @Test fun `short fast inference uses a punctuated vocabulary without dynamic folder names`() {
        val original = contextualWhisperPrompt(listOf("Работа", "Катя"))
        val short = whisperPromptForInference(original, true, 208, 1)
        assertTrue(short.contains("Напомни, напоминание, заметка"))
        assertTrue(short.contains("отмени"))
        assertFalse(short.contains("Работа"))
        assertFalse(short.contains("Катя"))
        assertEquals("", whisperPromptForInference(original, false, 208, 1))
        assertEquals(original, whisperPromptForInference(original, true, 768, 3))
        assertEquals(original, whisperPromptForInference(original, true, 512, 1))
        assertEquals(original, whisperPromptForInference(original, true, 0, 1))
    }

    @Test fun `word repetition signals a retry without rewriting intentional dictation`() {
        val intentional = "Да. Да, очень очень важно!"
        assertTrue(intentional.hasConsecutiveWhisperWordRepetition())
        assertEquals(intentional, intentional.collapseWhisperRepetition())
        assertFalse("Привет приветствие".hasConsecutiveWhisperWordRepetition())
        assertFalse("Я передам привет и приду завтра".hasConsecutiveWhisperWordRepetition())
    }

    @Test fun `split cancel is corrected only before an explicit command object`() {
        assertEquals("отмени напоминание проверить договор", speechCommandAlternatives("от меня напоминание проверить договор").first())
        assertEquals("от меня поступило сообщение", speechCommandAlternatives("от меня поступило сообщение").first())
        assertEquals("Создай заметку сообщение от меня передай завтра", speechCommandAlternatives("Создай заметку сообщение от меня передай завтра").first())
    }
    @Test fun `note dictation keeps commas and does not rewrite reminder wording`() {
        val note = "Напомню, завтра, восемь утра. Создай папку новую работу."
        assertEquals(note, note.normalizeWhisperTranscript(commandMode = false))
    }

    @Test fun `note segments keep sentence boundaries and repeated words`() {
        assertEquals("Да. Да, я согласен!", mergeWhisperSegments("Да.", "Да, я согласен!", commandMode = false))
        assertEquals("Куплю хлеб, если успею. Что ещё нужно?", mergeWhisperSegments("Куплю хлеб, если успею.", "Что ещё нужно?", commandMode = false))
        assertEquals("Если успею, куплю хлеб.", mergeWhisperSegments("Если успею,", "куплю хлеб.", commandMode = false))
    }

    @Test fun `empty note segments do not add whitespace`() {
        assertEquals("Заметка.", mergeWhisperSegments(" ", " Заметка. ", commandMode = false))
        assertEquals("Заметка.", mergeWhisperSegments(" Заметка. ", " ", commandMode = false))
    }

    @Test fun `commands still normalize overlapping transcript segments`() {
        assertEquals("Напомни позвонить Кате завтра", mergeWhisperSegments("Напомни позвонить Кате.", "позвонить Кате завтра", commandMode = true))
    }

    @Test fun `note dictation is not prompted with command examples in either mode`() {
        listOf(true, false).forEach { examples ->
            assertEquals("", contextualWhisperPrompt(listOf("Работа"), includeCommandExamples = examples, useCommandVocabulary = false))
        }
    }

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

    @Test fun `default prompt contains vocabulary without invented dates or note contents`() {
        val prompt = contextualWhisperPrompt(listOf("Работа"))
        assertEquals("$WHISPER_COMMAND_VOCABULARY Работа", prompt)
        assertFalse(prompt.contains("восемь утра"))
        assertFalse(prompt.contains("позвонить Кате"))
        assertFalse(prompt.contains("Слова пользователя"))
    }

    @Test fun `accurate prompt contains vocabulary without canned command sentences`() {
        assertEquals(WHISPER_COMMAND_VOCABULARY, contextualWhisperPrompt(emptyList(), includeCommandExamples = false))
        assertEquals("", contextualWhisperPrompt(emptyList(), includeCommandExamples = false, useCommandVocabulary = false))
        assertEquals("$WHISPER_COMMAND_VOCABULARY Работа Катя", contextualWhisperPrompt(listOf("Работа", "Катя", "Работа"), includeCommandExamples = false))
        assertFalse(contextualWhisperPrompt(listOf("Работа"), false).contains("Создай заметку"))
    }

}

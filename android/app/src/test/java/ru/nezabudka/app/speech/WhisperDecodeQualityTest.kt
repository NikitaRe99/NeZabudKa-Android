package ru.nezabudka.app.speech

import org.junit.Assert.*
import org.junit.Test

class WhisperDecodeQualityTest {
    @Test fun weakWordIsVisibleEvenWhenMeanIsHigh() {
        val quality = WhisperDecodeQuality(0.92f, -0.15, 0.15, 0.04, 25)
        assertTrue(quality.hasWeakWord)
        assertFalse(quality.needsRetry)
    }
    @Test fun slightlyHigherMeanDoesNotSilentlyReplaceDifferentWords() {
        assertFalse(whisperTextsAgree("сходить к врачу", "сходите к речу"))
        assertTrue(whisperTextsAgree("Привет, привет!", "привет привет"))
        assertFalse(whisperTextsAgree("Привет", "привет привет"))
    }
    @Test fun taskContextNeverLeaksCommandsIntoDictation() {
        for (window in listOf(0, 208, 288, 512, 768)) {
            assertEquals("", whisperRequestPrompt(listOf("Работа", "Напомни завтра", "Покажи заметки"), WhisperRequestContext.DICTATION, window))
        }
        val date = whisperRequestPrompt(listOf("Работа"), WhisperRequestContext.DATE_REPLY, 208)
        assertFalse(date.contains("создай", true)); assertFalse(date.contains("Работа"))
        assertTrue(date.contains("без времени"))
        val short = whisperRequestPrompt(listOf("Работа"), WhisperRequestContext.COMMAND, 288)
        assertFalse(short.contains("Работа"))
        val stable = whisperRequestPrompt(listOf("Работа", "Работа", "Личная/папка!"), WhisperRequestContext.COMMAND, 768)
        assertTrue(stable.contains("ежемесячно")); assertTrue(stable.contains("Работа"))
        assertFalse(stable.contains('/')); assertFalse(stable.contains('!'))
    }
    @Test fun tabVocabularyIsLimitedToItsDomainAndShortWindowsDoNotReceiveFolderNames() {
        val note = whisperRequestPrompt(listOf("Мастерская"), WhisperRequestContext.NOTE_COMMAND, 208)
        val reminder = whisperRequestPrompt(listOf("Мастерская"), WhisperRequestContext.REMINDER_COMMAND, 768)
        assertTrue(note.contains("заметки")); assertFalse(note.contains("ежемесячно"))
        assertTrue(reminder.contains("ежемесячно")); assertFalse(reminder.contains("папка"))
        assertFalse(note.contains("Мастерская")); assertFalse(reminder.contains("Мастерская"))
        assertTrue(whisperRequestPrompt(listOf("Мастерская"), WhisperRequestContext.NOTE_COMMAND, 768).contains("Мастерская"))
    }
    @Test fun expectedDateContextDoesNotRewriteMisheardWordsOrSplitPhrases() {
        assertEquals("Савтра, десять двадцать пять.", normalizeExpectedDateReply("Савтра, десять двадцать пять."))
        assertEquals("Послесафтра утром", normalizeExpectedDateReply("Послесафтра утром"))
        for (text in listOf("с утра в десять", "завтра", "в десять двадцать пять")) {
            assertEquals(text, normalizeExpectedDateReply(text))
        }
        assertEquals("Савтра — название моей папки", "Савтра — название моей папки".normalizeWhisperTranscript(commandMode = false))
    }
    @Test fun dateReplyProducesTheSpokenClockInsteadOfDefaultingToCurrentTime() {
        val now = java.time.LocalDateTime.of(2026, 10, 12, 20, 30)
        val parser = ru.nezabudka.app.domain.RussianTemporalParser()
        val corrected = normalizeExpectedDateReply("Завтра, десять двадцать пять.")
        assertEquals(now.toLocalDate().plusDays(1).atTime(10, 25), parser.parse(corrected, now).remindAt)
        assertEquals("завтра десять бумаг", normalizeExpectedDateReply("завтра десять бумаг"))
        assertEquals("Завтра в десять двадцать пять", normalizeExpectedDateReply("Завтра в десять двадцать пять"))
        assertTrue(isCompleteExpectedDateReply(corrected))
        assertFalse(isCompleteExpectedDateReply("Савтра, десять двадцать пять."))
        assertFalse(isCompleteExpectedDateReply("Савтра, в десять двадцать пять."))
        assertTrue(isCompleteExpectedDateReply("без даты"))
    }
}

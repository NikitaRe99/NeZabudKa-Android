package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderBeforeNoteActionTest {
    @Test fun `reported phrase works with or without the note noun`() {
        listOf("В папку работа добавь заметку сходить к врачу и купить бумаги",
            "В папку работа добавь сходить к врачу и купить бумаги").forEach { text ->
            assertEquals("работа" to "сходить к врачу и купить бумаги", extractNoteParts(text))
        }
    }
    @Test fun `punctuation around the action does not become part of the folder name`() {
        listOf(
            "В папку Работа добавь заметку отправить отчёт",
            "В папку Работа добавь, заметку отправить отчёт",
            "В папку Работа добавь заметку, отправить отчёт",
            "В папку Работа, добавь заметку отправить отчёт",
            "В папку Работа добавь. Заметку отправить отчёт",
            "В папку Работа: добавь заметку отправить отчёт",
        ).forEach { text ->
            assertEquals(text, "Работа" to "отправить отчёт", extractNoteParts(text))
        }
    }

    @Test fun `quoted folder names retain their literal action word and punctuation`() {
        assertEquals("Работа добавь" to "отправить отчёт",
            extractNoteParts("В папку «Работа добавь» добавь, заметку отправить отчёт"))
        assertEquals("Работа, проекты" to "отправить отчёт",
            extractNoteParts("В папку «Работа, проекты», добавь заметку отправить отчёт"))
    }
}

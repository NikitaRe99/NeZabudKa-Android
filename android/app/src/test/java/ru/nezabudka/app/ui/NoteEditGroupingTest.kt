package ru.nezabudka.app.ui

import ru.nezabudka.app.ui.screens.NoteEditGrouping
import ru.nezabudka.app.ui.screens.NoteTextField
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditGroupingTest {
    @Test
    fun `typing one word creates one undo group`() {
        val grouping = NoteEditGrouping()

        assertTrue(grouping.startsNewGroup(NoteTextField.BODY, value(""), value("т")))
        assertFalse(grouping.startsNewGroup(NoteTextField.BODY, value("т"), value("те")))
        assertFalse(grouping.startsNewGroup(NoteTextField.BODY, value("те"), value("тес")))
        assertFalse(grouping.startsNewGroup(NoteTextField.BODY, value("тес"), value("тест")))
        assertFalse(grouping.startsNewGroup(NoteTextField.BODY, value("тест"), value("тест ")))
        assertTrue(grouping.startsNewGroup(NoteTextField.BODY, value("тест "), value("тест с")))
    }

    @Test
    fun `backspace and different fields start predictable undo groups`() {
        val grouping = NoteEditGrouping()

        assertTrue(grouping.startsNewGroup(NoteTextField.TITLE, value("слово"), value("слов")))
        assertFalse(grouping.startsNewGroup(NoteTextField.TITLE, value("слов"), value("сло")))
        assertFalse(grouping.startsNewGroup(NoteTextField.TITLE, value("сло"), value("сл")))
        assertTrue(grouping.startsNewGroup(NoteTextField.BODY, value(""), value("т")))
    }

    @Test
    fun `typing at the end of a long note uses cursor positions`() {
        val grouping = NoteEditGrouping()
        val longText = "а".repeat(100_000)

        assertTrue(grouping.startsNewGroup(NoteTextField.BODY, value(longText), value(longText + "б")))
        assertFalse(grouping.startsNewGroup(NoteTextField.BODY, value(longText + "б"), value(longText + "бв")))
    }

    @Test
    fun `paste and selection replacement start separate groups`() {
        val grouping = NoteEditGrouping()

        assertTrue(grouping.startsNewGroup(NoteTextField.BODY, value("текст"), value("текст вставка")))
        assertTrue(
            grouping.startsNewGroup(
                NoteTextField.BODY,
                TextFieldValue("текст", TextRange(1, 3)),
                value("тXт", cursor = 2),
            ),
        )
    }

    private fun value(text: String, cursor: Int = text.length) =
        TextFieldValue(text, selection = TextRange(cursor))
}

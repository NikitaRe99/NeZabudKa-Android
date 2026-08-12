package ru.nezabudka.app.data

import ru.nezabudka.app.domain.defaultCustomTheme
import ru.nezabudka.app.domain.withAutomaticDependentColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomThemeSettingsTest {
    @Test
    fun `dependent colors are derived from user-facing settings`() {
        val theme = defaultCustomTheme("light").copy(
            accent = "#FFFFFF",
            talkButton = "#000000",
            background = "#FFFFFF",
            navigation = "#000001",
            notice = "#000002",
            cursor = "#000003",
            textSecondary = "#000000",
        ).withAutomaticDependentColors()

        assertEquals("#171717", theme.onAccent)
        assertEquals("#FFFFFF", theme.talkButton)
        assertEquals("#171717", theme.onTalkButton)
        assertEquals("#4D4D4D", theme.placeholder)
        assertEquals(theme.background, theme.modal)
        assertEquals(theme.card, theme.navigation)
        assertEquals(theme.selected, theme.notice)
        assertEquals(theme.textPrimary, theme.cursor)
        assertEquals(theme.card, theme.noteDefault)
    }

    @Test
    fun `dark base gets a readable placeholder`() {
        val theme = defaultCustomTheme("dark").withAutomaticDependentColors()

        assertEquals("#171717", theme.onAccent)
        assertEquals("#7D7D82", theme.placeholder)
    }
}

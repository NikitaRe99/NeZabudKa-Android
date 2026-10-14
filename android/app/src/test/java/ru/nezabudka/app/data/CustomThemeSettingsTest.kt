package ru.nezabudka.app.data

import ru.nezabudka.app.domain.defaultCustomTheme
import ru.nezabudka.app.domain.withAutomaticDependentColors
import ru.nezabudka.app.domain.automaticContentColorHex
import ru.nezabudka.app.domain.automaticDangerContainerHex
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
        assertEquals(automaticDangerContainerHex(theme.danger, theme.background), theme.dangerContainer)
        assertEquals(automaticContentColorHex(theme.dangerContainer), theme.onDangerContainer)
    }

    @Test
    fun `old custom deletion colors cannot override automatic contrast`() {
        for (base in listOf("light", "dark")) {
            val theme = defaultCustomTheme(base).copy(
                danger = "#1764D2", dangerContainer = "#000000", onDangerContainer = "#000000",
            ).withAutomaticDependentColors()
            assertEquals(automaticDangerContainerHex(theme.danger, theme.background), theme.dangerContainer)
            assertEquals(automaticContentColorHex(theme.dangerContainer), theme.onDangerContainer)
        }
    }

    @Test
    fun `dark base gets a readable placeholder`() {
        val theme = defaultCustomTheme("dark").withAutomaticDependentColors()

        assertEquals("#F7F7F7", theme.onAccent)
        assertEquals("#7D7D82", theme.placeholder)
    }
}

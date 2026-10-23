package ru.nezabudka.app.domain

import org.junit.Assert.*
import org.junit.Test

class AppScreensTest {
    @Test fun `all screen combinations keep a collection accessible`() {
        for (mask in 0..7) {
            val screens = DEFAULT_APP_SCREENS.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            assertEquals("Combination $screens", mask and 6 != 0, validAppScreens(screens))
            assertTrue(validAppScreens(normalizeAppScreens(screens)))
        }
    }

    @Test fun `restored settings discard unknown screens and preserve display order`() {
        assertEquals(listOf("assistant", "notes"), normalizeAppScreens(listOf("notes", "settings", "assistant", "notes")))
        assertEquals(listOf("notes"), normalizeAppScreens(listOf("assistant", "settings")))
        assertFalse(validAppScreens(listOf("settings")))
    }
}

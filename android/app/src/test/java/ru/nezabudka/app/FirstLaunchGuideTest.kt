package ru.nezabudka.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstLaunchGuideTest {
    @Test
    fun `short guide covers core screens voice options and optional alarm mode`() {
        val pages = firstLaunchGuidePages()

        assertEquals(6, pages.size)
        assertEquals(
            listOf(
                OnboardingTarget.NOTES,
                OnboardingTarget.REMINDERS,
                OnboardingTarget.ASSISTANT,
                OnboardingTarget.VOICE,
                OnboardingTarget.SETTINGS,
                null,
            ),
            pages.map { it.target },
        )
        val text = pages.joinToString(" ") { "${it.title} ${it.text}" }
        assertTrue("Заметки" in text)
        assertTrue("Напоминания" in text)
        assertTrue("Whisper" in text)
        assertTrue("облачные STT" in text)
        assertTrue("режим будильника выключен" in text)
        assertTrue("Разрешения запрашиваются только" in pages.last().instruction)
    }

    @Test
    fun `interactive steps open the screen selected on the previous step`() {
        assertEquals(null, onboardingRouteAfterStep(0))
        assertEquals("notes", onboardingRouteAfterStep(1))
        assertEquals("reminders", onboardingRouteAfterStep(2))
        assertEquals("assistant", onboardingRouteAfterStep(3))
        assertEquals("assistant", onboardingRouteAfterStep(4))
        assertEquals("settings", onboardingRouteAfterStep(5))
    }
}

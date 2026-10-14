package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialSttEngineTest {
    @Test fun recommendsGigaamOnlyForFreshInstallationWithoutSettings() {
        assertEquals("gigaam", initialSttEngine(false, false))
    }
    @Test fun preservesImplicitWhisperForUpdatesEvenWithoutSavedPreferences() {
        assertEquals("whisper", initialSttEngine(false, true))
        assertEquals("whisper", initialSttEngine(true, true))
    }
    @Test fun restoredSettingsRetainTheLegacyDefault() {
        assertEquals("whisper", initialSttEngine(true, false))
    }
}

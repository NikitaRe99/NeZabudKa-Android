package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRulesTest {
    @Test
    fun `alarm presentation accepts supported values and falls back to screen`() {
        assertEquals(ALARM_PRESENTATION_SCREEN, normalizeAlarmPresentation(ALARM_PRESENTATION_SCREEN))
        assertEquals(ALARM_PRESENTATION_NOTIFICATION, normalizeAlarmPresentation(ALARM_PRESENTATION_NOTIFICATION))
        assertEquals(ALARM_PRESENTATION_SCREEN, normalizeAlarmPresentation("unknown"))
    }

    @Test
    fun `ui performance mode accepts supported values and falls back to automatic`() {
        assertEquals(UI_PERFORMANCE_AUTO, normalizeUiPerformanceMode(UI_PERFORMANCE_AUTO))
        assertEquals(UI_PERFORMANCE_REDUCED, normalizeUiPerformanceMode(UI_PERFORMANCE_REDUCED))
        assertEquals(UI_PERFORMANCE_FULL, normalizeUiPerformanceMode(UI_PERFORMANCE_FULL))
        assertEquals(UI_PERFORMANCE_AUTO, normalizeUiPerformanceMode("future-mode"))
    }

    @Test
    fun `alarm cycle settings stay inside safe supported ranges`() {
        assertEquals(15, normalizeAlarmRingSeconds(0))
        assertEquals(300, normalizeAlarmRingSeconds(1_000))
        assertEquals(1, normalizeAlarmPauseMinutes(0))
        assertEquals(30, normalizeAlarmPauseMinutes(60))
        assertEquals(1, normalizeAlarmRepeatCount(0))
        assertEquals(10, normalizeAlarmRepeatCount(100))
    }
    @Test
    fun `unknown restored enum values fall back to supported defaults`() {
        assertEquals("light", normalizeTheme("future-theme"))
        assertEquals("whisper", normalizeSttEngine("future-provider"))
        assertEquals("my_order", normalizeReminderSort("random"))
        assertEquals("updated", normalizeNoteSort("random"))
        assertEquals("carousel", normalizeFolderInterface("random"))
        assertEquals(30, normalizeNoteTrashRetentionDays(14))
        assertEquals(0, normalizeNoteTrashRetentionDays(0))
        assertEquals(90, normalizeNoteTrashRetentionDays(90))
    }

    @Test
    fun `manual note order is a supported sort mode`() {
        assertEquals("manual", normalizeNoteSort("manual"))
    }

    @Test
    fun `notification sound accepts local Android uris and rejects external values`() {
        val fallback = "content://settings/system/notification_sound"
        assertEquals("content://media/external/audio/1", normalizeNotificationSound(" content://media/external/audio/1 ", fallback))
        assertEquals("android.resource://com.example/raw/tone", normalizeNotificationSound("android.resource://com.example/raw/tone", fallback))
        assertEquals(fallback, normalizeNotificationSound("https://example.test/tone", fallback))
        assertEquals("", normalizeNotificationSound("", fallback))
    }

    @Test
    fun `automatic Whisper threads adapt to device without occupying every core`() {
        assertEquals(1, automaticWhisperThreadCount(cpuCores = 1, lowRamDevice = false, memoryClassMb = 256))
        assertEquals(2, automaticWhisperThreadCount(cpuCores = 2, lowRamDevice = false, memoryClassMb = 256))
        assertEquals(3, automaticWhisperThreadCount(cpuCores = 4, lowRamDevice = false, memoryClassMb = 256))
        assertEquals(4, automaticWhisperThreadCount(cpuCores = 8, lowRamDevice = false, memoryClassMb = 512))
        assertEquals(2, automaticWhisperThreadCount(cpuCores = 8, lowRamDevice = true, memoryClassMb = 512))
        assertEquals(2, automaticWhisperThreadCount(cpuCores = 8, lowRamDevice = false, memoryClassMb = 192))
    }

    @Test
    fun `manual Whisper threads are sanitized and limited by available cores`() {
        assertEquals(0, normalizeWhisperThreadCount(-2))
        assertEquals(20, normalizeWhisperThreadCount(20))
        assertEquals(
            4,
            effectiveWhisperThreadCount(
                configured = 8,
                cpuCores = 4,
                lowRamDevice = false,
                memoryClassMb = 512,
            ),
        )
        assertEquals(
            8,
            effectiveWhisperThreadCount(
                configured = 8,
                cpuCores = 8,
                lowRamDevice = true,
                memoryClassMb = 512,
            ),
        )
        assertEquals(8, maximumWhisperThreadCount(cpuCores = 8))
        assertEquals(12, maximumWhisperThreadCount(cpuCores = 12))
        assertEquals(1, maximumWhisperThreadCount(cpuCores = 0))
    }
}

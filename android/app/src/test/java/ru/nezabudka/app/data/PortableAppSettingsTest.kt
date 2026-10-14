package ru.nezabudka.app.data

import ru.nezabudka.app.domain.AppSettings
import ru.nezabudka.app.domain.ALARM_PRESENTATION_NOTIFICATION
import ru.nezabudka.app.domain.ALARM_PRESENTATION_SCREEN
import ru.nezabudka.app.domain.TimeOfDaySetting
import ru.nezabudka.app.domain.UI_PERFORMANCE_AUTO
import ru.nezabudka.app.domain.UI_PERFORMANCE_REDUCED
import ru.nezabudka.app.domain.defaultCustomTheme
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableAppSettingsTest {
    @Test
    fun `portable settings include application preferences and model selection`() {
        val settings = AppSettings(
            theme = "custom",
            customTheme = defaultCustomTheme("dark"),
            popupDurationSeconds = 9,
            classicNluEnabled = false,
            keepWhisperModelLoaded = true,
            whisperThreadCount = 6,
            gigaamLivePreview = true,
            editorFullPunctuation = true,
            whisperAccurateMode = true,
            whisperRawMode = true,
            notePunctuationModel = "rupunct_small",
            timeOfDaySettings = listOf(TimeOfDaySetting("после работы", "18:30")),
            sttEngine = "whisper",
            audioPreprocessingEnabled = false,
            showPlaybackButton = false,
            showTechnicalLog = true,
            trustStt = true,
            reminderSort = "date",
            noteSort = "created",
            noteColumns = 2,
            noteFolderInterface = "drawer",
            noteTrashRetentionDays = 7,
            showNavigationLabels = true,
            notificationSound = "content://settings/system/notification_sound",
            notificationVibrationEnabled = false,
            reminderAlarmMode = true,
            reminderAlarmPresentation = ALARM_PRESENTATION_NOTIFICATION,
            reminderAlarmSound = "content://media/internal/audio/media/42",
            notificationWakeScreen = true,
            reminderAlarmRingSeconds = 30,
            reminderAlarmPauseMinutes = 3,
            reminderAlarmRepeatCount = 5,
            uiPerformanceMode = UI_PERFORMANCE_REDUCED,
        )

        val portable = settings.toPortableSettings(
            selectedWhisperModel = "ggml-small.bin",
            yandexFolderId = "folder-id",
            vkEndpoint = "https://voice.example.test",
        )

        assertEquals("custom", portable.theme)
        assertEquals(9, portable.popupDurationSeconds)
        assertEquals("ggml-small.bin", portable.selectedWhisperModel)
        assertFalse(portable.classicNluEnabled)
        assertTrue(portable.keepWhisperModelLoaded)
        assertEquals(6, portable.whisperThreadCount)
        assertTrue(portable.gigaamLivePreview)
        assertTrue(portable.editorFullPunctuation)
        assertTrue(portable.whisperAccurateMode)
        assertTrue(portable.whisperRawMode)
        assertFalse(portable.audioPreprocessingEnabled)
        assertEquals("rupunct_small", portable.notePunctuationModel)
        assertEquals("folder-id", portable.yandexFolderId)
        assertEquals("https://voice.example.test", portable.vkEndpoint)
        assertEquals("18:30", portable.timeOfDaySettings.single().time)
        assertEquals(7, portable.noteTrashRetentionDays)
        assertTrue(portable.showTechnicalLog)
        assertTrue(portable.reminderAlarmMode)
        assertEquals(ALARM_PRESENTATION_NOTIFICATION, portable.reminderAlarmPresentation)
        assertEquals("content://media/internal/audio/media/42", portable.reminderAlarmSound)
        assertTrue(portable.notificationWakeScreen)
        assertEquals(30, portable.reminderAlarmRingSeconds)
        assertEquals(3, portable.reminderAlarmPauseMinutes)
        assertEquals(5, portable.reminderAlarmRepeatCount)
        assertFalse(portable.notificationVibrationEnabled)
        assertFalse(portable.reminderAlarmVibrationEnabled)
        assertEquals(UI_PERFORMANCE_REDUCED, portable.uiPerformanceMode)
    }

    @Test
    fun `serialized portable settings contain no credential fields`() {
        val defaults = PortableAppSettings()
        val encoded = Json { encodeDefaults = true }.encodeToString(defaults)
        val normalized = encoded.lowercase()

        assertFalse("apiKey must not be exported", "apikey" in normalized)
        assertFalse("tokens must not be exported", "token" in normalized)
        assertFalse("credentials must not be exported", "credential" in normalized)
        assertFalse("removed realtime STT setting must not be exported", "realtimestt" in normalized)
        assertTrue("\"theme\"" in encoded)
        assertEquals("date", defaults.reminderSort)
        assertFalse(defaults.showPlaybackButton)
        assertFalse(defaults.reminderAlarmMode)
        assertTrue(defaults.showNavigationLabels)
        assertTrue(defaults.uiHapticsEnabled)
        assertFalse(defaults.hidePopupNotices)
        assertTrue(defaults.classicNluEnabled) // Legacy backup schema default; fresh SettingsStore uses false.
    }

    @Test
    fun `backup containing removed realtime STT setting remains readable`() {
        val restored = Json { ignoreUnknownKeys = true }
            .decodeFromString<PortableAppSettings>("""{"theme":"dark","trustStt":false,"realtimeStt":true,"localOnly":true}""")

        assertEquals("dark", restored.theme)
        assertTrue(restored.classicNluEnabled)
        assertFalse(restored.keepWhisperModelLoaded)
        assertEquals(0, restored.whisperThreadCount)
        assertFalse(restored.showTechnicalLog)
        assertFalse(restored.reminderAlarmMode)
        assertEquals(ALARM_PRESENTATION_SCREEN, restored.reminderAlarmPresentation)
        assertEquals("content://settings/system/alarm_alert", restored.reminderAlarmSound)
        assertFalse(restored.notificationWakeScreen)
        assertEquals(60, restored.reminderAlarmRingSeconds)
        assertEquals(5, restored.reminderAlarmPauseMinutes)
        assertEquals(3, restored.reminderAlarmRepeatCount)
        assertTrue(restored.notificationVibrationEnabled)
        assertTrue(restored.reminderAlarmVibrationEnabled)
        assertEquals(UI_PERFORMANCE_AUTO, restored.uiPerformanceMode)
    }

    @Test
    fun `removed local-only flag does not hide a restored cloud engine`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString<PortableAppSettings>(
            """{"sttEngine":"yandex","localOnly":true}""",
        )
        assertEquals("yandex", restored.sttEngine)
        assertFalse("localOnly" in json.encodeToString(restored))
    }
    @Test fun `haptic setting survives backup and older backups enable it by default`() {
        val json = Json { encodeDefaults = true }
        val silent = PortableAppSettings(uiHapticsEnabled = false, classicNluEnabled = false, hidePopupNotices = true)
        val restored = json.decodeFromString<PortableAppSettings>(json.encodeToString(silent))
        assertFalse(restored.uiHapticsEnabled)
        assertFalse(restored.classicNluEnabled)
        assertTrue(restored.hidePopupNotices)
        assertFalse(json.decodeFromString<PortableAppSettings>("{}").hidePopupNotices)
        assertTrue(json.decodeFromString<PortableAppSettings>("{}").uiHapticsEnabled)
    }
}

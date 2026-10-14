package ru.nezabudka.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class TimeOfDaySetting(val phrase: String, val time: String)

val DEFAULT_TIME_OF_DAY_SETTINGS = listOf(
    TimeOfDaySetting("утром", "09:00"),
    TimeOfDaySetting("днём", "14:00"),
    TimeOfDaySetting("вечером", "19:00"),
    TimeOfDaySetting("ночью", "00:00"),
)

const val DEFAULT_NOTE_TRASH_RETENTION_DAYS = 30
const val ALARM_PRESENTATION_SCREEN = "screen"
const val ALARM_PRESENTATION_NOTIFICATION = "notification"
const val DEFAULT_ALARM_RING_SECONDS = 60
const val DEFAULT_ALARM_PAUSE_MINUTES = 5
const val DEFAULT_ALARM_REPEAT_COUNT = 3
const val UI_PERFORMANCE_AUTO = "auto"
const val UI_PERFORMANCE_REDUCED = "reduced"
const val UI_PERFORMANCE_FULL = "full"

fun normalizeAlarmRingSeconds(value: Int): Int = value.coerceIn(15, 300)
fun normalizeAlarmPauseMinutes(value: Int): Int = value.coerceIn(1, 30)
fun normalizeAlarmRepeatCount(value: Int): Int = value.coerceIn(1, 10)

data class AppSettings(
    val theme: String,
    val customTheme: CustomThemeSettings,
    val popupDurationSeconds: Int,
    val hidePopupNotices: Boolean = false,
    val timeOfDaySettings: List<TimeOfDaySetting>,
    val sttEngine: String,
    val keepWhisperModelLoaded: Boolean = false,
    val keepGigaamModelLoaded: Boolean = false,
    val gigaamThreadCount: Int = 0,
    val gigaamLivePreview: Boolean = false,
    val editorFullPunctuation: Boolean = false,
    val audioPreprocessingEnabled: Boolean,
    val showPlaybackButton: Boolean,
    val trustStt: Boolean,
    val reminderSort: String,
    val noteSort: String,
    val noteColumns: Int,
    val noteFolderInterface: String,
    val noteTrashRetentionDays: Int = DEFAULT_NOTE_TRASH_RETENTION_DAYS,
    val showNavigationLabels: Boolean,
    val uiHapticsEnabled: Boolean = true,
    val notificationSound: String,
    val classicNluEnabled: Boolean = false,
    val showTechnicalLog: Boolean = false,
    val showAssistantWelcomeCard: Boolean = true,
    val reminderAlarmMode: Boolean = false,
    val reminderAlarmPresentation: String = ALARM_PRESENTATION_SCREEN,
    val reminderAlarmSound: String = "content://settings/system/alarm_alert",
    val notificationWakeScreen: Boolean = false,
    val reminderAlarmRingSeconds: Int = DEFAULT_ALARM_RING_SECONDS,
    val reminderAlarmPauseMinutes: Int = DEFAULT_ALARM_PAUSE_MINUTES,
    val reminderAlarmRepeatCount: Int = DEFAULT_ALARM_REPEAT_COUNT,
    val notificationVibrationEnabled: Boolean = true,
    val whisperThreadCount: Int = 0,
    val whisperAccurateMode: Boolean = false,
    val whisperRawMode: Boolean = false,
    val notePunctuationModel: String = "whisper",
    val uiPerformanceMode: String = UI_PERFORMANCE_AUTO,
)

package ru.nezabudka.app.data

import android.content.Context
import android.provider.Settings
import ru.nezabudka.app.domain.AppSettings
import ru.nezabudka.app.domain.ALARM_PRESENTATION_SCREEN
import ru.nezabudka.app.domain.CustomThemeSettings
import ru.nezabudka.app.domain.DEFAULT_TIME_OF_DAY_SETTINGS
import ru.nezabudka.app.domain.DEFAULT_NOTE_TRASH_RETENTION_DAYS
import ru.nezabudka.app.domain.DEFAULT_ALARM_RING_SECONDS
import ru.nezabudka.app.domain.DEFAULT_ALARM_PAUSE_MINUTES
import ru.nezabudka.app.domain.DEFAULT_ALARM_REPEAT_COUNT
import ru.nezabudka.app.domain.UI_PERFORMANCE_AUTO
import ru.nezabudka.app.domain.TimeOfDaySetting
import ru.nezabudka.app.domain.defaultCustomTheme
import ru.nezabudka.app.domain.withAutomaticDependentColors
import ru.nezabudka.app.domain.normalizeFolderInterface
import ru.nezabudka.app.domain.normalizeAlarmPresentation
import ru.nezabudka.app.domain.normalizeAlarmRingSeconds
import ru.nezabudka.app.domain.normalizeAlarmPauseMinutes
import ru.nezabudka.app.domain.normalizeAlarmRepeatCount
import ru.nezabudka.app.domain.normalizeNoteSort
import ru.nezabudka.app.domain.normalizeNoteTrashRetentionDays
import ru.nezabudka.app.domain.normalizeNotificationSound
import ru.nezabudka.app.domain.normalizeReminderSort
import ru.nezabudka.app.domain.normalizeSttEngine
import ru.nezabudka.app.domain.normalizeTheme
import ru.nezabudka.app.domain.normalizeUiPerformanceMode
import ru.nezabudka.app.domain.normalizeWhisperThreadCount
import ru.nezabudka.app.ports.SettingsPort
import ru.nezabudka.app.speech.normalizeNotePunctuationModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PortableAppSettings(
    val theme: String = "light",
    val customTheme: CustomThemeSettings = CustomThemeSettings(),
    val popupDurationSeconds: Int = 5,
    val hidePopupNotices: Boolean = false,
    val visibleScreens: List<String> = ru.nezabudka.app.domain.DEFAULT_APP_SCREENS,
    // Backup schema keeps the old default when reading files that omit this field.
    val classicNluEnabled: Boolean = true,
    val timeOfDaySettings: List<TimeOfDaySetting> = DEFAULT_TIME_OF_DAY_SETTINGS,
    val sttEngine: String = "whisper",
    val keepWhisperModelLoaded: Boolean = false,
    val keepGigaamModelLoaded: Boolean = false,
    val gigaamThreadCount: Int = 0,
    val gigaamLivePreview: Boolean = false,
    val editorFullPunctuation: Boolean = false,
    val spokenSymbolsEnabled: Boolean = false,
    val spokenSymbols: List<ru.nezabudka.app.domain.SpokenSymbol> = ru.nezabudka.app.domain.DEFAULT_SPOKEN_SYMBOLS,
    val whisperThreadCount: Int = 0,
    val whisperAccurateMode: Boolean = false,
    val whisperRawMode: Boolean = false,
    val notePunctuationModel: String = "whisper",
    val audioPreprocessingEnabled: Boolean = true,
    val showPlaybackButton: Boolean = false,
    val showTechnicalLog: Boolean = false,
    val showAssistantWelcomeCard: Boolean = true,
    val trustStt: Boolean = false,
    val reminderSort: String = "date",
    val noteSort: String = "updated",
    val noteColumns: Int = 1,
    val noteFolderInterface: String = "carousel",
    val noteTrashRetentionDays: Int = DEFAULT_NOTE_TRASH_RETENTION_DAYS,
    val showNavigationLabels: Boolean = true,
    val uiHapticsEnabled: Boolean = true,
    val uiPerformanceMode: String = UI_PERFORMANCE_AUTO,
    val notificationSound: String = "content://settings/system/notification_sound",
    val notificationVibrationEnabled: Boolean = true,
    val reminderAlarmMode: Boolean = false,
    val reminderAlarmPresentation: String = ALARM_PRESENTATION_SCREEN,
    val reminderAlarmSound: String = "content://settings/system/alarm_alert",
    val notificationWakeScreen: Boolean = false,
    val reminderAlarmRingSeconds: Int = DEFAULT_ALARM_RING_SECONDS,
    val reminderAlarmPauseMinutes: Int = DEFAULT_ALARM_PAUSE_MINUTES,
    val reminderAlarmRepeatCount: Int = DEFAULT_ALARM_REPEAT_COUNT,
    val reminderAlarmVibrationEnabled: Boolean = true,
    val selectedWhisperModel: String? = null,
    val yandexFolderId: String = "",
    val vkEndpoint: String = "",
)

data class ReminderNotificationSettingsSnapshot(
    val notificationSound: String,
    val notificationVibrationEnabled: Boolean,
    val reminderAlarmMode: Boolean,
    val reminderAlarmPresentation: String,
    val reminderAlarmSound: String,
    val notificationWakeScreen: Boolean,
    val reminderAlarmRingSeconds: Int,
    val reminderAlarmPauseMinutes: Int,
    val reminderAlarmRepeatCount: Int,
)

fun AppSettings.toPortableSettings(
    selectedWhisperModel: String?,
    yandexFolderId: String,
    vkEndpoint: String,
) = PortableAppSettings(
    theme = theme,
    customTheme = customTheme,
    popupDurationSeconds = popupDurationSeconds,
    hidePopupNotices = hidePopupNotices,
    visibleScreens = visibleScreens,
    classicNluEnabled = classicNluEnabled,
    timeOfDaySettings = timeOfDaySettings,
    sttEngine = sttEngine,
    keepWhisperModelLoaded = keepWhisperModelLoaded,
    keepGigaamModelLoaded = keepGigaamModelLoaded,
    gigaamThreadCount = gigaamThreadCount,
    gigaamLivePreview = gigaamLivePreview,
    editorFullPunctuation = editorFullPunctuation,
    spokenSymbolsEnabled = spokenSymbolsEnabled,
    spokenSymbols = spokenSymbols,
    whisperThreadCount = whisperThreadCount,
    whisperAccurateMode = whisperAccurateMode,
    whisperRawMode = whisperRawMode,
    notePunctuationModel = notePunctuationModel,
    audioPreprocessingEnabled = audioPreprocessingEnabled,
    showPlaybackButton = showPlaybackButton,
    showTechnicalLog = showTechnicalLog,
    showAssistantWelcomeCard = showAssistantWelcomeCard,
    trustStt = trustStt,
    reminderSort = reminderSort,
    noteSort = noteSort,
    noteColumns = noteColumns,
    noteFolderInterface = noteFolderInterface,
    noteTrashRetentionDays = noteTrashRetentionDays,
    showNavigationLabels = showNavigationLabels,
    uiHapticsEnabled = uiHapticsEnabled,
    uiPerformanceMode = uiPerformanceMode,
    notificationSound = notificationSound,
    notificationVibrationEnabled = notificationVibrationEnabled,
    reminderAlarmMode = reminderAlarmMode,
    reminderAlarmPresentation = reminderAlarmPresentation,
    reminderAlarmSound = reminderAlarmSound,
    notificationWakeScreen = notificationWakeScreen,
    reminderAlarmRingSeconds = reminderAlarmRingSeconds,
    reminderAlarmPauseMinutes = reminderAlarmPauseMinutes,
    reminderAlarmRepeatCount = reminderAlarmRepeatCount,
    reminderAlarmVibrationEnabled = notificationVibrationEnabled,
    selectedWhisperModel = selectedWhisperModel,
    yandexFolderId = yandexFolderId,
    vkEndpoint = vkEndpoint,
)

class SettingsStore(context: Context) : SettingsPort {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    init {
        val hadSettings = prefs.all.isNotEmpty()
        val editor = prefs.edit().remove("local_only").remove("external_data_backup")
        if (!prefs.getBoolean("nlu_default_migrated", false)) {
            val existing = prefs.all.keys.any { it !in setOf("local_only", "external_data_backup") } ||
                context.getSharedPreferences("nlu_models", Context.MODE_PRIVATE).contains("selected_model")
            val saved = if (prefs.contains("classic_nlu_enabled")) prefs.getBoolean("classic_nlu_enabled", false) else null
            editor.putBoolean("classic_nlu_enabled", ru.nezabudka.app.domain.migratedClassicNluEnabled(saved, existing))
                .putBoolean("nlu_default_migrated", true)
        }
        // Older installs could use the implicit Whisper default without an stt key.
        // Persist the initial choice once so later preference writes cannot change it.
        if (!prefs.contains("stt")) {
            val updatedInstallation = runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.lastUpdateTime > info.firstInstallTime
            }.getOrDefault(true)
            val engine = ru.nezabudka.app.domain.initialSttEngine(hadSettings, updatedInstallation)
            editor.putString("stt", engine)
        }
        editor.apply()
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(read())
    override val state: StateFlow<AppSettings> = _state

    companion object {
        fun currentReminderAlarmMode(context: Context): Boolean =
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("reminder_alarm_mode", false)

        fun currentReminderNotificationSettings(context: Context): ReminderNotificationSettingsSnapshot {
            val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            return ReminderNotificationSettingsSnapshot(
                notificationSound = normalizeNotificationSound(
                    preferences.getString("notification_sound", Settings.System.DEFAULT_NOTIFICATION_URI.toString()).orEmpty(),
                    Settings.System.DEFAULT_NOTIFICATION_URI.toString(),
                ),
                notificationVibrationEnabled = preferences.getBoolean("notification_vibration", true),
                reminderAlarmMode = preferences.getBoolean("reminder_alarm_mode", false),
                reminderAlarmPresentation = normalizeAlarmPresentation(
                    preferences.getString("reminder_alarm_presentation", ALARM_PRESENTATION_SCREEN).orEmpty(),
                ),
                reminderAlarmSound = normalizeNotificationSound(
                    preferences.getString("reminder_alarm_sound", Settings.System.DEFAULT_ALARM_ALERT_URI.toString()).orEmpty(),
                    Settings.System.DEFAULT_ALARM_ALERT_URI.toString(),
                ),
                notificationWakeScreen = preferences.getBoolean("notification_wake_screen", false),
                reminderAlarmRingSeconds = normalizeAlarmRingSeconds(
                    preferences.getInt("reminder_alarm_ring_seconds", DEFAULT_ALARM_RING_SECONDS),
                ),
                reminderAlarmPauseMinutes = normalizeAlarmPauseMinutes(
                    preferences.getInt("reminder_alarm_pause_minutes", DEFAULT_ALARM_PAUSE_MINUTES),
                ),
                reminderAlarmRepeatCount = normalizeAlarmRepeatCount(
                    preferences.getInt("reminder_alarm_repeat_count", DEFAULT_ALARM_REPEAT_COUNT),
                ),
            )
        }
    }
    override fun setTheme(value: String) {
        prefs.edit().putString("theme", normalizeTheme(value)).apply()
        _state.value = read()
    }
    override fun setCustomTheme(value: CustomThemeSettings) {
        val fallback = defaultCustomTheme(value.base)
        val sanitized = value.copy(
            base = if (value.base == "dark") "dark" else "light",
            background = sanitizeThemeColor(value.background, fallback.background),
            navigation = sanitizeThemeColor(value.navigation, fallback.navigation),
            card = sanitizeThemeColor(value.card, fallback.card),
            notice = sanitizeThemeColor(value.notice, fallback.notice),
            input = sanitizeThemeColor(value.input, fallback.input),
            modal = sanitizeThemeColor(value.modal, fallback.modal),
            folderArea = sanitizeThemeColor(value.folderArea, fallback.folderArea),
            selected = sanitizeThemeColor(value.selected, fallback.selected),
            accent = sanitizeThemeColor(value.accent, fallback.accent),
            onAccent = sanitizeThemeColor(value.onAccent, fallback.onAccent),
            talkButton = sanitizeThemeColor(value.talkButton, fallback.talkButton),
            onTalkButton = sanitizeThemeColor(value.onTalkButton, fallback.onTalkButton),
            textPrimary = sanitizeThemeColor(value.textPrimary, fallback.textPrimary),
            textSecondary = sanitizeThemeColor(value.textSecondary, fallback.textSecondary),
            cursor = sanitizeThemeColor(
                value.cursor ?: fallback.cursor.orEmpty(),
                fallback.cursor ?: fallback.textPrimary,
            ),
            placeholder = sanitizeThemeColor(value.placeholder, fallback.placeholder),
            outline = sanitizeThemeColor(value.outline, fallback.outline),
            danger = sanitizeThemeColor(value.danger, fallback.danger),
            dangerContainer = sanitizeThemeColor(value.dangerContainer, fallback.dangerContainer),
            onDangerContainer = sanitizeThemeColor(value.onDangerContainer, fallback.onDangerContainer),
            warning = sanitizeThemeColor(value.warning, fallback.warning),
            scrim = sanitizeThemeColor(value.scrim, fallback.scrim),
            noteDefault = sanitizeThemeColor(value.noteDefault, fallback.noteDefault),
            notePalette = value.notePalette.take(11).mapIndexed { index, color ->
                sanitizeThemeColor(color, fallback.notePalette.getOrElse(index) { fallback.noteDefault })
            }.let { colors -> colors + fallback.notePalette.drop(colors.size) },
            fontFamily = value.fontFamily.takeIf { it in setOf("sans", "serif", "monospace") } ?: "sans",
            fontScale = value.fontScale.coerceIn(0.8f, 1.4f),
            cornerScale = value.cornerScale.coerceIn(0.5f, 1.5f),
        )
        val normalized = sanitized.withAutomaticDependentColors()
        prefs.edit().putString("custom_theme", json.encodeToString(normalized)).apply()
        _state.value = read()
    }
    override fun setShowAssistantWelcomeCard(enabled: Boolean) { prefs.edit().putBoolean("show_assistant_welcome_card", enabled).apply(); _state.value = read() }
    override fun setPopupDurationSeconds(value: Int) { prefs.edit().putInt("popup_duration_seconds", value.coerceIn(0, 60)).apply(); _state.value = read() }
    override fun setVisibleScreens(screens: List<String>) {
        if (!ru.nezabudka.app.domain.validAppScreens(screens)) return
        prefs.edit().putStringSet("visible_screens", screens.toSet()).apply()
        _state.value = read()
    }
    override fun setHidePopupNotices(value: Boolean) { prefs.edit().putBoolean("hide_popup_notices", value).apply(); _state.value = read() }
    override fun setClassicNluEnabled(enabled: Boolean) { prefs.edit().putBoolean("classic_nlu_enabled", enabled).apply(); _state.value = read() }
    override fun setTimeOfDaySettings(values: List<TimeOfDaySetting>) {
        val sanitized = values.mapNotNull { value ->
            val phrase = value.phrase.trim().replace(Regex("\\s+"), " ")
            val time = value.time.takeIf { Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(it) }
            if (phrase.isBlank() || time == null) null else TimeOfDaySetting(phrase, time)
        }.distinctBy { it.phrase.lowercase().replace('ё', 'е') }
        prefs.edit().putString("time_of_day_settings", json.encodeToString(sanitized.ifEmpty { DEFAULT_TIME_OF_DAY_SETTINGS })).apply()
        _state.value = read()
    }
    override fun setSttEngine(value: String) {
        val normalized = normalizeSttEngine(value)
        prefs.edit().putString("stt", normalized).apply()
        _state.value = read()
    }
    override fun setKeepGigaamModelLoaded(value: Boolean) {
        prefs.edit().putBoolean("keep_gigaam_model_loaded", value).apply()
        _state.value = read()
    }
    override fun setGigaamThreadCount(value: Int) {
        prefs.edit().putInt("gigaam_thread_count", normalizeWhisperThreadCount(value)).apply()
        _state.value = read()
    }
    override fun setGigaamLivePreview(value: Boolean) {
        prefs.edit().putBoolean("gigaam_live_preview", value).apply(); _state.value = read()
    }
    override fun setSpokenSymbolsEnabled(value: Boolean) {
        prefs.edit().putBoolean("spoken_symbols_enabled", value).apply(); _state.value = read()
    }
    override fun setSpokenSymbols(values: List<ru.nezabudka.app.domain.SpokenSymbol>) {
        val clean = values.filter { it.phrase.isNotBlank() && it.symbol.isNotEmpty() }.map {
            it.copy(phrase = it.phrase.trim().take(100), symbol = it.symbol.take(20))
        }.distinctBy { it.phrase.lowercase() }.take(100)
        prefs.edit().putString("spoken_symbols", json.encodeToString(clean)).apply(); _state.value = read()
    }

    override fun setEditorFullPunctuation(value: Boolean) {
        prefs.edit().putBoolean("editor_full_punctuation", value).apply(); _state.value = read()
    }
    override fun setKeepWhisperModelLoaded(enabled: Boolean) {
        prefs.edit().putBoolean("keep_whisper_model_loaded", enabled).apply()
        _state.value = read()
    }
    override fun setWhisperThreadCount(value: Int) {
        prefs.edit().putInt("whisper_thread_count", normalizeWhisperThreadCount(value)).apply()
        _state.value = read()
    }
    override fun setWhisperAccurateMode(enabled: Boolean) {
        prefs.edit().putBoolean("whisper_accurate_mode", enabled).apply()
        _state.value = read()
    }
    override fun setWhisperRawMode(enabled: Boolean) {
        prefs.edit().putBoolean("whisper_raw_mode", enabled).apply()
        _state.value = read()
    }
    override fun setNotePunctuationModel(value: String) {
        prefs.edit().putString("note_punctuation_model", normalizeNotePunctuationModel(value)).apply()
        _state.value = read()
    }
    override fun setAudioPreprocessing(enabled: Boolean) {
        prefs.edit().putBoolean("audio_preprocessing", enabled).apply()
        _state.value = read()
    }
    override fun setShowPlaybackButton(enabled: Boolean) { prefs.edit().putBoolean("show_playback", enabled).apply(); _state.value = read() }
    override fun setShowTechnicalLog(enabled: Boolean) { prefs.edit().putBoolean("show_technical_log", enabled).apply(); _state.value = read() }
    override fun setTrustStt(enabled: Boolean) {
        prefs.edit().putBoolean("trust_stt", enabled).apply()
        _state.value = read()
    }
    override fun setReminderSort(value: String) { prefs.edit().putString("reminder_sort", normalizeReminderSort(value)).apply(); _state.value = read() }
    override fun setNoteSort(value: String) { prefs.edit().putString("note_sort", normalizeNoteSort(value)).apply(); _state.value = read() }
    override fun setNoteColumns(value: Int) { prefs.edit().putInt("note_columns", value.coerceIn(1, 2)).apply(); _state.value = read() }
    override fun setNoteFolderInterface(value: String) { prefs.edit().putString("note_folder_interface", normalizeFolderInterface(value)).apply(); _state.value = read() }
    override fun setNoteTrashRetentionDays(value: Int) {
        prefs.edit().putInt("note_trash_retention_days", normalizeNoteTrashRetentionDays(value)).apply()
        _state.value = read()
    }
    override fun setUiHapticsEnabled(enabled: Boolean) { prefs.edit().putBoolean("ui_haptics", enabled).apply(); _state.value = read() }
    override fun setShowNavigationLabels(enabled: Boolean) { prefs.edit().putBoolean("show_navigation_labels", enabled).apply(); _state.value = read() }
    override fun setUiPerformanceMode(value: String) {
        prefs.edit().putString("ui_performance_mode", normalizeUiPerformanceMode(value)).apply()
        _state.value = read()
    }
    override fun setNotificationSound(uri: String) {
        val fallback = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
        prefs.edit().putString("notification_sound", normalizeNotificationSound(uri, fallback)).apply()
        _state.value = read()
    }
    override fun setNotificationVibrationEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("notification_vibration", enabled)
            .putBoolean("reminder_alarm_vibration", enabled)
            .apply()
        _state.value = read()
    }
    override fun setReminderAlarmMode(enabled: Boolean) {
        prefs.edit().putBoolean("reminder_alarm_mode", enabled).apply()
        _state.value = read()
    }
    override fun setReminderAlarmPresentation(value: String) {
        prefs.edit().putString("reminder_alarm_presentation", normalizeAlarmPresentation(value)).apply()
        _state.value = read()
    }
    override fun setReminderAlarmSound(uri: String) {
        val fallback = Settings.System.DEFAULT_ALARM_ALERT_URI.toString()
        prefs.edit().putString("reminder_alarm_sound", normalizeNotificationSound(uri, fallback)).apply()
        _state.value = read()
    }
    override fun setNotificationWakeScreen(enabled: Boolean) {
        prefs.edit().putBoolean("notification_wake_screen", enabled).apply()
        _state.value = read()
    }
    override fun setReminderAlarmRingSeconds(value: Int) {
        prefs.edit().putInt("reminder_alarm_ring_seconds", normalizeAlarmRingSeconds(value)).apply()
        _state.value = read()
    }
    override fun setReminderAlarmPauseMinutes(value: Int) {
        prefs.edit().putInt("reminder_alarm_pause_minutes", normalizeAlarmPauseMinutes(value)).apply()
        _state.value = read()
    }
    override fun setReminderAlarmRepeatCount(value: Int) {
        prefs.edit().putInt("reminder_alarm_repeat_count", normalizeAlarmRepeatCount(value)).apply()
        _state.value = read()
    }
    fun restorePortableSettings(value: PortableAppSettings) {
        setCustomTheme(value.customTheme)
        setTheme(value.theme)
        setPopupDurationSeconds(value.popupDurationSeconds)
        setHidePopupNotices(value.hidePopupNotices)
        setVisibleScreens(ru.nezabudka.app.domain.normalizeAppScreens(value.visibleScreens))
        setShowAssistantWelcomeCard(value.showAssistantWelcomeCard)
        setClassicNluEnabled(value.classicNluEnabled)
        setTimeOfDaySettings(value.timeOfDaySettings)
        setSttEngine(value.sttEngine)
        setKeepGigaamModelLoaded(value.keepGigaamModelLoaded)
        setGigaamThreadCount(value.gigaamThreadCount)
        setGigaamLivePreview(value.gigaamLivePreview)
        setEditorFullPunctuation(value.editorFullPunctuation)
        setSpokenSymbolsEnabled(value.spokenSymbolsEnabled)
        setSpokenSymbols(value.spokenSymbols)
        setKeepWhisperModelLoaded(value.keepWhisperModelLoaded)
        setWhisperThreadCount(value.whisperThreadCount)
        setWhisperAccurateMode(value.whisperAccurateMode)
        setWhisperRawMode(value.whisperRawMode)
        setNotePunctuationModel(value.notePunctuationModel)
        setAudioPreprocessing(value.audioPreprocessingEnabled)
        setShowPlaybackButton(value.showPlaybackButton)
        setShowTechnicalLog(value.showTechnicalLog)
        setTrustStt(value.trustStt)
        setReminderSort(value.reminderSort)
        setNoteSort(value.noteSort)
        setNoteColumns(value.noteColumns)
        setNoteFolderInterface(value.noteFolderInterface)
        setNoteTrashRetentionDays(value.noteTrashRetentionDays)
        setShowNavigationLabels(value.showNavigationLabels)
        setUiHapticsEnabled(value.uiHapticsEnabled)
        setUiPerformanceMode(value.uiPerformanceMode)
        setNotificationSound(value.notificationSound)
        setNotificationVibrationEnabled(value.notificationVibrationEnabled)
        setReminderAlarmMode(value.reminderAlarmMode)
        setReminderAlarmPresentation(value.reminderAlarmPresentation)
        setReminderAlarmSound(value.reminderAlarmSound)
        setNotificationWakeScreen(value.notificationWakeScreen)
        setReminderAlarmRingSeconds(value.reminderAlarmRingSeconds)
        setReminderAlarmPauseMinutes(value.reminderAlarmPauseMinutes)
        setReminderAlarmRepeatCount(value.reminderAlarmRepeatCount)
    }
    private fun read(): AppSettings {
        val trustStt = prefs.getBoolean("trust_stt", false)
        val sttEngine = normalizeSttEngine(prefs.getString("stt", "gigaam").orEmpty())
        return AppSettings(
        theme = normalizeTheme(prefs.getString("theme", "light").orEmpty()),
        customTheme = readCustomTheme(),
        popupDurationSeconds = prefs.getInt("popup_duration_seconds", 5).coerceIn(0, 60),
        hidePopupNotices = prefs.getBoolean("hide_popup_notices", false),
        visibleScreens = ru.nezabudka.app.domain.normalizeAppScreens(prefs.getStringSet("visible_screens", null) ?: ru.nezabudka.app.domain.DEFAULT_APP_SCREENS.toSet()),
        classicNluEnabled = prefs.getBoolean("classic_nlu_enabled", false),
        timeOfDaySettings = readTimeOfDaySettings(),
        sttEngine = sttEngine,
        keepGigaamModelLoaded = prefs.getBoolean("keep_gigaam_model_loaded", false),
        gigaamThreadCount = normalizeWhisperThreadCount(prefs.getInt("gigaam_thread_count", 0)),
        gigaamLivePreview = prefs.getBoolean("gigaam_live_preview", false),
        editorFullPunctuation = prefs.getBoolean("editor_full_punctuation", false),
        spokenSymbolsEnabled = prefs.getBoolean("spoken_symbols_enabled", false),
        spokenSymbols = runCatching { json.decodeFromString<List<ru.nezabudka.app.domain.SpokenSymbol>>(prefs.getString("spoken_symbols", null) ?: "null") }.getOrDefault(ru.nezabudka.app.domain.DEFAULT_SPOKEN_SYMBOLS),
        keepWhisperModelLoaded = prefs.getBoolean("keep_whisper_model_loaded", false),
        whisperThreadCount = normalizeWhisperThreadCount(prefs.getInt("whisper_thread_count", 0)),
        whisperAccurateMode = prefs.getBoolean("whisper_accurate_mode", false),
        whisperRawMode = prefs.getBoolean("whisper_raw_mode", false),
        notePunctuationModel = normalizeNotePunctuationModel(prefs.getString("note_punctuation_model", "whisper").orEmpty()),
        audioPreprocessingEnabled = prefs.getBoolean("audio_preprocessing", true),
        showPlaybackButton = prefs.getBoolean("show_playback", false),
        showTechnicalLog = prefs.getBoolean("show_technical_log", false),
        showAssistantWelcomeCard = prefs.getBoolean("show_assistant_welcome_card", true),
        trustStt = trustStt,
        reminderSort = normalizeReminderSort(prefs.getString("reminder_sort", "date").orEmpty()),
        noteSort = normalizeNoteSort(prefs.getString("note_sort", "updated").orEmpty()),
        noteColumns = prefs.getInt("note_columns", 1).coerceIn(1, 2),
        noteFolderInterface = normalizeFolderInterface(prefs.getString("note_folder_interface", "carousel").orEmpty()),
        noteTrashRetentionDays = normalizeNoteTrashRetentionDays(
            prefs.getInt("note_trash_retention_days", DEFAULT_NOTE_TRASH_RETENTION_DAYS),
        ),
        showNavigationLabels = prefs.getBoolean("show_navigation_labels", true),
        uiHapticsEnabled = prefs.getBoolean("ui_haptics", true),
        uiPerformanceMode = normalizeUiPerformanceMode(
            prefs.getString("ui_performance_mode", UI_PERFORMANCE_AUTO).orEmpty(),
        ),
        notificationSound = normalizeNotificationSound(
            prefs.getString("notification_sound", Settings.System.DEFAULT_NOTIFICATION_URI.toString()).orEmpty(),
            Settings.System.DEFAULT_NOTIFICATION_URI.toString(),
        ),
        notificationVibrationEnabled = prefs.getBoolean("notification_vibration", true),
        reminderAlarmMode = prefs.getBoolean("reminder_alarm_mode", false),
        reminderAlarmPresentation = normalizeAlarmPresentation(
            prefs.getString("reminder_alarm_presentation", ALARM_PRESENTATION_SCREEN).orEmpty(),
        ),
        reminderAlarmSound = normalizeNotificationSound(
            prefs.getString("reminder_alarm_sound", Settings.System.DEFAULT_ALARM_ALERT_URI.toString()).orEmpty(),
            Settings.System.DEFAULT_ALARM_ALERT_URI.toString(),
        ),
        notificationWakeScreen = prefs.getBoolean("notification_wake_screen", false),
        reminderAlarmRingSeconds = normalizeAlarmRingSeconds(
            prefs.getInt("reminder_alarm_ring_seconds", DEFAULT_ALARM_RING_SECONDS),
        ),
        reminderAlarmPauseMinutes = normalizeAlarmPauseMinutes(
            prefs.getInt("reminder_alarm_pause_minutes", DEFAULT_ALARM_PAUSE_MINUTES),
        ),
        reminderAlarmRepeatCount = normalizeAlarmRepeatCount(
            prefs.getInt("reminder_alarm_repeat_count", DEFAULT_ALARM_REPEAT_COUNT),
        ),
        )
    }

    private fun readTimeOfDaySettings(): List<TimeOfDaySetting> {
        val raw = prefs.getString("time_of_day_settings", null) ?: return DEFAULT_TIME_OF_DAY_SETTINGS
        return runCatching { json.decodeFromString<List<TimeOfDaySetting>>(raw) }.getOrNull()
            ?.filter { it.phrase.isNotBlank() && Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(it.time) }
            ?.ifEmpty { DEFAULT_TIME_OF_DAY_SETTINGS }
            ?: DEFAULT_TIME_OF_DAY_SETTINGS
    }

    private fun readCustomTheme(): CustomThemeSettings {
        val raw = prefs.getString("custom_theme", null)
            ?: return defaultCustomTheme("light").withAutomaticDependentColors()
        return runCatching { json.decodeFromString<CustomThemeSettings>(raw) }.getOrNull()
            ?.withAutomaticDependentColors()
            ?: defaultCustomTheme("light").withAutomaticDependentColors()
    }

    private fun sanitizeThemeColor(value: String, fallback: String): String {
        val normalized = value.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
        return normalized.takeIf { Regex("#(?:[0-9A-F]{6}|[0-9A-F]{8})").matches(it) } ?: fallback
    }
}

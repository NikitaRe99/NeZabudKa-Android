package ru.nezabudka.app.ui.screens

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.ui.platform.LocalConfiguration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Info
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import ru.nezabudka.app.data.AppIconOption
import ru.nezabudka.app.updates.AppUpdateState
import ru.nezabudka.app.updates.GithubAppUpdater
import ru.nezabudka.app.R
import ru.nezabudka.app.data.DeviceCapabilities
import ru.nezabudka.app.domain.AppSettings
import ru.nezabudka.app.domain.ALARM_PRESENTATION_NOTIFICATION
import ru.nezabudka.app.domain.ALARM_PRESENTATION_SCREEN
import ru.nezabudka.app.domain.AUTOMATIC_WHISPER_THREAD_COUNT
import ru.nezabudka.app.domain.CustomThemeSettings
import ru.nezabudka.app.domain.DEFAULT_TIME_OF_DAY_SETTINGS
import ru.nezabudka.app.domain.NOTE_TRASH_RETENTION_OPTIONS
import ru.nezabudka.app.domain.TimeOfDaySetting
import ru.nezabudka.app.domain.effectiveWhisperThreadCount
import ru.nezabudka.app.domain.maximumWhisperThreadCount
import ru.nezabudka.app.ports.SpeechToTextEngine
import ru.nezabudka.app.ports.UserModelsStorageState
import ru.nezabudka.app.speech.AndroidSpeechRecognizerEngine
import ru.nezabudka.app.speech.CloudCredentialsState
import ru.nezabudka.app.speech.WhisperDownload
import ru.nezabudka.app.speech.WhisperModelsState
import ru.nezabudka.app.llm.ClassicNluModelState
import ru.nezabudka.app.ui.AppLayout
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.appShadowElevation
import ru.nezabudka.app.ui.AssistantUiState
import ru.nezabudka.app.ui.WhisperThreadBenchmarkUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val SettingsTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val SettingsTrailingSlotSize = 48.dp
private val SettingsTrailingSlotOffset = 12.dp

@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    engines: List<SpeechToTextEngine>,
    modelStorage: UserModelsStorageState,
    whisperModels: WhisperModelsState,
    whisperDownloads: List<WhisperDownload>,
    classicNluModel: ClassicNluModelState,
    classicNluDownloadAvailable: Boolean,
    cloudCredentials: CloudCredentialsState,
    deviceCapabilities: DeviceCapabilities,
    selectSttEngine: (String) -> Unit,
    setKeepWhisperModelLoaded: (Boolean) -> Unit,
    setWhisperThreadCount: (Int) -> Unit,
    setAudioPreprocessing: (Boolean) -> Unit,
    setShowPlaybackButton: (Boolean) -> Unit,
    setShowTechnicalLog: (Boolean) -> Unit,
    setShowAssistantWelcomeCard: (Boolean) -> Unit,
    setTrustStt: (Boolean) -> Unit,
    setTheme: (String) -> Unit,
    setCustomTheme: (CustomThemeSettings) -> Unit,
    setPopupDurationSeconds: (Int) -> Unit,
    setClassicNluEnabled: (Boolean) -> Unit,
    setTimeOfDaySettings: (List<TimeOfDaySetting>) -> Unit,
    setNoteTrashRetentionDays: (Int) -> Unit,
    setNoteFolderInterface: (String) -> Unit,
    setShowNavigationLabels: (Boolean) -> Unit,
    setUiPerformanceMode: (String) -> Unit,
    setNotificationSound: (String) -> Unit,
    setNotificationVibrationEnabled: (Boolean) -> Unit,
    setReminderAlarmMode: (Boolean) -> Unit,
    setReminderAlarmPresentation: (String) -> Unit,
    setReminderAlarmSound: (String) -> Unit,
    setNotificationWakeScreen: (Boolean) -> Unit,
    setReminderAlarmRingSeconds: (Int) -> Unit,
    setReminderAlarmPauseMinutes: (Int) -> Unit,
    setReminderAlarmRepeatCount: (Int) -> Unit,
    selectWhisper: (String) -> Unit,
    downloadWhisper: (WhisperDownload) -> Unit,
    importWhisper: (Uri) -> Unit,
    downloadClassicNlu: () -> Unit,
    importClassicNlu: (Uri) -> Unit,
    selectClassicNlu: (String) -> Unit,
    selectModelsDirectory: (Uri) -> Unit,
    clearModelCache: ((Long?) -> Unit) -> Unit,
    saveYandex: (String, String) -> Unit,
    saveGoogle: (String) -> Unit,
    saveVk: (String, String) -> Unit,
    notificationsEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
    fullScreenAlarmsAllowed: Boolean,
    overlayWindowsAllowed: Boolean,
    backgroundRunAllowed: Boolean,
    backgroundSettingsTitle: String,
    alarmVendorSettingsTitle: String?,
    autoStartSettingsTitle: String?,
    openNotificationSettings: () -> Unit,
    openExactAlarmSettings: () -> Unit,
    openFullScreenAlarmSettings: () -> Unit,
    openOverlaySettings: () -> Unit,
    openAlarmNotificationSettings: () -> Unit,
    openBackgroundRunSettings: () -> Unit,
    openAlarmVendorSettings: () -> Unit,
    openAutoStartSettings: () -> Unit,
    resetFirstUseSetup: () -> Unit,
    threadBenchmark: WhisperThreadBenchmarkUiState,
    startThreadBenchmark: () -> Unit,
    cancelThreadBenchmark: () -> Unit,
    applyThreadBenchmark: () -> Unit,
    setWhisperAccurateMode: (Boolean) -> Unit,
    setWhisperRawMode: (Boolean) -> Unit,
    setNotePunctuationModel: (String) -> Unit,
    punctuationModel: ru.nezabudka.app.speech.RupunctModelState,
    downloadPunctuationModel: () -> Unit,
    cancelPunctuationDownload: () -> Unit,
    requestedSearch: String? = null,
    consumeRequestedSearch: () -> Unit = {},
    appIcon: AppIconOption = AppIconOption.CHECK,
    selectAppIcon: (AppIconOption) -> Unit = {},
    appUpdateState: AppUpdateState,
    checkForUpdate: () -> Unit,
    installUpdate: () -> Unit,
    loadCloudCredential: (String) -> String,
    importPunctuation: (Uri) -> Unit,
    selectPunctuationFile: (String) -> Unit,
    runReminderTest: (Int) -> Unit,
    cancelReminderTest: () -> Unit,
    dataTransfer: ru.nezabudka.app.ui.DataTransferUiState,
    exportData: (Uri, ru.nezabudka.app.data.BackupContent) -> Unit,
    prepareDataRestore: (Uri, ru.nezabudka.app.data.BackupContent) -> Unit,
    confirmDataRestore: () -> Unit,
    cancelDataRestore: () -> Unit,
    setUpdateSearchEnabled: (Boolean) -> Unit,
    downloadUpdate: () -> Unit,
    deleteModel: (String, String) -> Unit,
    canDeleteModels: Boolean,
    requestedTarget: ru.nezabudka.app.domain.AssistantSettingsTarget? = null,
    consumeRequestedTarget: () -> Unit = {},
    onWhisperModelsOpened: () -> Unit = {},
    setUiHapticsEnabled: (Boolean) -> Unit = {},
    setHidePopupNotices: (Boolean) -> Unit = {},
    setVisibleScreens: (List<String>) -> Unit = {},
    singleScreenMode: Boolean = false,
    returnToMainScreen: () -> Unit = {},
    returnToAssistant: () -> Unit = {},
    setGigaamLivePreview: (Boolean) -> Unit = {},
    setEditorFullPunctuation: (Boolean) -> Unit = {},
    useDefaultModelsDirectory: (Boolean) -> Unit = {},
    setSpokenSymbolsEnabled: (Boolean) -> Unit = {},
    setSpokenSymbols: (List<ru.nezabudka.app.domain.SpokenSymbol>) -> Unit = {},
) {
    val gigaam = settings.sttEngine == "gigaam"
    val localThreadCount = if (gigaam) settings.gigaamThreadCount else settings.whisperThreadCount
    val keepLocalModel = if (gigaam) settings.keepGigaamModelLoaded else settings.keepWhisperModelLoaded
    var selectedCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var openedFromAssistant by rememberSaveable { mutableStateOf(false) }
    var openedFromNotes by rememberSaveable { mutableStateOf(false) }
    var targetSettingsKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(requestedSearch) {
        requestedSearch?.let {
            targetSettingsKey = null
            selectedCategory = SETTINGS_CATALOG.firstOrNull { entry -> entry.matches(it) }?.category
            searchQuery = it
            consumeRequestedSearch()
        }
    }
    val visibleCategories = SettingsCategory.entries.filter {
        when (it) {
            SettingsCategory.NOTES -> "notes" in settings.visibleScreens
            SettingsCategory.REMINDERS -> "reminders" in settings.visibleScreens
            else -> true
        }
    }
    val entries = remember(settings.sttEngine, settings.reminderAlarmMode, settings.visibleScreens) {
        availableSettingsEntries(settings.sttEngine, settings.reminderAlarmMode).filter { it.category in visibleCategories }
    }
    val matchingEntries = remember(entries, selectedCategory, searchQuery) {
        entries.filter { if (searchQuery.isNotBlank()) it.matches(searchQuery) else it.category == selectedCategory }
    }
    val matchingKeys = remember(matchingEntries) { matchingEntries.map { it.key }.toSet() }
    val showSection: (String) -> Boolean = { it in matchingKeys }
    BackHandler(openedFromAssistant || selectedCategory != null || searchQuery.isNotBlank()) {
        if (openedFromAssistant) returnToAssistant()
        else if (searchQuery.isNotBlank()) searchQuery = "" else selectedCategory = null
    }
    var resetFirstUseDialog by remember { mutableStateOf(false) }
    var firstUseResetDone by remember { mutableStateOf(false) }
    var punctuationExpanded by remember { mutableStateOf(false) }
    var sttExpanded by remember { mutableStateOf(false) }
    var alarmPresentationExpanded by remember { mutableStateOf(false) }
    var alarmRingDurationExpanded by remember { mutableStateOf(false) }
    var alarmPauseExpanded by remember { mutableStateOf(false) }
    var alarmRepeatExpanded by remember { mutableStateOf(false) }
    var trashRetentionExpanded by remember { mutableStateOf(false) }
    var deleteStorageFolderDialog by remember { mutableStateOf(false) }
    var spokenSymbolsDialog by remember { mutableStateOf(false) }
    if (spokenSymbolsDialog) SpokenSymbolsDialog(settings.spokenSymbols, { spokenSymbolsDialog = false }, setSpokenSymbols)
    if (deleteStorageFolderDialog) AppAlertDialog(
        onDismissRequest = { deleteStorageFolderDialog = false }, title = { Text("Удалить выбранную папку?") },
        text = { Text("${modelStorage.location}\n\nМодели будут перенесены в папку по умолчанию. Затем выбранная папка, включая резервные копии в data, будет удалена. Заметки и напоминания в приложении сохранятся. Папку с посторонними файлами удалить нельзя.") },
        confirmButton = { TextButton(onClick = { deleteStorageFolderDialog = false; useDefaultModelsDirectory(true) }) { Text("Перенести и удалить") } },
        dismissButton = { TextButton(onClick = { deleteStorageFolderDialog = false }) { Text("Отмена") } },
    )
    var folderInterfaceExpanded by remember { mutableStateOf(false) }
    var whisperExpanded by remember { mutableStateOf(false) }
    var whisperThreadsExpanded by remember { mutableStateOf(false) }
    var threadBenchmarkDialog by remember { mutableStateOf(false) }
    var reminderTestDialog by remember { mutableStateOf(false) }
    var soundPreviewError by remember { mutableStateOf<String?>(null) }
    var yandexKey by remember { mutableStateOf(loadCloudCredential("yandex")) }
    var yandexFolder by remember(cloudCredentials.yandexFolderId) { mutableStateOf(cloudCredentials.yandexFolderId) }
    var googleToken by remember { mutableStateOf(loadCloudCredential("google_cloud")) }
    var vkToken by remember { mutableStateOf(loadCloudCredential("vk_cloud")) }
    var vkEndpoint by remember(cloudCredentials.vkEndpoint) { mutableStateOf(cloudCredentials.vkEndpoint) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var timePickerIndex by remember { mutableStateOf<Int?>(null) }
    var addTimeOfDayDialog by remember { mutableStateOf(false) }
    var newTimePhrase by remember { mutableStateOf("") }
    var newTimeValue by remember { mutableStateOf("18:00") }
    var cloudHelp by remember { mutableStateOf<CloudSttHelp?>(null) }
    var themePickerOpen by remember { mutableStateOf(false) }
    var iconPickerOpen by remember { mutableStateOf(false) }
    var uiPerformanceExpanded by remember { mutableStateOf(false) }
    var popupDurationExpanded by remember { mutableStateOf(false) }
    var customThemeEditor by remember { mutableStateOf(false) }
    var soundPickerOpen by remember { mutableStateOf(false) }
    var soundPickerType by remember { mutableIntStateOf(RingtoneManager.TYPE_NOTIFICATION) }
    var pendingNotificationSound by remember(settings.notificationSound) {
        mutableStateOf(settings.notificationSound)
    }
    var soundOptions by remember { mutableStateOf<List<NotificationSoundOption>>(emptyList()) }
    var soundOptionsLoading by remember { mutableStateOf(false) }
    var modelCacheMessage by remember { mutableStateOf<String?>(null) }
    var storagePathDialog by remember { mutableStateOf(false) }
    val importWhisperModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(importWhisper) }
    val importPunctuationModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(importPunctuation) }
    val importClassicNluModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(importClassicNlu) }
    val exportNotesFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let { uri -> exportData(uri, ru.nezabudka.app.data.BackupContent.NOTES) }
    }
    val exportRemindersFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let { uri -> exportData(uri, ru.nezabudka.app.data.BackupContent.REMINDERS) }
    }
    val restoreNotesFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> prepareDataRestore(uri, ru.nezabudka.app.data.BackupContent.NOTES) }
    }
    val restoreRemindersFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> prepareDataRestore(uri, ru.nezabudka.app.data.BackupContent.REMINDERS) }
    }
    val selectModelsRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            selectModelsDirectory(it)
        }
    }
    val settingsListState = rememberLazyListState()
    LaunchedEffect(requestedTarget) {
        requestedTarget?.let { target ->
            searchQuery = ""
            openedFromAssistant = target != ru.nezabudka.app.domain.AssistantSettingsTarget.SCREENS
            openedFromNotes = target == ru.nezabudka.app.domain.AssistantSettingsTarget.TRASH
            targetSettingsKey = when (target) {
                ru.nezabudka.app.domain.AssistantSettingsTarget.SCREENS -> "app-screens"
                ru.nezabudka.app.domain.AssistantSettingsTarget.SPEECH -> "Распознавание речи"
                ru.nezabudka.app.domain.AssistantSettingsTarget.CLOUD -> settings.sttEngine
                ru.nezabudka.app.domain.AssistantSettingsTarget.WHISPER -> if (settings.sttEngine == "whisper") "Модель Whisper.cpp" else "Распознавание речи"
                ru.nezabudka.app.domain.AssistantSettingsTarget.GIGAAM,
                ru.nezabudka.app.domain.AssistantSettingsTarget.LIVE_TEXT -> if (gigaam) "Модель GigaAM" else "Распознавание речи"
                ru.nezabudka.app.domain.AssistantSettingsTarget.EDITOR_PUNCTUATION -> "Пунктуация голосового ввода"
                ru.nezabudka.app.domain.AssistantSettingsTarget.TIMES -> "Время суток"
                ru.nezabudka.app.domain.AssistantSettingsTarget.TRASH -> "Корзина заметок"
                ru.nezabudka.app.domain.AssistantSettingsTarget.FOLDERS -> "folder-interface"
                ru.nezabudka.app.domain.AssistantSettingsTarget.NLU -> "Распознавание команд"
                ru.nezabudka.app.domain.AssistantSettingsTarget.PUNCTUATION -> "Пунктуация голосового ввода"
                ru.nezabudka.app.domain.AssistantSettingsTarget.REMINDER_MODE -> "reminder-mode"
                ru.nezabudka.app.domain.AssistantSettingsTarget.REMINDER_ACCESS -> "notification-permissions"
                ru.nezabudka.app.domain.AssistantSettingsTarget.ALARM -> if (settings.reminderAlarmMode) "alarm-mode" else "reminder-mode"
                ru.nezabudka.app.domain.AssistantSettingsTarget.WELCOME -> "interface-behavior"
                else -> "Распознавание речи"
            }
            selectedCategory = when (target) {
                ru.nezabudka.app.domain.AssistantSettingsTarget.TIMES,
                ru.nezabudka.app.domain.AssistantSettingsTarget.REMINDER_MODE,
                ru.nezabudka.app.domain.AssistantSettingsTarget.REMINDER_ACCESS,
                ru.nezabudka.app.domain.AssistantSettingsTarget.ALARM -> SettingsCategory.REMINDERS
                ru.nezabudka.app.domain.AssistantSettingsTarget.TRASH,
                ru.nezabudka.app.domain.AssistantSettingsTarget.FOLDERS -> SettingsCategory.NOTES
                ru.nezabudka.app.domain.AssistantSettingsTarget.SCREENS,
                ru.nezabudka.app.domain.AssistantSettingsTarget.WELCOME -> SettingsCategory.APPEARANCE
                else -> SettingsCategory.SPEECH
            }
            consumeRequestedTarget()
        }
    }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "—" }
    }
    val theme = AppTheme.tokens
    val inputFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = theme.colors.input,
        unfocusedContainerColor = theme.colors.input,
    )
    val visibleEngines = engines
    val selectedEngine = visibleEngines.firstOrNull { it.id == settings.sttEngine }
    val androidEngine = engines.filterIsInstance<AndroidSpeechRecognizerEngine>().firstOrNull()
    val effectiveWhisperThreads = if (gigaam) (if (localThreadCount > 0) localThreadCount.coerceIn(1, deviceCapabilities.cpuCores.coerceAtLeast(1)) else minOf(4, deviceCapabilities.cpuCores.coerceAtLeast(1))) else effectiveWhisperThreadCount(
        configured = localThreadCount,
        cpuCores = deviceCapabilities.cpuCores,
        lowRamDevice = deviceCapabilities.lowRamDevice,
        memoryClassMb = deviceCapabilities.memoryClassMb,
    )
    val maximumWhisperThreads = maximumWhisperThreadCount(
        cpuCores = deviceCapabilities.cpuCores,
    )
    val automaticWhisperThreads = if (gigaam) minOf(4, deviceCapabilities.cpuCores.coerceAtLeast(1)) else effectiveWhisperThreadCount(
        configured = AUTOMATIC_WHISPER_THREAD_COUNT,
        cpuCores = deviceCapabilities.cpuCores,
        lowRamDevice = deviceCapabilities.lowRamDevice,
        memoryClassMb = deviceCapabilities.memoryClassMb,
    )
    val manualWhisperThreadOptions = 1..maximumWhisperThreads
    val builtInTimePhrases = remember {
        DEFAULT_TIME_OF_DAY_SETTINGS.mapTo(mutableSetOf()) { it.phrase.lowercase().replace('ё', 'е') }
    }
    DisposableEffect(Unit) { onDispose { previewPlayer?.release(); previewPlayer = null } }
    LaunchedEffect(previewPlayer) {
        val playing = previewPlayer
        if (playing != null) {
            kotlinx.coroutines.delay(5_000)
            if (previewPlayer === playing) { playing.release(); previewPlayer = null }
        }
    }
    LaunchedEffect(soundPickerOpen, soundPickerType) {
        if (soundPickerOpen) soundPreviewError = null
        if (soundPickerOpen && soundOptions.isEmpty()) {
            soundOptionsLoading = true
            soundOptions = withContext(Dispatchers.IO) {
                loadNotificationSoundOptions(context, soundPickerType)
            }
            soundOptionsLoading = false
        }
    }
    val themeLabel = when (settings.theme) {
        "dark" -> "Тёмная"
        "custom" -> "Своя тема"
        else -> "Светлая"
    }
    val soundTitle by produceState("Выбранный звук", context, settings.notificationSound) {
        value = withContext(Dispatchers.IO) {
            if (settings.notificationSound.isBlank()) "Без звука"
            else runCatching { RingtoneManager.getRingtone(context, Uri.parse(settings.notificationSound))?.getTitle(context) }.getOrNull() ?: "Выбранный звук"
        }
    }
    val alarmSoundTitle by produceState("Выбранная мелодия", context, settings.reminderAlarmSound) {
        value = withContext(Dispatchers.IO) {
            if (settings.reminderAlarmSound.isBlank()) "Без звука"
            else runCatching { RingtoneManager.getRingtone(context, Uri.parse(settings.reminderAlarmSound))?.getTitle(context) }.getOrNull() ?: "Выбранная мелодия"
        }
    }
    LaunchedEffect(selectedCategory, searchQuery, targetSettingsKey) {
        val displayedKeys = listOf("data-files", "Данные и хранилище", "storage-cache", "Интерфейс", "app-screens", "interface-behavior", "folder-interface", "reminder-mode", "Уведомления", "alarm-mode", "alarm-timing", "notification-permissions", "Время суток", "Корзина заметок", "Распознавание речи", "yandex", "google_cloud", "vk_cloud", "Модель Whisper.cpp", "Модель GigaAM", "Распознавание команд", "Пунктуация голосового ввода", "spoken-symbols", "github-updates", "О приложении").filter { it in matchingKeys }
        val index = displayedKeys.indexOf(targetSettingsKey).coerceAtLeast(0)
        settingsListState.scrollToItem(index)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = AppLayout.horizontalPadding)) {
        Row(Modifier.fillMaxWidth().padding(top = AppLayout.contentTopPadding, bottom = AppLayout.sectionSpacing), verticalAlignment = Alignment.CenterVertically) {
            if (singleScreenMode || selectedCategory != null || searchQuery.isNotBlank()) IconButton(onClick = {
                if (selectedCategory == null && searchQuery.isBlank()) returnToMainScreen()
                else
                if (openedFromAssistant) returnToAssistant() else { searchQuery = ""; selectedCategory = null; targetSettingsKey = null }
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (openedFromNotes) "Вернуться к заметкам" else if (openedFromAssistant) "Вернуться к подсказке" else if (selectedCategory != null || searchQuery.isNotBlank()) "Все настройки" else "Вернуться") }
        AppSearchField(
            value = searchQuery, onValueChange = { targetSettingsKey = null; searchQuery = it },
            placeholder = "Найти настройку",
            reducedEffects = settings.uiPerformanceMode == ru.nezabudka.app.domain.UI_PERFORMANCE_REDUCED,
            trailingContent = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Очистить поиск") } },
            modifier = Modifier.weight(1f),
        )
        }
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        state = settingsListState,
        contentPadding = PaddingValues(bottom = AppLayout.sectionSpacing),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectedCategory == null && searchQuery.isBlank()) {
            items(visibleCategories, key = { it.name }, contentType = { "category" }) { category ->
                Card(onClick = { selectedCategory = category }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(theme.shapes.card),
                    colors = CardDefaults.cardColors(containerColor = theme.colors.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(4.dp))) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = RoundedCornerShape(theme.shapes.tag), color = theme.colors.selected, contentColor = theme.colors.accent) {
                            Icon(when(category) {
                                SettingsCategory.SPEECH -> Icons.Default.Mic
                                SettingsCategory.REMINDERS -> Icons.Default.NotificationsNone
                                SettingsCategory.NOTES -> Icons.Default.FolderOpen
                                SettingsCategory.APPEARANCE -> Icons.Default.Palette
                                SettingsCategory.STORAGE -> Icons.Default.Storage
                                SettingsCategory.ABOUT -> Icons.Default.Info
                            }, null, Modifier.padding(12.dp).size(24.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(category.title, style = MaterialTheme.typography.titleMedium)
                            Text(category.description, color = theme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = theme.colors.textSecondary)
                    }
                }
            }
        }
        if (searchQuery.isNotBlank() && matchingEntries.isEmpty()) item(key = "search-empty") {
            AppEmptyState(Icons.Default.Search, "Настройка не найдена", "Попробуйте другое слово: например, «потоки», «звук» или «автозапуск».")
        }
        if (showSection("data-files")) item(key = "data-files", contentType = "settings-section") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSection("Заметки") {
                    SettingsSupportingText("Сохраните заметки вместе с папками, архивом и корзиной в JSON-файл для переноса или восстановления.")
                    SettingsActionRow(title = "Сохранить заметки в файл", enabled = !dataTransfer.busy,
                        onClick = { exportNotesFile.launch("nezabudka-notes-${java.time.LocalDate.now()}.json") })
                    SettingsActionRow(title = "Восстановить заметки из файла", enabled = !dataTransfer.busy,
                        onClick = { restoreNotesFile.launch(arrayOf("*/*")) })
                }
                SettingsSection("Напоминания") {
                    SettingsSupportingText("В файл входят даты, повторы, избранное и выполненные напоминания.")
                    SettingsActionRow(title = "Сохранить напоминания в файл", enabled = !dataTransfer.busy,
                        onClick = { exportRemindersFile.launch("nezabudka-reminders-${java.time.LocalDate.now()}.json") })
                    SettingsActionRow(title = "Восстановить напоминания из файла", enabled = !dataTransfer.busy,
                        onClick = { restoreRemindersFile.launch(arrayOf("*/*")) })
                    if (dataTransfer.busy) SettingsSupportingText("Обработка файла…")
                    dataTransfer.message?.let { SettingsSupportingText(it,
                        color = if (dataTransfer.error) MaterialTheme.colorScheme.error else theme.colors.accent) }
                }
            }
        }
        if (showSection("Данные и хранилище")) item(key = "Данные и хранилище", contentType = "settings-section") {
            SettingsSection("Каталог данных и моделей") {
                if (dataTransfer.busy) SettingsSupportingText("Перенос моделей… Дождитесь завершения.")
                if (dataTransfer.error) dataTransfer.message?.let { SettingsSupportingText(it, color = MaterialTheme.colorScheme.error) }
                SettingsSupportingText("В выбранной вами папке сохраняются модели и данные. После переустановки выберите её снова для восстановления. Без своей папки используйте сохранение в файл.")
                SettingsActionRow(title = "Папка хранения",
                    value = if (modelStorage.userSelectedDirectory) "Пользовательский каталог" else "Каталог приложения",
                    onClick = { selectModelsRoot.launch(null) }, trailingIcon = Icons.Default.FolderOpen, enabled = !dataTransfer.busy && canDeleteModels)
                if (modelStorage.configured) SettingsActionRow(title = "Текущий каталог",
                    value = modelStorage.location.substringAfterLast('/').substringAfterLast(':'),
                    onClick = { storagePathDialog = true }, trailingIcon = Icons.Default.Info)
                SettingsSupportingText("Модели — в models/whisper, models/gigaam, models/nlu и models/punctuation. Резервная копия — в data. При смене папки модели переносятся с проверкой файлов.")
                if (modelStorage.userSelectedDirectory) {
                    SettingsActionRow(title = "Вернуть папку по умолчанию", description = "Перенести модели в каталог приложения", onClick = { useDefaultModelsDirectory(false) }, enabled = canDeleteModels && !dataTransfer.busy)
                    SettingsActionRow(title = "Удалить выбранную папку", description = "Сначала перенести модели в папку по умолчанию", onClick = { deleteStorageFolderDialog = true }, enabled = canDeleteModels && !dataTransfer.busy)
                }
            }
        }
        if (showSection("storage-cache")) item(key = "storage-cache", contentType = "settings-section") {
            SettingsSection("Временные файлы") {
                val cache = modelStorage.cache
                SettingsSupportingText("Очистка удаляет рабочие копии моделей и незавершённые загрузки. Скачанные модели, заметки и напоминания сохраняются.")
                SettingsInfoRow(title = "Копии моделей", value = formatCacheSize(cache.nativeCopiesBytes))
                SettingsInfoRow(title = "Незавершённые загрузки", value = formatCacheSize(cache.incompleteDownloadsBytes))
                SettingsInfoRow(title = "Временное аудио", value = formatCacheSize(cache.temporaryAudioBytes))
                SettingsActionRow(title = "Очистить кэш моделей",
                    value = formatCacheSize(cache.nativeCopiesBytes + cache.incompleteDownloadsBytes),
                    enabled = whisperModels.downloadingId == null,
                    trailingIcon = Icons.Default.DeleteOutline,
                    onClick = {
                        clearModelCache { removedBytes -> modelCacheMessage = when {
                            removedBytes == null -> "Не удалось очистить кэш моделей"
                            removedBytes == 0L -> "Кэш моделей уже пуст"
                            else -> "Освобождено ${formatCacheSize(removedBytes)}"
                        } }
                    })
                modelCacheMessage?.let { SettingsSupportingText(it,
                    color = if (it.startsWith("Не удалось")) MaterialTheme.colorScheme.error else theme.colors.accent) }
            }
        }
        if (showSection("Интерфейс")) item(key = "Интерфейс", contentType = "settings-section") {
            SettingsSection("Интерфейс") {
                SettingsSupportingText("Выберите оформление приложения. В своей теме можно настроить цвета, шрифт и форму элементов.")
                SettingsActionRow(
                    title = "Значок приложения",
                    value = appIcon.title,
                    onClick = { iconPickerOpen = true },
                    trailingDescription = "Выбрать значок приложения",
                )
                SettingsActionRow(
                    title = "Тема",
                    value = themeLabel,
                    onClick = { themePickerOpen = true },
                    trailingDescription = "Выбрать тему",
                )
                if (settings.theme == "custom") {
                    SettingsActionRow(
                        title = "Настроить пользовательскую тему",
                        onClick = { customThemeEditor = true },
                        trailingIcon = Icons.Default.Palette,
                        trailingDescription = "Настроить тему",
                    )
                }
                    SettingsToggleRow(
                        title = "Тени",
                        description = "Тени карточек, меню и окон. В облегчённом режиме отключаются автоматически.",
                        checked = settings.customTheme.shadowsEnabled,
                        onCheckedChange = { enabled ->
                            setCustomTheme(settings.customTheme.copy(shadowsEnabled = enabled))
                        },
                    )
            }
        }
        if (showSection("app-screens")) item(key = "app-screens", contentType = "settings-section") {
            SettingsSection("Экраны приложения") {
                listOf("assistant" to "Помощник", "reminders" to "Напоминания", "notes" to "Заметки").forEach { (route, title) ->
                    val checked = route in settings.visibleScreens
                    val proposed = if (checked) settings.visibleScreens - route else settings.visibleScreens + route
                    SettingsToggleRow(title = title, checked = checked,
                        enabled = ru.nezabudka.app.domain.validAppScreens(proposed),
                        onCheckedChange = { setVisibleScreens(proposed) })
                }
                SettingsSupportingText("Оставьте хотя бы заметки или напоминания. Если выбран один экран, настройки доступны через его меню «…», а нижняя панель скрыта.")
                SettingsSupportingText("Отключение экранов не удаляет записи. Если напоминания отключены, сигналы и повторы будильника не срабатывают. После включения пропущенные останутся в «Просроченных». Будущие сигналы восстановятся, повторяющиеся продолжатся с ближайшей даты.")
            }
        }
        if (showSection("interface-behavior")) item(key = "interface-behavior", contentType = "settings-section") {
            SettingsSection("Поведение интерфейса") {
                    SettingsToggleRow(
                        title = "Не показывать всплывающие уведомления",
                        description = "Скрывает сообщения о действиях, включая отмену. Уточнения даты и ошибки остаются на экране.",
                        checked = settings.hidePopupNotices,
                        onCheckedChange = setHidePopupNotices,
                    )
                    if (!settings.hidePopupNotices) {
                    Box(Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            title = "Время показа всплывающих уведомлений",
                            value = if (settings.popupDurationSeconds == 0) {
                                "До закрытия"
                            } else {
                                "${settings.popupDurationSeconds} сек"
                            },
                            onClick = { popupDurationExpanded = true },
                            trailingDescription = "Выбрать время показа",
                        )
                        AppDropdownMenu(
                            expanded = popupDurationExpanded,
                            onDismissRequest = { popupDurationExpanded = false },
                        ) {
                            listOf(0, 3, 5, 10, 30, 60).forEach { seconds ->
                                AppMenuItem(
                                    text = if (seconds == 0) "До закрытия" else "$seconds секунд",
                                    selected = settings.popupDurationSeconds == seconds,
                                    onClick = {
                                        setPopupDurationSeconds(seconds)
                                        popupDurationExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    SettingsSupportingText(
                        "Определяет время показа сообщений о выполненных действиях и возможности отмены.",
                    )
                    }
                    SettingsToggleRow(
                        title = "Виброотклик интерфейса",
                        description = "Отклик при записи, выборе времени и действиях с записями",
                        checked = settings.uiHapticsEnabled,
                        onCheckedChange = setUiHapticsEnabled,
                    )
                    SettingsToggleRow(
                        title = "Отображать надписи под иконками",
                        checked = settings.showNavigationLabels,
                        onCheckedChange = setShowNavigationLabels,
                    )
                    Box(Modifier.fillMaxWidth()) {
                        val performanceLabel = when (settings.uiPerformanceMode) {
                            ru.nezabudka.app.domain.UI_PERFORMANCE_REDUCED -> "Облегчённый"
                            ru.nezabudka.app.domain.UI_PERFORMANCE_FULL -> "Полный"
                            else -> "Автоматически"
                        }
                        SettingsActionRow(
                            title = "Производительность интерфейса",
                            value = performanceLabel,
                            onClick = { uiPerformanceExpanded = true },
                            trailingDescription = "Выбрать режим производительности",
                        )
                        AppDropdownMenu(
                            expanded = uiPerformanceExpanded,
                            onDismissRequest = { uiPerformanceExpanded = false },
                        ) {
                            listOf(
                                ru.nezabudka.app.domain.UI_PERFORMANCE_AUTO to "Автоматически",
                                ru.nezabudka.app.domain.UI_PERFORMANCE_REDUCED to "Облегчённый",
                                ru.nezabudka.app.domain.UI_PERFORMANCE_FULL to "Полный",
                            ).forEach { (value, title) ->
                                AppMenuItem(
                                    text = title,
                                    selected = settings.uiPerformanceMode == value,
                                    onClick = {
                                        setUiPerformanceMode(value)
                                        uiPerformanceExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    SettingsSupportingText(
                        "Автоматический режим включает облегчённый интерфейс на слабых устройствах. Облегчённый режим уменьшает тени и постепенно выводит длинные списки.",
                    )
                    if ("assistant" in settings.visibleScreens) {
                    SettingsToggleRow(
                        title = "Начальная подсказка",
                        description = "Примеры команд на экране помощника",
                        checked = settings.showAssistantWelcomeCard,
                        onCheckedChange = setShowAssistantWelcomeCard,
                    )
                    SettingsToggleRow(
                        title = "Показывать технический лог",
                        description = "Расширенный журнал текущего сеанса будет показан на вкладке «Помощник»",
                        checked = settings.showTechnicalLog,
                        onCheckedChange = setShowTechnicalLog,
                    )
                    }
            }
        }
        if (showSection("folder-interface")) item(key = "folder-interface", contentType = "settings-section") {
            SettingsSection("Интерфейс папок") {
                SettingsSupportingText("Карусель показывает папки сверху. Боковая панель открывает их списком сбоку.")
                Box(Modifier.fillMaxWidth()) {
                    SettingsActionRow(
                        title = "Показывать папки",
                        value = if (settings.noteFolderInterface == "drawer") "Боковая панель" else "Карусель",
                        description = "Способ переключения папок на вкладке «Заметки»",
                        onClick = { folderInterfaceExpanded = true },
                        trailingDescription = "Выбрать интерфейс папок",
                    )
                    AppDropdownMenu(folderInterfaceExpanded, { folderInterfaceExpanded = false }) {
                        listOf("carousel" to "Карусель", "drawer" to "Боковая панель").forEach { (key, title) ->
                            AppMenuItem(title, onClick = { setNoteFolderInterface(key); folderInterfaceExpanded = false }, selected = settings.noteFolderInterface == key)
                        }
                    }
                }
            }
        }
        if (showSection("Корзина заметок")) item(key = "Корзина заметок", contentType = "settings-section") {
            SettingsSection("Корзина заметок") {
                SettingsSupportingText("Удалённые заметки можно восстановить, пока не истёк срок хранения в корзине.")
                SettingsSupportingText(
                    "Заметки старше выбранного срока будут окончательно удаляться автоматически.",
                )
                Box(Modifier.fillMaxWidth()) {
                    SettingsActionRow(
                        title = "Автоматическое удаление",
                        value = noteTrashRetentionLabel(settings.noteTrashRetentionDays),
                        onClick = { trashRetentionExpanded = true },
                        trailingDescription = "Выбрать срок",
                    )
                    AppDropdownMenu(
                            expanded = trashRetentionExpanded,
                            onDismissRequest = { trashRetentionExpanded = false },
                        ) {
                            NOTE_TRASH_RETENTION_OPTIONS.forEach { days ->
                                AppMenuItem(
                                    text = noteTrashRetentionLabel(days),
                                    onClick = {
                                        setNoteTrashRetentionDays(days)
                                        trashRetentionExpanded = false
                                    },
                                )
                            }
                        }
                }
            }
        }
        if (showSection("reminder-mode")) item(key = "reminder-mode", contentType = "settings-section") {
            SettingsSection("Режим напоминаний") {
                SettingsSupportingText("Уведомление — короткий сигнал. Будильник — длительный сигнал с возможностью повторов.")
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    SettingsActionRow(title = "Как напоминать",
                        value = if (settings.reminderAlarmMode) "Будильником" else "Уведомлением",
                        onClick = { expanded = true }, trailingDescription = "Выбрать режим напоминаний")
                    AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        AppMenuItem("Уведомлением", selected = !settings.reminderAlarmMode,
                            onClick = { setReminderAlarmMode(false); expanded = false })
                        AppMenuItem("Будильником", selected = settings.reminderAlarmMode,
                            onClick = { setReminderAlarmMode(true); expanded = false })
                    }
                }
                SettingsToggleRow(title = "Вибрация", checked = settings.notificationVibrationEnabled,
                    onCheckedChange = setNotificationVibrationEnabled)
                SettingsActionRow(title = "Проверить напоминание",
                    description = "Сейчас или после блокировки экрана",
                    onClick = { reminderTestDialog = true })
            }
        }
        if (showSection("Уведомления")) item(key = "Уведомления", contentType = "settings-section") {
            SettingsSection("Уведомления") {
                    SettingsToggleRow(
                        title = "Пробуждать экран для напоминаний",
                        description = "Включать экран при срабатывании",
                        checked = settings.notificationWakeScreen,
                        onCheckedChange = setNotificationWakeScreen,
                    )
                    SettingsActionRow(
                        title = "Уведомления на экране блокировки",
                        onClick = openAlarmNotificationSettings,
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        trailingDescription = "Открыть системные настройки",
                    )
                    SettingsActionRow(
                        title = "Звук уведомления",
                        value = soundTitle,
                        onClick = {
                            previewPlayer?.release(); previewPlayer = null
                            soundPickerType = RingtoneManager.TYPE_NOTIFICATION
                            soundOptions = emptyList()
                            pendingNotificationSound = settings.notificationSound
                            soundPickerOpen = true
                        },
                        trailingDescription = "Выбрать звук обычного напоминания",
                    )
                    SettingsSupportingText("Громкость: системные настройки уведомлений")
            }
        }
        if (showSection("alarm-mode")) item(key = "alarm-mode") {
            SettingsSection("Будильник") {
                    SettingsSupportingText("Отдельное окно показывает действия крупнее. Уведомление остаётся в шторке телефона.")
                    if (settings.reminderAlarmMode) {
                        Box(Modifier.fillMaxWidth()) {
                            SettingsActionRow(
                                title = "Показывать будильник",
                                value = if (settings.reminderAlarmPresentation == ALARM_PRESENTATION_NOTIFICATION) {
                                    "Уведомлением"
                                } else {
                                    "Отдельным окном"
                                },
                                onClick = { alarmPresentationExpanded = true },
                                trailingDescription = "Выбрать способ показа",
                            )
                            AppDropdownMenu(
                                expanded = alarmPresentationExpanded,
                                onDismissRequest = { alarmPresentationExpanded = false },
                            ) {
                                listOf(
                                    ALARM_PRESENTATION_SCREEN to "Отдельным окном",
                                    ALARM_PRESENTATION_NOTIFICATION to "Уведомлением",
                                ).forEach { (value, title) ->
                                    AppMenuItem(
                                        text = title,
                                        selected = settings.reminderAlarmPresentation == value,
                                        onClick = {
                                            setReminderAlarmPresentation(value)
                                            alarmPresentationExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                        SettingsActionRow(
                            title = "Мелодия будильника",
                            value = alarmSoundTitle,
                            onClick = {
                                previewPlayer?.release(); previewPlayer = null
                                soundPickerType = RingtoneManager.TYPE_ALARM
                                soundOptions = emptyList()
                                pendingNotificationSound = settings.reminderAlarmSound.ifBlank {
                                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
                                        ?: "content://settings/system/alarm_alert"
                                }
                                soundPickerOpen = true
                            },
                            trailingDescription = "Выбрать мелодию будильника",
                        )
                        SettingsSupportingText("Громкость: системные настройки будильника")
            }
        }
        if (showSection("alarm-timing")) item(key = "alarm-timing") {
            SettingsSection("Повторы будильника") {
                    SettingsSupportingText("Если не выполнить или не отложить напоминание, сигнал повторится после перерыва.")
                    if (settings.reminderAlarmMode) {
                        Box(Modifier.fillMaxWidth()) {
                            SettingsActionRow(
                                title = "Длительность сигнала",
                                value = "${settings.reminderAlarmRingSeconds} сек",
                                onClick = { alarmRingDurationExpanded = true },
                                trailingDescription = "Выбрать время показа будильника",
                            )
                            AppDropdownMenu(
                                expanded = alarmRingDurationExpanded,
                                onDismissRequest = { alarmRingDurationExpanded = false },
                            ) {
                                listOf(15, 30, 60, 120, 300).forEach { seconds ->
                                    AppMenuItem(
                                        text = "$seconds секунд",
                                        selected = settings.reminderAlarmRingSeconds == seconds,
                                        onClick = {
                                            setReminderAlarmRingSeconds(seconds)
                                            alarmRingDurationExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth()) {
                            SettingsActionRow(
                                title = "Перерыв между сигналами",
                                value = "${settings.reminderAlarmPauseMinutes} мин",
                                onClick = { alarmPauseExpanded = true },
                                trailingDescription = "Выбрать перерыв",
                            )
                            AppDropdownMenu(
                                expanded = alarmPauseExpanded,
                                onDismissRequest = { alarmPauseExpanded = false },
                            ) {
                                listOf(1, 3, 5, 10, 15, 30).forEach { minutes ->
                                    AppMenuItem(
                                        text = "$minutes минут",
                                        selected = settings.reminderAlarmPauseMinutes == minutes,
                                        onClick = {
                                            setReminderAlarmPauseMinutes(minutes)
                                            alarmPauseExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth()) {
                            SettingsActionRow(
                                title = "Всего сигналов",
                                value = settings.reminderAlarmRepeatCount.toString(),
                                onClick = { alarmRepeatExpanded = true },
                                trailingDescription = "Выбрать количество сигналов",
                            )
                            AppDropdownMenu(
                                expanded = alarmRepeatExpanded,
                                onDismissRequest = { alarmRepeatExpanded = false },
                            ) {
                                listOf(1, 2, 3, 5, 10).forEach { count ->
                                    AppMenuItem(
                                        text = count.toString(),
                                        selected = settings.reminderAlarmRepeatCount == count,
                                        onClick = {
                                            setReminderAlarmRepeatCount(count)
                                            alarmRepeatExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
            }
        }
        if (showSection("notification-permissions")) item(key = "notification-permissions") {
            SettingsSection("Разрешения и работа в фоне") {
                SettingsSupportingText("Эти разрешения помогают напоминаниям срабатывать вовремя, даже когда приложение закрыто.")
                SettingsActionRow(title = "Уведомления", description = if (notificationsEnabled) "Разрешены" else "Нужно разрешение", isError = !notificationsEnabled,
                    onClick = openNotificationSettings, trailingIcon = Icons.AutoMirrored.Filled.OpenInNew)
                if (!exactAlarmsAllowed) SettingsActionRow(title = "Точное время срабатывания",
                    description = "Разрешить напоминания без задержки", isError = true, onClick = openExactAlarmSettings,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew)
                if (settings.reminderAlarmMode && settings.reminderAlarmPresentation == ALARM_PRESENTATION_SCREEN) {
                    SettingsActionRow(title = "Будильник на экране блокировки",
                        description = if (fullScreenAlarmsAllowed) "Разрешён" else "Нужно разрешение", isError = !fullScreenAlarmsAllowed,
                        onClick = openFullScreenAlarmSettings, trailingIcon = Icons.AutoMirrored.Filled.OpenInNew)
                    SettingsActionRow(title = "Плавающее окно будильника",
                        description = if (overlayWindowsAllowed) "Разрешено" else "Нужно разрешение", isError = !overlayWindowsAllowed,
                        onClick = openOverlaySettings, trailingIcon = Icons.AutoMirrored.Filled.OpenInNew)
                    if (alarmVendorSettingsTitle != null) SettingsActionRow(title = "Показ окна в фоне",
                        description = "Проверьте вручную: состояние недоступно приложению", onClick = openAlarmVendorSettings,
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew)
                }
                SettingsActionRow(title = "Работа в фоне",
                    description = if (android.os.Build.MANUFACTURER.equals("samsung", true))
                        "Проверьте вручную: Батарея → Разрешение фоновой деятельности. Также проверьте, что приложение не переведено в спящий режим."
                        else if (backgroundRunAllowed) "Ограничение энергосбережения отключено" else "Проверьте ограничения батареи для напоминаний в фоне",
                    isError = !backgroundRunAllowed && !android.os.Build.MANUFACTURER.equals("samsung", true),
                    onClick = openBackgroundRunSettings, trailingIcon = Icons.AutoMirrored.Filled.OpenInNew)
                if (autoStartSettingsTitle != null) SettingsActionRow(title = "Автозапуск",
                    description = "Проверьте вручную: Android не сообщает, включён ли автозапуск", onClick = openAutoStartSettings,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew)
            }
        }
        if (showSection("Время суток")) item(key = "Время суток", contentType = "settings-section") {
            SettingsSection("Время суток") {
                SettingsSupportingText(
                    "Эти значения используются при создании напоминаний, например «Позвонить вечером».",
                )
                settings.timeOfDaySettings.forEachIndexed { index, value ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SettingsActionRow(
                            title = value.phrase,
                            value = value.time,
                            onClick = { timePickerIndex = index },
                            modifier = Modifier.weight(1f),
                            trailingDescription = "Изменить время",
                        )
                        if (value.phrase.lowercase().replace('ё', 'е') !in builtInTimePhrases) {
                            IconButton(onClick = {
                                setTimeOfDaySettings(
                                    settings.timeOfDaySettings.filterIndexed { itemIndex, _ ->
                                        itemIndex != index
                                    },
                                )
                            }) {
                                Icon(Icons.Default.DeleteOutline, "Удалить сопоставление")
                            }
                        }
                    }
                }
                SettingsActionRow(
                    title = "Добавить своё время суток",
                    onClick = { addTimeOfDayDialog = true },
                    trailingIcon = Icons.Default.Add,
                    trailingDescription = "Добавить",
                )
            }
        }
        if (showSection("Распознавание речи")) item(key = "Распознавание речи", contentType = "settings-section") {
            SettingsSection("Распознавание речи") {
                SettingsSupportingText("Распознавание превращает голос в текст. Выполнение команд настраивается отдельно.")
                    SttCatalogSelector(
                        expanded = sttExpanded,
                        setExpanded = { sttExpanded = it },
                        engines = visibleEngines,
                        selectedId = settings.sttEngine,
                        select = { engineId ->
                            selectSttEngine(engineId)
                        },
                    )
                    if (settings.sttEngine == "android") {
                        SettingsSupportingText(
                            androidEngine?.availabilityDescription()
                                ?: "Android SpeechRecognizer отсутствует",
                            color = if (androidEngine?.isAvailable() == true)
                                theme.colors.textSecondary else MaterialTheme.colorScheme.error,
                        )
                    }
                    SettingsToggleRow(
                        title = "Предобработка аудио",
                        description = if (settings.sttEngine == "android") {
                            "Android SpeechRecognizer обрабатывает звук системным сервисом напрямую"
                        } else {
                            "Снижать шум и выравнивать громкость перед распознаванием"
                        },
                        checked = settings.audioPreprocessingEnabled,
                        onCheckedChange = setAudioPreprocessing,
                        enabled = settings.sttEngine != "android",
                    )
                    SettingsToggleRow(
                        title = "Показывать кнопку «Прослушать»",
                        checked = settings.showPlaybackButton,
                        onCheckedChange = setShowPlaybackButton,
                    )
                    SettingsToggleRow(
                        title = "Выполнять команды сразу",
                        description = "Сразу выполнять распознанную команду без проверки текста",
                        checked = settings.trustStt,
                        onCheckedChange = setTrustStt,
                    )
            }
        }
        if (showSection("yandex")) item(key = "yandex") {
            SettingsSection(
                title = "Yandex SpeechKit",
                contentSpacing = 12.dp,
            ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (cloudCredentials.yandexConfigured) "API-ключ сохранён в защищённом хранилище" else "Требуется API-ключ сервисного аккаунта", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = theme.colors.textSecondary)
                        CloudSttHelpButton { cloudHelp = CloudSttHelp.YANDEX }
                    }
                    StoredCredentialField(
                        value = yandexKey,
                        onValueChange = { yandexKey = it },
                        configured = cloudCredentials.yandexConfigured,
                        label = { Text("API-ключ") },
                    )
                    OutlinedTextField(value = yandexFolder, onValueChange = { yandexFolder = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Folder ID (необязательно)") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = inputFieldColors)
                    Button(
                        onClick = { saveYandex(yandexKey, yandexFolder) },
                        enabled = yandexKey.isNotBlank() || cloudCredentials.yandexConfigured,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Сохранить настройки Yandex") }
                    SettingsSupportingText("Облачное распознавание требует интернет и может тарифицироваться.")
            }
        }
        if (showSection("google_cloud")) item(key = "google_cloud") {
            SettingsSection(
                title = "Google Cloud Speech-to-Text",
                contentSpacing = 12.dp,
            ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (cloudCredentials.googleConfigured) "OAuth-токен сохранён в защищённом хранилище" else "Требуется OAuth access token с доступом Cloud Speech-to-Text", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = theme.colors.textSecondary)
                        CloudSttHelpButton { cloudHelp = CloudSttHelp.GOOGLE }
                    }
                    StoredCredentialField(
                        value = googleToken,
                        onValueChange = { googleToken = it },
                        configured = cloudCredentials.googleConfigured,
                        label = { Text("OAuth access token") },
                    )
                    Button(
                        onClick = { saveGoogle(googleToken) },
                        enabled = googleToken.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Сохранить токен Google") }
                    SettingsSupportingText("Access token имеет ограниченный срок действия; приложение сообщит HTTP 401, когда его нужно обновить.")
            }
        }
        if (showSection("vk_cloud")) item(key = "vk_cloud") {
            SettingsSection(
                title = "VK Cloud Speech",
                contentSpacing = 12.dp,
            ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (cloudCredentials.vkConfigured) "Токен и endpoint сохранены в защищённом хранилище" else "Нужны сервисный токен и HTTPS endpoint Speech API", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = theme.colors.textSecondary)
                        CloudSttHelpButton { cloudHelp = CloudSttHelp.VK }
                    }
                    StoredCredentialField(
                        value = vkToken,
                        onValueChange = { vkToken = it },
                        configured = cloudCredentials.vkConfigured,
                        label = { Text("Сервисный токен") },
                    )
                    OutlinedTextField(value = vkEndpoint, onValueChange = { vkEndpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS-адрес Speech API") }, singleLine = true, shape = RoundedCornerShape(16.dp), supportingText = { Text("Адрес выдаётся в кабинете VK Cloud. Его можно добавить после сохранения токена") }, colors = inputFieldColors)
                    Button(
                        onClick = { saveVk(vkToken, vkEndpoint) },
                        enabled = vkToken.isNotBlank() || vkEndpoint.isNotBlank() || cloudCredentials.vkConfigured,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Сохранить настройки VK") }
            }
        }
        if (showSection(if (gigaam) "Модель GigaAM" else "Модель Whisper.cpp")) item(key = "local-speech-model", contentType = "settings-section") {
            SettingsSection(if (gigaam) "GigaAM" else "Whisper") {
                    if (!gigaam) {
                        SettingsSupportingText("Модели работают без интернета. Более крупная модель обычно точнее, но требует больше времени и памяти.")
                    }
                    ModelCatalogSelector(
                        expanded = whisperExpanded,
                        setExpanded = { whisperExpanded = it; if (it) onWhisperModelsOpened() },
                        dialogTitle = if (gigaam) "Выбор модели GigaAM" else "Выбор модели Whisper",
                        selectedName = whisperModels.selectedName,
                        installed = whisperModels.installed,
                        downloads = whisperDownloads,
                        modelId = WhisperDownload::id,
                        fileName = WhisperDownload::fileName,
                        title = WhisperDownload::title,
                        description = WhisperDownload::description,
                        approximateSize = WhisperDownload::approximateSize,
                        downloadingId = whisperModels.downloadingId,
                        progress = whisperModels.progress,
                        storageConfigured = modelStorage.configured,
                        select = selectWhisper,
                        download = downloadWhisper,
                        downloadAvailable = { model -> model.url.isNotBlank() },
                        importedDescription = if (gigaam) "Импортированная модель GigaAM CTC" else "Импортированная GGML-модель Whisper",
                        importModel = { importWhisperModel.launch("*/*") },
                        importFormat = if (gigaam) "ONNX (.onnx) GigaAM CTC для НеЗабудьКа, со встроенной обработкой аудио и словарём. Энкодер ONNX и RNNT не подходят" else "Модель Whisper в формате GGML (.bin)",
                        deleteModel = { deleteModel(if (gigaam) "gigaam" else "whisper", it) }, allowDelete = canDeleteModels,
                    )
                    if (!gigaam) {
                    SettingsToggleRow(
                        title = "Чистый Whisper",
                        description = "Текст выбранной модели без словарей, подсказок, исправлений и дополнительной пунктуации",
                        checked = settings.whisperRawMode,
                        onCheckedChange = setWhisperRawMode,
                    )
                    SettingsToggleRow(
                        title = "Точный режим",
                        description = if (settings.whisperRawMode) {
                            "Временно отключён режимом «Чистый Whisper»"
                        } else if (settings.whisperAccurateMode) {
                            "Сравнивает варианты распознавания. Требует больше времени"
                        } else {
                            "Включите для более тщательного распознавания"
                        },
                        checked = settings.whisperAccurateMode && !settings.whisperRawMode,
                        onCheckedChange = setWhisperAccurateMode,
                        enabled = !settings.whisperRawMode,
                    )
                    }
                    if (gigaam) SettingsSupportingText("Распознаёт русский текст без подсказок и исправления слов. Пунктуацию можно включить отдельно.")
                    if (gigaam) SettingsToggleRow(
                        title = "Текст во время записи",
                        description = "Предварительный текст появляется по мере речи. Итоговый текст и знаки уточняются после остановки",
                        checked = settings.gigaamLivePreview,
                        onCheckedChange = setGigaamLivePreview,
                    )
                    whisperModels.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    SettingsToggleRow(
                        title = "Хранить модель в памяти",
                        description = if (keepLocalModel) {
                            "Модель остаётся в памяти, пока приложение открыто. При сворачивании освобождается после завершения текущего распознавания"
                        } else {
                            if (gigaam) "Модель освобождается после распознавания" else "На мощном устройстве модель ненадолго остаётся в памяти, затем автоматически выгружается"
                        },
                        checked = keepLocalModel,
                        onCheckedChange = setKeepWhisperModelLoaded,
                    )
                    Box(Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            title = "Потоки обработки",
                            value = if (localThreadCount == AUTOMATIC_WHISPER_THREAD_COUNT) {
                                "Автоматически · $effectiveWhisperThreads"
                            } else {
                                "$effectiveWhisperThreads ${threadCountSuffix(effectiveWhisperThreads)} · ручной режим"
                            },
                            onClick = { whisperThreadsExpanded = true },
                            trailingDescription = "Выбрать количество потоков",
                        )
                        AppDropdownMenu(
                                expanded = whisperThreadsExpanded,
                                onDismissRequest = { whisperThreadsExpanded = false },
                            ) {
                                (listOf(AUTOMATIC_WHISPER_THREAD_COUNT) + manualWhisperThreadOptions).forEach { count ->
                                    AppMenuItem(
                                        text = if (count == AUTOMATIC_WHISPER_THREAD_COUNT) {
                                            "Автоматически ($automaticWhisperThreads)"
                                        } else {
                                            "$count ${threadCountSuffix(count)}"
                                        },
                                        onClick = {
                                            setWhisperThreadCount(count)
                                            whisperThreadsExpanded = false
                                        },
                                    )
                                }
                            }
                    }
                    SettingsSupportingText(
                        "Предел для этого устройства: $maximumWhisperThreads ${threadCountSuffix(maximumWhisperThreads)}.",
                    )
                    SettingsActionRow(
                        title = if (threadBenchmark.running) "Проверка потоков выполняется…" else "Проверить оптимальное количество потоков",
                        description = "Сравнить скорость на последней голосовой записи",
                        onClick = { threadBenchmarkDialog = true },
                        enabled = whisperModels.selectedName != null,
                    )
                    if (whisperDownloads.any { it.id == "small-ru" && it.url.isBlank() }) {
                        SettingsSupportingText(
                            "Small RU можно импортировать файлом ggml-small-ru-v2-q5_1.bin; загрузка появится после публикации модели.",
                        )
                    }
            }
        }
        if (showSection("Распознавание команд")) item(key = "Распознавание команд", contentType = "settings-section") {
            SettingsSection("Распознавание команд") {
                SettingsSupportingText("NLU-классификатор помогает понимать свободные формулировки. Стандартные команды работают и без него.")
                var expanded by remember { mutableStateOf(false) }
                SettingsToggleRow(
                    title = "Расширенное распознавание команд",
                    description = if (classicNluModel.installed) "Понимает свободные формулировки с помощью выбранной NLU-модели"
                        else "Скачайте модель для свободных формулировок. Стандартные команды работают без неё",
                    checked = settings.classicNluEnabled && classicNluModel.installed,
                    onCheckedChange = setClassicNluEnabled, enabled = classicNluModel.installed,
                )
                ModelCatalogSelector(
                    expanded = expanded, setExpanded = { expanded = it },
                    dialogTitle = "Выбор модели NLU",
                    selectedName = classicNluModel.selectedName,
                    installed = classicNluModel.installedModels,
                    downloads = listOf("classic_nlu_v8_int8.onnx"),
                    modelId = { "classic-nlu-v8" }, fileName = { it },
                    title = { "NLU v8" }, description = { "Локальное понимание свободных команд" },
                    approximateSize = { "29 МБ" },
                    downloadingId = if (classicNluModel.downloading) "classic-nlu-v8" else null,
                    progress = classicNluModel.progress, storageConfigured = modelStorage.configured,
                    select = selectClassicNlu, download = { downloadClassicNlu() },
                    downloadAvailable = { classicNluDownloadAvailable },
                    importedDescription = "Импортированная ONNX-модель команд",
                    importModel = { importClassicNluModel.launch("*/*") },
                    importFormat = "ONNX (.onnx), совместимая с NLU приложения",
                    deleteModel = { deleteModel("nlu", it) }, allowDelete = canDeleteModels,
                )

                classicNluModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (showSection("Пунктуация голосового ввода")) item(key = "Пунктуация голосового ввода", contentType = "settings-section") {
            SettingsSection("Пунктуация голосового ввода") {
                SettingsSupportingText("Дополнительная модель расставляет знаки в готовом тексте после распознавания.")
                SettingsToggleRow(
                    title = "Использовать дополнительную обработку",
                    checked = settings.notePunctuationModel == "rupunct_small",
                    description = "Расставлять запятые выбранной моделью",
                    onCheckedChange = { enabled ->
                        if (enabled && !punctuationModel.installed) punctuationExpanded = true
                        else setNotePunctuationModel(if (enabled) "rupunct_small" else "whisper")
                    },
                )
                SettingsToggleRow(
                    title = "Другие знаки в редакторе заметок",
                    description = "Пробовать расставлять остальные знаки после диктовки. Вопросы и границы предложений стоит проверить. Только для редактора",
                    checked = settings.editorFullPunctuation,
                    onCheckedChange = setEditorFullPunctuation,
                    enabled = settings.notePunctuationModel == "rupunct_small",
                )
                ModelCatalogSelector(
                    expanded = punctuationExpanded, setExpanded = { punctuationExpanded = it },
                    dialogTitle = "Выбор модели пунктуации",
                    selectedName = punctuationModel.selectedName,
                    installed = punctuationModel.installedModels,
                    downloads = listOf(ru.nezabudka.app.data.RupunctModel.FILE_NAME), modelId = { "rupunct-small" }, fileName = { it },
                    title = { "RUPunct Small" }, description = { "Расставляет запятые в распознанном тексте" },
                    approximateSize = { "29 МБ" },
                    downloadingId = if (punctuationModel.downloading) "rupunct-small" else null,
                    progress = punctuationModel.progress, storageConfigured = modelStorage.configured,
                    select = selectPunctuationFile, download = { downloadPunctuationModel(); setNotePunctuationModel("rupunct_small") },
                    importedDescription = "ONNX-модель, совместимая с RUPunct",
                    importModel = { importPunctuationModel.launch("*/*") },
                    importFormat = "ONNX (.onnx), совместимая с токенизатором и метками RUPunct",
                    deleteModel = { deleteModel("punctuation", it) }, allowDelete = canDeleteModels,
                    selectorValue = punctuationModel.selectedName?.let { if (it == ru.nezabudka.app.data.RupunctModel.FILE_NAME) "RUPunct Small" else it } ?: "Не выбрана",
                    introduction = {
                        if (punctuationModel.downloading) SettingsActionRow(title = "Отменить загрузку", onClick = cancelPunctuationDownload)
                    },
                )
                if (settings.sttEngine == "whisper" && settings.whisperRawMode) SettingsSupportingText(
                    "Дополнительная пунктуация временно отключена режимом «Чистый Whisper».")
                punctuationModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (showSection("spoken-symbols")) item(key = "spoken-symbols", contentType = "settings-section") {
            SettingsSection("Слова в символы") {
                SettingsToggleRow(title = "Заменять названия знаков", checked = settings.spokenSymbolsEnabled, onCheckedChange = setSpokenSymbolsEnabled,
                    description = "Например, «двоеточие» → «:». Применяется к голосовому вводу после распознавания.")
                SettingsActionRow(title = "Слова и символы", value = "Замен: ${settings.spokenSymbols.size}", onClick = { spokenSymbolsDialog = true })
            }
        }
        if (showSection("github-updates")) item(key = "github-updates", contentType = "settings-section") {
            SettingsSection("Обновления") {
                SettingsSupportingText("Обновления приложения доступны в релизах GitHub.")
                if (GithubAppUpdater.supported) {
                    SettingsToggleRow(title = "Искать обновления при запуске", checked = appUpdateState.searchEnabled,
                        onCheckedChange = setUpdateSearchEnabled,
                        description = "Показывать предложение, когда найдена новая версия")
                    val status = when (appUpdateState.status) {
                        "checking" -> "Проверяем новые версии…"
                        "downloading" -> "Скачивается ${appUpdateState.version.orEmpty()}: ${appUpdateState.percent}%"
                        "ready" -> "Версия ${appUpdateState.version} готова к установке"
                        "available" -> "Доступна версия ${appUpdateState.version}"
                        "none" -> "Более новых версий нет"
                        "error" -> appUpdateState.message ?: "Не удалось проверить обновления"
                        else -> "Нажмите «Проверить обновления», чтобы узнать о новой версии"
                    }
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = theme.colors.input) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(status, style = MaterialTheme.typography.bodyMedium,
                                color = if (appUpdateState.status == "error") MaterialTheme.colorScheme.error else theme.colors.textPrimary)
                            if (appUpdateState.version != null && appUpdateState.changes.isNotBlank()) Text(appUpdateState.changes, style = MaterialTheme.typography.bodySmall)
                            if (appUpdateState.status == "downloading") androidx.compose.material3.LinearProgressIndicator(
                                progress = { appUpdateState.percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    val busy = appUpdateState.status in setOf("checking", "downloading")
                    if (appUpdateState.status == "ready") SettingsActionRow(title = "Установить новую версию", onClick = installUpdate)
                    if (appUpdateState.status == "available") SettingsActionRow(title = "Скачать новую версию",
                        description = "При ручной загрузке можно использовать любую сеть", onClick = downloadUpdate)
                    SettingsActionRow(title = "Проверить обновления", onClick = checkForUpdate, enabled = !busy,
                        description = "Проверка без скачивания APK")
                } else SettingsSupportingText("Обновления доступны в релизной версии приложения")
            }
        }
        if (showSection("О приложении")) item(key = "О приложении", contentType = "settings-section") {
            SettingsSection("О приложении") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1782F2), modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(appIcon.foreground), null, tint = Color.Unspecified, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Text("Версия $appVersion", style = MaterialTheme.typography.bodySmall, color = theme.colors.textSecondary)
                    }
                }
                SettingsSupportingText(stringResource(R.string.app_tagline))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    title = "Сбросить первый запуск",
                    description = "Повторить предложения настройки при следующих событиях",
                    onClick = { resetFirstUseDialog = true },
                    trailingDescription = "Сбросить предложения настройки",
                )
                if (firstUseResetDone) SettingsSupportingText("Готово. При следующем использовании функций предложения появятся снова.")
                SettingsActionRow(
                    title = "Автор",
                    value = "Revenko Nikita",
                    onClick = { uriHandler.openUri("mailto:renikk99@yandex.ru") },
                    trailingDescription = "Написать автору",
                )
            }
        }
    }
    }
    if (iconPickerOpen) AppAlertDialog(
        onDismissRequest = { iconPickerOpen = false },
        title = { Text("Значок приложения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIconOption.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { selectAppIcon(option); iconPickerOpen = false }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1782F2), modifier = Modifier.size(56.dp)) {
                            Icon(painterResource(option.foreground), null, tint = Color.Unspecified, modifier = Modifier.fillMaxSize())
                        }
                        Text(option.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        RadioButton(selected = option == appIcon, onClick = { selectAppIcon(option); iconPickerOpen = false })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { iconPickerOpen = false }) { Text("Закрыть") } },
    )
    if (threadBenchmarkDialog) WhisperThreadBenchmarkDialog(
        gigaam = gigaam,
        state = threadBenchmark,
        canApply = threadBenchmark.modelName == whisperModels.selectedName,
        start = startThreadBenchmark,
        cancel = cancelThreadBenchmark,
        apply = { applyThreadBenchmark(); threadBenchmarkDialog = false },
        dismiss = { cancelThreadBenchmark(); threadBenchmarkDialog = false },
    )
    if (storagePathDialog) AppAlertDialog(
        onDismissRequest = { storagePathDialog = false }, title = { Text("Каталог данных и моделей") },
        text = { Text(modelStorage.location) },
        confirmButton = { TextButton(onClick = { storagePathDialog = false }) { Text("Понятно") } },
    )
    dataTransfer.preview?.let { preview ->
        AppAlertDialog(
            onDismissRequest = cancelDataRestore,
            title = { Text("Восстановить ${preview.title.lowercase()}?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Записей в файле: ${preview.records}" + if (preview.folders > 0) " · Папок: ${preview.folders}" else "")
                Text("Новые записи будут добавлены к текущим. Точные совпадения будут пропущены. Настройки приложения не изменятся.")
            } },
            confirmButton = { TextButton(onClick = confirmDataRestore) { Text("Восстановить") } },
            dismissButton = { TextButton(onClick = cancelDataRestore) { Text("Отмена") } },
        )
    }
    if (themePickerOpen) {
        AppAlertDialog(
            onDismissRequest = { themePickerOpen = false },
            title = { Text("Выбрать тему") },
            text = {
                Column {
                    listOf(
                        "light" to "Светлая",
                        "dark" to "Тёмная",
                        "custom" to "Своя тема",
                    ).forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    setTheme(value)
                                    themePickerOpen = false
                                }
                                .padding(vertical = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.theme == value,
                                onClick = {
                                    setTheme(value)
                                    themePickerOpen = false
                                },
                            )
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themePickerOpen = false }) { Text("Отмена") }
            },
        )
    }
    if (reminderTestDialog) AppAlertDialog(
        onDismissRequest = { reminderTestDialog = false },
        title = { Text("Проверка напоминания") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (settings.reminderAlarmMode) "Выбран режим будильника" else "Выбран режим уведомления")
                SettingsActionRow(title = "Проверить сейчас", onClick = {
                    reminderTestDialog = false; runReminderTest(0)
                })
                SettingsActionRow(title = "Проверить на заблокированном экране",
                    description = "Запуск через 5 секунд. После нажатия заблокируйте телефон",
                    onClick = { reminderTestDialog = false; runReminderTest(ru.nezabudka.app.notifications.ReminderDisplayTest.LOCK_SCREEN_DELAY_SECONDS) })
            }
        },
        confirmButton = { TextButton(onClick = { reminderTestDialog = false }) { Text("Закрыть") } },
        dismissButton = { TextButton(onClick = { cancelReminderTest(); reminderTestDialog = false }) { Text("Отменить тест") } },
    )
    if (soundPickerOpen) {
        val alarmSoundPicker = soundPickerType == RingtoneManager.TYPE_ALARM
        val defaultSoundUri = (
            RingtoneManager.getDefaultUri(soundPickerType)
                ?: Uri.parse(
                    if (alarmSoundPicker) {
                        "content://settings/system/alarm_alert"
                    } else {
                        "content://settings/system/notification_sound"
                    },
                )
            ).toString()
        fun selectSound(uri: String) {
            pendingNotificationSound = uri
            previewPlayer?.release()
            previewPlayer = null
            soundPreviewError = null
            if (uri.isBlank()) return
            val audio = context.getSystemService(AudioManager::class.java)
            val stream = if (alarmSoundPicker) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
            if (audio.getStreamVolume(stream) == 0) {
                soundPreviewError = if (alarmSoundPicker) "Громкость будильника выключена. Увеличьте её в настройках звука телефона"
                    else "Громкость уведомлений выключена. Увеличьте её в настройках звука телефона"
                return
            }
            if (!alarmSoundPicker && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                soundPreviewError = "Включён беззвучный режим или вибрация. Звук уведомлений выключен"
                return
            }
            val player = MediaPlayer()
            previewPlayer = player
            runCatching {
                val selected = Uri.parse(uri)
                val actual = if (RingtoneManager.isDefault(selected))
                    RingtoneManager.getActualDefaultRingtoneUri(context, soundPickerType) ?: selected else selected
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(
                    if (alarmSoundPicker) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                player.setDataSource(context, actual)
                player.setOnPreparedListener { ready ->
                    if (previewPlayer === ready) ready.start()
                }
                player.setOnCompletionListener { finished ->
                    if (previewPlayer === finished) { finished.release(); previewPlayer = null }
                }
                player.setOnErrorListener { failed, _, _ ->
                    if (previewPlayer === failed) {
                        failed.release(); previewPlayer = null
                        soundPreviewError = "Не удалось воспроизвести эту мелодию. Выберите другую"
                    }
                    true
                }
                player.prepareAsync()
            }.onFailure {
                player.release(); previewPlayer = null
                soundPreviewError = "Мелодия недоступна для прослушивания. Выберите другую"
            }
        }
        AppAlertDialog(
            onDismissRequest = {
                previewPlayer?.release(); previewPlayer = null
                soundPickerOpen = false
            },
            title = { Text(if (alarmSoundPicker) "Мелодия будильника" else "Звук напоминания") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = minOf(360f, LocalConfiguration.current.screenHeightDp * 0.45f).dp),
                ) {
                    item {
                        Text(if (alarmSoundPicker) "Прослушивание использует громкость будильника" else "Прослушивание использует громкость уведомлений",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Режим «Не беспокоить» может заглушать звук", style = MaterialTheme.typography.bodySmall)
                        soundPreviewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    item {
                        NotificationSoundRow(
                            title = if (alarmSoundPicker) "Мелодия будильника по умолчанию" else "Звук уведомления по умолчанию",
                            selected = pendingNotificationSound == defaultSoundUri,
                            onClick = { selectSound(defaultSoundUri) },
                        )
                    }
                    if (!alarmSoundPicker) {
                        item {
                            NotificationSoundRow(
                                title = "Без звука",
                                selected = pendingNotificationSound.isBlank(),
                                onClick = { selectSound("") },
                            )
                        }
                    }
                    if (soundOptionsLoading) {
                        item {
                            Text(
                                "Загрузка мелодий…",
                                Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        items(soundOptions, key = NotificationSoundOption::uri) { option ->
                            NotificationSoundRow(
                                title = option.title,
                                selected = pendingNotificationSound == option.uri,
                                onClick = { selectSound(option.uri) },
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        previewPlayer?.release(); previewPlayer = null
                        soundPickerOpen = false
                    },
                ) { Text("Отмена") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        previewPlayer?.release(); previewPlayer = null
                        if (alarmSoundPicker) {
                            setReminderAlarmSound(pendingNotificationSound)
                        } else {
                            setNotificationSound(pendingNotificationSound)
                        }
                        soundPickerOpen = false
                    },
                ) { Text("Готово") }
            },
        )
    }
    timePickerIndex?.let { index ->
        val value = settings.timeOfDaySettings.getOrNull(index)
        if (value == null) {
            timePickerIndex = null
        } else {
            SettingsTimePickerDialog(
                initialTime = runCatching { LocalTime.parse(value.time) }.getOrDefault(LocalTime.of(9, 0)),
                dismiss = { timePickerIndex = null },
                confirm = { time ->
                    setTimeOfDaySettings(settings.timeOfDaySettings.toMutableList().also {
                        it[index] = value.copy(time = time.format(SettingsTimeFormatter))
                    })
                    timePickerIndex = null
                },
            )
        }
    }
    if (resetFirstUseDialog) AppAlertDialog(
        onDismissRequest = { resetFirstUseDialog = false },
        title = { Text("Повторить первые настройки?") },
        text = { Text("Предложения настройки снова появятся при упоминании времени суток, создании папки и напоминания, а также при нераспознанной команде с выключенным NLU. Данные, модели, выбранные настройки и разрешения Android сохранятся.") },
        confirmButton = { TextButton(onClick = {
            resetFirstUseSetup()
            firstUseResetDone = true
            resetFirstUseDialog = false
        }) { Text("Сбросить") } },
        dismissButton = { TextButton(onClick = { resetFirstUseDialog = false }) { Text("Отмена") } },
    )
    if (addTimeOfDayDialog) {
        val parsedTime = runCatching { LocalTime.parse(newTimeValue) }.getOrNull()
        val normalizedPhrase = newTimePhrase.trim().lowercase().replace('ё', 'е')
        val duplicate = settings.timeOfDaySettings.any { it.phrase.lowercase().replace('ё', 'е') == normalizedPhrase }
        AppAlertDialog(
            onDismissRequest = { addTimeOfDayDialog = false },
            title = { Text("Новое время суток") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSupportingText("Укажите, какое время использовать, когда вы произносите эту фразу.")
                    OutlinedTextField(
                        value = newTimePhrase,
                        onValueChange = { newTimePhrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Слово или фраза") },
                        placeholder = { Text("например, после работы") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = inputFieldColors,
                    )
                    OutlinedTextField(
                        value = newTimeValue,
                        onValueChange = { newTimeValue = it.take(5) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Время") },
                        placeholder = { Text("18:00") },
                        supportingText = { Text(if (parsedTime == null) "Введите время в формате ЧЧ:ММ" else "Например: после работы → 18:00") },
                        singleLine = true,
                        isError = parsedTime == null,
                        shape = RoundedCornerShape(16.dp),
                        colors = inputFieldColors,
                    )
                    if (duplicate) Text("Такое сопоставление уже существует", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        setTimeOfDaySettings(settings.timeOfDaySettings + TimeOfDaySetting(newTimePhrase.trim(), newTimeValue))
                        newTimePhrase = ""
                        newTimeValue = "18:00"
                        addTimeOfDayDialog = false
                    },
                    enabled = normalizedPhrase.isNotBlank() && parsedTime != null && !duplicate,
                ) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { addTimeOfDayDialog = false }) { Text("Отмена") } },
        )
    }
    cloudHelp?.let { help ->
        CloudSttHelpDialog(help = help, dismiss = { cloudHelp = null })
    }
    if (customThemeEditor) {
        CustomThemeEditorDialog(
            initial = settings.customTheme,
            dismiss = { customThemeEditor = false },
            save = { theme ->
                setCustomTheme(theme)
                setTheme("custom")
                customThemeEditor = false
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    titleAction: (@Composable () -> Unit)? = null,
    contentSpacing: androidx.compose.ui.unit.Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = AppTheme.tokens
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = theme.colors.textPrimary,
            )
            titleAction?.invoke()
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(theme.shapes.card),
            colors = CardDefaults.cardColors(
                containerColor = theme.colors.card,
                contentColor = theme.colors.textPrimary,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(4.dp)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    val theme = AppTheme.tokens
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) theme.colors.textPrimary else theme.colors.textSecondary,
            )
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.colors.textSecondary,
                )
            }
        }
        Box(
            Modifier.size(SettingsTrailingSlotSize).offset(x = SettingsTrailingSlotOffset),
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    description: String? = null,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = Icons.Default.ChevronRight,
    trailingDescription: String? = null,
    isError: Boolean = false,
) {
    val theme = AppTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) theme.colors.textPrimary else theme.colors.textSecondary,
            )
            (value ?: description)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else theme.colors.textSecondary,
                )
            }
        }
        trailingIcon?.let {
            Box(
                Modifier.size(SettingsTrailingSlotSize).offset(x = SettingsTrailingSlotOffset),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    it,
                    trailingDescription,
                    tint = theme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String,
    valueColor: Color = AppTheme.tokens.colors.textSecondary,
) {
    val theme = AppTheme.tokens
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = theme.colors.textPrimary,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

@Composable
private fun SettingsSupportingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.tokens.colors.textSecondary,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

private data class NotificationSoundOption(
    val title: String,
    val uri: String,
)

private fun loadNotificationSoundOptions(context: Context, ringtoneType: Int): List<NotificationSoundOption> {
    return runCatching {
        val manager = RingtoneManager(context).apply {
            setType(ringtoneType)
        }
        manager.cursor.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val uri = manager.getRingtoneUri(cursor.position)?.toString() ?: continue
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                        ?.takeIf(String::isNotBlank)
                        ?: "Мелодия"
                    add(NotificationSoundOption(title = title, uri = uri))
                }
            }.distinctBy(NotificationSoundOption::uri)
        }
    }.getOrDefault(emptyList())
}

@Composable
private fun NotificationSoundRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, Modifier.weight(1f).padding(start = 8.dp))
    }
}

internal fun noteTrashRetentionLabel(days: Int): String = when (days) {
    0 -> "Никогда"
    1 -> "Через 1 день"
    else -> "Через $days дней"
}

internal fun threadCountSuffix(count: Int): String = when (count) {
    1 -> "поток"
    in 2..4 -> "потока"
    else -> "потоков"
}

private enum class CloudSttHelp(
    val title: String,
    val description: String,
    val steps: List<String>,
    val consoleUrl: String,
    val consoleLabel: String,
) {
    YANDEX(
        title = "Где взять API-ключ Yandex",
        description = "Ключ создаётся для сервисного аккаунта в Yandex Cloud.",
        steps = listOf(
            "Перейдите в консоль Yandex Cloud и создайте или выберите каталог.",
            "Создайте в этом каталоге сервисный аккаунт.",
            "Назначьте сервисному аккаунту роль ai.speechkit-stt.user.",
            "Откройте сервисный аккаунт и создайте для него API-ключ с доступом к SpeechKit STT.",
            "Скопируйте ключ сразу после создания. Folder ID можно скопировать со страницы каталога.",
        ),
        consoleUrl = "https://console.yandex.cloud/",
        consoleLabel = "Открыть Yandex Cloud",
    ),
    GOOGLE(
        title = "Где взять токен Google",
        description = "Приложению нужен временный OAuth access token, а не JSON-файл сервисного аккаунта.",
        steps = listOf(
            "Перейдите в Google Cloud Console и создайте или выберите проект.",
            "Подключите биллинг и включите Cloud Speech-to-Text API.",
            "Установите Google Cloud CLI, выполните gcloud init и войдите в аккаунт.",
            "Выполните команду gcloud auth print-access-token.",
            "Скопируйте выведенный токен. Обычно он действует около одного часа, после чего его нужно получить заново.",
        ),
        consoleUrl = "https://console.cloud.google.com/apis/library/speech.googleapis.com",
        consoleLabel = "Открыть Google Cloud",
    ),
    VK(
        title = "Где взять данные VK Cloud",
        description = "Для подключения нужны сервисный токен и HTTPS endpoint распознавания вашего региона.",
        steps = listOf(
            "Перейдите в личный кабинет VK Cloud и создайте или выберите проект.",
            "Подключите Cloud Voice / Voice ASR. Если сервиса нет в кабинете, обратитесь в поддержку VK Cloud.",
            "Откройте раздел API подключённого сервиса.",
            "Скопируйте сервисный токен и HTTPS endpoint распознавания для выбранного региона в соответствующие поля приложения.",
        ),
        consoleUrl = "https://cloud.vk.com/",
        consoleLabel = "Открыть VK Cloud",
    ),
}

@Composable
private fun CloudSttHelpButton(
    showHelp: () -> Unit,
) {
    OutlinedIconButton(
        onClick = showHelp,
        modifier = Modifier.size(36.dp),
    ) {
        Text("?", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StoredCredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    configured: Boolean,
    label: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val theme = AppTheme.tokens
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = label,
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        shape = RoundedCornerShape(16.dp),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    if (visible) "Скрыть ключ" else "Показать ключ")
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = theme.colors.input,
            unfocusedContainerColor = theme.colors.input,
        ),
    )
}

@Composable
private fun CloudSttHelpDialog(
    help: CloudSttHelp,
    dismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AppAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(help.title) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(help.description, style = MaterialTheme.typography.bodyMedium)
                }
                itemsIndexed(help.steps) { index, step ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("${index + 1}.", style = MaterialTheme.typography.titleSmall)
                        Text(step, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { uriHandler.openUri(help.consoleUrl) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    Text("Консоль сервиса", Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = dismiss) { Text("Понятно") }
            }
        },
    )
}

@Composable
private fun SttCatalogSelector(
    expanded: Boolean,
    setExpanded: (Boolean) -> Unit,
    engines: List<SpeechToTextEngine>,
    selectedId: String,
    select: (String) -> Unit,
) {
    val selected = engines.firstOrNull { it.id == selectedId }
    SettingsActionRow(
        title = "Способ распознавания",
        value = selected?.displayName ?: selectedId,
        onClick = { setExpanded(true) },
        trailingDescription = "Открыть выбор STT",
    )
    if (expanded) {
        AppAlertDialog(
            onDismissRequest = { setExpanded(false) },
            title = { Text("Выбор способа распознавания речи") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                ) {
                    itemsIndexed(engines, key = { _, engine -> engine.id }) { index, engine ->
                        val android = engine as? AndroidSpeechRecognizerEngine
                        val available = android?.isAvailable() ?: true
                        ModelCatalogRow(
                            title = engine.displayName,
                            description = sttEngineDescription(engine),
                            size = null,
                            installed = false,
                            selected = engine.id == selectedId,
                            downloading = false,
                            progress = null,
                            enabled = available,
                            onClick = {
                                select(engine.id)
                                setExpanded(false)
                            },
                        )
                        if (index != engines.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { setExpanded(false) }) { Text("Отмена") }
            },
        )
    }
}

private fun sttEngineDescription(
    engine: SpeechToTextEngine,
): String = when (engine) {
    is AndroidSpeechRecognizerEngine -> engine.availabilityDescription()
    else -> when (engine.id) {
        "gigaam" -> "Локальное распознавание русской речи на устройстве."
        "whisper" -> "Локальное распознавание на устройстве с выбранной моделью Whisper."
        "yandex" -> "Облачное распознавание Yandex SpeechKit. Требуются интернет и API-ключ."
        "google_cloud" -> "Облачное распознавание Google. Требуются интернет и OAuth-токен."
        "vk_cloud" -> "Облачное распознавание VK Cloud. Требуются интернет, токен и endpoint."
        else -> "Преобразует записанную речь в текст."
    }
}

@Composable
internal fun WhisperModelsMenu(
    models: WhisperModelsState,
    downloads: List<WhisperDownload>,
    storageConfigured: Boolean,
    chooseStorage: () -> Unit,
    select: (String) -> Unit,
    download: (WhisperDownload) -> Unit,
    dismiss: () -> Unit,
    import: (Uri) -> Unit,
    delete: (String) -> Unit,
    gigaam: Boolean = false,
) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(import) }
    ModelCatalogSelector(
        expanded = true,
        setExpanded = { if (!it) dismiss() },
        dialogTitle = if (gigaam) "Скачать модель GigaAM" else "Скачать модель Whisper",
        selectedName = models.selectedName,
        installed = models.installed,
        downloads = downloads,
        modelId = WhisperDownload::id,
        fileName = WhisperDownload::fileName,
        title = WhisperDownload::title,
        description = WhisperDownload::description,
        approximateSize = WhisperDownload::approximateSize,
        downloadingId = models.downloadingId,
        progress = models.progress,
        storageConfigured = storageConfigured,
        select = select,
        download = download,
        deleteModel = delete,
        downloadAvailable = { it.url.isNotBlank() },
        importedDescription = if (gigaam) "Импортированная модель GigaAM CTC" else "Импортированная GGML-модель Whisper",
        showSelectorRow = false,
        importModel = { importLauncher.launch("*/*") },
        importFormat = if (gigaam) "ONNX (.onnx) GigaAM CTC для НеЗабудьКа, со встроенной обработкой аудио и словарём. Энкодер ONNX и RNNT не подходят" else "Модель Whisper в формате GGML (.bin)",
        introduction = {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Для голосового ввода нужна модель. Выберите вариант ниже, чтобы скачать его.")
                if (!storageConfigured) {
                    Text("Сначала выберите папку для хранения моделей.")
                    Button(onClick = chooseStorage) { Text("Выбрать папку") }
                }
                models.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun <T> ModelCatalogSelector(
    expanded: Boolean,
    setExpanded: (Boolean) -> Unit,
    dialogTitle: String,
    selectedName: String?,
    installed: List<String>,
    downloads: List<T>,
    modelId: (T) -> String,
    fileName: (T) -> String,
    title: (T) -> String,
    description: (T) -> String,
    approximateSize: (T) -> String,
    downloadingId: String?,
    progress: Float?,
    storageConfigured: Boolean,
    select: (String) -> Unit,
    download: (T) -> Unit,
    downloadAvailable: (T) -> Boolean = { true },
    importedDescription: String,
    showSelectorRow: Boolean = true,
    introduction: @Composable () -> Unit = {},
    importModel: (() -> Unit)? = null,
    importFormat: String = "Модель ONNX (.onnx)",
    selectorValue: String? = selectedName,
    excludeInstalled: Set<String> = emptySet(),
    deleteModel: ((String) -> Unit)? = null,
    allowDelete: Boolean = true,
) {
    var deletionCandidate by remember { mutableStateOf<String?>(null) }
    val knownNames = downloads.map(fileName).toSet()
    val unknownInstalled = installed.filterNot { it in knownNames || it in excludeInstalled }
    if (showSelectorRow) SettingsActionRow(
        title = "Модель",
        value = selectorValue ?: "не выбрана",
        onClick = { setExpanded(true) },
        trailingDescription = "Открыть выбор модели",
    )
    if (expanded) {
        AppAlertDialog(
            onDismissRequest = { setExpanded(false) },
            title = { Text(dialogTitle) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                ) {
                    item(key = "model-introduction") { introduction() }
                    if (importModel != null) item(key = "model-import") {
                        SettingsActionRow(title = "Выбрать свой файл", description = importFormat,
                            enabled = storageConfigured && downloadingId == null,
                            trailingIcon = Icons.Default.FileOpen,
                            onClick = { setExpanded(false); importModel() })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    itemsIndexed(downloads, key = { _, model -> modelId(model) }) { index, model ->
                    val id = modelId(model)
                    val name = fileName(model)
                    val isInstalled = name in installed
                    val isSelected = name == selectedName
                    val isDownloading = id == downloadingId
                    ModelCatalogRow(
                        title = title(model),
                        description = description(model),
                        size = approximateSize(model),
                        installed = isInstalled,
                        selected = isSelected,
                        downloading = isDownloading,
                        progress = if (isDownloading) progress else null,
                        enabled = isInstalled || (
                            downloadAvailable(model) && storageConfigured && downloadingId == null
                        ),
                        onClick = {
                            if (isInstalled) {
                                select(name)
                                setExpanded(false)
                            } else download(model)
                        },
                        onDelete = if (isInstalled && deleteModel != null && allowDelete && downloadingId == null)
                            ({ deletionCandidate = name }) else null,
                    )
                    if (index != downloads.lastIndex || unknownInstalled.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    }
                    items(
                        items = unknownInstalled,
                        key = { "imported:$it" },
                    ) { name ->
                        ModelCatalogRow(
                            title = name,
                            description = importedDescription,
                            size = null,
                            installed = true,
                            selected = name == selectedName,
                            downloading = false,
                            progress = null,
                            enabled = true,
                            onClick = { select(name); setExpanded(false) },
                            onDelete = if (deleteModel != null && allowDelete && downloadingId == null)
                                ({ deletionCandidate = name }) else null,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { setExpanded(false) }) { Text("Отмена") }
            },
        )
    }
    deletionCandidate?.let { name ->
        AppAlertDialog(
            onDismissRequest = { deletionCandidate = null },
            title = { Text("Удалить модель?") },
            text = { Text("$name\n\nМодель будет удалена из каталога приложения. Её можно скачать или импортировать снова. Если удалена выбранная модель, будет выбрана другая установленная.") },
            confirmButton = { TextButton(enabled = allowDelete && downloadingId == null, onClick = {
                deleteModel?.invoke(name); deletionCandidate = null
            }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletionCandidate = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ModelCatalogRow(
    title: String,
    description: String,
    size: String?,
    installed: Boolean,
    selected: Boolean,
    downloading: Boolean,
    progress: Float?,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val theme = AppTheme.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(theme.shapes.card))
            .then(if (selected) Modifier.border(1.dp, theme.colors.accent, RoundedCornerShape(theme.shapes.card)) else Modifier)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            onDelete?.let { remove ->
                IconButton(onClick = remove) { Icon(Icons.Default.DeleteOutline, "Удалить модель $title", tint = theme.colors.danger) }
            }

            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = theme.colors.textPrimary,
            )
            size?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = theme.colors.textSecondary) }
            if (installed) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(theme.colors.accent))
            }
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = theme.colors.textSecondary)
        if (downloading) {
            LinearProgressIndicator(
                progress = { progress ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                progress?.let { "Загрузка в фоне: ${(it * 100).roundToInt()}%" } ?: "Загрузка в фоне…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatCacheSize(bytes: Long): String {
    val mebibytes = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mebibytes >= 1.0) {
        String.format(Locale.forLanguageTag("ru"), "%.1f МБ", mebibytes)
    } else {
        "${(bytes + 1023L) / 1024L} КБ"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTimePickerDialog(
    initialTime: LocalTime,
    dismiss: () -> Unit,
    confirm: (LocalTime) -> Unit,
) {
    AppTimePickerDialog(initialTime, dismiss, confirm)
}

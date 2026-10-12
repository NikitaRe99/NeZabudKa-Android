package ru.nezabudka.app.ui.screens

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.AudioAttributes
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import ru.nezabudka.app.data.AppIconOption
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
import ru.nezabudka.app.speech.WhisperCppSttEngine
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
    whisperRuntime: WhisperCppSttEngine.RuntimeState,
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
    setLocalOnly: (Boolean) -> Unit,
    setClassicNluEnabled: (Boolean) -> Unit,
    setExternalDataBackupEnabled: (Boolean) -> Unit,
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
    setNotePunctuationModel: (String) -> Unit,
    punctuationModel: ru.nezabudka.app.speech.RupunctModelState,
    downloadPunctuationModel: () -> Unit,
    cancelPunctuationDownload: () -> Unit,
    requestedSearch: String? = null,
    consumeRequestedSearch: () -> Unit = {},
    appIcon: AppIconOption = AppIconOption.CHECK,
    selectAppIcon: (AppIconOption) -> Unit = {},
) {
    var selectedCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(requestedSearch) {
        requestedSearch?.let { selectedCategory = SettingsCategory.SPEECH; searchQuery = it; consumeRequestedSearch() }
    }
    val entries = remember(settings.sttEngine, settings.localOnly, settings.reminderAlarmMode) {
        availableSettingsEntries(settings.sttEngine, settings.localOnly, settings.reminderAlarmMode)
    }
    val matchingEntries = remember(entries, selectedCategory, searchQuery) {
        entries.filter { if (searchQuery.isNotBlank()) it.matches(searchQuery) else it.category == selectedCategory }
    }
    val matchingKeys = remember(matchingEntries) { matchingEntries.map { it.key }.toSet() }
    val showSection: (String) -> Boolean = { it in matchingKeys }
    BackHandler(selectedCategory != null || searchQuery.isNotBlank()) {
        if (searchQuery.isNotBlank()) searchQuery = "" else selectedCategory = null
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
    var folderInterfaceExpanded by remember { mutableStateOf(false) }
    var whisperExpanded by remember { mutableStateOf(false) }
    var whisperThreadsExpanded by remember { mutableStateOf(false) }
    var threadBenchmarkDialog by remember { mutableStateOf(false) }
    var yandexKey by remember { mutableStateOf("") }
    var yandexFolder by remember(cloudCredentials.yandexFolderId) { mutableStateOf(cloudCredentials.yandexFolderId) }
    var googleToken by remember { mutableStateOf("") }
    var vkToken by remember { mutableStateOf("") }
    var vkEndpoint by remember(cloudCredentials.vkEndpoint) { mutableStateOf(cloudCredentials.vkEndpoint) }
    var previewRingtone by remember { mutableStateOf<Ringtone?>(null) }
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
    val importWhisperModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(importWhisper) }
    val importClassicNluModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(importClassicNlu) }
    val selectModelsRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            selectModelsDirectory(it)
        }
    }
    val settingsListState = rememberLazyListState()
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
    val cloudEngineIds = remember { setOf("yandex", "google_cloud", "vk_cloud") }
    val visibleEngines = remember(engines, settings.localOnly) {
        if (settings.localOnly) engines.filterNot { it.id in cloudEngineIds } else engines
    }
    val selectedEngine = visibleEngines.firstOrNull { it.id == settings.sttEngine }
    val androidEngine = engines.filterIsInstance<AndroidSpeechRecognizerEngine>().firstOrNull()
    val effectiveWhisperThreads = effectiveWhisperThreadCount(
        configured = settings.whisperThreadCount,
        cpuCores = deviceCapabilities.cpuCores,
        lowRamDevice = deviceCapabilities.lowRamDevice,
        memoryClassMb = deviceCapabilities.memoryClassMb,
    )
    val maximumWhisperThreads = maximumWhisperThreadCount(
        cpuCores = deviceCapabilities.cpuCores,
    )
    val automaticWhisperThreads = effectiveWhisperThreadCount(
        configured = AUTOMATIC_WHISPER_THREAD_COUNT,
        cpuCores = deviceCapabilities.cpuCores,
        lowRamDevice = deviceCapabilities.lowRamDevice,
        memoryClassMb = deviceCapabilities.memoryClassMb,
    )
    val manualWhisperThreadOptions = 1..maximumWhisperThreads
    val builtInTimePhrases = remember {
        DEFAULT_TIME_OF_DAY_SETTINGS.mapTo(mutableSetOf()) { it.phrase.lowercase().replace('ё', 'е') }
    }
    DisposableEffect(Unit) { onDispose { previewRingtone?.stop() } }
    LaunchedEffect(soundPickerOpen, soundPickerType) {
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
    LaunchedEffect(selectedCategory, searchQuery) { settingsListState.scrollToItem(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = AppLayout.horizontalPadding)) {
        Row(Modifier.fillMaxWidth().padding(top = AppLayout.contentTopPadding, bottom = AppLayout.sectionSpacing), verticalAlignment = Alignment.CenterVertically) {
            if (selectedCategory != null || searchQuery.isNotBlank()) IconButton(onClick = {
                searchQuery = ""; selectedCategory = null
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Все настройки") }
        AppSearchField(
            value = searchQuery, onValueChange = { searchQuery = it },
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
            items(SettingsCategory.entries, key = { it.name }, contentType = { "category" }) { category ->
                Card(onClick = { selectedCategory = category }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(theme.shapes.card),
                    colors = CardDefaults.cardColors(containerColor = theme.colors.card)) {
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
        if (showSection("Данные и хранилище")) item(key = "Данные и хранилище", contentType = "settings-section") {
            SettingsSection("Данные и хранилище") {
                Text(
                    if (modelStorage.configured) "Каталог: ${modelStorage.location}" else modelStorage.location,
                    color = theme.colors.textSecondary,
                )
                SettingsSupportingText(
                    "В выбранном каталоге хранятся модели Whisper и NLU, а также резервная копия данных.",
                )
                val cache = modelStorage.cache
                Text(
                    "Временные файлы: ${formatCacheSize(cache.totalBytes)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.colors.textSecondary,
                )
                SettingsSupportingText(
                    "Native-копии: ${formatCacheSize(cache.nativeCopiesBytes)} · " +
                        "загрузки: ${formatCacheSize(cache.incompleteDownloadsBytes)} · " +
                        "аудио: ${formatCacheSize(cache.temporaryAudioBytes)}",
                )
                SettingsActionRow(
                    title = if (modelStorage.configured) "Изменить каталог" else "Выбрать каталог пользователя",
                    onClick = { selectModelsRoot.launch(null) },
                    trailingDescription = "Открыть выбор каталога",
                )
                SettingsActionRow(
                    title = "Очистить кэш моделей",
                    description = "Удалить временные native-копии и незавершённые загрузки",
                    onClick = {
                        clearModelCache { removedBytes ->
                            modelCacheMessage = when {
                                removedBytes == null -> "Не удалось очистить кэш моделей"
                                removedBytes == 0L -> "Кэш моделей уже пуст"
                                else -> "Освобождено ${formatCacheSize(removedBytes)}"
                            }
                        }
                    },
                    enabled = whisperModels.downloadingId == null,
                    trailingIcon = Icons.Default.DeleteOutline,
                    trailingDescription = "Очистить",
                )
                SettingsSupportingText(
                    "Удаляются только временные native-копии и незавершённые загрузки, если загрузка сейчас не выполняется. Исходные модели и данные сохраняются.",
                )
                modelCacheMessage?.let { message ->
                    Text(
                        message,
                        color = if (message.startsWith("Не удалось")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            theme.colors.success
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SettingsToggleRow(
                    title = "Сохранять данные и настройки",
                    description = "Заметки, напоминания и настройки сохраняются в data. API-ключи и токены в резервную копию не входят.",
                    checked = settings.externalDataBackupEnabled,
                    onCheckedChange = setExternalDataBackupEnabled,
                    enabled = modelStorage.configured,
                )
            }
        }
        if (showSection("Интерфейс")) item(key = "Интерфейс", contentType = "settings-section") {
            SettingsSection("Интерфейс") {
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
        if (showSection("interface-behavior")) item(key = "interface-behavior", contentType = "settings-section") {
            SettingsSection("Поведение интерфейса") {
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
                    SettingsToggleRow(
                        title = "Начальные подсказки",
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
                    SettingsToggleRow(
                        title = "Использовать приложение только локально",
                        description = "Облачные функции и их настройки будут скрыты",
                        checked = settings.localOnly,
                        onCheckedChange = setLocalOnly,
                    )
            }
        }
        if (showSection("folder-interface")) item(key = "folder-interface", contentType = "settings-section") {
            SettingsSection("Интерфейс папок") {
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
        if (showSection("Уведомления")) item(key = "Уведомления", contentType = "settings-section") {
            SettingsSection("Уведомления") {
                    SettingsToggleRow(
                        title = "Пробуждать экран для напоминаний",
                        description = "Включает экран при срабатывании, но не обходит PIN-код, пароль или биометрию",
                        checked = settings.notificationWakeScreen,
                        onCheckedChange = setNotificationWakeScreen,
                    )
                    SettingsSupportingText(
                        "Для экрана блокировки разрешите показ уведомлений и их содержимого. На vivo это две отдельные настройки канала.",
                    )
                    SettingsActionRow(
                        title = "Уведомления на экране блокировки",
                        onClick = openAlarmNotificationSettings,
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        trailingDescription = "Открыть системные настройки",
                    )
                    SettingsActionRow(
                        title = "Звук обычного напоминания",
                        value = soundTitle,
                        onClick = {
                            previewRingtone?.stop()
                            soundPickerType = RingtoneManager.TYPE_NOTIFICATION
                            soundOptions = emptyList()
                            pendingNotificationSound = settings.notificationSound
                            soundPickerOpen = true
                        },
                        trailingDescription = "Выбрать звук обычного напоминания",
                    )
                    SettingsSupportingText("Громкость берётся из системного уровня «Уведомления».")
            }
        }
        if (showSection("alarm-mode")) item(key = "alarm-mode") {
            SettingsSection("Будильник") {
                    SettingsToggleRow(
                        title = "Режим будильника",
                        description = "Использовать мелодию будильника и повторять сигнал с заданными перерывами",
                        checked = settings.reminderAlarmMode,
                        onCheckedChange = setReminderAlarmMode,
                    )
                    if (
                        settings.reminderAlarmMode &&
                        settings.reminderAlarmPresentation == ALARM_PRESENTATION_SCREEN &&
                        !fullScreenAlarmsAllowed
                    ) {
                        Text(
                            "Полноэкранный будильник запрещён системой — на заблокированном экране останется только уведомление.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        SettingsActionRow(
                            title = "Разрешить полноэкранный будильник",
                            onClick = openFullScreenAlarmSettings,
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            trailingDescription = "Открыть системные настройки",
                        )
                    }
                    if (
                        settings.reminderAlarmMode &&
                        settings.reminderAlarmPresentation == ALARM_PRESENTATION_SCREEN &&
                        !overlayWindowsAllowed
                    ) {
                        Text(
                            "Автоматическое компактное окно запрещено системой — при разблокированном экране останется уведомление.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        SettingsActionRow(
                            title = "Разрешить поверх других приложений",
                            onClick = openOverlaySettings,
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            trailingDescription = "Открыть системные настройки",
                        )
                    }
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
                        if (settings.reminderAlarmPresentation == ALARM_PRESENTATION_NOTIFICATION) {
                            SettingsSupportingText("Будильник будет показан через тот же канал уведомлений.")
                        } else {
                            alarmVendorSettingsTitle?.let { title ->
                                SettingsSupportingText(
                                    "Для отдельного окна включите здесь «Отображать на заблок. экране» и «Всплывающие окна в фоновом режиме».",
                                )
                                SettingsActionRow(
                                    title = title,
                                    onClick = openAlarmVendorSettings,
                                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                                    trailingDescription = "Открыть системные настройки",
                                )
                            }
                        }
                    }
            }
        }
        if (showSection("alarm-sound")) item(key = "alarm-sound") {
            SettingsSection("Мелодия будильника") {
                    if (settings.reminderAlarmMode) {
                        SettingsActionRow(
                            title = "Мелодия будильника",
                            value = alarmSoundTitle,
                            onClick = {
                                previewRingtone?.stop()
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
                        SettingsSupportingText("Громкость берётся из системного уровня «Будильник».")
                    }
            }
        }
        if (showSection("alarm-timing")) item(key = "alarm-timing") {
            SettingsSection("Повторы будильника") {
                    if (settings.reminderAlarmMode) {
                        Box(Modifier.fillMaxWidth()) {
                            SettingsActionRow(
                                title = "Время показа и сигнала",
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
                                title = "Количество сигналов",
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
            SettingsSection("Разрешения и фоновая работа") {
                    SettingsActionRow(
                        title = "Плавающие окна",
                        description = if (overlayWindowsAllowed) "Разрешено показывать окно будильника поверх приложений" else "Разрешить окно будильника поверх других приложений",
                        onClick = openOverlaySettings,
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        trailingDescription = "Открыть разрешение плавающих окон",
                    )
                    alarmVendorSettingsTitle?.let {
                        SettingsActionRow(
                            title = "Всплывающие окна в фоновом режиме",
                            description = "Проверьте также показ на заблокированном экране в разрешениях телефона",
                            onClick = openAlarmVendorSettings,
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            trailingDescription = "Открыть разрешения телефона",
                        )
                    }
                    SettingsToggleRow(
                        title = "Вибрация",
                        description = "Использовать вибрацию для напоминаний и будильников",
                        checked = settings.notificationVibrationEnabled,
                        onCheckedChange = setNotificationVibrationEnabled,
                    )
                    if (!notificationsEnabled) {
                        Text("Уведомления отключены системой", color = MaterialTheme.colorScheme.error)
                        SettingsActionRow(
                            title = "Разрешить уведомления",
                            onClick = openNotificationSettings,
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            trailingDescription = "Открыть системные настройки",
                        )
                    }
                    if (!exactAlarmsAllowed) {
                        Text("Нет доступа к точным будильникам — срабатывание может задержаться", color = MaterialTheme.colorScheme.error)
                        SettingsActionRow(
                            title = "Разрешить точные напоминания",
                            onClick = openExactAlarmSettings,
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            trailingDescription = "Открыть системные настройки",
                        )
                    }
                    SettingsSupportingText(
                        if (backgroundRunAllowed) {
                            "Системное ограничение батареи для приложения снято."
                        } else {
                            "Если напоминания задерживаются после закрытия приложения, откройте батарею приложения и разрешите фоновую работу."
                        },
                    )
                    SettingsActionRow(
                        title = backgroundSettingsTitle,
                        onClick = openBackgroundRunSettings,
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        trailingDescription = "Открыть системные настройки приложения",
                    )
                    autoStartSettingsTitle?.let { title ->
                            SettingsSupportingText(
                                "Оболочка устройства может потребовать автозапуск, чтобы напоминания приходили до открытия приложения.",
                            )
                            SettingsActionRow(
                                title = title,
                                onClick = openAutoStartSettings,
                                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                                trailingDescription = "Открыть системные настройки",
                            )
                    }
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
        if (showSection("Корзина заметок")) item(key = "Корзина заметок", contentType = "settings-section") {
            SettingsSection("Корзина заметок") {
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
        if (showSection("Распознавание речи")) item(key = "Распознавание речи", contentType = "settings-section") {
            SettingsSection("Распознавание речи") {
                    SttCatalogSelector(
                        expanded = sttExpanded,
                        setExpanded = { sttExpanded = it },
                        engines = visibleEngines,
                        selectedId = settings.sttEngine,
                        localOnly = settings.localOnly,
                        select = { engineId ->
                            selectSttEngine(engineId)
                        },
                    )
                    if (settings.sttEngine == "android") {
                        SettingsSupportingText(
                            androidEngine?.availabilityDescription(settings.localOnly)
                                ?: "Android SpeechRecognizer отсутствует",
                            color = if (androidEngine?.isAvailable(settings.localOnly) == true)
                                theme.colors.textSecondary else MaterialTheme.colorScheme.error,
                        )
                    }
                    SettingsToggleRow(
                        title = "Предобработка аудио",
                        description = if (settings.sttEngine == "android") {
                            "Android SpeechRecognizer обрабатывает звук системным сервисом напрямую"
                        } else {
                            "Шумоподавление, удаление DC-смещения и нормализация перед STT"
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
                titleAction = { CloudSttHelpButton { cloudHelp = CloudSttHelp.YANDEX } },
            ) {
                    SettingsSupportingText(if (cloudCredentials.yandexConfigured) "API-ключ сохранён в защищённом хранилище" else "Требуется API-ключ сервисного аккаунта")
                    StoredCredentialField(
                        value = yandexKey,
                        onValueChange = { yandexKey = it },
                        configured = cloudCredentials.yandexConfigured,
                        label = { Text(if (cloudCredentials.yandexConfigured) "Новый API-ключ (необязательно)" else "API-ключ") },
                    )
                    OutlinedTextField(value = yandexFolder, onValueChange = { yandexFolder = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Folder ID (необязательно для сервисного аккаунта)") }, singleLine = true, colors = inputFieldColors)
                    Button(
                        onClick = { saveYandex(yandexKey, yandexFolder); yandexKey = "" },
                        enabled = yandexKey.isNotBlank() || cloudCredentials.yandexConfigured,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(theme.shapes.noteCard),
                    ) { Text("Сохранить настройки Yandex") }
                    SettingsSupportingText("Облачное распознавание требует интернет и может тарифицироваться.")
            }
        }
        if (showSection("google_cloud")) item(key = "google_cloud") {
            SettingsSection(
                title = "Google Cloud Speech-to-Text",
                titleAction = { CloudSttHelpButton { cloudHelp = CloudSttHelp.GOOGLE } },
            ) {
                    SettingsSupportingText(if (cloudCredentials.googleConfigured) "OAuth-токен сохранён в защищённом хранилище" else "Требуется OAuth access token с доступом Cloud Speech-to-Text")
                    StoredCredentialField(
                        value = googleToken,
                        onValueChange = { googleToken = it },
                        configured = cloudCredentials.googleConfigured,
                        label = { Text(if (cloudCredentials.googleConfigured) "Новый OAuth access token" else "OAuth access token") },
                    )
                    Button(
                        onClick = { saveGoogle(googleToken); googleToken = "" },
                        enabled = googleToken.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(theme.shapes.noteCard),
                    ) { Text("Сохранить токен Google") }
                    SettingsSupportingText("Access token имеет ограниченный срок действия; приложение сообщит HTTP 401, когда его нужно обновить.")
            }
        }
        if (showSection("vk_cloud")) item(key = "vk_cloud") {
            SettingsSection(
                title = "VK Cloud Speech",
                titleAction = { CloudSttHelpButton { cloudHelp = CloudSttHelp.VK } },
            ) {
                    SettingsSupportingText(if (cloudCredentials.vkConfigured) "Токен и endpoint сохранены в защищённом хранилище" else "Нужны сервисный токен и HTTPS endpoint Speech API")
                    StoredCredentialField(
                        value = vkToken,
                        onValueChange = { vkToken = it },
                        configured = cloudCredentials.vkConfigured,
                        label = { Text(if (cloudCredentials.vkConfigured) "Новый сервисный токен" else "Сервисный токен") },
                    )
                    OutlinedTextField(value = vkEndpoint, onValueChange = { vkEndpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS endpoint распознавания") }, singleLine = true, colors = inputFieldColors)
                    Button(
                        onClick = { saveVk(vkToken, vkEndpoint); vkToken = "" },
                        enabled = vkEndpoint.startsWith("https://") &&
                            (vkToken.isNotBlank() || cloudCredentials.vkConfigured),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(theme.shapes.noteCard),
                    ) { Text("Сохранить настройки VK") }
            }
        }
        if (showSection("Пунктуация голосовых заметок")) item(key = "Пунктуация голосовых заметок", contentType = "settings-section") {
            SettingsSection("Пунктуация голосовых заметок") {
                Box(Modifier.fillMaxWidth()) {
                    SettingsActionRow(
                        title = "Модель расстановки запятых",
                        value = if (settings.notePunctuationModel == "rupunct_small") "RUPunct Small" else "Как распознал Whisper / STT",
                        onClick = { punctuationExpanded = true },
                        trailingDescription = "Выбрать модель пунктуации",
                    )
                    AppDropdownMenu(expanded = punctuationExpanded, onDismissRequest = { punctuationExpanded = false }) {
                        AppMenuItem(text = "Как распознал Whisper / STT", onClick = {
                            setNotePunctuationModel("whisper"); punctuationExpanded = false
                        })
                        AppMenuItem(text = "RUPunct Small · " + if (punctuationModel.installed) "скачана" else "29 МБ", onClick = {
                            setNotePunctuationModel("rupunct_small"); punctuationExpanded = false
                        })
                    }
                }
                SettingsSupportingText(if (settings.notePunctuationModel == "rupunct_small")
                    "Модель исправляет запятые в готовом тексте. После скачивания работает офлайн. Слова и остальные знаки сохраняются."
                    else "Сохраняется пунктуация распознавания. Дополнительная обработка отключена.")
                SettingsSupportingText("Применяется к новой диктовке в редакторе заметки.")
                SettingsSupportingText(if (punctuationModel.installed) "RUPunct Small скачана" else "RUPunct Small не скачана · около 29 МБ")
                if (punctuationModel.downloading) {
                    SettingsSupportingText(punctuationModel.progress?.let { "Загрузка RUPunct Small: ${(it * 100).toInt()}%" }
                        ?: "RUPunct Small: ожидание сети или начала загрузки…")
                    SettingsActionRow(title = "Отменить загрузку", onClick = cancelPunctuationDownload)
                } else if (!punctuationModel.installed || punctuationModel.error != null) {
                    SettingsActionRow(title = if (punctuationModel.error == null) "Скачать RUPunct Small" else "Повторить скачивание RUPunct Small",
                        description = "Около 29 МБ · работает без Интернета после скачивания", onClick = downloadPunctuationModel)
                }
                punctuationModel.error?.let { SettingsSupportingText("Ошибка загрузки: $it") }
            }
        }
        if (showSection("Модель Whisper.cpp")) item(key = "Модель Whisper.cpp", contentType = "settings-section") {
            SettingsSection("Модель Whisper.cpp") {
                    SettingsToggleRow(
                        title = "Режим распознавания: " + if (settings.whisperAccurateMode) "Точно" else "Быстро",
                        description = if (settings.whisperAccurateMode) {
                            "Поиск по 3 вариантам. При низкой уверенности — полное окно и 5 вариантов; при превышении времени — быстрый режим"
                        } else {
                            "Быстрое распознавание. Включите для выбора режима «Точно»"
                        },
                        checked = settings.whisperAccurateMode,
                        onCheckedChange = setWhisperAccurateMode,
                    )
                    SettingsToggleRow(
                        title = "Хранить модель в памяти",
                        description = if (settings.keepWhisperModelLoaded) {
                            "Следующие распознавания запускаются быстрее, но Whisper постоянно занимает оперативную память"
                        } else {
                            "На мощном устройстве модель ненадолго остаётся в памяти, затем автоматически выгружается"
                        },
                        checked = settings.keepWhisperModelLoaded,
                        onCheckedChange = setKeepWhisperModelLoaded,
                    )
                    Box(Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            title = "Потоки обработки",
                            value = if (settings.whisperThreadCount == AUTOMATIC_WHISPER_THREAD_COUNT) {
                                "Автоматически: $effectiveWhisperThreads"
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
                    SettingsSupportingText("Каталог: models/whisper")
                    SettingsSupportingText("Состояние: ${whisperRuntime.stage}")
                    if (whisperRuntime.lastDurationMs != null) {
                        SettingsSupportingText(
                            "Последний запуск: ${whisperRuntime.lastDurationMs} мс · " +
                                "аудио ${"%.1f".format(Locale.ROOT, whisperRuntime.audioSeconds)} с · " +
                                "RTF ${"%.2f".format(Locale.ROOT, whisperRuntime.realtimeFactor)} · " +
                                "потоков ${whisperRuntime.threads}",
                        )
                        SettingsSupportingText("Проверка речи: ${whisperRuntime.vadDurationMs} мс · " +
                            "подготовка: ${whisperRuntime.preparationDurationMs} мс · " +
                            "Whisper: ${whisperRuntime.nativeDurationMs} мс · проходов: ${whisperRuntime.nativePasses}")
                    }
                    ModelCatalogSelector(
                        expanded = whisperExpanded,
                        setExpanded = { whisperExpanded = it },
                        dialogTitle = "Выбор модели Whisper",
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
                        importedDescription = "Импортированная GGML-модель Whisper",
                    )
                    SettingsActionRow(
                        title = "Импортировать модель",
                        onClick = { importWhisperModel.launch("*/*") },
                        enabled = modelStorage.configured && whisperModels.downloadingId == null,
                        trailingIcon = Icons.Default.FileOpen,
                        trailingDescription = "Импортировать",
                    )
                    whisperModels.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (whisperDownloads.any { it.id == "small-ru" && it.url.isBlank() }) {
                        SettingsSupportingText(
                            "Small RU можно импортировать файлом ggml-small-ru-v2-q5_1.bin; загрузка появится после публикации модели.",
                        )
                    }
            }
        }
        if (showSection("Распознавание команд")) item(key = "Распознавание команд", contentType = "settings-section") {
            SettingsSection("Распознавание команд") {
                var modelExpanded by remember { mutableStateOf(false) }
                SettingsToggleRow(
                    title = "Расширенное распознавание команд",
                    description = if (settings.classicNluEnabled) {
                        if (classicNluModel.installed) {
                            "Локально обрабатывает свободные формулировки. Модель загружается только для нестандартной команды"
                        } else {
                            "Установите локальную NLU-модель; стандартные команды продолжат работать без неё"
                        }
                    } else {
                        "Стандартные команды продолжат работать без дополнительной модели"
                    },
                    checked = settings.classicNluEnabled && classicNluModel.installed,
                    onCheckedChange = setClassicNluEnabled,
                    enabled = classicNluModel.installed,
                )
                SettingsSupportingText("Каталог: models/nlu")
                Box(Modifier.fillMaxWidth()) {
                    SettingsActionRow(
                        title = "Активная NLU-модель",
                        value = classicNluModel.selectedName ?: "Не установлена",
                        description = "Установлено моделей: ${classicNluModel.installedModels.size}",
                        onClick = { modelExpanded = true },
                        enabled = classicNluModel.installedModels.isNotEmpty() && !classicNluModel.downloading,
                        trailingDescription = "Выбрать модель",
                    )
                    AppDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        classicNluModel.installedModels.forEach { name ->
                            AppMenuItem(text = name, selected = name == classicNluModel.selectedName, onClick = {
                                selectClassicNlu(name)
                                modelExpanded = false
                            })
                        }
                    }
                }
                SettingsSupportingText(
                    when {
                        classicNluModel.downloading -> "Загрузка: ${classicNluModel.progress?.let { "${(it * 100).roundToInt()}%" } ?: "подготовка"}"
                        classicNluModel.installed -> "Модель установлена"
                        else -> "Модель не установлена"
                    },
                )
                SettingsActionRow(
                    title = "Скачать NLU v7",
                    description = if (classicNluDownloadAvailable) "Новая модель команд · 29 МБ" else "Станет доступно после публикации модели",
                    onClick = downloadClassicNlu,
                    enabled = classicNluDownloadAvailable && modelStorage.configured && !classicNluModel.downloading,
                    trailingDescription = "Скачать",
                )
                SettingsActionRow(
                    title = "Импортировать NLU-модель",
                    description = "Файл classic_nlu_v7_int8.onnx или другая ONNX-модель",
                    onClick = { importClassicNluModel.launch("*/*") },
                    enabled = modelStorage.configured && !classicNluModel.downloading,
                    trailingIcon = Icons.Default.FileOpen,
                    trailingDescription = "Импортировать",
                )
                classicNluModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (showSection("О приложении")) item(key = "О приложении", contentType = "settings-section") {
            SettingsSection("О приложении") {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.colors.textPrimary,
                )
                Text(
                    stringResource(R.string.app_tagline),
                    color = theme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsInfoRow(title = "Версия", value = appVersion, valueColor = theme.colors.textPrimary)
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
        state = threadBenchmark,
        canApply = threadBenchmark.modelName == whisperModels.selectedName,
        start = startThreadBenchmark,
        cancel = cancelThreadBenchmark,
        apply = { applyThreadBenchmark(); threadBenchmarkDialog = false },
        dismiss = { cancelThreadBenchmark(); threadBenchmarkDialog = false },
    )
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
                                .padding(vertical = 8.dp),
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
            previewRingtone?.stop()
            previewRingtone = uri.takeIf(String::isNotBlank)?.let { selected ->
                runCatching {
                    RingtoneManager.getRingtone(context, Uri.parse(selected))?.also { ringtone ->
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(
                                if (alarmSoundPicker) {
                                    AudioAttributes.USAGE_ALARM
                                } else {
                                    AudioAttributes.USAGE_NOTIFICATION
                                },
                            )
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        ringtone.play()
                    }
                }.getOrNull()
            }
        }
        AppAlertDialog(
            onDismissRequest = {
                previewRingtone?.stop()
                soundPickerOpen = false
            },
            title = { Text(if (alarmSoundPicker) "Мелодия будильника" else "Звук напоминания") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                ) {
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
                        previewRingtone?.stop()
                        soundPickerOpen = false
                    },
                ) { Text("Отмена") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        previewRingtone?.stop()
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTimePhrase,
                        onValueChange = { newTimePhrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Слово или фраза") },
                        placeholder = { Text("например, после работы") },
                        singleLine = true,
                        colors = inputFieldColors,
                    )
                    OutlinedTextField(
                        value = newTimeValue,
                        onValueChange = { newTimeValue = it.take(5) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Время в формате ЧЧ:ММ") },
                        singleLine = true,
                        isError = parsedTime == null,
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
            elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    color = theme.colors.textSecondary,
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, Modifier.weight(1f).padding(start = 8.dp))
    }
}

private fun noteTrashRetentionLabel(days: Int): String = when (days) {
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
    var focused by remember { mutableStateOf(false) }
    val theme = AppTheme.tokens
    OutlinedTextField(
        value = if (configured && value.isBlank() && !focused) "••••••••••••" else value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        label = label,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
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
                item {
                    TextButton(
                        onClick = { uriHandler.openUri(help.consoleUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        Text(help.consoleLabel, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss) { Text("Понятно") }
        },
    )
}

@Composable
private fun SttCatalogSelector(
    expanded: Boolean,
    setExpanded: (Boolean) -> Unit,
    engines: List<SpeechToTextEngine>,
    selectedId: String,
    localOnly: Boolean,
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
                        val available = android?.isAvailable(localOnly) ?: true
                        ModelCatalogRow(
                            title = engine.displayName,
                            description = sttEngineDescription(engine, localOnly),
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
    localOnly: Boolean,
): String = when (engine) {
    is AndroidSpeechRecognizerEngine -> engine.availabilityDescription(localOnly)
    else -> when (engine.id) {
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
) {
    ModelCatalogSelector(
        expanded = true,
        setExpanded = { if (!it) dismiss() },
        dialogTitle = "Скачать модель Whisper",
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
        downloadAvailable = { it.url.isNotBlank() },
        importedDescription = "Импортированная GGML-модель Whisper",
        showSelectorRow = false,
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
) {
    val knownNames = downloads.map(fileName).toSet()
    val unknownInstalled = installed.filterNot(knownNames::contains)
    if (showSelectorRow) SettingsActionRow(
        title = "Модель",
        value = selectedName ?: "не выбрана",
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
                    if (!showSelectorRow) item(key = "model-introduction") { introduction() }
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val theme = AppTheme.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .background(if (selected) theme.colors.selected else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = theme.colors.textPrimary,
            )
            size?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = theme.colors.textSecondary) }
            if (installed) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(theme.colors.success))
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Check, "Выбрана", tint = theme.colors.accent)
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
    val state = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = true)
    val theme = AppTheme.tokens
    AppDialog(onDismissRequest = dismiss) {
        Surface(
            shape = RoundedCornerShape(theme.shapes.dialog),
            tonalElevation = 6.dp,
            shadowElevation = appShadowElevation(8.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Выбор времени", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(20.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Отмена") }
                    TextButton(onClick = { confirm(LocalTime.of(state.hour, state.minute)) }) { Text("ОК") }
                }
            }
        }
    }
}

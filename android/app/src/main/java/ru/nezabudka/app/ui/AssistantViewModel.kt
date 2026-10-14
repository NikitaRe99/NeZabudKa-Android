package ru.nezabudka.app.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import ru.nezabudka.app.AppContainer
import ru.nezabudka.app.application.ActionEffect
import ru.nezabudka.app.application.ReminderDateClarification
import ru.nezabudka.app.domain.AppSettings
import ru.nezabudka.app.domain.CustomThemeSettings
import ru.nezabudka.app.domain.TimeOfDaySetting
import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.data.FirstUseSetupPreferences
import ru.nezabudka.app.data.AssistantTipPreferences
import ru.nezabudka.app.domain.AssistantTipController
import ru.nezabudka.app.domain.AssistantTipKind
import ru.nezabudka.app.domain.AssistantSettingsTarget
import ru.nezabudka.app.data.BackupContent
import ru.nezabudka.app.data.BackupRestorePlan
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.domain.WhisperThreadTiming
import ru.nezabudka.app.domain.bestWhisperThreadCount
import ru.nezabudka.app.domain.maximumWhisperThreadCount
import ru.nezabudka.app.speech.PcmWavSegmenter
import ru.nezabudka.app.domain.CommandContext
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.data.detectDeviceCapabilities
import ru.nezabudka.app.ports.CancellableAudioRecorder
import ru.nezabudka.app.ports.ContextualSpeechToTextEngine
import ru.nezabudka.app.ports.LiveSpeechToTextEngine
import ru.nezabudka.app.ports.SpeechRecognition
import ru.nezabudka.app.speech.WhisperDownload
import ru.nezabudka.app.speech.WhisperModelMissingException
import ru.nezabudka.app.BuildConfig
import ru.nezabudka.app.speech.AndroidSpeechRecognizerEngine
import ru.nezabudka.app.notifications.NotificationChannels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class AssistantUiState(
    val recordingPreview: String? = null,
    val recognizedText: String = "",
    val recognizedAlternatives: List<String> = emptyList(),
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val errorSettingsTarget: AssistantSettingsTarget? = null,
    val whisperModelMissing: Boolean = false,
    val result: String? = null,
    val log: List<String> = emptyList(),
    val lastAudio: File? = null,
    val awaitingSttConfirmation: Boolean = false,
    val noteDictation: NoteDictationResult? = null,
    val noteDictationActive: Boolean = false,
)

data class DataRestorePreview(val title: String, val records: Int, val folders: Int)
data class DataTransferUiState(
    val busy: Boolean = false, val preview: DataRestorePreview? = null,
    val message: String? = null, val error: Boolean = false,
)

@Immutable
data class VoiceActionUiState(
    val recordingPreview: String? = null,
    val recognizedText: String = "",
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val awaitingSttConfirmation: Boolean = false,
)

@Immutable
data class NoteDictationUiState(
    val preview: String? = null,
    val result: NoteDictationResult? = null,
    val active: Boolean = false,
    val recording: Boolean = false,
)

internal fun AssistantUiState.voiceActionState() = VoiceActionUiState(
    recordingPreview = recordingPreview,
    recognizedText = recognizedText,
    isRecording = isRecording,
    isProcessing = isProcessing,
    awaitingSttConfirmation = awaitingSttConfirmation,
)

internal fun AssistantUiState.noteDictationState() = NoteDictationUiState(
    preview = recordingPreview,
    result = noteDictation,
    active = noteDictationActive,
    recording = isRecording,
)

internal fun AssistantUiState.assistantDisplayState(showTechnicalLog: Boolean): AssistantUiState =
    if (showTechnicalLog) this else copy(log = emptyList())

data class ActionNotice(val id: Long, val message: String, val effect: ActionEffect?, val isError: Boolean = false)
data class WhisperThreadBenchmarkUiState(
    val running: Boolean = false,
    val stage: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val modelName: String? = null,
    val audioSeconds: Double? = null,
    val timings: List<WhisperThreadTiming> = emptyList(),
    val bestThreads: Int? = null,
    val error: String? = null,
)
data class NoteDictationResult(val sessionId: Long, val text: String)

sealed interface AssistantUiEvent {
    data class Action(val notice: ActionNotice) : AssistantUiEvent
}

enum class ReminderSort(val key: String, val title: String) {
    MY_ORDER("my_order", "В моём порядке"), DATE("date", "По дате"),
    RECENTLY_MARKED("recently_marked", "Сначала отмеченные"), TITLE("title", "По названию");

    companion object {
        fun fromKey(value: String): ReminderSort = entries.firstOrNull { it.key == value } ?: DATE
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModel(private val container: AppContainer) : ViewModel() {
    private val _dataTransfer = MutableStateFlow(DataTransferUiState())
    val dataTransfer: StateFlow<DataTransferUiState> = _dataTransfer
    private var pendingDataRestore: BackupRestorePlan? = null

    fun exportDataFile(uri: Uri, content: BackupContent) {
        if (_dataTransfer.value.busy) return
        _dataTransfer.value = DataTransferUiState(busy = true)
        viewModelScope.launch {
            runCatching { container.dataBackup.exportFile(container.app, uri, content) }
                .onSuccess { _dataTransfer.value = DataTransferUiState(message = "${content.title} сохранены в файл") }
                .onFailure { _dataTransfer.value = DataTransferUiState(message = it.message ?: "Не удалось сохранить файл", error = true) }
        }
    }

    fun prepareDataRestore(uri: Uri, content: BackupContent) {
        if (_dataTransfer.value.busy) return
        pendingDataRestore = null
        _dataTransfer.value = DataTransferUiState(busy = true)
        viewModelScope.launch {
            runCatching { container.dataBackup.readFile(container.app, uri, content) }
                .onSuccess { plan ->
                    pendingDataRestore = plan
                    _dataTransfer.value = DataTransferUiState(preview = DataRestorePreview(content.title,
                        if (content == BackupContent.NOTES) plan.notes.size else plan.reminders.size, plan.folders.size))
                }
                .onFailure { _dataTransfer.value = DataTransferUiState(message = it.message ?: "Не удалось прочитать файл", error = true) }
        }
    }

    fun cancelDataRestore() {
        if (_dataTransfer.value.busy) return
        pendingDataRestore = null
        _dataTransfer.value = DataTransferUiState()
    }

    fun confirmDataRestore() {
        if (_dataTransfer.value.busy) return
        val plan = pendingDataRestore ?: return
        pendingDataRestore = null
        _dataTransfer.value = DataTransferUiState(busy = true)
        viewModelScope.launch {
            runCatching { container.dataBackup.restoreFile(plan) }
                .onSuccess { result -> _dataTransfer.value = DataTransferUiState(
                    message = "Добавлено: ${result.added}. Совпадающих записей пропущено: ${result.skipped}" +
                        (if (result.foldersAdded > 0) ". Новых папок: ${result.foldersAdded}" else "") +
                        (if (result.backgroundReady) "" else ". Перезапустите приложение для обновления фоновых задач"),
                    error = !result.backgroundReady) }
                .onFailure { _dataTransfer.value = DataTransferUiState(message = it.message ?: "Не удалось восстановить данные", error = true) }
        }
    }
    val appIcon = container.appIcons.selected
    fun refreshAppIcon() = container.appIcons.refresh()
    fun setAppIcon(option: ru.nezabudka.app.data.AppIconOption) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { container.appIcons.select(option) }.onFailure(::fail) }
    }
    private val tips = AssistantTipController(AssistantTipPreferences(container.app))
    private val resolvedPermissionTips = MutableStateFlow<Set<AssistantTipKind>>(emptySet())
    val assistantTips = kotlinx.coroutines.flow.combine(tips.pending, resolvedPermissionTips, container.settings.state) { pending, resolved, current ->
        pending.filterNot { it in resolved || when (it) {
            AssistantTipKind.LIVE_TEXT -> current.gigaamLivePreview || current.sttEngine != "gigaam"
            AssistantTipKind.GIGAAM_SPEED -> current.sttEngine != "gigaam"
            AssistantTipKind.WHISPER_SPEED -> current.sttEngine != "whisper"
            AssistantTipKind.IMMEDIATE_COMMANDS -> current.trustStt
            AssistantTipKind.PUNCTUATION -> current.notePunctuationModel != "whisper" || (current.sttEngine == "whisper" && current.whisperRawMode)
            AssistantTipKind.EDITOR_PUNCTUATION -> current.editorFullPunctuation || current.notePunctuationModel == "whisper"
            else -> false
        } }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, tips.pending.value)
    fun refreshPermissionTips(status: ru.nezabudka.app.domain.ReminderPermissionStatus, alarmWindow: Boolean, manualBackgroundCheck: Boolean) {
        resolvedPermissionTips.value = buildSet {
            if (status.deliveryReady() && !manualBackgroundCheck) add(AssistantTipKind.REMINDER_DELIVERY)
            if (!alarmWindow || status.alarmWindowReady()) add(AssistantTipKind.ALARM_WINDOW)
        }
    }
    private var voiceInputPending = false
    private var recognitionEngineId = ""
    private var gigaamPreviewJob: Job? = null
    fun offerTip(kind: AssistantTipKind) = tips.offer(kind)
    fun acknowledgeTip(kind: AssistantTipKind) = tips.acknowledge(kind)
    fun dismissError() = update("Ошибка закрыта") { it.copy(error = null, errorSettingsTarget = null, whisperModelMissing = false) }
    fun hideAssistantWelcomeCard() {
        setShowAssistantWelcomeCard(false)
        tips.offer(AssistantTipKind.WELCOME_RESTORE)
    }
    fun resetFirstUseSetup() {
        tips.reset()
        FirstUseSetupPreferences(container.app).reset()
        update("Подсказки первого использования сброшены") { it }
    }
    private fun offerFolderSetup(folder: NoteFolder) = tips.offer(AssistantTipKind.FOLDERS)
    private fun offerReminderSetup(reminder: Reminder) = tips.offer(AssistantTipKind.REMINDER_MODES)

    private val _ui = MutableStateFlow(AssistantUiState(lastAudio = container.preprocessor.lastProcessed()))
    val ui: StateFlow<AssistantUiState> = _ui
    private val _whisperModelsMenu = MutableStateFlow(false)
    val whisperModelsMenu: StateFlow<Boolean> = _whisperModelsMenu
    fun whisperModelsOpened() {
        val selected = if (settings.value.sttEngine == "gigaam") gigaamModels.value.selectedName else whisperModels.value.selectedName
        if (_ui.value.whisperModelMissing || selected == null) tips.offer(AssistantTipKind.RECOGNITION)
    }
    fun openWhisperModelsMenu() {
        whisperModelsOpened()
        _whisperModelsMenu.value = true
    }
    fun dismissWhisperModelsMenu() { _whisperModelsMenu.value = false }
    val voiceActionUi: StateFlow<VoiceActionUiState> = ui
        .map(AssistantUiState::voiceActionState)
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            _ui.value.voiceActionState(),
        )
    val noteDictationUi: StateFlow<NoteDictationUiState> = ui
        .map(AssistantUiState::noteDictationState)
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            _ui.value.noteDictationState(),
        )
    private val _event = MutableStateFlow<AssistantUiEvent?>(null)
    val event: StateFlow<AssistantUiEvent?> = _event
    val settings: StateFlow<AppSettings> = container.settings.state
    // Hidden diagnostic events should not invalidate the assistant's entire screen.
    val assistantUi: StateFlow<AssistantUiState> = combine(
        ui,
        settings.map { it.showTechnicalLog }.distinctUntilChanged(),
    ) { state, showLog -> state.assistantDisplayState(showLog) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            _ui.value.assistantDisplayState(settings.value.showTechnicalLog))
    private val reminderFeature = ReminderFeature(
        container.reminders, container.reminderActions, viewModelScope, ::fail,
        onReminderCreated = ::offerReminderSetup,
    )
    private val notesFeature = NotesFeature(
        container.notes, viewModelScope, ::publishNotice, ::fail,
        onFolderCreated = ::offerFolderSetup,
        needsFolderSetup = { !tips.wasOffered(AssistantTipKind.FOLDERS) },
    )
    val reminders: StateFlow<List<Reminder>> = reminderFeature.reminders
    val notesUi: StateFlow<NotesUiState> = notesFeature.state
    val folders: StateFlow<List<NoteFolder>> = notesFeature.folders
    val engines = container.stt.all()
    val whisperModels = container.whisperModels.state
    val gigaamModels = container.gigaamModels.state
    val whisperRuntime = container.whisper.runtime
    val punctuationModel = container.notePunctuation.models.state
    val whisperDownloads: List<WhisperDownload> = container.whisperModels.downloads
    val modelStorage = container.modelStorage.state
    val classicNluModel = container.classicNlu.modelState
    val classicNluDownloadAvailable: Boolean = BuildConfig.CLASSIC_NLU_MODEL_URL.isNotBlank()
    val cloudSttCredentials = container.cloudSttCredentials.state
    val deviceCapabilities = detectDeviceCapabilities(container.app)
    private val _whisperThreadBenchmark = MutableStateFlow(WhisperThreadBenchmarkUiState())
    val whisperThreadBenchmark: StateFlow<WhisperThreadBenchmarkUiState> = _whisperThreadBenchmark
    private var whisperBenchmarkJob: Job? = null
    private var noticeDismissJob: Job? = null
    private var playbackJob: Job? = null
    private val playbackLock = Any()
    private var playback: MediaPlayer? = null
    private var pendingCommandScope = CommandScope.ASSISTANT
    private var recognitionTarget = RecognitionTarget.COMMAND
    private var noteDictationSessionId: Long? = null
    private var pendingReminderDateClarification: ReminderDateClarification? = null
    private val recordingStopRequested = AtomicBoolean(false)

    fun observeReminder(id: Long) = reminderFeature.observe(id)

    fun editText(value: String) {
        _ui.value = _ui.value.copy(
            recognizedText = value,
            recognizedAlternatives = emptyList(),
            error = null,
            awaitingSttConfirmation = _ui.value.awaitingSttConfirmation && value.isNotBlank(),
        )
    }
    fun selectFolder(id: Long?) { notesFeature.dispatch(NoteUiEvent.SelectFolder(id)) }
    suspend fun noteForWidget(id: Long): Note? = withContext(Dispatchers.IO) { container.notes.byId(id) }
    suspend fun reminderForWidget(id: Long): Reminder? = withContext(Dispatchers.IO) { container.reminders.observe(id).first() }
    suspend fun folderForWidget(id: Long?): Long? = withContext(Dispatchers.IO) {
        id?.takeIf { value -> container.database.notesDao().allFolders().any { it.id == value } }
    }
    fun dismissActionNotice() {
        noticeDismissJob?.cancel()
        _event.value = null
        if (pendingReminderDateClarification != null) {
            pendingReminderDateClarification = null
            update("Уточнение даты отменено пользователем") {
                it.copy(result = "Создание напоминания отменено.")
            }
        }
    }
    fun dismissNoteNotice() = dismissActionNotice()
    fun answerReminderDateClarification(answer: String) {
        if (pendingReminderDateClarification == null) return
        editText(answer)
        execute()
    }
    fun selectSttEngine(engine: String) {
        if (_ui.value.isRecording || _ui.value.isProcessing) return
        val selected = container.stt.all().firstOrNull { it.id == engine }
        if (selected is AndroidSpeechRecognizerEngine && !selected.isAvailable()) {
            fail(IllegalStateException(selected.availabilityDescription()))
            return
        }
        val previousEngine = settings.value.sttEngine
        container.settings.setSttEngine(engine)
        if (previousEngine == "whisper" && engine != "whisper") {
            viewModelScope.launch(Dispatchers.Default) { container.whisper.release() }
        }
        if (previousEngine == "gigaam" && engine != "gigaam") viewModelScope.launch { container.gigaam.release() }
    }
    fun setTheme(value: String) = container.settings.setTheme(value)
    fun setCustomTheme(value: CustomThemeSettings) = container.settings.setCustomTheme(value)
    fun setPopupDurationSeconds(value: Int) = container.settings.setPopupDurationSeconds(value)
    fun setHidePopupNotices(value: Boolean) = container.settings.setHidePopupNotices(value)
    fun setClassicNluEnabled(enabled: Boolean) {
        container.settings.setClassicNluEnabled(enabled)
        if (!enabled) viewModelScope.launch(Dispatchers.Default) { container.classicNlu.release() }
    }
    fun setKeepWhisperModelLoaded(enabled: Boolean) {
        if (settings.value.sttEngine == "gigaam") {
            container.settings.setKeepGigaamModelLoaded(enabled)
            if (!enabled) viewModelScope.launch { container.gigaam.release() }
            return
        }
        container.settings.setKeepWhisperModelLoaded(enabled)
        if (!enabled) viewModelScope.launch(Dispatchers.Default) {
            container.whisper.release()
        }
    }
    fun setWhisperAccurateMode(enabled: Boolean) = container.settings.setWhisperAccurateMode(enabled)
    fun setGigaamLivePreview(enabled: Boolean) = container.settings.setGigaamLivePreview(enabled)
    fun setEditorFullPunctuation(enabled: Boolean) = container.settings.setEditorFullPunctuation(enabled)
    fun setWhisperRawMode(enabled: Boolean) = container.settings.setWhisperRawMode(enabled)
    fun setNotePunctuationModel(value: String) = container.settings.setNotePunctuationModel(value)
    fun downloadPunctuationModel() = viewModelScope.launch(Dispatchers.IO) {
        runCatching { container.notePunctuation.models.download() }.onFailure(::fail)
    }
    fun cancelPunctuationDownload() { container.notePunctuation.models.cancelDownload() }
    fun importPunctuationModel(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            container.notePunctuation.models.importModel(uri)
            container.settings.setNotePunctuationModel("rupunct_small")
        }.onFailure(::fail)
    }
    fun selectPunctuationModel(name: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            container.notePunctuation.models.selectModel(name)
            container.settings.setNotePunctuationModel("rupunct_small")
        }.onFailure(::fail)
    }

    fun setWhisperThreadCount(value: Int) {
        val normalized = value.coerceIn(0, maximumWhisperThreadCount(deviceCapabilities.cpuCores))
        if (settings.value.sttEngine == "gigaam") container.settings.setGigaamThreadCount(normalized)
        else container.settings.setWhisperThreadCount(normalized)
    }

    fun startWhisperThreadBenchmark() {
        if (_whisperThreadBenchmark.value.running) return
        if (_ui.value.isProcessing || _ui.value.isRecording) {
            _whisperThreadBenchmark.value = WhisperThreadBenchmarkUiState(error = "Сначала завершите текущую запись или распознавание.")
            return
        }
        _whisperThreadBenchmark.value = WhisperThreadBenchmarkUiState(running = true, stage = "Подготовка записи…")
        _ui.value = _ui.value.copy(isProcessing = true)
        whisperBenchmarkJob = viewModelScope.launch {
            var sample: File? = null
            try {
                sample = withContext(Dispatchers.IO) {
                    val source = _ui.value.lastAudio?.takeIf(File::isFile)
                        ?: container.preprocessor.lastProcessed()?.takeIf(File::isFile)
                        ?: File(container.app.filesDir, "recordings/last_raw.wav").takeIf(File::isFile)
                        ?: error("Сначала запишите в помощнике фразу длительностью 3–5 секунд, затем повторите проверку.")
                    File.createTempFile("whisper-threads-", ".wav", container.app.cacheDir).also { output ->
                        try { PcmWavSegmenter.copyFirstSeconds(source, output, 5) }
                        catch (error: Throwable) { output.delete(); throw error }
                    }
                }
                _whisperThreadBenchmark.value = _whisperThreadBenchmark.value.copy(audioSeconds = (sample.length() - 44) / 32_000.0)
                val (modelName, timings) = if (settings.value.sttEngine == "gigaam") container.gigaam.benchmarkThreads(
                    sample, maximumWhisperThreadCount(deviceCapabilities.cpuCores),
                ) { completed, total, threads ->
                    _whisperThreadBenchmark.value = _whisperThreadBenchmark.value.copy(completed = completed, total = total, stage = "Проверка GigaAM: $threads потоков · $completed из $total")
                } else container.whisper.benchmarkThreads(
                    sample, maximumWhisperThreadCount(deviceCapabilities.cpuCores),
                ) { completed, total, threads ->
                    _whisperThreadBenchmark.value = _whisperThreadBenchmark.value.copy(
                        completed = completed, total = total,
                        stage = if (threads == 0) "Прогрев модели…" else "Проверка: $threads потоков · $completed из $total замеров",
                    )
                }
                _whisperThreadBenchmark.value = _whisperThreadBenchmark.value.copy(
                    modelName = modelName, timings = timings, bestThreads = bestWhisperThreadCount(timings), stage = "Проверка завершена",
                )
            } catch (cancelled: CancellationException) {
                _whisperThreadBenchmark.value = _whisperThreadBenchmark.value.copy(stage = "Проверка отменена")
            } catch (error: Throwable) {
                _whisperThreadBenchmark.value = _whisperThreadBenchmark.value.copy(error = error.message ?: "Не удалось выполнить проверку")
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { sample?.delete() }
                _whisperThreadBenchmark.value = _whisperThreadBenchmark.value.copy(running = false)
                _ui.value = _ui.value.copy(isProcessing = false)
            }
        }
    }

    fun cancelWhisperThreadBenchmark() {
        if (_whisperThreadBenchmark.value.running) {
            _whisperThreadBenchmark.value = _whisperThreadBenchmark.value.copy(stage = "Останавливаю проверку…")
            whisperBenchmarkJob?.cancel()
        }
    }

    fun applyBestWhisperThreadCount() {
        val result = _whisperThreadBenchmark.value
        val activeModels = if (settings.value.sttEngine == "gigaam") gigaamModels else whisperModels
        if (!result.running && result.modelName == activeModels.value.selectedName) {
            result.bestThreads?.let(::setWhisperThreadCount)
        }
    }
    fun setTimeOfDaySettings(values: List<TimeOfDaySetting>) = container.settings.setTimeOfDaySettings(values)
    fun setAudioPreprocessing(enabled: Boolean) = container.settings.setAudioPreprocessing(enabled)
    fun setShowPlaybackButton(enabled: Boolean) = container.settings.setShowPlaybackButton(enabled)
    fun setShowAssistantWelcomeCard(enabled: Boolean) = container.settings.setShowAssistantWelcomeCard(enabled)
    fun setShowTechnicalLog(enabled: Boolean) = container.settings.setShowTechnicalLog(enabled)
    fun setTrustStt(enabled: Boolean) = container.settings.setTrustStt(enabled)
    fun setReminderSort(sort: ReminderSort) = container.settings.setReminderSort(sort.key)
    fun setNoteSort(value: String) = container.settings.setNoteSort(value)
    fun setNoteColumns(value: Int) = container.settings.setNoteColumns(value)
    fun setNoteFolderInterface(value: String) = container.settings.setNoteFolderInterface(value)
    fun setNoteTrashRetentionDays(value: Int) = container.setNoteTrashRetentionDays(value)
    fun setUiHapticsEnabled(enabled: Boolean) = container.settings.setUiHapticsEnabled(enabled)
    fun setShowNavigationLabels(enabled: Boolean) = container.settings.setShowNavigationLabels(enabled)
    fun setUiPerformanceMode(value: String) = container.settings.setUiPerformanceMode(value)
    fun setNotificationSound(uri: String) {
        container.settings.setNotificationSound(uri)
        recreateNotificationChannels()
    }
    fun setNotificationVibrationEnabled(enabled: Boolean) {
        container.settings.setNotificationVibrationEnabled(enabled)
        recreateNotificationChannels()
    }
    fun runReminderDisplayTest(delaySeconds: Int) = runCatching {
        ru.nezabudka.app.notifications.ReminderDisplayTest.start(container.app, delaySeconds)
    }.onSuccess {
        publishNotice(if (delaySeconds == 0) "Тест запущен" else "Тест через $delaySeconds секунд. Заблокируйте экран", null)
    }.onFailure(::fail)
    fun cancelReminderDisplayTest() {
        ru.nezabudka.app.notifications.ReminderDisplayTest.cancel(container.app)
        publishNotice("Тест отменён", null)
    }
    fun setReminderAlarmMode(enabled: Boolean) {
        container.settings.setReminderAlarmMode(enabled)
        if (enabled) tips.offer(AssistantTipKind.ALARM_WINDOW)
        viewModelScope.launch(Dispatchers.IO) {
            if (!enabled) NotificationChannels.cancelActiveAlarms(container.app)
            NotificationChannels.recreate(container.app)
            container.restoreReminderSchedules()
        }
    }
    fun setReminderAlarmPresentation(value: String) {
        container.settings.setReminderAlarmPresentation(value)
        recreateNotificationChannels()
    }
    fun setReminderAlarmSound(uri: String) {
        container.settings.setReminderAlarmSound(uri)
        recreateNotificationChannels()
    }
    fun setNotificationWakeScreen(enabled: Boolean) = container.settings.setNotificationWakeScreen(enabled)
    fun setReminderAlarmRingSeconds(value: Int) = container.settings.setReminderAlarmRingSeconds(value)
    fun setReminderAlarmPauseMinutes(value: Int) = container.settings.setReminderAlarmPauseMinutes(value)
    fun setReminderAlarmRepeatCount(value: Int) = container.settings.setReminderAlarmRepeatCount(value)

    private fun recreateNotificationChannels() {
        viewModelScope.launch(Dispatchers.IO) { NotificationChannels.recreate(container.app) }
    }
    fun saveYandexCredentials(apiKey: String, folderId: String) = runCatching {
        container.cloudSttCredentials.saveYandex(apiKey, folderId)
    }.onFailure(::fail)
    fun loadCloudCredential(engine: String): String = runCatching { when (engine) {
        "yandex" -> container.cloudSttCredentials.yandexKey()
        "google_cloud" -> container.cloudSttCredentials.googleToken()
        "vk_cloud" -> container.cloudSttCredentials.vkToken()
        else -> null
    }.orEmpty() }.getOrDefault("")
    fun saveGoogleCredentials(accessToken: String) = runCatching {
        container.cloudSttCredentials.saveGoogle(accessToken)
    }.onFailure(::fail)
    fun saveVkCredentials(serviceToken: String, endpoint: String) = runCatching {
        container.cloudSttCredentials.saveVk(serviceToken, endpoint)
    }.onFailure(::fail)
    fun selectModelsDirectory(uri: Uri) = viewModelScope.launch {
        runCatching {
            val result = container.dataBackup.selectRoot(uri)
            withContext(Dispatchers.IO) { container.startDataBackupServices() }
            withContext(Dispatchers.IO) {
                container.whisperModels.refresh()
                container.gigaamModels.refresh()
                container.classicNlu.refreshModel()
                container.notePunctuation.models.refresh()
                if (result.restored) {
                    NotificationChannels.recreate(container.app)
                    container.restoreReminderSchedules()
                }
            }
            result
        }.onSuccess { result ->
            update("${result.message}: ${container.modelStorage.description()}") { it.copy(error = null) }
        }.onFailure(::fail)
    }

    fun clearModelCache(onComplete: (Long?) -> Unit) = viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.Default) {
                container.whisper.release()
                container.gigaam.release()
            }
            withContext(Dispatchers.IO) {
                container.modelStorage.clearDisposableModelCache()
            }
        }.onSuccess { removedBytes ->
            onComplete(removedBytes)
        }.onFailure { error ->
            fail(error)
            onComplete(null)
        }
    }

    fun refreshModelCacheStats() = viewModelScope.launch(Dispatchers.IO) {
        container.startSettingsResources()
        container.modelStorage.refreshCacheStats()
    }

    fun selectWhisperModel(fileName: String) = viewModelScope.launch(Dispatchers.Default) {
        runCatching {
            if (settings.value.sttEngine == "gigaam") {
                container.gigaam.release(); container.gigaamModels.select(fileName)
                return@runCatching
            }
            if (container.whisperModels.currentSelectedModelName() == fileName) return@runCatching
            container.whisper.release()
            container.whisperModels.select(fileName)
        }.onFailure(::fail)
    }

    fun deleteDownloadedModel(kind: String, fileName: String) {
        if (_ui.value.isRecording || _ui.value.isProcessing || _whisperThreadBenchmark.value.running) {
            fail(IllegalStateException("Дождитесь завершения записи или обработки"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                when (kind) {
                    "gigaam" -> {
                        check(container.gigaamModels.state.value.downloadingId == null) { "Дождитесь завершения загрузки" }
                        container.gigaam.release(); container.gigaamModels.delete(fileName)
                    }
                    "whisper" -> {
                        check(container.whisperModels.state.value.downloadingId == null) { "Дождитесь завершения загрузки" }
                        if (container.whisperModels.currentSelectedModelName() == fileName) container.whisper.release()
                        container.whisperModels.delete(fileName)
                    }
                    "nlu" -> {
                        check(!container.classicNlu.modelState.value.downloading) { "Дождитесь завершения загрузки" }
                        container.classicNlu.deleteModel(fileName)
                        if (!container.classicNlu.modelState.value.installed) container.settings.setClassicNluEnabled(false)
                    }
                    "punctuation" -> {
                        check(!container.notePunctuation.models.state.value.downloading) { "Дождитесь завершения загрузки" }
                        container.notePunctuation.models.deleteModel(fileName)
                        if (!container.notePunctuation.models.state.value.installed) container.settings.setNotePunctuationModel("whisper")
                    }
                    else -> error("Неизвестный тип модели")
                }
            }.onSuccess { publishNotice("Модель удалена", null) }.onFailure(::fail)
        }
    }
    fun downloadWhisperModel(model: WhisperDownload) = viewModelScope.launch {
        update("Подготовка фоновой загрузки Whisper ${model.fileName}") { it.copy(error = null) }
        runCatching {
            if (settings.value.sttEngine == "gigaam") container.gigaamModels.download(model)
            else container.whisperModels.download(model)
        }
            .onSuccess { update("Whisper-модель поставлена в фоновую загрузку") { it } }
            .onFailure(::fail)
    }
    fun importWhisperModel(uri: Uri) = viewModelScope.launch {
        update("Импорт GGML-модели") { it.copy(error = null) }
        runCatching {
            if (settings.value.sttEngine == "gigaam") { container.gigaam.release(); container.gigaamModels.import(uri) }
            else container.whisperModels.import(uri)
        }
            .onSuccess { update("Модель импортирована в пользовательскую папку models/whisper") { it } }
            .onFailure(::fail)
    }

    fun downloadClassicNluModel() = viewModelScope.launch {
        update("Подготовка фоновой загрузки NLU-модели") { it.copy(error = null) }
        runCatching { container.classicNlu.downloadModel(BuildConfig.CLASSIC_NLU_MODEL_URL) }
            .onSuccess { container.settings.setClassicNluEnabled(true); update("NLU-модель поставлена в фоновую загрузку") { it } }
            .onFailure(::fail)
    }

    fun importClassicNluModel(uri: Uri) = viewModelScope.launch {
        update("Импорт NLU-модели") { it.copy(error = null) }
        runCatching { container.classicNlu.importModel(uri) }
            .onSuccess { container.settings.setClassicNluEnabled(true); update("NLU-модель сохранена в models/nlu") { it } }
            .onFailure(::fail)
    }

    fun selectClassicNluModel(fileName: String) = viewModelScope.launch {
        runCatching { container.classicNlu.selectModel(fileName) }
            .onSuccess { container.settings.setClassicNluEnabled(true); update("Выбрана NLU-модель: $fileName") { it.copy(error = null) } }
            .onFailure(::fail)
    }

    fun toggleRecording() {
        val engine = container.stt.require(settings.value.sttEngine)
        if (engine is LiveSpeechToTextEngine) {
            if (_ui.value.isRecording) {
                update("Остановка Android SpeechRecognizer") { it.copy(isRecording = false, isProcessing = true) }
                engine.stop()
            } else startLiveRecognition(engine)
        } else if (_ui.value.isRecording) stopRecording() else startRecording()
    }

    private fun startLiveRecognition(engine: LiveSpeechToTextEngine) {
        recognitionEngineId = engine.id
        update("Android SpeechRecognizer слушает микрофон") { it.copy(isRecording = true, isProcessing = false, error = null, result = null) }
        val onResult: (SpeechRecognition) -> Unit = { recognition ->
            completeRecognition(recognition, "Android SpeechRecognizer завершил распознавание")
        }
        engine.start(onResult, ::fail)
    }

    private fun startRecording() {
        recognitionEngineId = settings.value.sttEngine
        recordingStopRequested.set(false)
        runCatching { container.recorder.start() }
            .onSuccess {
                update("Запись начата") {
                    it.copy(isRecording = true, recordingPreview = null, error = null, result = null, awaitingSttConfirmation = false)
                }
                if (recognitionEngineId == "gigaam" && settings.value.gigaamLivePreview) startGigaamPreview()
            }
            .onFailure(::fail)
    }

    private fun stopRecording() {
        if (!recordingStopRequested.compareAndSet(false, true)) return
        gigaamPreviewJob?.cancel()
        viewModelScope.launch {
            update("Запись остановлена, обработка аудио") { it.copy(isRecording = false, isProcessing = true, error = null) }
            runCatching {
                val raw = container.recorder.stop()
                gigaamPreviewJob?.join()
                val processed = if (settings.value.audioPreprocessingEnabled) {
                    val preprocessingStarted = SystemClock.elapsedRealtime()
                    container.preprocessor.process(raw).also {
                        update("Аудио обработано за ${SystemClock.elapsedRealtime() - preprocessingStarted} мс: ${it.name}") { state -> state.copy(lastAudio = it) }
                    }
                } else {
                    update("Предобработка аудио отключена — исходный WAV передан в STT") { it.copy(lastAudio = raw) }
                    raw
                }
                val engine = container.stt.require(settings.value.sttEngine)
                recognitionEngineId = engine.id
                (engine as? ContextualSpeechToTextEngine)?.setCommandVocabularyEnabled(recognitionTarget == RecognitionTarget.COMMAND)
                (engine as? ContextualSpeechToTextEngine)?.setCommandScope(pendingCommandScope)
                (engine as? ContextualSpeechToTextEngine)?.setAwaitingDateResponse(
                    recognitionTarget == RecognitionTarget.COMMAND && pendingReminderDateClarification != null,
                )
                (engine as? ContextualSpeechToTextEngine)?.setContextHints(
                    if (recognitionTarget == RecognitionTarget.COMMAND) speechContextHints() else emptyList(),
                )
                update("STT: ${engine.displayName}") { it }
                val sttStarted = SystemClock.elapsedRealtime()
                engine.transcribe(processed) to (SystemClock.elapsedRealtime() - sttStarted)
            }.onSuccess { (recognition, elapsed) ->
                completeRecognition(
                    recognition,
                    "Распознано за ${elapsed} мс${recognition.confidence?.let { ", confidence=${"%.2f".format(it)}" }.orEmpty()}",
                )
            }.onFailure(::fail)
            recordingStopRequested.set(false)
        }
    }

    private fun startGigaamPreview() {
        gigaamPreviewJob?.cancel()
        _ui.value = _ui.value.copy(recordingPreview = "")
        gigaamPreviewJob = viewModelScope.launch {
            val committed = mutableMapOf<IntRange, String>()
            try {
                delay(800)
                while (isActive && _ui.value.isRecording && settings.value.gigaamLivePreview) {
                    val started = SystemClock.elapsedRealtime()
                    val samples = withContext(Dispatchers.IO) { container.recorder.previewSamples() }
                    if (samples != null) {
                        val text = container.gigaam.preview(samples, committed)
                        if (isActive && _ui.value.isRecording) _ui.value = _ui.value.copy(recordingPreview = text)
                    }
                    // Serial, adaptive updates: never enqueue an inference backlog.
                    delay((SystemClock.elapsedRealtime() - started).coerceIn(1000, 4000))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                update("Предварительный показ остановлен; полная запись будет распознана после отпускания кнопки: ${error.javaClass.simpleName}") { it }
            } finally {
                if (!recordingStopRequested.get()) withContext(NonCancellable + Dispatchers.IO) { container.gigaam.release() }
            }
        }
    }

    private fun completeRecognition(recognition: SpeechRecognition, log: String) {
        try { recognition.requireRecognizedSpeech() } catch (error: IllegalStateException) {
            fail(error)
            return
        }
        if (recognition.text.isNotBlank() && recognitionEngineId == "whisper") tips.offer(AssistantTipKind.WHISPER_SPEED)
        if (recognition.text.isNotBlank() && recognitionEngineId == "gigaam") {
            if (!tips.wasOffered(AssistantTipKind.GIGAAM_SPEED)) tips.offer(AssistantTipKind.GIGAAM_SPEED)
            else if (!settings.value.gigaamLivePreview) tips.offer(AssistantTipKind.LIVE_TEXT)
            if (settings.value.notePunctuationModel == "whisper") tips.offer(AssistantTipKind.PUNCTUATION)
        }
        recognition.notice?.let { publishNotice(it, null) }
        val target = recognitionTarget
        val sessionId = noteDictationSessionId
        val scope = pendingCommandScope
        val punctuationModel = if (settings.value.sttEngine == "whisper" && settings.value.whisperRawMode)
            "whisper" else settings.value.notePunctuationModel
        update(log) { it.copy(isRecording = false, isProcessing = true) }
        viewModelScope.launch {
            val punctuated = ru.nezabudka.app.speech.restoreVoicePunctuation(
                recognition, punctuationModel, { text, model ->
                    container.notePunctuation.restore(text, model,
                        fullPunctuation = target == RecognitionTarget.NOTE_DICTATION && settings.value.editorFullPunctuation)
                },
            ) { error ->
                update("Ошибка дополнительной пунктуации") { it.copy(
                    error = if (error is ru.nezabudka.app.speech.RupunctModelMissingException)
                        "Модель пунктуации не установлена. Сохранён текст распознавания."
                        else "Не удалось обработать знаки препинания. Сохранён текст распознавания.",
                    errorSettingsTarget = AssistantSettingsTarget.PUNCTUATION, whisperModelMissing = false) }
            }
            if (recognitionTarget != target || noteDictationSessionId != sessionId) return@launch
            if (target == RecognitionTarget.NOTE_DICTATION) {
                if (punctuated.text.isNotBlank()) offerPunctuationTip(editor = true)
                recognitionTarget = RecognitionTarget.COMMAND
                noteDictationSessionId = null
                update("Пунктуация голосового ввода: $punctuationModel") {
                    it.copy(
                        noteDictation = sessionId?.let { id -> punctuated.text.takeIf(String::isNotBlank)?.let { NoteDictationResult(id, it) } },
                        noteDictationActive = false, recordingPreview = null,
                        isRecording = false,
                        isProcessing = false,
                        awaitingSttConfirmation = false,
                    )
                }
                return@launch
            }
            voiceInputPending = punctuated.text.isNotBlank()
            val executeImmediately = settings.value.trustStt && !punctuated.requiresConfirmation &&
                (punctuated.confidence == null || punctuated.confidence >= STT_AUTO_EXECUTION_CONFIDENCE)
            update("Пунктуация голосового ввода: $punctuationModel") {
                it.copy(
                    recognizedText = punctuated.text.replaceFirstChar { it.titlecase() },
                    recordingPreview = null,
                    recognizedAlternatives = punctuated.alternatives,
                    isRecording = false,
                    isProcessing = false,
                    awaitingSttConfirmation = !executeImmediately && punctuated.text.isNotBlank(),
                )
            }
            if (executeImmediately) execute(scope)
        }
    }

    fun execute(scope: CommandScope = pendingCommandScope) = viewModelScope.launch {
        if (_ui.value.isProcessing) return@launch
        val text = _ui.value.recognizedText
        val alternatives = _ui.value.recognizedAlternatives
        update("Обработка подтверждённого текста") { it.copy(isProcessing = true, error = null, result = null, awaitingSttConfirmation = false) }
        val started = SystemClock.elapsedRealtime()
        val activeFolderName = notesUi.value.selectedFolderId?.let { id ->
            folders.value.firstOrNull { it.id == id }?.name
        }
        val nluRequestId = container.classicNlu.diagnostic.value.requestId
        val pendingClarification = pendingReminderDateClarification
        val wasVoiceInput = voiceInputPending
        voiceInputPending = false
        val previousFolders = if (!tips.wasOffered(AssistantTipKind.FOLDERS)) {
            runCatching { container.notes.observeFolders().first().map { it.id }.toSet() }.getOrNull()
        } else null
        val outcome = runCatching {
            container.execute(
                text,
                activeFolderName,
                CommandContext(scope),
                pendingClarification,
                alternatives,
            )
        }
        appendCommandDiagnostics(nluRequestId)
        outcome
            .onSuccess { result ->
                if (previousFolders != null) runCatching { container.notes.observeFolders().first() }
                    .getOrNull()?.firstOrNull { it.id !in previousFolders }?.let(::offerFolderSetup)
                pendingReminderDateClarification = result.reminderDateClarification
                update("Команда обработана за ${SystemClock.elapsedRealtime() - started} мс") {
                    it.afterCommandResult(result)
                }
                if (!result.commandRecognized) {
                    update("Команда не распознана") { it.copy(error = "Не удалось понять команду. Попробуйте изменить формулировку.", result = null,
                        errorSettingsTarget = AssistantSettingsTarget.NLU, whisperModelMissing = false) }
                    if (ru.nezabudka.app.domain.shouldOfferNluSetup(false, settings.value.classicNluEnabled, classicNluModel.value.installed))
                        tips.offer(AssistantTipKind.NLU_ENABLE)
                    publishNotice("Не удалось понять команду. Переформулируйте.", null, isError = true)
                } else {
                    publishNotice(result.message, result.effect)
                    if (wasVoiceInput && scope == CommandScope.ASSISTANT)
                        tips.assistantVoiceCommandCompleted(settings.value.trustStt)
                }
                if (result.effect is ActionEffect.ReminderCreated && container.execute.namedTimePhraseUsed(text) != null)
                    tips.offer(AssistantTipKind.TIME_OF_DAY)
                if (wasVoiceInput && result.effect is ActionEffect.NoteCreated) offerPunctuationTip(editor = false)
            }
            .onFailure(::fail)
    }

    private fun appendCommandDiagnostics(previousNluRequestId: Long) {
        if (!settings.value.showTechnicalLog) return
        val diagnostic = container.classicNlu.diagnostic.value
        val lines = mutableListOf<String>()
        if (diagnostic.requestId == previousNluRequestId) {
            lines += "Маршрут команды: стандартный Kotlin-разбор"
            lines += "Классическая NLU: не использовалась"
        } else {
            lines += "Маршрут команды: классическая NLU"
            lines += "Классическая NLU: использовалась, модель ${if (diagnostic.modelAlreadyLoaded) "уже была в памяти" else "загружена для запроса"}, ${diagnostic.elapsedMs} мс"
            lines += "NLU-вариант: ${diagnostic.inputText}"
            lines += "WordPiece: ${diagnostic.tokenPieces.joinToString(" | ").ifBlank { "нет токенов" }}"
            lines += "BIO: ${diagnostic.bioLabels.joinToString(" | ").ifBlank { "нет меток" }}"
            lines += "Intent: ${diagnostic.intentScores.joinToString("; ") { score -> "${score.intent}=${formatPercent(score.confidence)}" }}"
            val threshold = diagnostic.requiredConfidence?.let(::formatPercent) ?: "—"
            lines += "Выбрано: ${diagnostic.selectedIntent ?: "—"}; уверенность=${diagnostic.confidence?.let(::formatPercent) ?: "—"}; порог=$threshold"
            lines += "Слоты: ${diagnostic.slots.entries.joinToString { (name, value) -> "$name=«$value»" }.ifBlank { "не найдены" }}"
            lines += "Решение NLU: ${diagnostic.decision}"
            diagnostic.error?.let { lines += "Ошибка NLU: $it" }
        }
        update(lines.joinToString("\n")) { it }
    }

    private fun formatPercent(value: Double): String = "${(value * 1_000).toInt() / 10.0}%"

    private fun speechContextHints(): List<String> = buildList {
        // Folder names are a vocabulary hint. Previous reminder sentences can
        // bias decoding of unrelated speech, especially after a bad transcript.
        notesUi.value.selectedFolderId?.let { id -> folders.value.firstOrNull { it.id == id }?.name }?.let(::add)
        folders.value.asSequence().map { it.name }.filter(String::isNotBlank).forEach(::add)
    }.distinctBy { it.lowercase().replace('ё', 'е') }.take(8)

    fun primaryAction(scope: CommandScope = CommandScope.ASSISTANT) {
        val state = _ui.value
        when {
            state.isRecording -> toggleRecording()
            state.recognizedText.isNotBlank() -> execute(scope)
            else -> beginPushToTalk(scope)
        }
    }

    fun undoLastAction() {
        val notice = (_event.value as? AssistantUiEvent.Action)?.notice ?: return
        val effect = notice.effect ?: return
        noticeDismissJob?.cancel()
        viewModelScope.launch {
            runCatching {
                when (effect) {
                    is ActionEffect.ReminderCreated -> {
                        container.reminderActions.delete(effect.reminder.id)
                    }
                    is ActionEffect.ReminderDeleted -> {
                        container.reminderActions.restore(effect.reminder)
                    }
                    is ActionEffect.NoteCreated -> container.notes.deleteNote(effect.note.id)
                    is ActionEffect.FolderCreated -> container.notes.deleteFolder(effect.folder.id)
                    is ActionEffect.FolderDeleted -> container.notes.restoreFolder(effect.snapshot)
                    is ActionEffect.RemindersListed -> Unit
                    is ActionEffect.ReminderDateRequested -> Unit
                }
            }.onSuccess {
                _event.value = null
                _ui.value = _ui.value.copy(result = "Последнее действие отменено")
            }.onFailure(::fail)
        }
    }

    fun beginPushToTalk(scope: CommandScope = CommandScope.ASSISTANT) {
        val state = _ui.value
        if (!state.isProcessing && !state.isRecording && state.recognizedText.isBlank()) {
            recognitionTarget = RecognitionTarget.COMMAND
            noteDictationSessionId = null
            pendingCommandScope = scope
            toggleRecording()
        }
    }

    fun beginNoteDictation(sessionId: Long) {
        val state = _ui.value
        if (state.isProcessing || state.isRecording) return
        recognitionTarget = RecognitionTarget.NOTE_DICTATION
        noteDictationSessionId = sessionId
        update("Голосовой ввод заметки запущен") {
            it.copy(
                noteDictation = null,
                noteDictationActive = true,
                error = null,
                awaitingSttConfirmation = false,
            )
        }
        toggleRecording()
    }

    fun endNoteDictation(sessionId: Long) {
        if (
            _ui.value.isRecording &&
            recognitionTarget == RecognitionTarget.NOTE_DICTATION &&
            noteDictationSessionId == sessionId
        ) {
            toggleRecording()
        }
    }

    fun consumeNoteDictation(sessionId: Long) {
        if (_ui.value.noteDictation?.sessionId == sessionId) {
            _ui.value = _ui.value.copy(noteDictation = null)
        }
    }

    fun cancelNoteDictation(sessionId: Long) {
        if (_ui.value.noteDictation?.sessionId == sessionId) {
            _ui.value = _ui.value.copy(noteDictation = null)
        }
        if (recognitionTarget != RecognitionTarget.NOTE_DICTATION ||
            noteDictationSessionId != sessionId
        ) return
        gigaamPreviewJob?.cancel()
        recognitionTarget = RecognitionTarget.COMMAND
        noteDictationSessionId = null
        (runCatching {
            container.stt.require(settings.value.sttEngine)
        }.getOrNull() as? LiveSpeechToTextEngine)?.cancel()
        if (container.recorder.isRecording && !recordingStopRequested.get()) {
            viewModelScope.launch {
                runCatching { container.recorder.stop() }
            }
        }
        update("Голосовой ввод заметки отменён") {
            it.copy(
                recordingPreview = null,
                isRecording = false,
                isProcessing = false,
                noteDictationActive = false,
            )
        }
    }

    fun endPushToTalk() {
        if (_ui.value.isRecording) toggleRecording()
    }

    fun onAppBackgrounded() {
        gigaamPreviewJob?.cancel()
        stopPlayback()
        (runCatching {
            container.stt.require(settings.value.sttEngine)
        }.getOrNull() as? LiveSpeechToTextEngine)?.cancel()
        if (container.recorder.isRecording && !recordingStopRequested.get()) {
            viewModelScope.launch {
                runCatching { container.recorder.stop() }.onFailure(::fail)
            }
        }
        if (_ui.value.isRecording) {
            recognitionTarget = RecognitionTarget.COMMAND
            noteDictationSessionId = null
            update("Запись отменена при переходе приложения в фон") {
                it.copy(
                    recordingPreview = null,
                    isRecording = false,
                    isProcessing = false,
                    noteDictationActive = false,
                )
            }
        }
    }

    fun deleteReminder(id: Long): Job = reminderFeature.dispatch(ReminderUiEvent.Delete(id))

    fun moveReminder(id: Long, beforeId: Long?): Job = reminderFeature.move(id, beforeId)

    fun restoreReminder(reminder: Reminder): Job =
        reminderFeature.dispatch(ReminderUiEvent.Restore(reminder))

    fun createReminder(
        text: String,
        remindAt: java.time.LocalDateTime?,
        recurrence: String?,
        favorite: Boolean,
        onCreated: () -> Unit = {},
    ): Job = reminderFeature.create(text, remindAt, recurrence, favorite, onCreated)

    fun saveReminder(reminder: Reminder, text: String, remindAt: java.time.LocalDateTime?, recurrence: String?, favorite: Boolean, completed: Boolean, onSaved: () -> Unit = {}): Job {
        return reminderFeature.save(reminder, text, remindAt, recurrence, favorite, completed, onSaved)
    }

    fun changeReminderTime(reminder: Reminder, remindAt: java.time.LocalDateTime?, recurrence: String? = reminder.recurrence): Job {
        return reminderFeature.rescheduleWithRecurrence(reminder, remindAt, recurrence, ::dismissActionNotice)
    }

    fun snoozeReminder(reminder: Reminder, remindAt: java.time.LocalDateTime, onSaved: () -> Unit = {}): Job {
        return reminderFeature.reschedule(reminder, remindAt, onSaved)
    }

    fun toggleReminderFavorite(reminder: Reminder): Job {
        return reminderFeature.dispatch(ReminderUiEvent.ToggleFavorite(reminder))
    }

    fun toggleReminderCompleted(reminder: Reminder): Job {
        return reminderFeature.dispatch(ReminderUiEvent.ToggleCompleted(reminder))
    }

    fun setReminderCompleted(id: Long, completed: Boolean): Job {
        return reminderFeature.dispatch(ReminderUiEvent.SetCompleted(id, completed))
    }

    fun deleteCompletedReminders(): Job = reminderFeature.dispatch(ReminderUiEvent.DeleteCompleted)

    fun deleteOverdueReminders(): Job =
        reminderFeature.dispatch(ReminderUiEvent.DeleteOverdue(java.time.LocalDateTime.now()))

    fun createNoteFolder(name: String, onCreated: (NoteFolder) -> Unit = {}): Job =
        notesFeature.createFolder(name, publish = true, onCreated)

    fun createNoteFolderSilently(name: String, onCreated: (NoteFolder) -> Unit = {}): Job =
        notesFeature.createFolder(name, publish = false, onCreated)

    fun renameNoteFolder(folder: NoteFolder, name: String): Job = notesFeature.renameFolder(folder, name)
    fun moveNoteFolder(folder: NoteFolder, direction: Int): Job = notesFeature.moveFolder(folder, direction)
    fun moveNote(id: Long, beforeId: Long?): Job = notesFeature.moveNote(id, beforeId)
    fun deleteNoteFolder(folder: NoteFolder): Job = notesFeature.deleteFolder(folder)

    fun saveNote(
        id: Long?,
        folderNames: List<String>,
        title: String,
        text: String,
        pinned: Boolean,
        archived: Boolean,
        color: String?,
        onSaved: () -> Unit,
    ): Job = notesFeature.save(id, folderNames, title, text, pinned, archived, color, onSaved)

    fun trashNote(note: Note) { notesFeature.dispatch(NoteUiEvent.ToggleTrashed(note)) }
    fun pinNotes(notes: List<Note>) { notesFeature.dispatch(NoteUiEvent.TogglePinned(notes)) }
    fun colorNotes(notes: List<Note>, color: String?) { notesFeature.dispatch(NoteUiEvent.SetColor(notes, color)) }
    fun archiveNotes(notes: List<Note>) { notesFeature.dispatch(NoteUiEvent.ToggleArchived(notes)) }
    fun trashNotes(notes: List<Note>) { notesFeature.dispatch(NoteUiEvent.MoveToTrash(notes)) }
    fun emptyNoteTrash() { notesFeature.dispatch(NoteUiEvent.EmptyTrash) }
    fun setNotesFolder(notes: List<Note>, folderName: String, included: Boolean) {
        notesFeature.dispatch(NoteUiEvent.SetFolder(notes, folderName, included))
    }

    private fun offerPunctuationTip(editor: Boolean) {
        if (settings.value.sttEngine == "whisper" && settings.value.whisperRawMode) return
        if (settings.value.notePunctuationModel == "whisper") tips.offer(AssistantTipKind.PUNCTUATION)
        else if (editor && punctuationModel.value.installed && !settings.value.editorFullPunctuation)
            tips.offer(AssistantTipKind.EDITOR_PUNCTUATION)
    }

    private fun publishNotice(message: String, effect: ActionEffect?, isError: Boolean = false) {
        when (effect) {
            is ActionEffect.FolderCreated -> offerFolderSetup(effect.folder)
            is ActionEffect.ReminderCreated -> offerReminderSetup(effect.reminder)
            else -> Unit
        }
        val notice = ActionNotice(SystemClock.elapsedRealtime(), message, effect, isError)
        _event.value = AssistantUiEvent.Action(notice)
        noticeDismissJob?.cancel()
        val durationSeconds = settings.value.popupDurationSeconds
        if (durationSeconds > 0 && effect !is ActionEffect.ReminderDateRequested) {
            noticeDismissJob = viewModelScope.launch {
                delay(durationSeconds * 1_000L)
                val current = (_event.value as? AssistantUiEvent.Action)?.notice
                if (current?.id == notice.id) _event.value = null
            }
        }
    }

    fun playLast() {
        val file = _ui.value.lastAudio ?: return
        stopPlayback()
        playbackJob = viewModelScope.launch(Dispatchers.IO) {
            val player = MediaPlayer()
            var started = false
            try {
                player.setDataSource(file.absolutePath)
                player.prepare()
                if (!isActive) return@launch
                player.setOnCompletionListener { completed ->
                    synchronized(playbackLock) {
                        if (playback === completed) playback = null
                    }
                    completed.release()
                }
                synchronized(playbackLock) { playback = player }
                player.start()
                started = true
            } catch (error: Throwable) {
                if (error !is CancellationException) fail(error)
            } finally {
                if (!started) {
                    synchronized(playbackLock) {
                        if (playback === player) playback = null
                    }
                    runCatching { player.release() }
                }
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        val player = synchronized(playbackLock) {
            playback.also { playback = null }
        }
        runCatching { player?.release() }
    }

    private fun fail(error: Throwable) {
        gigaamPreviewJob?.cancel()
        if (container.recorder.isRecording) {
            (container.recorder as? CancellableAudioRecorder)?.cancel()
        }
        recognitionTarget = RecognitionTarget.COMMAND
        noteDictationSessionId = null
        pendingCommandScope = CommandScope.ASSISTANT
        update("Ошибка: ${error.message}") {
            it.copy(
                isProcessing = false,
                recordingPreview = null,
                isRecording = false,
                noteDictationActive = false,
                error = assistantErrorText(error),
                errorSettingsTarget = when {
                    error is SecurityException && error.message.orEmpty().contains("микрофон", ignoreCase = true) -> AssistantSettingsTarget.MICROPHONE
                    error.message.orEmpty().contains("NLU", ignoreCase = true) -> AssistantSettingsTarget.NLU
                    error.message.orEmpty().contains("RUPunct", ignoreCase = true) ||
                        error.message.orEmpty().contains("пунктуа", ignoreCase = true) -> AssistantSettingsTarget.PUNCTUATION
                    error is WhisperModelMissingException || settings.value.sttEngine == "whisper" -> AssistantSettingsTarget.SPEECH
                    settings.value.sttEngine in setOf("yandex", "google_cloud", "vk_cloud") -> AssistantSettingsTarget.CLOUD
                    else -> AssistantSettingsTarget.SPEECH
                },
                whisperModelMissing = error is WhisperModelMissingException || error is ru.nezabudka.app.speech.GigaamModelMissingException,
            )
        }
        assistantErrorNoticeText(error)?.let { publishNotice(it, null, isError = true) }
        if (error is WhisperModelMissingException || error is ru.nezabudka.app.speech.GigaamModelMissingException) openWhisperModelsMenu()
    }
    private fun update(log: String, change: (AssistantUiState) -> AssistantUiState) {
        _ui.value = change(_ui.value).let { it.copy(log = (it.log + log).takeLast(200)) }
    }

    override fun onCleared() {
        stopPlayback()
        (container.recorder as? CancellableAudioRecorder)?.cancel()
        (runCatching { container.stt.require(settings.value.sttEngine) }.getOrNull() as? LiveSpeechToTextEngine)?.cancel()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AssistantViewModel(container) as T
    }

    companion object {
        private const val STT_AUTO_EXECUTION_CONFIDENCE = 0.80f
    }

    private enum class RecognitionTarget { COMMAND, NOTE_DICTATION }
}

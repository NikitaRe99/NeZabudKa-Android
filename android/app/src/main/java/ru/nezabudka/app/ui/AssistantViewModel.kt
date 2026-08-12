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
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.domain.CommandContext
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.data.detectDeviceCapabilities
import ru.nezabudka.app.ports.CancellableAudioRecorder
import ru.nezabudka.app.ports.ContextualSpeechToTextEngine
import ru.nezabudka.app.ports.LiveSpeechToTextEngine
import ru.nezabudka.app.ports.SpeechRecognition
import ru.nezabudka.app.speech.WhisperDownload
import ru.nezabudka.app.BuildConfig
import ru.nezabudka.app.speech.AndroidSpeechRecognizerEngine
import ru.nezabudka.app.notifications.NotificationChannels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class AssistantUiState(
    val recognizedText: String = "",
    val recognizedAlternatives: List<String> = emptyList(),
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val result: String? = null,
    val log: List<String> = emptyList(),
    val lastAudio: File? = null,
    val awaitingSttConfirmation: Boolean = false,
    val noteDictation: NoteDictationResult? = null,
    val noteDictationActive: Boolean = false,
)

@Immutable
data class VoiceActionUiState(
    val recognizedText: String = "",
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val awaitingSttConfirmation: Boolean = false,
)

@Immutable
data class NoteDictationUiState(
    val result: NoteDictationResult? = null,
    val active: Boolean = false,
    val recording: Boolean = false,
)

internal fun AssistantUiState.voiceActionState() = VoiceActionUiState(
    recognizedText = recognizedText,
    isRecording = isRecording,
    isProcessing = isProcessing,
    awaitingSttConfirmation = awaitingSttConfirmation,
)

internal fun AssistantUiState.noteDictationState() = NoteDictationUiState(
    result = noteDictation,
    active = noteDictationActive,
    recording = isRecording,
)

data class ActionNotice(val id: Long, val message: String, val effect: ActionEffect?)
data class NoteDictationResult(val sessionId: Long, val text: String)

sealed interface AssistantUiEvent {
    data class Action(val notice: ActionNotice) : AssistantUiEvent
}

enum class ReminderSort(val key: String, val title: String) {
    MY_ORDER("my_order", "В моём порядке"), DATE("date", "По дате"),
    RECENTLY_MARKED("recently_marked", "Сначала отмеченные"), TITLE("title", "По названию");

    companion object {
        fun fromKey(value: String): ReminderSort = entries.firstOrNull { it.key == value } ?: MY_ORDER
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(AssistantUiState(lastAudio = container.preprocessor.lastProcessed()))
    val ui: StateFlow<AssistantUiState> = _ui
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
    private val reminderFeature = ReminderFeature(
        container.reminders, container.reminderActions, viewModelScope, ::fail,
    )
    private val notesFeature = NotesFeature(
        container.notes, viewModelScope, ::publishNotice, ::fail,
    )
    val reminders: StateFlow<List<Reminder>> = reminderFeature.reminders
    val notesUi: StateFlow<NotesUiState> = notesFeature.state
    val folders: StateFlow<List<NoteFolder>> = notesFeature.folders
    val engines = container.stt.all()
    val whisperModels = container.whisperModels.state
    val whisperRuntime = container.whisper.runtime
    val whisperDownloads: List<WhisperDownload> = container.whisperModels.downloads
    val modelStorage = container.modelStorage.state
    val classicNluModel = container.classicNlu.modelState
    val classicNluDownloadAvailable: Boolean = BuildConfig.CLASSIC_NLU_MODEL_URL.isNotBlank()
    val cloudSttCredentials = container.cloudSttCredentials.state
    val deviceCapabilities = detectDeviceCapabilities(container.app)
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
        if (settings.value.localOnly && engine in CLOUD_STT_IDS) return
        val selected = container.stt.all().firstOrNull { it.id == engine }
        if (selected is AndroidSpeechRecognizerEngine && !selected.isAvailable(settings.value.localOnly)) {
            fail(IllegalStateException(selected.availabilityDescription(settings.value.localOnly)))
            return
        }
        val previousEngine = settings.value.sttEngine
        container.settings.setSttEngine(engine)
        if (previousEngine == "whisper" && engine != "whisper") {
            viewModelScope.launch(Dispatchers.Default) { container.whisper.release() }
        }
    }
    fun setTheme(value: String) = container.settings.setTheme(value)
    fun setCustomTheme(value: CustomThemeSettings) = container.settings.setCustomTheme(value)
    fun setPopupDurationSeconds(value: Int) = container.settings.setPopupDurationSeconds(value)
    fun setClassicNluEnabled(enabled: Boolean) {
        container.settings.setClassicNluEnabled(enabled)
        if (!enabled) viewModelScope.launch(Dispatchers.Default) { container.classicNlu.release() }
    }
    fun setKeepWhisperModelLoaded(enabled: Boolean) {
        container.settings.setKeepWhisperModelLoaded(enabled)
        if (!enabled) viewModelScope.launch(Dispatchers.Default) {
            container.whisper.release()
        }
    }
    fun setWhisperThreadCount(value: Int) = container.settings.setWhisperThreadCount(value)
    fun setLocalOnly(enabled: Boolean) {
        container.settings.setLocalOnly(enabled)
        val android = container.stt.all().filterIsInstance<AndroidSpeechRecognizerEngine>().firstOrNull()
        val selectedCannotStayLocal = settings.value.sttEngine in CLOUD_STT_IDS ||
            (settings.value.sttEngine == "android" && android?.isAvailable(localOnly = true) != true)
        if (enabled && selectedCannotStayLocal) {
            container.settings.setSttEngine("whisper")
        }
    }
    fun setExternalDataBackupEnabled(enabled: Boolean) = viewModelScope.launch {
        runCatching {
            if (enabled) withContext(Dispatchers.IO) { container.startDataBackupServices() }
            container.dataBackup.setEnabled(enabled)
        }
            .onSuccess { result ->
                if (result.restored) {
                    withContext(Dispatchers.IO) {
                        container.whisperModels.refresh()
                        NotificationChannels.recreate(container.app)
                        container.restoreReminderSchedules()
                    }
                }
                update(result.message) { it.copy(error = null) }
            }
            .onFailure(::fail)
    }
    fun setTimeOfDaySettings(values: List<TimeOfDaySetting>) = container.settings.setTimeOfDaySettings(values)
    fun setAudioPreprocessing(enabled: Boolean) = container.settings.setAudioPreprocessing(enabled)
    fun setShowPlaybackButton(enabled: Boolean) = container.settings.setShowPlaybackButton(enabled)
    fun setShowTechnicalLog(enabled: Boolean) = container.settings.setShowTechnicalLog(enabled)
    fun setTrustStt(enabled: Boolean) = container.settings.setTrustStt(enabled)
    fun setReminderSort(sort: ReminderSort) = container.settings.setReminderSort(sort.key)
    fun setNoteSort(value: String) = container.settings.setNoteSort(value)
    fun setNoteColumns(value: Int) = container.settings.setNoteColumns(value)
    fun setNoteFolderInterface(value: String) = container.settings.setNoteFolderInterface(value)
    fun setNoteTrashRetentionDays(value: Int) = container.setNoteTrashRetentionDays(value)
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
    fun setReminderAlarmMode(enabled: Boolean) {
        container.settings.setReminderAlarmMode(enabled)
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
    fun saveGoogleCredentials(accessToken: String) = runCatching {
        container.cloudSttCredentials.saveGoogle(accessToken)
    }.onFailure(::fail)
    fun saveVkCredentials(serviceToken: String, endpoint: String) = runCatching {
        container.cloudSttCredentials.saveVk(serviceToken, endpoint)
    }.onFailure(::fail)
    fun selectModelsDirectory(uri: Uri) = viewModelScope.launch {
        runCatching {
            val result = container.dataBackup.selectRoot(uri)
            withContext(Dispatchers.IO) {
                container.whisperModels.refresh()
                container.classicNlu.refreshModel()
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
            if (container.whisperModels.currentSelectedModelName() == fileName) return@runCatching
            container.whisper.release()
            container.whisperModels.select(fileName)
        }.onFailure(::fail)
    }
    fun downloadWhisperModel(model: WhisperDownload) = viewModelScope.launch {
        update("Подготовка фоновой загрузки Whisper ${model.fileName}") { it.copy(error = null) }
        runCatching { container.whisperModels.download(model) }
            .onSuccess { update("Whisper-модель поставлена в фоновую загрузку") { it } }
            .onFailure(::fail)
    }
    fun importWhisperModel(uri: Uri) = viewModelScope.launch {
        update("Импорт GGML-модели") { it.copy(error = null) }
        runCatching { container.whisperModels.import(uri) }
            .onSuccess { update("Модель импортирована в пользовательскую папку models/whisper") { it } }
            .onFailure(::fail)
    }

    fun downloadClassicNluModel() = viewModelScope.launch {
        update("Подготовка фоновой загрузки NLU-модели") { it.copy(error = null) }
        runCatching { container.classicNlu.downloadModel(BuildConfig.CLASSIC_NLU_MODEL_URL) }
            .onSuccess { update("NLU-модель поставлена в фоновую загрузку") { it } }
            .onFailure(::fail)
    }

    fun importClassicNluModel(uri: Uri) = viewModelScope.launch {
        update("Импорт NLU-модели") { it.copy(error = null) }
        runCatching { container.classicNlu.importModel(uri) }
            .onSuccess { update("NLU-модель сохранена в models/nlu") { it } }
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
        update("Android SpeechRecognizer слушает микрофон") { it.copy(isRecording = true, isProcessing = false, error = null, result = null) }
        val onResult: (SpeechRecognition) -> Unit = { recognition ->
            completeRecognition(recognition, "Android SpeechRecognizer завершил распознавание")
        }
        if (engine is AndroidSpeechRecognizerEngine) {
            engine.start(settings.value.localOnly, onResult, ::fail)
        } else {
            engine.start(onResult, ::fail)
        }
    }

    private fun startRecording() {
        recordingStopRequested.set(false)
        runCatching { container.recorder.start() }
            .onSuccess {
                update("Запись начата") {
                    it.copy(isRecording = true, error = null, result = null, awaitingSttConfirmation = false)
                }
            }
            .onFailure(::fail)
    }

    private fun stopRecording() {
        if (!recordingStopRequested.compareAndSet(false, true)) return
        viewModelScope.launch {
            update("Запись остановлена, обработка аудио") { it.copy(isRecording = false, isProcessing = true, error = null) }
            runCatching {
                val raw = container.recorder.stop()
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

    private fun completeRecognition(recognition: SpeechRecognition, log: String) {
        if (recognitionTarget == RecognitionTarget.NOTE_DICTATION) {
            val sessionId = noteDictationSessionId
            recognitionTarget = RecognitionTarget.COMMAND
            noteDictationSessionId = null
            update(log) {
                it.copy(
                    noteDictation = sessionId?.let { id ->
                        recognition.text.takeIf(String::isNotBlank)?.let { text -> NoteDictationResult(id, text) }
                    },
                    noteDictationActive = false,
                    isRecording = false,
                    isProcessing = false,
                    awaitingSttConfirmation = false,
                )
            }
            return
        }
        val executeImmediately = settings.value.trustStt &&
            (recognition.confidence == null || recognition.confidence >= STT_AUTO_EXECUTION_CONFIDENCE)
        update(log) {
            it.copy(
                recognizedText = recognition.text,
                recognizedAlternatives = recognition.alternatives,
                isRecording = false,
                isProcessing = false,
                awaitingSttConfirmation = !executeImmediately && recognition.text.isNotBlank(),
            )
        }
        if (executeImmediately) execute(pendingCommandScope)
    }

    fun execute(scope: CommandScope = pendingCommandScope) = viewModelScope.launch {
        val text = _ui.value.recognizedText
        val alternatives = _ui.value.recognizedAlternatives
        update("Обработка подтверждённого текста") { it.copy(isProcessing = true, error = null, result = null, awaitingSttConfirmation = false) }
        val started = SystemClock.elapsedRealtime()
        val activeFolderName = notesUi.value.selectedFolderId?.let { id ->
            folders.value.firstOrNull { it.id == id }?.name
        }
        val nluRequestId = container.classicNlu.diagnostic.value.requestId
        val pendingClarification = pendingReminderDateClarification
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
                pendingReminderDateClarification = result.reminderDateClarification
                update("Команда обработана за ${SystemClock.elapsedRealtime() - started} мс") {
                    it.copy(
                        isProcessing = false,
                        result = result.message,
                        recognizedText = "",
                        recognizedAlternatives = emptyList(),
                        awaitingSttConfirmation = false,
                    )
                }
                publishNotice(result.message, result.effect)
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
        folders.value.asSequence().map { it.name }.filter(String::isNotBlank).take(8).forEach(::add)
        reminders.value.asSequence().map { it.text }.filter(String::isNotBlank).take(8).forEach(::add)
    }.distinctBy { it.lowercase().replace('ё', 'е') }

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
        recognitionTarget = RecognitionTarget.COMMAND
        noteDictationSessionId = null
        (runCatching {
            container.stt.require(settings.value.sttEngine)
        }.getOrNull() as? LiveSpeechToTextEngine)?.cancel()
        if (container.recorder.isRecording) {
            viewModelScope.launch {
                runCatching { container.recorder.stop() }
            }
        }
        update("Голосовой ввод заметки отменён") {
            it.copy(
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
        stopPlayback()
        (runCatching {
            container.stt.require(settings.value.sttEngine)
        }.getOrNull() as? LiveSpeechToTextEngine)?.cancel()
        if (container.recorder.isRecording) {
            viewModelScope.launch {
                runCatching { container.recorder.stop() }.onFailure(::fail)
            }
        }
        if (_ui.value.isRecording) {
            recognitionTarget = RecognitionTarget.COMMAND
            noteDictationSessionId = null
            update("Запись отменена при переходе приложения в фон") {
                it.copy(
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

    fun changeReminderTime(reminder: Reminder, remindAt: java.time.LocalDateTime?): Job {
        return reminderFeature.reschedule(reminder, remindAt, ::dismissActionNotice)
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

    private fun publishNotice(message: String, effect: ActionEffect?) {
        val notice = ActionNotice(SystemClock.elapsedRealtime(), message, effect)
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
        if (container.recorder.isRecording) {
            (container.recorder as? CancellableAudioRecorder)?.cancel()
        }
        recognitionTarget = RecognitionTarget.COMMAND
        noteDictationSessionId = null
        pendingCommandScope = CommandScope.ASSISTANT
        update("Ошибка: ${error.message}") {
            it.copy(
                isProcessing = false,
                isRecording = false,
                noteDictationActive = false,
                error = error.message ?: "Неизвестная ошибка",
            )
        }
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
        private val CLOUD_STT_IDS = setOf("yandex", "google_cloud", "vk_cloud")
        private const val STT_AUTO_EXECUTION_CONFIDENCE = 0.80f
    }

    private enum class RecognitionTarget { COMMAND, NOTE_DICTATION }
}

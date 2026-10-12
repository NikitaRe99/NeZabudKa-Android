package ru.nezabudka.app.speech

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.net.Uri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ru.nezabudka.app.data.ModelDownloadWorker
import ru.nezabudka.app.data.UserModelsStorage
import ru.nezabudka.app.data.AndroidModelLibrary
import ru.nezabudka.app.BuildConfig
import ru.nezabudka.app.domain.estimatedWhisperPeakBytes
import ru.nezabudka.app.domain.effectiveWhisperThreadCount
import ru.nezabudka.app.domain.hasNativeMemoryHeadroom
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.ports.SpeechRecognition
import ru.nezabudka.app.ports.SpeechToTextEngine
import ru.nezabudka.app.ports.ContextualSpeechToTextEngine
import ru.nezabudka.app.ports.PortableModelSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import ru.nezabudka.app.domain.WhisperThreadTiming
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class WhisperModelMissingException : IllegalStateException("Модель Whisper не установлена. Выберите и скачайте модель для голосового ввода.")

data class WhisperDownload(
    val id: String,
    val title: String,
    val description: String,
    val fileName: String,
    val approximateSize: String,
    val url: String,
)

data class WhisperModelsState(
    val installed: List<String> = emptyList(),
    val selectedName: String? = null,
    val downloadingId: String? = null,
    val progress: Float? = null,
    val error: String? = null,
)

class WhisperModelManager(
    private val context: Context,
    private val storage: UserModelsStorage,
) : PortableModelSelection {
    private val library = AndroidModelLibrary(
        context, storage, "whisper", "bin", "whisper_models", "ggml-imported.bin", ::validate,
    )
    private val _state = MutableStateFlow(WhisperModelsState())
    val state: StateFlow<WhisperModelsState> = _state
    override val selectedModelName: Flow<String?> = state
        .map { modelState: WhisperModelsState -> modelState.selectedName }
        .distinctUntilChanged()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManager.getInstance(context)
    }
    private val observerStarted = AtomicBoolean(false)

    val downloads = listOf(
        WhisperDownload("tiny", "Tiny", "Максимальная скорость, минимальная точность", "ggml-tiny.bin", "≈75 МБ", hf("ggml-tiny.bin")),
        WhisperDownload("base", "Base", "Оптимальный вариант для большинства телефонов", "ggml-base.bin", "≈142 МБ", hf("ggml-base.bin")),
        WhisperDownload("small-q5", "Small Q5", "Выше точность при умеренном размере", "ggml-small-q5_1.bin", "≈181 МБ", hf("ggml-small-q5_1.bin")),
        WhisperDownload(
            "small-ru",
            "Small RU Q5",
            "Русская модель повышенной точности",
            "ggml-small-ru-v2-q5_1.bin",
            "≈190 МБ",
            BuildConfig.WHISPER_SMALL_RU_URL,
        ),
    )

    fun start() {
        if (!observerStarted.compareAndSet(false, true)) return
        scope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.uniqueWorkName(ModelDownloadWorker.KIND_WHISPER))
                .collectLatest(::applyWorkState)
        }
    }

    fun selectedModel(alreadyLoadedPath: String? = null, pcmWavBytes: Long = 0L): File {
        val retained = alreadyLoadedPath?.let(::File)?.takeIf { file ->
            file.isFile && file.name == state.value.selectedName
        }
        return (retained ?: library.selectedFile(
            "Модель Whisper не установлена в пользовательском каталоге models/whisper",
            missingException = { WhisperModelMissingException() },
        )).also {
            ensureDeviceCapacity(it, pcmWavBytes, it.absolutePath == alreadyLoadedPath)
        }
    }

    fun recommendedThreadCount(configured: Int = 0): Int {
        val manager = context.getSystemService(ActivityManager::class.java)
        return effectiveWhisperThreadCount(
            configured = configured,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            lowRamDevice = manager.isLowRamDevice,
            memoryClassMb = manager.memoryClass,
        )
    }

    fun select(fileName: String) {
        library.select(fileName)
        refresh()
    }

    override fun currentSelectedModelName(): String? = state.value.selectedName

    override fun selectIfAvailable(fileName: String) = select(fileName)

    suspend fun download(model: WhisperDownload) = withContext(Dispatchers.IO) {
        _state.value = scan().copy(downloadingId = model.id, progress = 0f, error = null)
        ModelDownloadWorker.enqueue(
            context, ModelDownloadWorker.KIND_WHISPER, model.id, model.title, model.fileName, model.url,
        )
    }

    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        library.import(uri)
        refresh()
    }

    fun refresh() { _state.value = scan() }

    private fun applyWorkState(values: List<WorkInfo>) {
        val work = values.firstOrNull {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED || it.state == WorkInfo.State.RUNNING
        } ?: values.lastOrNull() ?: return
        val modelId = work.tags.firstOrNull { it.startsWith("model-id:") }?.substringAfter(':')
            ?: _state.value.downloadingId
        when (work.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                _state.value = scan().copy(downloadingId = modelId, progress = 0f)
            }
            WorkInfo.State.RUNNING -> {
                val copied = work.progress.getLong(ModelDownloadWorker.KEY_COPIED, 0L)
                val total = work.progress.getLong(ModelDownloadWorker.KEY_TOTAL, -1L)
                _state.value = scan().copy(
                    downloadingId = modelId,
                    progress = if (total > 0) copied.toFloat() / total else null,
                )
            }
            WorkInfo.State.SUCCEEDED -> refresh()
            WorkInfo.State.FAILED -> if (_state.value.downloadingId != null) {
                _state.value = scan().copy(error = work.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Ошибка загрузки модели")
            }
            WorkInfo.State.CANCELLED -> if (_state.value.downloadingId != null) refresh()
        }
    }

    private fun scan(): WhisperModelsState {
        val snapshot = library.snapshot()
        return WhisperModelsState(installed = snapshot.installed, selectedName = snapshot.selectedName)
    }

    fun idleRetentionMillis(): Long {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return automaticWhisperIdleRetentionMillis(
            lowRamDevice = manager.isLowRamDevice,
            memoryClassMb = manager.memoryClass,
            availableRamBytes = memory.availMem,
        )
    }

    private fun ensureDeviceCapacity(model: File, pcmWavBytes: Long, alreadyLoaded: Boolean) {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val estimatedPeak = estimatedWhisperPeakBytes(model.length(), pcmWavBytes, modelWeightsRetained = alreadyLoaded)
        require(hasNativeMemoryHeadroom(estimatedPeak, memory.availMem, memory.totalMem)) {
            "Недостаточно свободной памяти для ${model.name} и этой записи. Освободите память или выберите модель Whisper Tiny."
        }
    }

    private fun validate(file: File) {
        require(file.length() > 1_000_000) { "Файл слишком мал и не похож на GGML-модель" }
    }

    companion object {
        private fun hf(file: String) = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file?download=true"
    }
}

internal object WhisperNative {
    init {
        // Some Android 10 linkers fail to resolve this transitive dependency
        // from the app namespace even though both libraries are packaged.
        System.loadLibrary("c++_shared")
        System.loadLibrary("localassistant_whisper")
    }
    external fun transcribeWav(
        modelPath: String,
        wavPath: String,
        vadModelPath: String,
        language: String,
        prompt: String,
        threadCount: Int,
        audioContext: Int,
        maxTokens: Int,
        singleSegment: Boolean,
        beamSize: Int = 1,
        timeLimitMs: Int = 0,
        temperatureIncrement: Float = 0.2f,
    ): String
    external fun cancel()
    external fun release()
    external fun detectSpeechSegments(
        vadModelPath: String,
        wavPath: String,
        threadCount: Int,
        maxSpeechSeconds: Float,
    ): LongArray
    external fun lastConfidence(): Float
    /** all-token mean, text mean/log-mean/min, weak fraction/count, no-speech, load/decode ms. */
    external fun lastMetrics(): DoubleArray
    external fun systemInfo(): String
}

internal data class WhisperInferencePolicy(
    val audioContext: Int,
    val maxTokens: Int,
    val singleSegment: Boolean,
    val beamSize: Int = 1,
    val timeLimitMs: Int = 0,
)

internal fun whisperInferencePolicy(
    wavBytes: Long,
    tinyModel: Boolean = false,
    accurateMode: Boolean = false,
): WhisperInferencePolicy {
    val samples = ((wavBytes - 44L).coerceAtLeast(0L) / 2L)
    // Whisper's standard 30-second window contains 1500 encoder positions.
    // The measured fast window is reserved for short commands. Longer speech
    // keeps about half a second of encoder padding so phrase endings are not
    // lost without rounding medium recordings to an unnecessarily large graph.
    val padding = if (samples <= FAST_CONTEXT_MAX_SAMPLES) 4L else 24L
    val requiredContext = (samples + 319L) / 320L + padding
    val measuredAudioContext = when {
        requiredContext <= 208L -> 208
        requiredContext <= 1280L -> (((requiredContext + 31L) / 32L) * 32L).toInt()
        else -> 0
    }
    // Tiny is less stable than Base/Small in the very short encoder window. On
    // real devices a 208-position window repeatedly triggered decoder fallback,
    // while 512 removed the loop and completed faster despite the larger encoder.
    val audioContext = if (tinyModel && measuredAudioContext in 1 until TINY_MIN_AUDIO_CONTEXT) {
        TINY_MIN_AUDIO_CONTEXT
    } else {
        measuredAudioContext
    }
    val shortUtterance = samples <= 12L * 16_000L
    return WhisperInferencePolicy(
        // A stable 15-second minimum preserves more padding than the fast mode,
        // without encoding 30 seconds of zeros for every short command.
        audioContext = if (accurateMode && audioContext != 0) maxOf(768, audioContext) else audioContext,
        // Russian words can span several tokens. A fixed 96-token cap could
        // end a dense short recording in the middle of a word; let Whisper
        // finish at its end-of-text token and retain its native context limit.
        maxTokens = 0,
        singleSegment = shortUtterance,
        beamSize = if (accurateMode) 3 else 1,
        timeLimitMs = if (accurateMode) whisperAccurateTimeLimitMs(wavBytes) else whisperFastTimeLimitMs(wavBytes),
    )
}

internal fun whisperAccurateRetryPolicy(
    policy: WhisperInferencePolicy,
    confidence: Float?,
    blankText: Boolean,
    quality: WhisperDecodeQuality? = null,
): WhisperInferencePolicy? = if (
    policy.beamSize == 3 && policy.audioContext != 0 &&
    (blankText || (quality?.needsRetry ?: (confidence == null || confidence < WHISPER_ACCURATE_RETRY_CONFIDENCE)))
) policy.copy(audioContext = 0, beamSize = 3) else null

internal fun whisperRetryAudioContext(
    wavBytes: Long,
    audioContext: Int,
    confidence: Float?,
): Int? {
    if (audioContext == 0 || confidence == null || confidence >= FAST_CONTEXT_MIN_CONFIDENCE) return null
    val samples = ((wavBytes - 44L).coerceAtLeast(0L) / 2L)
    val capacitySamples = audioContext.toLong() * 320L
    if (samples * 100L < capacitySamples * FAST_CONTEXT_RETRY_FILL_PERCENT) return null
    return when {
        audioContext == 208 -> 256
        audioContext < 1280 -> audioContext + 64
        else -> 0
    }
}

/** The short calendar vocabulary looped on a measured 208-position Small RU recording. */
internal fun whisperPolicyForRequest(policy: WhisperInferencePolicy, context: WhisperRequestContext): WhisperInferencePolicy =
    if (context in setOf(WhisperRequestContext.DATE_REPLY, WhisperRequestContext.REMINDER_COMMAND) &&
        policy.audioContext in 1 until 512) policy.copy(audioContext = 512) else policy

internal fun whisperRepetitionRetryAudioContext(audioContext: Int, text: String): Int? =
    // The 208-position greedy window can duplicate a short word with a vocabulary
    // prompt, even at high token confidence. Re-decode the unchanged audio in the
    // measured stable window; genuine repetitions remain audible to the model.
    if (audioContext in 1 until 512 && text.hasConsecutiveWhisperWordRepetition()) 512 else null

class WhisperCppSttEngine(
    private val models: WhisperModelManager,
    private val vadModels: WhisperVadModelProvider,
    private val temporaryDirectory: File,
    private val keepModelLoaded: () -> Boolean = { false },
    private val configuredThreadCount: () -> Int = { 0 },
    private val accurateMode: () -> Boolean = { false },
) : SpeechToTextEngine, ContextualSpeechToTextEngine {
    data class RuntimeState(
        val stage: String = "Whisper не загружен в память",
        val modelLoaded: Boolean = false,
        val lastDurationMs: Long? = null,
        val audioSeconds: Double? = null,
        val realtimeFactor: Double? = null,
        val threads: Int? = null,
        val usedFastFallback: Boolean = false,
        val usedFullRetry: Boolean = false,
        val usedRepetitionRetry: Boolean = false,
        val usedStableFallback: Boolean = false,
        val nativePasses: Int = 0,
        val vadDurationMs: Long = 0,
        val preparationDurationMs: Long = 0,
        val nativeDurationMs: Long = 0,
    )

    override val id = "whisper"
    override val displayName = "Whisper.cpp (локально)"
    private val _runtime = MutableStateFlow(RuntimeState())
    val runtime: StateFlow<RuntimeState> = _runtime
    private val nativeMutex = Mutex()
    private val inferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleReleaseJob: Job? = null
    private var retainedModelPath: String? = null
    @Volatile private var contextHints: List<String> = emptyList()
    @Volatile private var commandVocabularyEnabled: Boolean = true
    @Volatile private var awaitingDateResponse: Boolean = false
    @Volatile private var commandScope: CommandScope = CommandScope.ASSISTANT

    override fun setCommandVocabularyEnabled(enabled: Boolean) { commandVocabularyEnabled = enabled }
    override fun setAwaitingDateResponse(enabled: Boolean) { awaitingDateResponse = enabled }
    override fun setCommandScope(scope: CommandScope) { commandScope = scope }

    override fun setContextHints(hints: List<String>) {
        contextHints = hints.toList()
    }

    /** Same audio/model/decoding policy, warmed up once, two oppositely ordered rounds. */
    suspend fun benchmarkThreads(
        sample: File,
        maximumThreads: Int,
        onProgress: (Int, Int, Int) -> Unit,
    ): Pair<String, List<WhisperThreadTiming>> = withContext(Dispatchers.Default) {
        nativeMutex.withLock {
            idleReleaseJob?.cancel()
            try {
                val model = withContext(Dispatchers.IO) { models.selectedModel(retainedModelPath, sample.length()) }
                retainedModelPath = model.absolutePath
                val requestAccurateMode = accurateMode()
                val policy = whisperInferencePolicy(sample.length(), model.length() <= TINY_WHISPER_MODEL_MAX_BYTES, requestAccurateMode)
                val prompt = whisperRequestPrompt(emptyList(), if (commandVocabularyEnabled) WhisperRequestContext.COMMAND
                    else WhisperRequestContext.DICTATION, policy.audioContext)
                suspend fun measure(threads: Int): Long {
                    coroutineContext.ensureActive()
                    val started = SystemClock.elapsedRealtime()
                    val text = runNative {
                        WhisperNative.transcribeWav(model.absolutePath, sample.absolutePath, "", "ru", prompt,
                            threads, policy.audioContext, policy.maxTokens, policy.singleSegment, policy.beamSize, policy.timeLimitMs, 0f)
                    }
                    require(text.isNotBlank()) { "Whisper не распознал тестовую фразу. Запишите новую фразу длительностью 3–5 секунд." }
                    return (SystemClock.elapsedRealtime() - started).coerceAtLeast(1)
                }
                val maximum = maximumThreads.coerceIn(1, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
                val total = maximum * 2
                onProgress(0, total, 0)
                measure(models.recommendedThreadCount())
                val durations = LongArray(maximum)
                var completed = 0
                for (round in 0..1) {
                    val counts = if (round == 0) (1..maximum).toList() else (maximum downTo 1).toList()
                    for (threads in counts) {
                        onProgress(completed, total, threads)
                        durations[threads - 1] += measure(threads)
                        completed++
                        onProgress(completed, total, threads)
                        delay(150)
                    }
                }
                model.name to (1..maximum).map { WhisperThreadTiming(it, durations[it - 1] / 2) }
            } finally {
                // Cancellation waits for the native call to finish before another
                // request can reuse model weights or remove benchmark files.
                withContext(NonCancellable + Dispatchers.Default) {
                    releaseWhisperNativeSafely()
                    retainedModelPath = null
                    _runtime.value = _runtime.value.copy(modelLoaded = false)
                }
            }
        }
    }

    private suspend fun <T> runNative(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        val task = inferenceScope.launch {
            val result = runCatching(block)
            if (continuation.isActive) continuation.resumeWith(result)
        }
        continuation.invokeOnCancellation {
            cancelWhisperNativeSafely()
            task.cancel()
        }
    }

    override suspend fun transcribe(processedWav: File): SpeechRecognition {
        return withContext(Dispatchers.Default) {
            val requestStarted = SystemClock.elapsedRealtime()
            idleReleaseJob?.cancel()
            nativeMutex.withLock {
                val requestAccurateMode = accurateMode()
                val requestCommandMode = commandVocabularyEnabled
                val requestHints = contextHints.toList()
                val requestContext = when {
                    !requestCommandMode -> WhisperRequestContext.DICTATION
                    awaitingDateResponse -> WhisperRequestContext.DATE_REPLY
                    commandScope == CommandScope.NOTES -> WhisperRequestContext.NOTE_COMMAND
                    commandScope == CommandScope.REMINDERS -> WhisperRequestContext.REMINDER_COMMAND
                    else -> WhisperRequestContext.COMMAND
                }
                val threads = models.recommendedThreadCount(configuredThreadCount())
                val vadStarted = SystemClock.elapsedRealtime()
                val speechBoundaries = try {
                    check(shouldAnalyzeWhisperVad(processedWav.length())) { "Речь не обнаружена. Запись пуста." }
                    val vadModelPath = withContext(Dispatchers.IO) { vadModels.modelFile().absolutePath }
                    _runtime.value = _runtime.value.copy(stage = "Silero VAD анализирует запись…")
                    runNative {
                        WhisperNative.detectSpeechSegments(
                            vadModelPath, processedWav.absolutePath, threads,
                            DYNAMIC_SPEECH_SEGMENT_SECONDS.toFloat(),
                        )
                    }.also { coroutineContext.ensureActive() }
                } catch (error: Throwable) {
                    _runtime.value = _runtime.value.copy(stage = "Проверка речи прервана: ${error.message}")
                    if (!keepModelLoaded()) withContext(NonCancellable + Dispatchers.Default) {
                        releaseWhisperNativeSafely()
                        retainedModelPath = null
                        _runtime.value = _runtime.value.copy(modelLoaded = false)
                    }
                    throw error
                }
                if (!hasWhisperSpeechRanges(speechBoundaries)) {
                    _runtime.value = _runtime.value.copy(stage = "Речь не обнаружена")
                    if (!keepModelLoaded()) withContext(NonCancellable + Dispatchers.Default) {
                        releaseWhisperNativeSafely()
                        retainedModelPath = null
                        _runtime.value = _runtime.value.copy(modelLoaded = false)
                    }
                    throw IllegalStateException("Речь не обнаружена. Попробуйте произнести фразу ближе к микрофону.")
                }
                val vadDuration = SystemClock.elapsedRealtime() - vadStarted
                val preparationStarted = SystemClock.elapsedRealtime()
                val segments = withContext(Dispatchers.IO) {
                    PcmWavSegmenter.splitForWhisper(
                        processedWav, temporaryDirectory, speechBoundaries, DYNAMIC_SPEECH_SEGMENT_SECONDS,
                    )
                }
                val model = try {
                    withContext(Dispatchers.IO) {
                        models.selectedModel(retainedModelPath, segments.maxOf(File::length))
                    }
                } catch (error: Throwable) {
                    withContext(Dispatchers.IO) { PcmWavSegmenter.cleanup(segments, processedWav) }
                    throw error
                }
                try {
                    retainedModelPath = model.absolutePath
                    val tinyModel = model.length() <= TINY_WHISPER_MODEL_MAX_BYTES
                    val audioSeconds = ((processedWav.length() - 44L).coerceAtLeast(0L) / 32_000.0)
                    val preparationDuration = SystemClock.elapsedRealtime() - preparationStarted
                    _runtime.value = _runtime.value.copy(
                        stage = "Whisper распознаёт запись…",
                        modelLoaded = true,
                        threads = threads,
                    )
                    var usedFastFallback = false
                    var usedStableFallback = false
                    var usedRepetitionRetry = false
                    var usedFullRetry = false
                    var nativePasses = 0
                    var nativeDuration = 0L
                    var weakWords = false
                    var conflictingResults = false
                    var contextCorrected = false
                    var incompleteDateReply = false
                    var recoveredDecoderLoop = false
                    var unresolvedDecoderLoop = false
                    var text = ""
                    var confidenceTotal = 0f
                    var confidenceCount = 0
                    segments.forEachIndexed { index, segment ->
                        _runtime.value = _runtime.value.copy(
                            stage = if (segments.size == 1) "Whisper распознаёт запись…"
                            else "Whisper: часть ${index + 1} из ${segments.size}",
                        )
                        val policy = whisperPolicyForRequest(
                            whisperInferencePolicy(segment.length(), tinyModel, requestAccurateMode), requestContext)
                        val segmentStarted = SystemClock.elapsedRealtime()
                        val segmentBudget = if (requestAccurateMode) policy.timeLimitMs + whisperFastTimeLimitMs(segment.length())
                            else policy.timeLimitMs * 2
                        data class Result(val text: String, val quality: WhisperDecodeQuality, val decoderLoop: Boolean)
                        suspend fun decode(audioContext: Int, beam: Int, limitMs: Int): Result = runNative {
                            val passStarted = SystemClock.elapsedRealtime()
                            nativePasses++
                            try {
                                val raw = WhisperNative.transcribeWav(
                                    model.absolutePath,
                                    segment.absolutePath,
                                    "", // VAD has already selected and padded the speech ranges.
                                    "ru",
                                    whisperRequestPrompt(requestHints, requestContext, audioContext),
                                    threads,
                                    audioContext,
                                    policy.maxTokens,
                                    policy.singleSegment,
                                    beam,
                                    limitMs.coerceAtLeast(1),
                                    0f, // One deterministic pass; retries are bounded and visible in Kotlin.
                                )
                                val contextual = if (requestContext == WhisperRequestContext.DATE_REPLY) normalizeExpectedDateReply(raw) else raw
                                contextCorrected = contextCorrected || contextual != raw
                                val value = contextual.collapseWhisperRepetition().normalizeWhisperTranscript(commandMode = requestCommandMode)
                                incompleteDateReply = incompleteDateReply || (requestContext == WhisperRequestContext.DATE_REPLY && !isCompleteExpectedDateReply(value))
                                Result(value, WhisperDecodeQuality.from(WhisperNative.lastMetrics()),
                                    raw.hasWhisperDecoderLoop((segment.length() - 44L).coerceAtLeast(0L) / 32_000.0))
                            } finally { nativeDuration += SystemClock.elapsedRealtime() - passStarted }
                        }
                        var retried = false
                        fun remaining() = (segmentBudget - (SystemClock.elapsedRealtime() - segmentStarted)).coerceAtLeast(1).toInt()
                        var segmentResult = try {
                            decode(policy.audioContext, policy.beamSize, policy.timeLimitMs)
                        } catch (timeout: TimeoutException) {
                            coroutineContext.ensureActive()
                            retried = true
                            if (requestAccurateMode) usedFastFallback = true else usedStableFallback = true
                            _runtime.value = _runtime.value.copy(stage = "Первый проход затянулся. Whisper проверяет запись в устойчивом окне…")
                            val context = if (policy.audioContext == 0) 0 else maxOf(512, policy.audioContext)
                            decode(context, 1, remaining())
                        }
                        if (!retried) {
                            val retryPolicy = if (requestAccurateMode) whisperAccurateRetryPolicy(
                                policy, segmentResult.quality.meanProbability, segmentResult.text.isBlank(), segmentResult.quality,
                            ) else if (segmentResult.text.isBlank()) {
                                policy.copy(audioContext = if (policy.audioContext == 0) 0 else maxOf(512, policy.audioContext))
                            } else if (segmentResult.decoderLoop && policy.audioContext in 1 until 768) {
                                policy.copy(audioContext = if (policy.audioContext < 512) 512 else 768)
                            } else null
                            if (retryPolicy != null && remaining() >= 2_000) {
                                if (segmentResult.decoderLoop) usedRepetitionRetry = true
                                usedFullRetry = requestAccurateMode
                                usedStableFallback = usedStableFallback || !requestAccurateMode
                                _runtime.value = _runtime.value.copy(stage = "Whisper уточняет неуверенно распознанную фразу…")
                                // A timeout during optional refinement must preserve the completed first text.
                                val retry = try { decode(retryPolicy.audioContext, retryPolicy.beamSize, remaining()) }
                                catch (timeout: TimeoutException) { coroutineContext.ensureActive(); null }
                                if (retry != null && retry.text.isNotBlank()) {
                                    if (segmentResult.text.isBlank()) segmentResult = retry
                                    else if (segmentResult.decoderLoop && !retry.decoderLoop) {
                                        recoveredDecoderLoop = true
                                        segmentResult = retry
                                    }
                                    else if (!whisperTextsAgree(segmentResult.text, retry.text)) {
                                        conflictingResults = true
                                        // A higher decoder score cannot establish which different word was spoken.
                                        // Preserve the completed first transcript and ask the user to check it.
                                    }
                                }
                            }
                        }
                        val segmentText = segmentResult.text
                        unresolvedDecoderLoop = unresolvedDecoderLoop || segmentResult.decoderLoop
                        weakWords = weakWords || segmentResult.quality.hasWeakWord
                        segmentResult.quality.meanProbability?.let {
                            confidenceTotal += it
                            confidenceCount++
                        }
                        text = mergeWhisperSegments(text, segmentText, commandMode = requestCommandMode)
                    }
                    if (text.isBlank()) throw IllegalStateException("Whisper.cpp не распознал речь")
                    val elapsed = SystemClock.elapsedRealtime() - requestStarted
                    _runtime.value = RuntimeState(
                        stage = if (usedFastFallback) "Распознано в быстром режиме: точный поиск превысил время" else "Whisper завершил распознавание",
                        usedFastFallback = usedFastFallback,
                        usedFullRetry = usedFullRetry,
                        usedRepetitionRetry = usedRepetitionRetry,
                        usedStableFallback = usedStableFallback,
                        nativePasses = nativePasses,
                        vadDurationMs = vadDuration,
                        preparationDurationMs = preparationDuration,
                        nativeDurationMs = nativeDuration,
                        modelLoaded = true,
                        lastDurationMs = elapsed,
                        audioSeconds = audioSeconds,
                        realtimeFactor = if (audioSeconds > 0.0) elapsed / 1_000.0 / audioSeconds else null,
                        threads = threads,
                    )
                    val finalConfidence = if (confidenceCount > 0) confidenceTotal / confidenceCount else null
                    android.util.Log.i("LocalAssistantWhisper", "REQUEST_DONE total=${elapsed}ms vad=${vadDuration}ms " +
                        "prepare=${preparationDuration}ms native=${nativeDuration}ms passes=$nativePasses " +
                        "weak=$weakWords conflict=$conflictingResults loopRetry=$usedRepetitionRetry")
                    val needsConfirmation = weakWords || conflictingResults || contextCorrected || incompleteDateReply ||
                        recoveredDecoderLoop || unresolvedDecoderLoop || (requestAccurateMode &&
                        (finalConfidence == null || finalConfidence < WHISPER_ACCURATE_MIN_CONFIDENCE))
                    SpeechRecognition(
                        text = text,
                        confidence = finalConfidence,
                        requiresConfirmation = needsConfirmation,
                        alternatives = if (requestCommandMode) speechCommandAlternatives(text).filterNot { it == text } else emptyList(),
                        notice = when {
                            unresolvedDecoderLoop -> "Whisper повторил часть фразы. Проверьте текст перед выполнением."
                            recoveredDecoderLoop -> "Whisper уточнил фразу после повтора. Проверьте текст."
                            conflictingResults -> "Варианты распознавания различаются. Проверьте текст."
                            usedStableFallback -> "Whisper повторно проверил запись после неуверенного или затянувшегося прохода." +
                                if (needsConfirmation) " Проверьте текст." else ""
                            usedFastFallback -> "Точный поиск превысил время. Фраза распознана в быстром режиме." +
                                if (needsConfirmation) " Проверьте текст." else ""
                            needsConfirmation -> if (requestCommandMode) "Речь распознана неуверенно. Проверьте текст перед выполнением."
                                else "Речь распознана неуверенно. Проверьте текст заметки."
                            else -> null
                        },
                    )
                } catch (error: Throwable) {
                    _runtime.value = _runtime.value.copy(stage = "Ошибка Whisper: ${error.message}")
                    throw error
                } finally {
                    withContext(Dispatchers.IO) { PcmWavSegmenter.cleanup(segments, processedWav) }
                    if (!keepModelLoaded()) {
                        val retention = models.idleRetentionMillis()
                        if (retention == 0L) {
                            releaseWhisperNativeSafely()
                            retainedModelPath = null
                            _runtime.value = _runtime.value.copy(modelLoaded = false)
                        } else {
                            idleReleaseJob = inferenceScope.launch {
                                delay(retention)
                                nativeMutex.withLock {
                                    releaseWhisperNativeSafely()
                                    retainedModelPath = null
                                    _runtime.value = _runtime.value.copy(modelLoaded = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun release() = withContext(Dispatchers.Default) {
        idleReleaseJob?.cancel()
        cancelWhisperNativeSafely()
        nativeMutex.withLock {
            releaseWhisperNativeSafely()
            retainedModelPath = null
            _runtime.value = _runtime.value.copy(modelLoaded = false)
        }
    }

}

private fun cancelWhisperNativeSafely() {
    runCatching { WhisperNative.cancel() }
}

private fun releaseWhisperNativeSafely() {
    runCatching { WhisperNative.release() }
}

internal const val RUSSIAN_WHISPER_PROMPT =
    "Напомни через три дня позвонить Кате. Напомни о встрече завтра в восемь утра. " +
        "Создай заметку для работы. Покажи планы на следующую неделю. Удали напоминание о встрече."

internal const val WHISPER_ACCURATE_RETRY_CONFIDENCE = 0.85f
internal const val WHISPER_ACCURATE_MIN_CONFIDENCE = 0.90f

private const val FAST_CONTEXT_MIN_CONFIDENCE = 0.80f
private const val FAST_CONTEXT_RETRY_FILL_PERCENT = 95L
private const val FAST_CONTEXT_MAX_SAMPLES = 67_200L
private const val TINY_WHISPER_MODEL_MAX_BYTES = 96L * 1024L * 1024L
private const val TINY_MIN_AUDIO_CONTEXT = 512
private const val DYNAMIC_SPEECH_SEGMENT_SECONDS = 19

// A stalled beam must not block a short command for minutes. Longer speech gets
// a proportional budget; the caller retries with a fresh greedy state on timeout.
internal fun whisperAccurateTimeLimitMs(wavBytes: Long): Int =
    (((wavBytes - 44L).coerceAtLeast(0L) / 32L) * 6L).coerceIn(20_000L, 120_000L).toInt()

// Native results also contain zero-length preferred pause markers.
internal fun hasWhisperSpeechRanges(boundaries: LongArray): Boolean =
    boundaries.size % 2 == 0 && boundaries.asList().chunked(2).any { it[0] >= 0 && it[1] > it[0] }

internal fun shouldAnalyzeWhisperVad(wavBytes: Long): Boolean {
    val samples = ((wavBytes - 44L).coerceAtLeast(0L) / 2L)
    return samples > 0L
}

internal fun automaticWhisperIdleRetentionMillis(
    lowRamDevice: Boolean,
    memoryClassMb: Int,
    availableRamBytes: Long,
): Long = if (
    lowRamDevice || memoryClassMb <= 192 || availableRamBytes < 768L * 1024L * 1024L
) {
    0L
} else {
    20_000L
}

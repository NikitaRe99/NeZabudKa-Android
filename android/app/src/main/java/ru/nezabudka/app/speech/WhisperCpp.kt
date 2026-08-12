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
import ru.nezabudka.app.ports.SpeechRecognition
import ru.nezabudka.app.ports.SpeechToTextEngine
import ru.nezabudka.app.ports.ContextualSpeechToTextEngine
import ru.nezabudka.app.ports.PortableModelSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import java.util.concurrent.atomic.AtomicBoolean

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
        val modelBytes = if (alreadyLoaded) 0L else model.length()
        val estimatedPeak = estimatedWhisperPeakBytes(modelBytes, pcmWavBytes)
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
    external fun systemInfo(): String
}

internal data class WhisperInferencePolicy(
    val audioContext: Int,
    val maxTokens: Int,
    val singleSegment: Boolean,
)

internal fun whisperInferencePolicy(
    wavBytes: Long,
    tinyModel: Boolean = false,
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
        audioContext = audioContext,
        maxTokens = if (shortUtterance) 96 else 0,
        singleSegment = shortUtterance,
    )
}

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

class WhisperCppSttEngine(
    private val models: WhisperModelManager,
    private val vadModels: WhisperVadModelProvider,
    private val temporaryDirectory: File,
    private val keepModelLoaded: () -> Boolean = { false },
    private val configuredThreadCount: () -> Int = { 0 },
) : SpeechToTextEngine, ContextualSpeechToTextEngine {
    data class RuntimeState(
        val stage: String = "Whisper не загружен в память",
        val modelLoaded: Boolean = false,
        val lastDurationMs: Long? = null,
        val audioSeconds: Double? = null,
        val realtimeFactor: Double? = null,
        val threads: Int? = null,
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

    override fun setContextHints(hints: List<String>) {
        contextHints = hints.toList()
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
            idleReleaseJob?.cancel()
            nativeMutex.withLock {
                val threads = models.recommendedThreadCount(configuredThreadCount())
                var vadModelPath = if (shouldAnalyzeWhisperVad(processedWav.length())) {
                    withContext(Dispatchers.IO) {
                        runCatching { vadModels.modelFile().absolutePath }.getOrDefault("")
                    }
                } else ""
                val speechBoundaries = if (vadModelPath.isBlank()) null else try {
                    _runtime.value = _runtime.value.copy(stage = "Silero VAD анализирует запись…")
                    runNative {
                        WhisperNative.detectSpeechSegments(
                            vadModelPath,
                            processedWav.absolutePath,
                            threads,
                            DYNAMIC_SPEECH_SEGMENT_SECONDS.toFloat(),
                        )
                    }.takeIf { it.isNotEmpty() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }
                val segments = withContext(Dispatchers.IO) {
                    if (speechBoundaries != null) {
                        PcmWavSegmenter.splitAtSpeechRanges(
                            processedWav,
                            temporaryDirectory,
                            speechBoundaries,
                            DYNAMIC_SPEECH_SEGMENT_SECONDS,
                        )
                    } else {
                        PcmWavSegmenter.splitIfLong(processedWav, temporaryDirectory)
                    }
                }
                // Filtered files already contain only VAD-selected speech.
                if (speechBoundaries != null) vadModelPath = ""
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
                    val started = SystemClock.elapsedRealtime()
                    _runtime.value = _runtime.value.copy(
                        stage = "Whisper распознаёт запись…",
                        modelLoaded = true,
                        threads = threads,
                    )
                    var text = ""
                    var confidenceTotal = 0f
                    var confidenceCount = 0
                    segments.forEachIndexed { index, segment ->
                        _runtime.value = _runtime.value.copy(
                            stage = if (segments.size == 1) "Whisper распознаёт запись…"
                            else "Whisper: часть ${index + 1} из ${segments.size}",
                        )
                        val prompt = contextualWhisperPrompt(contextHints)
                        val policy = whisperInferencePolicy(segment.length(), tinyModel)
                        fun transcribe(audioContext: Int, useVad: Boolean): Pair<String, Float?> {
                            val value = WhisperNative.transcribeWav(
                                model.absolutePath,
                                segment.absolutePath,
                                vadModelPath.takeIf { useVad }.orEmpty(),
                                "ru",
                                prompt,
                                threads,
                                audioContext,
                                policy.maxTokens,
                                policy.singleSegment,
                            ).collapseWhisperRepetition().normalizeWhisperTranscript()
                            return value to WhisperNative.lastConfidence().takeIf { it > 0f }
                        }
                        suspend fun transcribeWithVadFallback(audioContext: Int): Pair<String, Float?> {
                            if (vadModelPath.isBlank()) return runNative { transcribe(audioContext, false) }
                            return try {
                                runNative { transcribe(audioContext, true) }.takeIf { it.first.isNotBlank() }
                                    ?: run {
                                        vadModelPath = ""
                                        runNative { transcribe(audioContext, false) }
                                    }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                vadModelPath = ""
                                runNative { transcribe(audioContext, false) }
                            }
                        }
                        var segmentResult = transcribeWithVadFallback(policy.audioContext)
                        // Non-standard converted models may not tolerate a reduced
                        // encoder window. Preserve compatibility by retrying only
                        // when the fast pass produced no usable text.
                        if (segmentResult.first.isBlank() && policy.audioContext != 0) {
                            segmentResult = runNative { transcribe(0, false) }
                        } else {
                            whisperRetryAudioContext(
                                wavBytes = segment.length(),
                                audioContext = policy.audioContext,
                                confidence = segmentResult.second,
                            )?.let { retryContext ->
                                val retry = transcribeWithVadFallback(retryContext)
                                if (
                                    retry.first.isNotBlank() &&
                                    (retry.second ?: 0f) > (segmentResult.second ?: 0f)
                                ) {
                                    segmentResult = retry
                                }
                            }
                        }
                        val segmentText = segmentResult.first
                        segmentResult.second?.let {
                            confidenceTotal += it
                            confidenceCount++
                        }
                        text = mergeTranscripts(text, segmentText)
                    }
                    if (text.isBlank()) throw IllegalStateException("Whisper.cpp не распознал речь")
                    val elapsed = SystemClock.elapsedRealtime() - started
                    _runtime.value = RuntimeState(
                        stage = "Whisper завершил распознавание",
                        modelLoaded = true,
                        lastDurationMs = elapsed,
                        audioSeconds = audioSeconds,
                        realtimeFactor = if (audioSeconds > 0.0) elapsed / 1_000.0 / audioSeconds else null,
                        threads = threads,
                    )
                    SpeechRecognition(
                        text = text,
                        confidence = if (confidenceCount > 0) confidenceTotal / confidenceCount else null,
                        alternatives = speechCommandAlternatives(text).filterNot { it == text },
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

private const val FAST_CONTEXT_MIN_CONFIDENCE = 0.80f
private const val FAST_CONTEXT_RETRY_FILL_PERCENT = 95L
private const val FAST_CONTEXT_MAX_SAMPLES = 67_200L
private const val TINY_WHISPER_MODEL_MAX_BYTES = 96L * 1024L * 1024L
private const val TINY_MIN_AUDIO_CONTEXT = 512
private const val DYNAMIC_SPEECH_SEGMENT_SECONDS = 19
private const val VAD_MIN_RECORDING_SAMPLES = 20L * 16_000L

internal fun shouldAnalyzeWhisperVad(wavBytes: Long): Boolean {
    val samples = ((wavBytes - 44L).coerceAtLeast(0L) / 2L)
    return samples > VAD_MIN_RECORDING_SAMPLES
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

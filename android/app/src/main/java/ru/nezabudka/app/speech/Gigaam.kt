package ru.nezabudka.app.speech

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import ru.nezabudka.app.data.AndroidModelLibrary
import ru.nezabudka.app.data.ModelDownloadWorker
import ru.nezabudka.app.data.UserModelsStorage
import ru.nezabudka.app.domain.WhisperThreadTiming
import ru.nezabudka.app.ports.SpeechRecognition
import ru.nezabudka.app.ports.SpeechToTextEngine
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GigaamModelMissingException : IllegalStateException("Модель GigaAM не установлена. Выберите модель в настройках распознавания речи.")

class GigaamModelManager(private val context: Context, storage: UserModelsStorage) {
    private val library = AndroidModelLibrary(context, storage, "gigaam", "onnx", "gigaam_models", "gigaam-imported.onnx") { file ->
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            OrtEnvironment.getEnvironment().createSession(file.path, options).use { validateGigaam(it) }
        }
    }
    private val _state = MutableStateFlow(WhisperModelsState())
    val state = _state.asStateFlow()
    private var observer: Job? = null
    fun refresh() { val snapshot = library.snapshot(); _state.value = WhisperModelsState(snapshot.installed, snapshot.selectedName) }
    fun start(scope: CoroutineScope) {
        if (observer != null) return
        observer = scope.launch(Dispatchers.IO) {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.uniqueWorkName("gigaam")).collectLatest { values ->
                refresh()
                val work = values.firstOrNull { !it.state.isFinished } ?: values.lastOrNull() ?: return@collectLatest
                if (!work.state.isFinished) {
                    val copied = work.progress.getLong(ModelDownloadWorker.KEY_COPIED, 0)
                    val total = work.progress.getLong(ModelDownloadWorker.KEY_TOTAL, -1)
                    _state.value = state.value.copy(downloadingId = work.tags.firstOrNull { it.startsWith("model-id:") }?.substringAfter(':'), progress = if (total > 0) copied.toFloat() / total else null)
                } else if (work.state == WorkInfo.State.FAILED) _state.value = state.value.copy(error = work.outputData.getString(ModelDownloadWorker.KEY_ERROR))
            }
        }
    }
    fun selectedModel(): File = library.selectedFile("GigaAM не установлена", { GigaamModelMissingException() })
    fun select(name: String) { library.select(name); refresh() }
    fun delete(name: String) { library.delete(name); refresh() }
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) { library.import(uri); refresh() }
    fun download(model: WhisperDownload) {
        require(GigaamCatalog.downloads.any { it == model })
        ModelDownloadWorker.enqueue(context, "gigaam", model.id, model.title, model.fileName, model.url)
    }
}

internal fun validateGigaam(session: OrtSession): List<String> {
    val metadata = session.metadata.customMetadata
    require(metadata["nezabudka.format"] == "gigaam-waveform-ctc-1" && metadata["nezabudka.sample_rate"] == "16000") {
        "Нужна ONNX-модель GigaAM CTC для НеЗабудьКа со встроенной обработкой аудио. Обычный экспорт энкодера и RNNT не подходят."
    }
    require(session.inputNames == setOf("audio", "length") && session.outputNames == setOf("labels", "encoded_length")) { "Несовместимые входы или выходы GigaAM" }
    val vocabulary = JSONArray(requireNotNull(metadata["nezabudka.vocabulary"]))
    require(vocabulary.length() in 2..256) { "Некорректный словарь GigaAM" }
    return List(vocabulary.length()) { vocabulary.getString(it) }
}

class GigaamSttEngine(
    private val context: Context,
    private val models: GigaamModelManager,
    private val threads: () -> Int,
    private val keepLoaded: () -> Boolean,
) : SpeechToTextEngine {
    override val id = "gigaam"
    override val displayName = "GigaAM"
    private val mutex = Mutex()
    private var session: OrtSession? = null
    private var loadedPath: String? = null
    private var loadedThreads = 0
    private var vocabulary = emptyList<String>()
    private val deadlines = Executors.newSingleThreadScheduledExecutor()
    private val env get() = OrtEnvironment.getEnvironment()
    fun effectiveThreads(configured: Int = threads()): Int = if (configured > 0) configured.coerceIn(1, Runtime.getRuntime().availableProcessors()) else minOf(4, Runtime.getRuntime().availableProcessors())
    suspend fun release() = mutex.withLock { close() }
    private fun close() { session?.close(); session = null; loadedPath = null; vocabulary = emptyList() }
    private fun load(count: Int): OrtSession {
        val file = models.selectedModel()
        if (loadedPath == file.path && loadedThreads == count) return requireNotNull(session)
        close()
        val info = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        require(info.availMem > file.length() * 2 + 100L * 1024 * 1024) { "Недостаточно свободной памяти для GigaAM. Освободите память или выберите Whisper Base Q5." }
        val opened = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(count); options.setInterOpNumThreads(1)
            env.createSession(file.path, options)
        }
        try { vocabulary = validateGigaam(opened) } catch (error: Throwable) { opened.close(); throw error }
        session = opened; loadedPath = file.path; loadedThreads = count
        return opened
    }
    override suspend fun transcribe(processedWav: File): SpeechRecognition = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val samples = readGigaamWav(processedWav)
                val active = load(effectiveThreads())
                val chunks = gigaamChunkRanges(samples)
                val texts = chunks.map { range ->
                    currentCoroutineContext().ensureActive()
                    infer(active, samples.copyOfRange(range.first, range.last + 1))
                }
                SpeechRecognition(texts.filter(String::isNotBlank).joinToString(" ").trim())
            } finally { if (!keepLoaded()) close() }
        }
    }
    /** Preview is disposable; final transcription always re-reads the full stopped WAV. */
    suspend fun preview(audio: FloatArray, committed: MutableMap<IntRange, String>): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val active = load(effectiveThreads())
            val ranges = gigaamChunkRanges(audio)
            ranges.mapIndexed { index, range ->
                currentCoroutineContext().ensureActive()
                if (index < ranges.lastIndex && committed.containsKey(range)) committed.getValue(range)
                else infer(active, audio.copyOfRange(range.first, range.last + 1), 1500, currentCoroutineContext()[Job]).also {
                    if (index < ranges.lastIndex) committed[range] = it
                }
            }.filter(String::isNotBlank).joinToString(" ")
        }
    }

    private fun infer(active: OrtSession, audio: FloatArray, timeoutMillis: Long = 90_000, cancellation: Job? = null): String {
        require(audio.size >= 400) { "Речь не обнаружена. Попробуйте произнести фразу ближе к микрофону." }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(audio), longArrayOf(1, audio.size.toLong())).use { wave ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(audio.size.toLong())), longArrayOf(1)).use { length ->
                OrtSession.RunOptions().use { run ->
                    val timeout = deadlines.schedule({ runCatching { run.setTerminate(true) } }, timeoutMillis, TimeUnit.MILLISECONDS)
                    val cancelMonitor = if (cancellation != null) deadlines.scheduleAtFixedRate({
                        if (cancellation.isCancelled) runCatching { run.setTerminate(true) }
                    }, 50, 50, TimeUnit.MILLISECONDS) else null
                    try {
                        active.run(mapOf("audio" to wave, "length" to length), run).use { result ->
                            val labels = (result[0].value as Array<LongArray>)[0]
                            val rawLength = result[1].value
                            val size = when (rawLength) { is IntArray -> rawLength[0]; is LongArray -> rawLength[0].toInt(); else -> error("Некорректная длина GigaAM") }
                            return decodeGigaamCtc(labels, size, vocabulary)
                        }
                    } finally { timeout.cancel(false); cancelMonitor?.cancel(false) }
                }
            }
        }
    }
    suspend fun benchmarkThreads(file: File, maximum: Int, progress: (Int, Int, Int) -> Unit): Pair<String, List<WhisperThreadTiming>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val audio = readGigaamWav(file)
                val options = (1..maximum).toList()
                val result = mutableListOf<WhisperThreadTiming>()
                for ((index, count) in options.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    progress(index, options.size, count)
                    val active = load(count)
                    infer(active, audio)
                    val started = android.os.SystemClock.elapsedRealtime()
                    infer(active, audio)
                    result += WhisperThreadTiming(count, android.os.SystemClock.elapsedRealtime() - started)
                }
                models.state.value.selectedName.orEmpty() to result
            } finally { close() }
        }
    }
}

internal fun decodeGigaamCtc(labels: LongArray, length: Int, vocabulary: List<String>): String {
    var previous = -1L
    return buildString {
        for (i in 0 until length.coerceIn(0, labels.size)) {
            val token = labels[i]
            if (token != previous && token != vocabulary.size.toLong()) {
                require(token in 0 until vocabulary.size.toLong()) { "Некорректный токен GigaAM" }
                append(vocabulary[token.toInt()])
            }
            previous = token
        }
    }
}

internal fun readGigaamWav(file: File): FloatArray {
    val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    require(bytes.remaining() >= 44 && bytes.int == 0x46464952) { "Нужна WAV-запись" }
    bytes.int; require(bytes.int == 0x45564157)
    var valid = false
    while (bytes.remaining() >= 8) {
        val id = bytes.int; val size = bytes.int; val start = bytes.position()
        require(size >= 0 && size <= bytes.remaining()) { "Повреждённый WAV" }
        if (id == 0x20746d66) {
            require(size >= 16)
            val encoding = bytes.short.toInt(); val channels = bytes.short.toInt(); val rate = bytes.int
            bytes.int; bytes.short; val bits = bytes.short.toInt()
            valid = encoding == 1 && channels == 1 && rate == 16000 && bits == 16
        } else if (id == 0x61746164) {
            require(valid && size % 2 == 0) { "Нужна PCM16 mono 16 кГц" }
            return FloatArray(size / 2) { bytes.short / 32768f }
        }
        bytes.position(start + size + (size and 1))
    }
    error("В записи нет звука")
}

/** Preserve every sample once; select a quiet boundary for audio longer than 25s. */
internal fun gigaamChunkRanges(audio: FloatArray): List<IntRange> {
    val result = mutableListOf<IntRange>(); var start = 0
    while (audio.size - start > 25 * 16000) {
        var best = start + 20 * 16000; var minimum = Double.MAX_VALUE
        for (candidate in start + 18 * 16000..start + 22 * 16000 step 320) {
            var energy = 0.0
            for (i in candidate - 320 until candidate + 320) energy += audio[i].toDouble() * audio[i]
            if (energy < minimum) { minimum = energy; best = candidate }
        }
        result += start until best; start = best
    }
    if (start < audio.size) result += start until audio.size
    return result
}

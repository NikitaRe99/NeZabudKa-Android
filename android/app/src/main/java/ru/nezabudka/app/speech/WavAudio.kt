package ru.nezabudka.app.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.annotation.RequiresPermission
import ru.nezabudka.app.ports.AudioPreprocessor
import ru.nezabudka.app.ports.AudioRecorder
import ru.nezabudka.app.ports.CancellableAudioRecorder
import ru.nezabudka.app.ports.LiveSpeechToTextEngine
import ru.nezabudka.app.ports.SpeechRecognition
import ru.nezabudka.app.ports.SpeechToTextEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WavAudioRecorder(
    private val context: Context,
    private val sampleRate: Int = 16_000,
    private val captureSources: IntArray? = null,
    private val echoCancellationEnabled: Boolean = true,
    private val recordingTailMs: Long = 0L,
    private val outputFile: File? = null,
    private val softwarePreprocessingEnabled: () -> Boolean = { true },
) : AudioRecorder, CancellableAudioRecorder {
    @Volatile private var recorder: AudioRecord? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var thread: Thread? = null
    private val recording = AtomicBoolean(false)
    @Volatile private var recordingFailure: Throwable? = null
    private lateinit var output: File
    private val previewReader = PcmPreviewReader()
    @Volatile var lastCaptureInfo: String = ""
        private set
    override val isRecording: Boolean get() = recording.get()

    override fun start() {
        check(!recording.get()) { "Запись уже идёт" }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Разрешите доступ к микрофону")
        }
        recordingFailure = null
        previewReader.reset()
        val capture = startCapture(findBuiltInMicrophone())
        output = (outputFile ?: File(context.filesDir, "recordings/last_raw.wav")).apply { parentFile?.mkdirs() }
        try {
            recording.set(true)
            thread = Thread {
                var bytes = 0L
                val buffer = ByteArray(capture.readBufferBytes)
                val resampler = Pcm16StreamingResampler(capture.sampleRate, sampleRate)
                try {
                    FileOutputStream(output).use { stream ->
                        writeWavHeader(stream, sampleRate, 0)
                        while (recording.get()) {
                            val read = capture.recorder.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                val outputPcm = if (capture.sampleRate == sampleRate) buffer else resampler.process(buffer, read)
                                val outputSize = if (capture.sampleRate == sampleRate) read else outputPcm.size
                                if (outputSize == 0) continue
                                stream.write(outputPcm, 0, outputSize)
                                bytes += outputSize
                            } else if (read < 0 && recording.get()) {
                                throw IllegalStateException(
                                    "Встроенный микрофон перестал передавать звук (AudioRecord: $read)",
                                )
                            } else if (read == 0) {
                                Thread.sleep(EMPTY_READ_RETRY_DELAY_MS)
                            }
                        }
                        stream.flush()
                    }
                } catch (error: Throwable) {
                    if (recording.get()) recordingFailure = error
                    recording.set(false)
                } finally {
                    runCatching {
                        RandomAccessFile(output, "rw").use { patchWavHeader(it, bytes, sampleRate) }
                    }.onFailure { if (recordingFailure == null) recordingFailure = it }
                }
            }.apply { name = "wav-recorder"; start() }
        } catch (error: Throwable) {
            recording.set(false)
            runCatching { recorder?.stop() }
            releaseAudioResources()
            throw error
        }
    }

    override suspend fun stop(): File = withContext(Dispatchers.IO) {
        // Preserve the end of a word when the finger lifts during its last syllable.
        if (recording.get() && recordingTailMs > 0) delay(recordingTailMs.coerceAtMost(300L))
        val wasRecording = recording.getAndSet(false)
        check(wasRecording || recordingFailure != null) { "Запись не запущена" }
        try {
            runCatching { recorder?.stop() }
            thread?.join(3_000)
        } finally {
            releaseAudioResources()
        }
        recordingFailure?.let { error ->
            recordingFailure = null
            throw error
        }
        check(output.length() > WAV_HEADER_BYTES) {
            "Встроенный микрофон не передал звук"
        }
        output
    }

    /** Snapshot only completed PCM samples; the live WAV header is patched on stop. */
    fun previewSamples(): FloatArray? {
        if (!recording.get() || !::output.isInitialized) return null
        return previewReader.snapshot(output)
    }

    override fun cancel() {
        if (!recording.getAndSet(false)) return
        runCatching { recorder?.stop() }
        releaseAudioResources()
    }

    private fun releaseAudioResources() {
        previewReader.reset()
        releaseAudioEffects()
        recorder?.release(); recorder = null; thread = null
    }

    private fun releaseAudioEffects() {
        acousticEchoCanceler?.release(); acousticEchoCanceler = null
        automaticGainControl?.release(); automaticGainControl = null
        noiseSuppressor?.release(); noiseSuppressor = null
    }

    private fun configureAudioEffects(audioRecord: AudioRecord) {
        releaseAudioEffects()
        acousticEchoCanceler = if (echoCancellationEnabled && AcousticEchoCanceler.isAvailable()) {
            enableAudioEffect { AcousticEchoCanceler.create(audioRecord.audioSessionId) }
        } else null
        // Do not process the signal twice. When the deterministic WAV
        // preprocessor is enabled it owns gain/noise handling; otherwise use
        // the device DSP effects when they are available.
        if (!softwarePreprocessingEnabled()) {
            automaticGainControl = runCatching {
                if (AutomaticGainControl.isAvailable()) enableAudioEffect { AutomaticGainControl.create(audioRecord.audioSessionId) } else null
            }.getOrNull()
            noiseSuppressor = runCatching {
                if (NoiseSuppressor.isAvailable()) enableAudioEffect { NoiseSuppressor.create(audioRecord.audioSessionId) } else null
            }.getOrNull()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startCapture(
        preferredInput: AudioDeviceInfo?,
    ): Capture {
        val capture = openAudioRecord(preferredInput)
        return try {
            configureAudioEffects(capture.recorder)
            capture.recorder.startRecording()
            lastCaptureInfo = "source=${capture.recorder.audioSource},rate=${capture.sampleRate}," +
                "preferred=${preferredInput?.type},routed=${capture.recorder.routedDevice?.type}," +
                "aec=${acousticEchoCanceler?.enabled == true},agc=${automaticGainControl?.enabled == true}," +
                "ns=${noiseSuppressor?.enabled == true},tailMs=$recordingTailMs"
            android.util.Log.i("LocalAssistantAudio", "CAPTURE $lastCaptureInfo")
            recorder = capture.recorder
            capture
        } catch (error: Throwable) {
            releaseAudioEffects()
            runCatching { capture.recorder.release() }
            throw error
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openAudioRecord(preferredInput: AudioDeviceInfo?): Capture {
        for (source in captureSources ?: CAPTURE_SOURCES) {
            for (captureRate in CAPTURE_SAMPLE_RATES) {
                val minimum = AudioRecord.getMinBufferSize(
                    captureRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minimum <= 0) continue
                val candidate = runCatching {
                    AudioRecord(
                        source,
                        captureRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minimum.coerceAtLeast(MIN_CAPTURE_BUFFER_BYTES) * 2,
                    )
                }.getOrNull() ?: continue
                val deviceSelected = preferredInput == null ||
                    runCatching { candidate.setPreferredDevice(preferredInput) }.getOrDefault(false)
                if (candidate.state == AudioRecord.STATE_INITIALIZED && deviceSelected) {
                    return Capture(candidate, captureRate, minimum.coerceAtLeast(MIN_CAPTURE_BUFFER_BYTES))
                }
                candidate.release()
            }
        }
        error("Не удалось открыть микрофон в поддерживаемом формате")
    }

    private fun findBuiltInMicrophone(): AudioDeviceInfo {
        val manager = context.getSystemService(AudioManager::class.java)
        return runCatching { manager.getDevices(AudioManager.GET_DEVICES_INPUTS) }
            .getOrDefault(emptyArray())
            .firstOrNull { isBuiltInMicrophoneType(it.type) }
            ?: error("Встроенный микрофон недоступен")
    }

    private fun <T : AudioEffect> enableAudioEffect(create: () -> T?): T? {
        val effect = runCatching(create).getOrNull() ?: return null
        val enabled = runCatching {
            effect.enabled = true
            effect.enabled
        }.getOrDefault(false)
        return if (enabled) effect else {
            effect.release()
            null
        }
    }

    private data class Capture(
        val recorder: AudioRecord,
        val sampleRate: Int,
        val readBufferBytes: Int,
    )

    private companion object {
        const val WAV_HEADER_BYTES = 44L
        const val MIN_CAPTURE_BUFFER_BYTES = 4_096
        const val EMPTY_READ_RETRY_DELAY_MS = 10L
        val CAPTURE_SAMPLE_RATES = intArrayOf(16_000, 48_000, 44_100, 8_000)
        val CAPTURE_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
        )
    }
}

internal fun isBuiltInMicrophoneType(type: Int): Boolean =
    type == AudioDeviceInfo.TYPE_BUILTIN_MIC

class PcmAudioPreprocessor(private val context: Context) : AudioPreprocessor {
    private val prefs = context.getSharedPreferences("audio_preprocessor", Context.MODE_PRIVATE)
    private val output = File(context.filesDir, "recordings/last_processed.wav")

    override suspend fun process(inputWav: File): File = withContext(Dispatchers.IO) {
        val fingerprint = fingerprint(inputWav)
        if (output.exists() && prefs.getString("fingerprint", null) == fingerprint) return@withContext output
        processFile(inputWav, output)
        prefs.edit().putString("fingerprint", fingerprint).apply()
        output
    }

    private fun processFile(inputWav: File, destination: File): File =
        PcmWavBlockProcessor.process(inputWav, destination)

    override fun lastProcessed(): File? = output.takeIf(File::exists)
    private fun fingerprint(file: File): String = "level6:${file.length()}:${file.lastModified()}"
}

internal enum class AndroidRecognizerMode { UNAVAILABLE, SYSTEM, ON_DEVICE }

internal fun androidRecognizerMode(
    sdk: Int,
    systemAvailable: Boolean,
    onDeviceAvailable: Boolean,
): AndroidRecognizerMode = when {
    sdk >= 31 && onDeviceAvailable -> AndroidRecognizerMode.ON_DEVICE
    systemAvailable -> AndroidRecognizerMode.SYSTEM
    else -> AndroidRecognizerMode.UNAVAILABLE
}

class AndroidSpeechRecognizerEngine(private val context: Context) : LiveSpeechToTextEngine {
    override val id = "android"
    override val displayName = "Android SpeechRecognizer"
    private val main = Handler(Looper.getMainLooper())
    private var liveRecognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = recognizerMode() != AndroidRecognizerMode.UNAVAILABLE

    fun availabilityDescription(): String = when {
        recognizerMode() == AndroidRecognizerMode.ON_DEVICE -> "Доступно локальное распознавание Android"
        recognizerMode() == AndroidRecognizerMode.SYSTEM -> "Системный сервис может использовать интернет"
        else -> "На устройстве не установлена служба распознавания речи"
    }

    override fun start(
        onResult: (SpeechRecognition) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        main.post {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                onError(SecurityException("Разрешите доступ к микрофону")); return@post
            }
            val mode = recognizerMode()
            if (mode == AndroidRecognizerMode.UNAVAILABLE) {
                onError(IllegalStateException(availabilityDescription())); return@post
            }
            cancelInternal()
            val recognizer = createRecognizer(mode)
            liveRecognizer = recognizer
            recognizer.setRecognitionListener(listener(
                recognizer = recognizer,
                onResult = onResult,
                onError = onError,
            ))
            val intent = baseIntent().apply {
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            startWithLanguageCheck(
                recognizer = recognizer,
                intent = intent,
                onDevice = mode == AndroidRecognizerMode.ON_DEVICE,
                reject = { message ->
                    if (liveRecognizer === recognizer) liveRecognizer = null
                    recognizer.destroy()
                    onError(IllegalStateException(message))
                },
                proceed = { recognizer.startListening(intent) },
            )
        }
    }

    override fun stop() { main.post { liveRecognizer?.stopListening() } }
    override fun cancel() { main.post { cancelInternal() } }

    private fun cancelInternal() {
        liveRecognizer?.cancel()
        liveRecognizer?.destroy()
        liveRecognizer = null
    }

    override suspend fun transcribe(processedWav: File): SpeechRecognition {
        if (Build.VERSION.SDK_INT < 33) {
            throw IllegalStateException("Передача обработанного WAV в Android SpeechRecognizer требует Android 13+; выберите Vosk или Whisper")
        }
        val pcm = withContext(Dispatchers.IO) {
            File(context.cacheDir, "speech/last_processed.pcm").apply {
                parentFile?.mkdirs()
                processedWav.inputStream().buffered().use { input ->
                    val header = ByteArray(44)
                    require(input.read(header) == header.size && header.copyOfRange(0, 4).decodeToString() == "RIFF") {
                        "Ожидался WAV PCM файл"
                    }
                    outputStream().buffered().use(input::copyTo)
                }
            }
        }
        return try {
            suspendCancellableCoroutine { continuation ->
                main.post {
                    if (!continuation.isActive) return@post
                    if (!isAvailable()) {
                        continuation.resumeWithException(IllegalStateException("SpeechRecognizer недоступен на устройстве")); return@post
                    }
                    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", pcm)
                    val mode = recognizerMode()
                    val recognizer = createRecognizer(mode)
                    recognizer.setRecognitionListener(listener(recognizer,
                        { if (continuation.isActive) continuation.resume(it) },
                        { if (continuation.isActive) continuation.resumeWithException(it) }))
                    continuation.invokeOnCancellation { main.post { recognizer.cancel(); recognizer.destroy() } }
                    val intent = baseIntent().apply {
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, uri)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startWithLanguageCheck(
                        recognizer = recognizer,
                        intent = intent,
                        onDevice = mode == AndroidRecognizerMode.ON_DEVICE,
                        reject = { message ->
                            recognizer.destroy()
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                        },
                        proceed = { recognizer.startListening(intent) },
                    )
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { pcm.delete() }
        }
    }

    private fun baseIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    private fun systemRecognitionAvailable(): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    private fun onDeviceRecognitionAvailable(): Boolean = Build.VERSION.SDK_INT >= 31 &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    private fun recognizerMode(): AndroidRecognizerMode = androidRecognizerMode(
        sdk = Build.VERSION.SDK_INT,
        systemAvailable = systemRecognitionAvailable(),
        onDeviceAvailable = onDeviceRecognitionAvailable(),
    )

    private fun createRecognizer(mode: AndroidRecognizerMode): SpeechRecognizer =
        if (mode == AndroidRecognizerMode.ON_DEVICE && Build.VERSION.SDK_INT >= 31)
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)

    private fun startWithLanguageCheck(
        recognizer: SpeechRecognizer,
        intent: Intent,
        onDevice: Boolean,
        reject: (String) -> Unit,
        proceed: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < 33 || !onDevice) {
            proceed()
            return
        }
        runCatching {
            recognizer.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val installed = support.installedOnDeviceLanguages.supportsRussian()
                    val pending = support.pendingOnDeviceLanguages.supportsRussian()
                    val supported = support.supportedOnDeviceLanguages.supportsRussian()
                    when {
                        installed -> proceed()
                        pending -> reject("Русская модель Android SpeechRecognizer ещё загружается")
                        supported -> {
                            runCatching { recognizer.triggerModelDownload(intent) }
                            reject("Русская модель Android SpeechRecognizer не установлена. Запрос на загрузку отправлен системе")
                        }
                        else -> reject("Локальный Android SpeechRecognizer не поддерживает русский язык")
                    }
                }

                override fun onError(error: Int) {
                    // Some vendor services implement recognition but not the API 33
                    // support probe. Preserve the Android 10–12 behavior for them.
                    proceed()
                }
            })
        }.onFailure { proceed() }
    }

    private fun List<String>.supportsRussian(): Boolean = any { tag ->
        Locale.forLanguageTag(tag.replace('_', '-')).language.equals("ru", ignoreCase = true)
    }

    private fun listener(
        recognizer: SpeechRecognizer,
        onResult: (SpeechRecognition) -> Unit,
        onError: (Throwable) -> Unit,
    ) = object : RecognitionListener {
        private fun finish() { if (liveRecognizer === recognizer) liveRecognizer = null; recognizer.destroy() }
        override fun onResults(results: Bundle) {
            val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
            finish()
            if (texts.isNullOrEmpty()) onError(IllegalStateException("Android SpeechRecognizer не распознал речь"))
            else {
                val candidates = texts.asSequence()
                    .take(4)
                    .flatMap { speechCommandAlternatives(it).asSequence() }
                    .distinct()
                    .toList()
                onResult(
                    SpeechRecognition(
                        text = candidates.first(),
                        confidence = confidence,
                        alternatives = candidates.drop(1),
                    ),
                )
            }
        }
        override fun onPartialResults(results: Bundle?) = Unit
        override fun onError(error: Int) { finish(); onError(IllegalStateException(speechError(error))) }
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun speechError(error: Int) = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи звука Android SpeechRecognizer"
        SpeechRecognizer.ERROR_CLIENT -> "Сеанс Android SpeechRecognizer отменён"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на запись звука"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Сервис распознавания недоступен без сети"
        SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Android SpeechRecognizer уже занят"
        SpeechRecognizer.ERROR_SERVER -> "Ошибка системного сервиса распознавания"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Системный распознаватель не поддерживает русский язык"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Русская модель распознавания не установлена"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Системный сервис распознавания отключился"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Слишком много запросов к системному распознавателю"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не обнаружена"
        else -> "Ошибка Android SpeechRecognizer: код $error"
    }
}

class SttRegistry(engines: List<SpeechToTextEngine>) {
    private val values = engines.associateBy { it.id }
    fun all(): List<SpeechToTextEngine> = values.values.toList()
    fun require(id: String): SpeechToTextEngine = values[id] ?: error("Неизвестный STT-движок: $id")
}

private fun writeWavHeader(stream: FileOutputStream, sampleRate: Int, dataSize: Long) {
    stream.write(wavHeader(sampleRate, dataSize))
}

private fun patchWavHeader(file: RandomAccessFile, dataSize: Long, sampleRate: Int) {
    file.seek(0)
    file.write(wavHeader(sampleRate, dataSize))
}

private fun wavHeader(sampleRate: Int, dataSize: Long): ByteArray =
    ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".encodeToByteArray())
        .putInt((dataSize + 36).toInt())
        .put("WAVEfmt ".encodeToByteArray())
        .putInt(16)
        .putShort(1.toShort())
        .putShort(1.toShort())
        .putInt(sampleRate)
        .putInt(sampleRate * 2)
        .putShort(2.toShort())
        .putShort(16.toShort())
        .put("data".encodeToByteArray())
        .putInt(dataSize.toInt())
        .array()

package ru.nezabudka.app.speech

import android.content.Context
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import org.json.JSONObject
import ru.nezabudka.app.data.AndroidModelLibrary
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.nezabudka.app.data.ModelDownloadWorker
import ru.nezabudka.app.data.RupunctModel
import ru.nezabudka.app.data.UserModelsStorage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class RupunctModelState(
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val error: String? = null,
    val installedModels: List<String> = emptyList(),
    val selectedName: String? = null,
)

class RupunctModelMissingException : IllegalStateException("Скачайте RUPunct Small в настройках пунктуации голосовых заметок.")

class RupunctModelManager(context: Context, private val storage: UserModelsStorage) {
    private val app = context.applicationContext
    private val library = AndroidModelLibrary(app, storage, ModelDownloadWorker.KIND_PUNCTUATION,
        "onnx", "punctuation_models", RupunctModel.FILE_NAME, ::validateImported)
    private val workManager by lazy { WorkManager.getInstance(app) }
    private val observerStarted = AtomicBoolean(false)
    private val _state = MutableStateFlow(RupunctModelState())
    val state: StateFlow<RupunctModelState> = _state

    fun start(scope: CoroutineScope) {
        refresh()
        if (!observerStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.uniqueWorkName(ModelDownloadWorker.KIND_PUNCTUATION))
                .collectLatest { values ->
                    val active = values.firstOrNull { !it.state.isFinished } ?: values.lastOrNull() ?: return@collectLatest
                    val snapshot = library.snapshot()
                    val installed = snapshot.selectedName != null
                    val downloading = !active.state.isFinished
                    val copied = active.progress.getLong(ModelDownloadWorker.KEY_COPIED, 0)
                    val total = active.progress.getLong(ModelDownloadWorker.KEY_TOTAL, -1)
                    _state.value = RupunctModelState(installed, downloading,
                        if (downloading && total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else null,
                        if (active.state == WorkInfo.State.FAILED) active.outputData.getString(ModelDownloadWorker.KEY_ERROR) else null,
                        snapshot.installed, snapshot.selectedName)
                }
        }
    }

    @Synchronized fun refresh() {
        // Preserve the already prepared 0.2.9 model when upgrading to downloadable weights.
        val legacy = File(app.noBackupFilesDir, "punctuation/${RupunctModel.FILE_NAME}")
        if (legacy.isFile && !isInstalled()) {
            RupunctModel.validate(legacy)
            storage.store(ModelDownloadWorker.KIND_PUNCTUATION, RupunctModel.FILE_NAME, legacy)
        }
        if (legacy.isFile && isInstalled()) legacy.delete()
        val snapshot = library.snapshot()
        _state.value = _state.value.copy(installed = snapshot.selectedName != null,
            installedModels = snapshot.installed, selectedName = snapshot.selectedName)
    }

    fun download() {
        ModelDownloadWorker.enqueue(app, ModelDownloadWorker.KIND_PUNCTUATION, "rupunct-small", "RUPunct Small",
            RupunctModel.FILE_NAME, RupunctModel.URL)
        _state.value = _state.value.copy(downloading = true, progress = null, error = null)
    }

    fun cancelDownload() = workManager.cancelUniqueWork(ModelDownloadWorker.uniqueWorkName(ModelDownloadWorker.KIND_PUNCTUATION))

    fun modelFile(): File {
        refresh()
        return library.selectedFile("Выберите модель пунктуации", ::RupunctModelMissingException)
    }

    fun importModel(uri: Uri) { library.import(uri); refresh() }
    fun selectModel(name: String) { library.select(name); refresh() }
    @Synchronized fun deleteModel(name: String) { library.delete(name); refresh() }

    private fun validateImported(file: File) {
        if (file.name == "${RupunctModel.FILE_NAME}.import.part") RupunctModel.validate(file)
        val labels = JSONObject(app.assets.open("punctuation/config.json").bufferedReader().use { it.readText() })
            .getJSONObject("id2label").length()
        val environment = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            environment.createSession(file.absolutePath, options).use { session ->
                val names = setOf("input_ids", "attention_mask", "token_type_ids")
                require(session.inputNames == names) { "Нужна ONNX-модель, совместимая с RUPunct: input_ids, attention_mask, token_type_ids" }
                val shape = longArrayOf(1L, 128L)
                OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(128)), shape).use { ids ->
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(128) { 1L }), shape).use { mask ->
                        OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(128)), shape).use { types ->
                            session.run(mapOf("input_ids" to ids, "attention_mask" to mask, "token_type_ids" to types)).use { result ->
                                @Suppress("UNCHECKED_CAST")
                                val logits = result[0].value as? Array<Array<FloatArray>>
                                require(logits?.size == 1 && logits[0].size == 128 && logits[0].all { it.size == labels }) {
                                    "Несовместимый результат модели: требуется формат меток RUPunct"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isInstalled() = storage.exists(ModelDownloadWorker.KIND_PUNCTUATION, RupunctModel.FILE_NAME)
}

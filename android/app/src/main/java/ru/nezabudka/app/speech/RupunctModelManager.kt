package ru.nezabudka.app.speech

import android.content.Context
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
)

class RupunctModelMissingException : IllegalStateException("Скачайте RUPunct Small в настройках пунктуации голосовых заметок.")

class RupunctModelManager(context: Context, private val storage: UserModelsStorage) {
    private val app = context.applicationContext
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
                    val installed = isInstalled()
                    val downloading = !active.state.isFinished
                    val copied = active.progress.getLong(ModelDownloadWorker.KEY_COPIED, 0)
                    val total = active.progress.getLong(ModelDownloadWorker.KEY_TOTAL, -1)
                    _state.value = RupunctModelState(installed, downloading,
                        if (downloading && total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else null,
                        if (active.state == WorkInfo.State.FAILED) active.outputData.getString(ModelDownloadWorker.KEY_ERROR) else null)
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
        _state.value = _state.value.copy(installed = isInstalled())
    }

    fun download() {
        ModelDownloadWorker.enqueue(app, ModelDownloadWorker.KIND_PUNCTUATION, "rupunct-small", "RUPunct Small",
            RupunctModel.FILE_NAME, RupunctModel.URL)
        _state.value = _state.value.copy(downloading = true, progress = null, error = null)
    }

    fun cancelDownload() = workManager.cancelUniqueWork(ModelDownloadWorker.uniqueWorkName(ModelDownloadWorker.KIND_PUNCTUATION))

    fun modelFile(): File {
        refresh()
        if (!isInstalled()) throw RupunctModelMissingException()
        return storage.materialize(ModelDownloadWorker.KIND_PUNCTUATION, RupunctModel.FILE_NAME)
    }

    private fun isInstalled() = storage.exists(ModelDownloadWorker.KIND_PUNCTUATION, RupunctModel.FILE_NAME)
}

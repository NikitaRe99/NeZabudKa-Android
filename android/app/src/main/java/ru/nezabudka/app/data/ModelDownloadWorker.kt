package ru.nezabudka.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .build()

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND)?.takeIf { it in setOf(KIND_WHISPER, KIND_NLU, KIND_PUNCTUATION) }
            ?: return Result.failure(errorData("Неизвестный тип модели"))
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure(errorData("Не указана модель"))
        val title = inputData.getString(KEY_TITLE) ?: modelId
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure(errorData("Не указан файл модели"))
        val url = inputData.getString(KEY_URL) ?: return Result.failure(errorData("Не указан адрес модели"))
        if (kind == KIND_PUNCTUATION && (fileName != RupunctModel.FILE_NAME || url != RupunctModel.URL)) {
            return Result.failure(errorData("Неизвестная модель пунктуации"))
        }
        val partial = File(applicationContext.cacheDir, "model-downloads/$kind/$fileName.part").apply {
            parentFile?.mkdirs()
        }
        partial.parentFile?.listFiles()?.forEach { file ->
            if (file.isFile && file.extension == "part" && file != partial) file.delete()
        }

        return try {
            setForeground(foregroundInfo(title, 0, indeterminate = true))
            download(url, partial, modelId, title)
            if (kind == KIND_PUNCTUATION) RupunctModel.validate(partial) else validate(partial)
            if (kind == KIND_NLU && fileName == NLU_V7_FILE_NAME) validateNluV7(partial)
            UserModelsStorage(applicationContext).store(kind, fileName, partial)
            applicationContext.getSharedPreferences("${kind}_models", Context.MODE_PRIVATE)
                .edit().putString(KEY_SELECTED_MODEL, fileName).commit()
            partial.delete()
            Result.success(Data.Builder().putString(KEY_MODEL_ID, modelId).putString(KEY_FILE_NAME, fileName).build())
        } catch (error: Throwable) {
            if (runAttemptCount < 3 && error is IOException) {
                Result.retry()
            } else {
                if (error !is IOException) partial.delete()
                Result.failure(errorData(error.message ?: "Ошибка загрузки модели"))
            }
        }
    }

    private suspend fun download(url: String, partial: File, modelId: String, title: String) {
        val alreadyDownloaded = partial.takeIf(File::isFile)?.length() ?: 0L
        val request = Request.Builder().url(url).apply {
            if (alreadyDownloaded > 0L) header("Range", "bytes=$alreadyDownloaded-")
        }.build()
        http.newCall(request).execute().use { response ->
            if (response.code == 416 && alreadyDownloaded > 0L) return
            if (!response.isSuccessful) throw IOException("Сервер модели вернул HTTP ${response.code}")
            val append = alreadyDownloaded > 0L && response.code == 206
            val initial = if (append) alreadyDownloaded else 0L
            val body = response.body ?: throw IOException("Пустой ответ сервера модели")
            val responseLength = body.contentLength()
            val total = if (responseLength > 0) initial + responseLength else -1L
            body.byteStream().use { input ->
                FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var copied = initial
                    var lastPercent = -1
                    while (true) {
                        if (isStopped) throw IOException("Загрузка отменена")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val percent = if (total > 0) ((copied * 100L) / total).toInt().coerceIn(0, 100) else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            setProgress(
                                Data.Builder()
                                    .putString(KEY_MODEL_ID, modelId)
                                    .putLong(KEY_COPIED, copied)
                                    .putLong(KEY_TOTAL, total)
                                    .build(),
                            )
                            setForeground(foregroundInfo(title, percent.coerceAtLeast(0), total <= 0))
                        }
                    }
                }
            }
        }
    }

    private fun validate(file: File) {
        require(file.length() > 1_000_000) { "Файл слишком мал и не похож на модель" }
    }

    private fun validateNluV7(file: File) {
        require(file.length() == NLU_V7_BYTES) { "Размер NLU v7 не совпадает с опубликованной моделью" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == NLU_V7_SHA256) { "Контрольная сумма NLU v7 не совпадает" }
    }

    private fun foregroundInfo(title: String, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Загрузка моделей", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Загрузка модели")
            .setContentText(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .addAction(
                android.R.drawable.ic_delete,
                "Отмена",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            )
            .build()
        return ForegroundInfo(notificationId(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun notificationId(): Int = NOTIFICATION_ID + (id.hashCode() and 0x0fff)

    private fun errorData(message: String) = Data.Builder().putString(KEY_ERROR, message).build()

    companion object {
        const val KIND_WHISPER = "whisper"
        const val KIND_NLU = "nlu"
        const val KIND_PUNCTUATION = "punctuation"
        const val KEY_KIND = "kind"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_TITLE = "title"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_URL = "url"
        const val KEY_COPIED = "copied"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 4102
        private const val NLU_V7_FILE_NAME = "classic_nlu_v7_int8.onnx"
        private const val NLU_V7_BYTES = 29_265_839L
        private const val NLU_V7_SHA256 = "9f684131355751923109798bcf8ed38927d63a816f366fde9f7c5cfa73d9931a"

        fun enqueue(context: Context, kind: String, modelId: String, title: String, fileName: String, url: String) {
            val input = Data.Builder()
                .putString(KEY_KIND, kind)
                .putString(KEY_MODEL_ID, modelId)
                .putString(KEY_TITLE, title)
                .putString(KEY_FILE_NAME, fileName)
                .putString(KEY_URL, url)
                .build()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(input)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("model-download-$kind")
                .addTag("model-id:$modelId")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(kind),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun uniqueWorkName(kind: String) = "model-download-$kind"
    }
}

package ru.nezabudka.app.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import okhttp3.Cache
import okhttp3.Request
import ru.nezabudka.app.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppUpdateState(
    val status: String,
    val version: String?,
    val percent: Int,
    val message: String?,
    val searchEnabled: Boolean = true,
    val checkedAt: Long = 0L,
    val changes: String = "",
    val size: Long = 0L,
)

object GithubAppUpdater {
    private const val PREFERENCES = "github_app_updates"
    private const val PERIODIC = "github-release-periodic"
    private const val MANUAL = "github-release-manual"
    private const val CHANNEL = "app_updates"
    internal const val API_URL = "https://api.github.com/repos/NikitaRe99/NeZabudKa-Android/releases?per_page=30"
    internal val supported: Boolean get() = BuildConfig.GITHUB_UPDATES_ENABLED && BuildConfig.BUILD_TYPE == "release" && BuildConfig.APPLICATION_ID == "ru.nezabudka.app"

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    internal fun file(context: Context) = File(context.cacheDir, "updates/release.apk")

    fun read(context: Context): AppUpdateState {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) return AppUpdateState("disabled", null, 0, null, searchEnabled = false)
        val prefs = preferences(context)
        val status = prefs.getString("status", "idle") ?: "idle"
        val version = prefs.getString("version", null)
        val actionable = status in setOf("ready", "available", "downloading")
        val newer = isNewerAppVersion(version, BuildConfig.VERSION_NAME)
        return AppUpdateState(
            status = when {
                actionable && !newer -> "none"
                status == "ready" && !file(context).isFile -> "available"
                else -> status
            },
            version = version.takeIf { newer },
            percent = prefs.getInt("percent", 0),
            message = prefs.getString("message", null),
            searchEnabled = prefs.getBoolean("search_enabled", true),
            checkedAt = prefs.getLong("checked_at", 0L),
            changes = prefs.getString("changes", "").orEmpty(),
            size = prefs.getLong("size", 0L),
        )
    }

    fun observe(context: Context): Flow<AppUpdateState> = callbackFlow {
        val prefs = preferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    internal fun update(context: Context, status: String, version: String? = null, percent: Int = 0, message: String? = null) {
        val editor = preferences(context).edit()
            .putString("status", status)
            .putString("version", version)
            .putInt("percent", percent)
            .putString("message", message)
        if (status in setOf("none", "available", "ready")) editor.putLong("checked_at", System.currentTimeMillis())
        editor.apply()
    }

    internal fun releaseDetails(context: Context, release: GithubRelease) {
        preferences(context).edit().putString("changes", release.changes).putLong("size", release.size).apply()
    }


    fun setSearchEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("search_enabled", enabled).apply()
        configure(context)
    }

    fun configure(context: Context) {
        // Cancel periodic downloads retained by older versions.
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        if (!supported) WorkManager.getInstance(context).cancelUniqueWork(MANUAL)
        preferences(context).edit().remove("automatic").apply()
    }

    fun checkNow(context: Context, download: Boolean = false, startup: Boolean = false) {
        if (!supported) return
        val state = read(context)
        if (startup && (!state.searchEnabled || !shouldCheckOnStartup(System.currentTimeMillis(), state.checkedAt))) return
        val request = OneTimeWorkRequestBuilder<GithubUpdateWorker>()
            .setInputData(workDataOf("download" to download, "startup" to startup))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL,
            if (startup) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
    }

    fun install(context: Context) {
        if (!supported) return
        context.startActivity(Intent(context, UpdateInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal fun notifyReady(context: Context, version: String) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Обновления приложения", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, UpdateInstallActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notifications.notify(5460, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("НеЗабудьКа $version готова к установке")
            .setContentText("Нажмите, чтобы подтвердить обновление")
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build())
    }
}

class GithubUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    companion object {
        private val operationMutex = Mutex()
        @Volatile private var sharedClient: OkHttpClient? = null
        @Synchronized private fun client(context: Context): OkHttpClient = sharedClient ?: OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            // OkHttp stores ETags and merges conditional 304 responses with cached pages.
            .cache(Cache(File(context.cacheDir, "updates/http"), 5L * 1024 * 1024)).build().also { sharedClient = it }
    }
    override suspend fun doWork(): Result = operationMutex.withLock { performUpdate() }
    private suspend fun performUpdate(): Result = withContext(Dispatchers.IO) {
        if (!GithubAppUpdater.supported) return@withContext Result.success()
        val context = applicationContext
        if (inputData.getBoolean("automatic", false)) return@withContext Result.success()
        val preferences = GithubAppUpdater.read(context)
        if (inputData.getBoolean("startup", false) && !preferences.searchEnabled)
            return@withContext Result.success()
        if (inputData.getBoolean("startup", false) && !shouldCheckOnStartup(System.currentTimeMillis(), preferences.checkedAt))
            return@withContext Result.success()
        try {
            GithubAppUpdater.update(context, "checking")
            val client = client(context)
            val pages = mutableListOf<kotlinx.serialization.json.JsonElement>()
            var page = 1
            while (true) {
                val request = Request.Builder().url("${GithubAppUpdater.API_URL}&page=$page")
                    .header("Cache-Control", "max-age=0")
                    .header("Accept", "application/vnd.github+json").header("User-Agent", "NeZabudKa-Android").build()
                val batch = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("GitHub: HTTP ${response.code}")
                    kotlinx.serialization.json.Json.parseToJsonElement(response.body?.string() ?: error("Пустой ответ GitHub")) as kotlinx.serialization.json.JsonArray
                }
                pages.addAll(batch)
                if (batch.size < 30) break
                if (page == 1 && !needsOlderReleasePages(batch.toString(), BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS.toList())) break
                page++
                if (isStopped) throw CancellationException()
            }
            val releases = kotlinx.serialization.json.JsonArray(pages).toString()
            val release = findAppRelease(releases, BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS.toList())
            if (release == null) {
                GithubAppUpdater.update(context, "none")
                return@withContext Result.success()
            }
            GithubAppUpdater.releaseDetails(context, release)
            val target = GithubAppUpdater.file(context)
            if (target.isFile && validDownload(target, release)) {
                GithubAppUpdater.update(context, "ready", release.version, 100)
                if (inputData.getBoolean("download", false)) GithubAppUpdater.notifyReady(context, release.version)
                return@withContext Result.success()
            }
            if (!inputData.getBoolean("download", false)) {
                GithubAppUpdater.update(context, "available", release.version)
                return@withContext Result.success()
            }
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, "release.part")
            partial.delete()
            GithubAppUpdater.update(context, "downloading", release.version)
            setForeground(downloadForegroundInfo(release.version))
            val download = Request.Builder().url(release.downloadUrl).header("User-Agent", "NeZabudKa-Android")
                .header("Cache-Control", "no-store").build()
            client.newCall(download).execute().use { response ->
                if (!response.isSuccessful) error("Загрузка: HTTP ${response.code}")
                val body = response.body ?: error("Пустой файл обновления")
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastPercent = -1
                        while (true) {
                            if (isStopped) error("Загрузка отменена")
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > release.size) throw UpdateValidationException("Размер APK превышает указанный в релизе", retryable = true)
                            output.write(buffer, 0, count)
                            val percent = (total * 100 / release.size).toInt()
                            if (percent >= lastPercent + 5) {
                                GithubAppUpdater.update(context, "downloading", release.version, percent)
                                lastPercent = percent
                            }
                        }
                    }
                }
            }
            downloadValidationError(partial, release)?.let {
                // Once size and digest match, an incompatible package/signature cannot
                // be fixed by downloading these exact bytes again.
                val integrityMatches = partial.length() == release.size && sha256(partial).equals(release.sha256, true)
                throw UpdateValidationException(it, retryable = !integrityMatches)
            }
            target.delete()
            if (!partial.renameTo(target)) error("Не удалось сохранить APK")
            GithubAppUpdater.update(context, "ready", release.version, 100)
            GithubAppUpdater.notifyReady(context, release.version)
            Result.success()
        } catch (cancelled: CancellationException) {
            GithubAppUpdater.update(context, "idle")
            throw cancelled
        } catch (error: Exception) {
            GithubAppUpdater.update(context, "error", message = error.message ?: "Ошибка обновления")
            when {
                error is UpdateValidationException -> if (error.retryable && runAttemptCount < 1) Result.retry() else Result.failure()
                runAttemptCount < 2 -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    private fun validDownload(file: File, release: GithubRelease): Boolean = downloadValidationError(file, release) == null

    private fun downloadValidationError(file: File, release: GithubRelease): String? {
        if (file.length() != release.size) return "Размер APK не совпадает с указанным в релизе. Повторите загрузку"
        if (!sha256(file).equals(release.sha256, true))
            return "Хеш APK не совпадает с указанным в релизе. Повторите загрузку"
        return UpdateInstallActivity.apkValidationError(applicationContext, file)
    }

    private fun sha256(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadForegroundInfo(version: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("app_update_download", "Загрузка обновления", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "app_update_download")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("НеЗабудьКа $version")
            .setContentText("Загрузка обновления")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        return ForegroundInfo(5461, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}

private class UpdateValidationException(message: String, val retryable: Boolean) : IllegalStateException(message)

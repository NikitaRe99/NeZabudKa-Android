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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.nezabudka.app.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppUpdateState(
    val automatic: Boolean,
    val status: String,
    val version: String?,
    val percent: Int,
    val message: String?,
)

object GithubAppUpdater {
    private const val PREFERENCES = "github_app_updates"
    private const val PERIODIC = "github-release-periodic"
    private const val MANUAL = "github-release-manual"
    private const val CHANNEL = "app_updates"
    internal const val API_URL = "https://api.github.com/repos/NikitaRe99/NeZabudKa-Android/releases?per_page=30"
    internal val supported: Boolean get() = BuildConfig.BUILD_TYPE == "release" && BuildConfig.APPLICATION_ID == "ru.nezabudka.app"

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    internal fun file(context: Context) = File(context.cacheDir, "updates/release.apk")

    fun read(context: Context): AppUpdateState {
        val prefs = preferences(context)
        val status = prefs.getString("status", "idle") ?: "idle"
        return AppUpdateState(
            automatic = prefs.getBoolean("automatic", true),
            status = if (status == "ready" && !file(context).isFile) "available" else status,
            version = prefs.getString("version", null),
            percent = prefs.getInt("percent", 0),
            message = prefs.getString("message", null),
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
        preferences(context).edit()
            .putString("status", status)
            .putString("version", version)
            .putInt("percent", percent)
            .putString("message", message)
            .apply()
    }

    fun setAutomatic(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("automatic", enabled).apply()
        configure(context)
    }

    fun configure(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!supported || !preferences(context).getBoolean("automatic", true)) {
            manager.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<GithubUpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun checkNow(context: Context) {
        if (!supported) return
        val request = OneTimeWorkRequestBuilder<GithubUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.REPLACE, request)
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
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!GithubAppUpdater.supported) return@withContext Result.success()
        val context = applicationContext
        try {
            GithubAppUpdater.update(context, "checking")
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(GithubAppUpdater.API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NeZabudKa-Android")
                .build()
            val releases = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub: HTTP ${response.code}")
                response.body?.string() ?: error("Пустой ответ GitHub")
            }
            val release = findAppRelease(releases, BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS.toList())
            if (release == null) {
                GithubAppUpdater.update(context, "none")
                return@withContext Result.success()
            }
            val target = GithubAppUpdater.file(context)
            if (target.isFile && validDownload(target, release)) {
                GithubAppUpdater.update(context, "ready", release.version, 100)
                GithubAppUpdater.notifyReady(context, release.version)
                return@withContext Result.success()
            }
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, "release.part")
            partial.delete()
            GithubAppUpdater.update(context, "downloading", release.version)
            setForeground(downloadForegroundInfo(release.version))
            val download = Request.Builder().url(release.downloadUrl).header("User-Agent", "NeZabudKa-Android").build()
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
                            if (total > release.size) error("Размер APK превышает указанный в релизе")
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
            if (!validDownload(partial, release)) error("APK не прошёл проверку размера, хеша или подписи")
            target.delete()
            if (!partial.renameTo(target)) error("Не удалось сохранить APK")
            GithubAppUpdater.update(context, "ready", release.version, 100)
            GithubAppUpdater.notifyReady(context, release.version)
            Result.success()
        } catch (error: Exception) {
            GithubAppUpdater.update(context, "error", message = error.message ?: "Ошибка обновления")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun validDownload(file: File, release: GithubRelease): Boolean {
        if (file.length() != release.size) return false
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        if (!hash.digest().joinToString("") { "%02x".format(it) }.equals(release.sha256, true)) return false
        return UpdateInstallActivity.isValidApk(applicationContext, file)
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

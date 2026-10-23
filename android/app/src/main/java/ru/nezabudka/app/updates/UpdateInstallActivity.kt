package ru.nezabudka.app.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import ru.nezabudka.app.BuildConfig
import java.io.File

/** Installation always goes through Android's confirmation screen. */
class UpdateInstallActivity : ComponentActivity() {
    private val settingsResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) openSystemInstaller()
        else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!GithubAppUpdater.supported || GithubAppUpdater.read(this).status != "ready" ||
            !isValidApk(this, GithubAppUpdater.file(this))
        ) {
            Toast.makeText(this, "Проверенный APK обновления не найден", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (packageManager.canRequestPackageInstalls()) openSystemInstaller()
        else settingsResult.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:$packageName")))
    }

    private fun openSystemInstaller() {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", GithubAppUpdater.file(this))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось открыть системный установщик", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        internal fun isValidApk(context: Context, file: File): Boolean = apkValidationError(context, file) == null

        internal fun apkValidationError(context: Context, file: File): String? = runCatching {
            if (!file.isFile) return@runCatching "Файл обновления не найден"
            if (!GithubAppUpdater.supported) return@runCatching "Обновления недоступны для этой сборки"
            val manager = context.packageManager
            val archive = readUpdateArchive(manager, file)
                ?: return@runCatching "Android не удалось прочитать APK обновления"
            val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            if (archive.packageName != BuildConfig.APPLICATION_ID) return@runCatching "APK предназначен для другого приложения"
            if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed))
                return@runCatching "Версия APK уже установлена или старее установленной"
            val archiveSigners = archive.signingInfo?.apkContentsSigners
                ?: return@runCatching "Android не удалось прочитать сертификат APK"
            val installedSigners = installed.signingInfo?.apkContentsSigners
                ?: return@runCatching "Android не удалось прочитать сертификат установленного приложения"
            val matches = archiveSigners.isNotEmpty() && archiveSigners.size == installedSigners.size && archiveSigners.all { candidate ->
                installedSigners.any { it.toByteArray().contentEquals(candidate.toByteArray()) }
            }
            if (matches) null else "Подпись APK не совпадает с установленным приложением"
        }.getOrElse { "Не удалось проверить APK: ${it.message ?: "ошибка Android"}" }
    }
}

/** Android 10 requires GET_SIGNATURES too to populate archive.signingInfo. */
@Suppress("DEPRECATION")
internal fun readUpdateArchive(manager: PackageManager, file: File): PackageInfo? =
    manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)

package ru.nezabudka.app.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        internal fun isValidApk(context: Context, file: File): Boolean = runCatching {
            if (!file.isFile || !GithubAppUpdater.supported) return@runCatching false
            val manager = context.packageManager
            val archive = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: return@runCatching false
            val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            if (archive.packageName != BuildConfig.APPLICATION_ID ||
                PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)
            ) return@runCatching false
            val archiveSigners = archive.signingInfo?.apkContentsSigners ?: return@runCatching false
            val installedSigners = installed.signingInfo?.apkContentsSigners ?: return@runCatching false
            archiveSigners.size == installedSigners.size && archiveSigners.all { candidate ->
                installedSigners.any { it.toByteArray().contentEquals(candidate.toByteArray()) }
            }
        }.getOrDefault(false)
    }
}

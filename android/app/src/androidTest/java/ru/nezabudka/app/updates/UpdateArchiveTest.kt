package ru.nezabudka.app.updates

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateArchiveTest {
    @Test fun archiveCertificateIsAvailableForApkAndPartialDownload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val expected = installed.signingInfo!!.apkContentsSigners.map { it.toCharsString() }.toSet()
        for (extension in listOf("apk", "part")) {
            val copy = File(context.cacheDir, "update-archive-test.$extension")
            try {
                File(context.applicationInfo.sourceDir).copyTo(copy, overwrite = true)
                val archive = readUpdateArchive(context.packageManager, copy)
                assertNotNull("Archive could not be parsed: $extension", archive)
                assertEquals(context.packageName, archive!!.packageName)
                assertNotNull("Certificate missing: $extension", archive.signingInfo)
                assertEquals(expected, archive.signingInfo!!.apkContentsSigners.map { it.toCharsString() }.toSet())
            } finally { copy.delete() }
        }
    }
}

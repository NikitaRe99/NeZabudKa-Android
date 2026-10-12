package ru.nezabudka.app.speech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.ModelDownloadWorker
import ru.nezabudka.app.data.RupunctModel
import ru.nezabudka.app.data.UserModelsStorage
import java.io.File

@RunWith(AndroidJUnit4::class)
class RupunctDownloadTest {
    @Test fun weights_are_not_bundled_and_downloaded_model_is_verified() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_punctuation_download") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(context.assets.list("punctuation").orEmpty().contains(RupunctModel.FILE_NAME))
        val storage = UserModelsStorage(context)
        val manager = RupunctModelManager(context, storage)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val workManager = WorkManager.getInstance(context)
        val name = ModelDownloadWorker.uniqueWorkName(ModelDownloadWorker.KIND_PUNCTUATION)
        val prior = workManager.getWorkInfosForUniqueWork(name).get().map { it.id }.toSet()
        try {
            manager.start(scope)
            manager.download()
            val result = withTimeout(90_000) {
                val work = workManager.getWorkInfosForUniqueWorkFlow(name).first { values -> values.any { it.id !in prior } }
                    .first { it.id !in prior }
                workManager.getWorkInfosForUniqueWorkFlow(name).first { values -> values.any { it.id == work.id && it.state.isFinished } }
                    .first { it.id == work.id }
            }
            assertTrue("Download failed: ${result.outputData.getString(ModelDownloadWorker.KEY_ERROR)}", result.state == WorkInfo.State.SUCCEEDED)
            manager.refresh()
            assertTrue(manager.state.value.installed)
            RupunctModel.validate(manager.modelFile())
            assertFalse(File(context.noBackupFilesDir, "punctuation/${RupunctModel.FILE_NAME}").exists())
            val output = NotePunctuation(context, storage).restore("Я знаю что ты придёшь завтра.", NOTE_PUNCTUATION_RUPUNCT)
            assertTrue(output.contains("знаю,"))
        } finally {
            manager.cancelDownload()
            scope.cancel()
        }
    }
}

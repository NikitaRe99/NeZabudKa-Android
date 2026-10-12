package ru.nezabudka.app.speech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.UserModelsStorage
import ru.nezabudka.app.domain.extractFolderNoteInsertion
import java.io.File

/** Reads a copied recording and parses the transcript. Never dispatches a command. */
@RunWith(AndroidJUnit4::class)
class FolderCommandRecognitionTest {
    @Test fun real_folder_command_excludes_the_action_from_the_folder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recording = File(context.filesDir, "folder-command-reference.wav")
        assumeTrue(recording.isFile)
        val models = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        assumeTrue(models.state.value.selectedName != null)
        val engine = WhisperCppSttEngine(models, WhisperVadModelProvider(context), File(context.cacheDir, "folder-command-test-segments"),
            configuredThreadCount = { 6 }, accurateMode = { false })
        try {
            engine.setCommandVocabularyEnabled(true)
            engine.setContextHints(listOf("Работа"))
            val transcript = engine.transcribe(recording).text
            val parts = extractFolderNoteInsertion(transcript)
            File(context.filesDir, "folder-command-report.json").writeText(JSONObject()
                .put("transcript", transcript).put("folder", parts?.first).put("text", parts?.second).toString(2))
            assertEquals("work folder must exclude the action: $transcript", "работа", parts?.first?.lowercase())
            assertFalse(parts?.second.orEmpty().startsWith(','))
            assertFalse(parts?.second.isNullOrBlank())
        } finally { engine.release() }
    }
}

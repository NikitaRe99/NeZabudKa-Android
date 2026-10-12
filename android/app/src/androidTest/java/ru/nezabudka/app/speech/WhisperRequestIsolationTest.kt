package ru.nezabudka.app.speech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.UserModelsStorage

/** Uses the installed model and a copy of the last recording, without editing user data. */
@RunWith(AndroidJUnit4::class)
class WhisperRequestIsolationTest {
    @Test fun silent_and_failed_requests_do_not_change_the_next_transcript() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WhisperModelManager(context, UserModelsStorage(context))
        manager.refresh()
        assumeTrue("Install a Whisper model before running this regression", manager.state.value.selectedName != null)
        val source = File(context.filesDir, "recordings/last_processed.wav")
            .takeIf { it.isFile } ?: File(context.filesDir, "recordings/last_raw.wav")
        assumeTrue("Record a phrase before running this regression", source.isFile && source.length() > 44)
        val directory = File(context.cacheDir, "whisper-isolation-${System.nanoTime()}").apply { mkdirs() }
        val recording = source.copyTo(File(directory, "phrase.wav"))
        val silent = File(directory, "silence.wav")
        val broken = File(directory, "broken.wav").apply { writeText("invalid WAV") }
        val model = manager.selectedModel(pcmWavBytes = recording.length())
        val policy = whisperInferencePolicy(recording.length(), model.length() <= 90_000_000L)
        fun recognize(file: File, prompt: String, audioContext: Int): String = WhisperNative.transcribeWav(
            model.absolutePath, file.absolutePath, "", "ru", prompt,
            manager.recommendedThreadCount(), audioContext, policy.maxTokens, policy.singleSegment,
        )
        try {
            val prompt = contextualWhisperPrompt(emptyList())
            val reference = recognize(recording, prompt, policy.audioContext)
            assumeTrue("The reference recording must contain recognizable speech", reference.isNotBlank())
            val pcmSize = 2 * 16_000 * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + pcmSize)
                .put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(16_000).putInt(32_000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(pcmSize).array()
            silent.outputStream().use { it.write(header); it.write(ByteArray(pcmSize)) }
            // A different prompt and encoder size must not carry into the next request.
            recognize(silent, "Посторонняя фраза, не относящаяся к записи.", 0)
            assertEquals(reference, recognize(recording, prompt, policy.audioContext))
            try {
                recognize(broken, prompt, policy.audioContext)
                fail("An invalid recording must fail")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().isNotBlank())
            }
            assertEquals(reference, recognize(recording, prompt, policy.audioContext))
        } finally {
            WhisperNative.release()
            directory.deleteRecursively()
        }
    }
}

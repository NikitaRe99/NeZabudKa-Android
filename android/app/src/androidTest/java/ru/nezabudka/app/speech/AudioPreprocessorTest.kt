package ru.nezabudka.app.speech

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AudioPreprocessorTest {
    @Test fun processing_is_cached_and_removes_dc_offset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("audio_preprocessor", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "recordings/last_processed.wav").delete()
        val source = File(context.cacheDir, "source.wav")
        writeTestWav(
            source,
            ShortArray(1_599) { index ->
                val signal = when (index % 3) {
                    0 -> -1_000
                    1 -> 0
                    else -> 1_000
                }
                (4_000 + signal).toShort()
            },
        )
        val preprocessor = PcmAudioPreprocessor(context)
        val first = preprocessor.process(source)
        val modified = first.lastModified()
        val second = preprocessor.process(source)
        assertEquals(first.absolutePath, second.absolutePath)
        assertEquals(modified, second.lastModified())
        val bytes = second.readBytes()
        val samples = (44 until bytes.size step 2).map { ((bytes[it].toInt() and 0xff) or (bytes[it + 1].toInt() shl 8)).toShort().toInt() }
        assertTrue(abs(samples.average()) < 2.0)
        val peak = samples.maxOf { abs(it) }
        assertTrue(peak > 1_000)
        assertTrue(peak < 10_000) // quiet input must not amplify its noise to near-clipping
    }

    private fun writeTestWav(file: File, samples: ShortArray) {
        val data = samples.size * 2
        val output = ByteArrayOutputStream()
        fun le16(v: Int) { output.write(v and 255); output.write(v shr 8 and 255) }
        fun le32(v: Int) { repeat(4) { output.write(v shr (it * 8) and 255) } }
        output.write("RIFF".encodeToByteArray()); le32(data + 36); output.write("WAVEfmt ".encodeToByteArray()); le32(16)
        le16(1); le16(1); le32(16000); le32(32000); le16(2); le16(16); output.write("data".encodeToByteArray()); le32(data)
        samples.forEach { le16(it.toInt()) }
        FileOutputStream(file).use { it.write(output.toByteArray()) }
    }
}

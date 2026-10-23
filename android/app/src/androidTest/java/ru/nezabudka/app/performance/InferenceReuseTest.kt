package ru.nezabudka.app.performance

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.UserModelsStorage
import ru.nezabudka.app.speech.*

/** Optional device fixtures are staged only in the separate debug application's storage. */
@RunWith(AndroidJUnit4::class)
class InferenceReuseTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun gigaamFinalTextMatchesLegacyInferenceAndWarmSession() = runBlocking {
        check(context.packageName.endsWith(".debug"))
        val root = requireNotNull(context.getExternalFilesDir(null))
        val model = File(root, "models/gigaam/gigaam-v3-ctc-int8.onnx")
        val inputs = File(root, "quality-inputs").listFiles { file -> file.extension == "wav" }.orEmpty().sortedBy { it.name }
        assumeTrue("Stage GigaAM and WAV fixtures for device quality checks", model.isFile && inputs.isNotEmpty())
        val env = OrtEnvironment.getEnvironment()
        val expected = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(4); options.setInterOpNumThreads(1)
            options.addConfigEntry("session.intra_op.allow_spinning", "1")
            options.addConfigEntry("session.inter_op.allow_spinning", "1")
            env.createSession(model.path, options).use { session ->
                val vocabulary = validateGigaam(session)
                inputs.map { file ->
                    val audio = readGigaamWav(file)
                    gigaamChunkRanges(audio).map { range ->
                        val chunk = audio.copyOfRange(range.first, range.last + 1)
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(chunk), longArrayOf(1, chunk.size.toLong())).use { wave ->
                            OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(chunk.size.toLong())), longArrayOf(1)).use { length ->
                                session.run(mapOf("audio" to wave, "length" to length)).use { output ->
                                    @Suppress("UNCHECKED_CAST")
                                    val labels = (output[0].value as Array<LongArray>)[0]
                                    val rawLength = output[1].value
                                    val count = if (rawLength is IntArray) rawLength[0] else (rawLength as LongArray)[0].toInt()
                                    decodeGigaamCtc(labels, count, vocabulary)
                                }
                            }
                        }
                    }.filter(String::isNotBlank).joinToString(" ").trim()
                }
            }
        }
        val engine = GigaamSttEngine(context, GigaamModelManager(context, UserModelsStorage(context)), { 4 }, { false }, { true })
        try {
            inputs.forEachIndexed { index, file -> assertEquals(file.name, expected[index], engine.transcribe(file).text) }
            inputs.forEachIndexed { index, file -> assertEquals("warm ${file.name}", expected[index], engine.transcribe(file).text) }
            engine.release()
            assertEquals("after release", expected.first(), engine.transcribe(inputs.first()).text)
        } finally { engine.release() }
    }

    @Test fun punctuationMatchesAcrossColdWarmAndReleasedSessions() = runBlocking {
        check(context.packageName.endsWith(".debug"))
        val model = File(context.getExternalFilesDir(null), "models/punctuation/rupunct_small_int8.onnx")
        assumeTrue("Stage RUPunct fixture for device quality checks", model.isFile)
        val cold = NotePunctuation(context)
        val warm = NotePunctuation(context, canRetain = { true })
        val texts = listOf("завтра куплю хлеб молоко и кофе потом зайду в аптеку", "если будет дождь останемся дома и посмотрим фильм",
            "привет как дела сегодня я хочу рассказать о поездке на байкал")
        try {
            for (full in listOf(false, true)) {
                for (text in texts) {
                    val expected = cold.restore(text, NOTE_PUNCTUATION_RUPUNCT, full)
                    assertEquals(expected, warm.restore(text, NOTE_PUNCTUATION_RUPUNCT, full))
                    assertEquals(expected, warm.restore(text, NOTE_PUNCTUATION_RUPUNCT, full))
                    warm.release()
                    assertEquals(expected, warm.restore(text, NOTE_PUNCTUATION_RUPUNCT, full))
                }
            }
        } finally { cold.release(); warm.release() }
    }
}

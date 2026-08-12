package ru.nezabudka.app.speech

import java.io.File
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloudSpeechRequestBodyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun pcmBodyStreamsOnlySamplesAfterWavHeader() {
        val pcm = ByteArray(32_769) { (it % 251).toByte() }
        val wav = wav(pcm)
        val body = PcmWavRequestBody(wav, "application/octet-stream".toMediaType())
        val sink = Buffer()

        body.writeTo(sink)

        assertEquals(pcm.size.toLong(), body.contentLength())
        assertArrayEquals(pcm, sink.readByteArray())
    }

    @Test
    fun googleBodyStreamsValidBase64JsonWithExactLength() {
        val pcm = ByteArray(32_769) { (it % 239).toByte() }
        val body = GoogleSpeechJsonRequestBody(wav(pcm))
        val sink = Buffer()

        body.writeTo(sink)
        val bytes = sink.readByteArray()
        val content = Json.parseToJsonElement(bytes.decodeToString()).jsonObject["audio"]!!
            .jsonObject["content"]!!.jsonPrimitive.content

        assertEquals(bytes.size.toLong(), body.contentLength())
        assertArrayEquals(pcm, Base64.getDecoder().decode(content))
    }

    private fun wav(pcm: ByteArray): File = temporaryFolder.newFile("audio-${pcm.size}.wav").apply {
        outputStream().use { output ->
            output.write(ByteArray(44))
            output.write(pcm)
        }
    }
}

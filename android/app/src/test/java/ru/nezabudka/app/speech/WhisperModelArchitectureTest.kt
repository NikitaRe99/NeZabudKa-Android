package ru.nezabudka.app.speech

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WhisperModelArchitectureTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun `quantized Base is not treated as Tiny regardless of file size or name`() {
        val file = files.newFile("ggml-tiny.bin")
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0, 0x67676d6c).putInt(20, 6).array()
        file.writeBytes(header)
        assertFalse(isTinyWhisperModel(file))
        file.writeBytes(ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(20, 4).array())
        assertTrue(isTinyWhisperModel(file))
    }

    @Test fun `invalid or truncated headers do not select Tiny policy`() {
        val file = files.newFile()
        file.writeBytes(ByteArray(12))
        assertFalse(isTinyWhisperModel(file))
        file.writeBytes(ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(20, 4).array())
        assertFalse(isTinyWhisperModel(file))
    }
}

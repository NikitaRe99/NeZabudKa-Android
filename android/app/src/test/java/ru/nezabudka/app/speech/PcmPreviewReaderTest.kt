package ru.nezabudka.app.speech

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PcmPreviewReaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun pcm(values: ShortArray): ByteArray = ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        .apply { values.forEach(::putShort) }.array()
    private fun expected(values: ShortArray) = FloatArray(values.size) { values[it] / 32768f }

    @Test fun growingSnapshotsAreBitExactAndDoNotMutateEarlierSnapshots() {
        val file = temporary.newFile()
        file.writeBytes(ByteArray(44))
        val reader = PcmPreviewReader()
        val values = ShortArray(80_003) { (it * 7919).toShort() }
        var previous: FloatArray? = null
        for (end in listOf(8_000, 8_001, 16_401, 32_768, values.size)) {
            val from = previous?.size ?: 0
            file.appendBytes(pcm(values.copyOfRange(from, end)))
            val snapshot = reader.snapshot(file)!!
            assertArrayEquals(expected(values.copyOfRange(0, end)), snapshot, 0f)
            previous?.let { assertArrayEquals(expected(values.copyOfRange(0, it.size)), it, 0f) }
            previous = snapshot
        }
        assertArrayEquals(previous, reader.snapshot(file), 0f)
    }

    @Test fun incompletePcmSampleWaitsForItsSecondByte() {
        val file = temporary.newFile()
        val bytes = pcm(ShortArray(8_001) { (-32_768 + it).toShort() })
        file.writeBytes(ByteArray(44) + bytes.copyOf(bytes.size - 1))
        val reader = PcmPreviewReader()
        assertEquals(8_000, reader.snapshot(file)!!.size)
        file.appendBytes(byteArrayOf(bytes.last()))
        assertArrayEquals(expected(ShortArray(8_001) { (-32_768 + it).toShort() }), reader.snapshot(file), 0f)
    }

    @Test fun recordingLimitAndResetPreserveTheOriginalPreviewContract() {
        val file = temporary.newFile()
        val reader = PcmPreviewReader(maxSamples = 9_000)
        file.writeBytes(ByteArray(44) + pcm(ShortArray(10_000) { 123 }))
        assertEquals(9_000, reader.snapshot(file)!!.size)
        reader.reset()
        file.writeBytes(ByteArray(44) + pcm(ShortArray(8_000) { -456 }))
        assertArrayEquals(FloatArray(8_000) { -456 / 32768f }, reader.snapshot(file), 0f)
        file.writeBytes(ByteArray(44))
        assertNull(reader.snapshot(file))
    }
}

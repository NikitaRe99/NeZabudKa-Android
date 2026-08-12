package ru.nezabudka.app.speech

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Pcm16StreamingResamplerTest {
    @Test fun `48 kHz input is converted to exact 16 kHz sample count`() {
        val input = pcm16(ShortArray(48_000) { (it % 10_000).toShort() })

        val output = Pcm16StreamingResampler(48_000, 16_000).process(input)

        assertEquals(16_000 * 2, output.size)
        val samples = samples(output)
        assertEquals(0, samples[0].toInt())
        assertEquals(3, samples[1].toInt())
        assertEquals(6, samples[2].toInt())
    }

    @Test fun `resampling is continuous across recorder block boundaries`() {
        val input = pcm16(ShortArray(44_100) { ((it % 20_000) - 10_000).toShort() })
        val whole = Pcm16StreamingResampler(44_100, 16_000).process(input)
        val streaming = Pcm16StreamingResampler(44_100, 16_000)
        val chunks = ByteArrayOutputStream()
        var offset = 0
        while (offset < input.size) {
            val size = minOf(8_192, input.size - offset)
            chunks.write(streaming.process(input.copyOfRange(offset, offset + size)))
            offset += size
        }

        assertArrayEquals(whole, chunks.toByteArray())
        assertEquals(16_000 * 2, whole.size)
    }

    @Test fun `same sample rate preserves PCM bytes`() {
        val input = pcm16(shortArrayOf(Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE))

        assertArrayEquals(input, Pcm16StreamingResampler(16_000, 16_000).process(input))
    }

    @Test fun `8 kHz microphone input is converted to 16 kHz`() {
        val input = pcm16(ShortArray(8_000) { (it % 1_000).toShort() })

        val output = Pcm16StreamingResampler(8_000, 16_000).process(input)

        assertEquals(15_999 * 2, output.size)
        val samples = samples(output)
        assertEquals(0, samples[0].toInt())
        assertEquals(0, samples[1].toInt())
        assertEquals(1, samples[2].toInt())
    }

    private fun pcm16(samples: ShortArray): ByteArray = ByteArray(samples.size * 2).also { output ->
        samples.forEachIndexed { index, sample ->
            output[index * 2] = sample.toInt().toByte()
            output[index * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
    }

    private fun samples(pcm: ByteArray): ShortArray = ShortArray(pcm.size / 2) { index ->
        ((pcm[index * 2].toInt() and 0xff) or (pcm[index * 2 + 1].toInt() shl 8)).toShort()
    }
}

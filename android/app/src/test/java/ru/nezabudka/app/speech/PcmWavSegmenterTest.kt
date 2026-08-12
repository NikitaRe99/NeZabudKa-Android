package ru.nezabudka.app.speech

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PcmWavSegmenterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun shortRecordingIsNotCopied() {
        val input = wav("short.wav", seconds = 3)
        val work = File(temporaryFolder.root, "segments")

        val result = PcmWavSegmenter.splitIfLong(input, work)

        assertEquals(1, result.size)
        assertSame(input, result.single())
        assertFalse(work.exists())
    }

    @Test
    fun longRecordingUsesBoundedOverlappingSegmentsAndCleansThem() {
        val input = wav("long.wav", seconds = 65)
        val work = File(temporaryFolder.root, "segments")

        val result = PcmWavSegmenter.splitIfLong(input, work)

        assertEquals(listOf(30L, 30L, 7L), result.map { (it.length() - 44L) / 32_000L })
        assertEquals(sampleAt(input, 29L * 16_000L), sampleAt(result[1], 0L))
        assertEquals(sampleAt(input, 58L * 16_000L), sampleAt(result[2], 0L))
        assertTrue(result.all(File::isFile))

        PcmWavSegmenter.cleanup(result, input)
        assertFalse(work.exists())
        assertTrue(input.exists())
    }

    @Test
    fun vadRangesRemoveInternalSilenceAndKeepAShortBridge() {
        val input = wav("paused.wav", seconds = 20)
        val work = File(temporaryFolder.root, "vad-segments")

        val result = PcmWavSegmenter.splitAtSpeechRanges(
            source = input,
            workDirectory = work,
            boundaries = longArrayOf(0L, 4L * 16_000L, 12L * 16_000L, 16L * 16_000L),
            targetSeconds = 14,
        )

        assertEquals(1, result.size)
        assertEquals(8_100L, (result.single().length() - 44L) / 32L)
        assertEquals(sampleAt(input, 0L), sampleAt(result.single(), 0L))
        assertEquals(0, sampleAt(result.single(), 4L * 16_000L).toInt())
        assertEquals(sampleAt(input, 12L * 16_000L), sampleAt(result.single(), 4_100L * 16L))
    }

    @Test
    fun vadRangesAreGroupedAtDetectedSpeechBoundaries() {
        val input = wav("long-paused.wav", seconds = 30)
        val work = File(temporaryFolder.root, "vad-groups")

        val result = PcmWavSegmenter.splitAtSpeechRanges(
            input,
            work,
            longArrayOf(
                0L, 8L * 16_000L,
                10L * 16_000L, 18L * 16_000L,
                20L * 16_000L, 28L * 16_000L,
            ),
            targetSeconds = 14,
        )

        assertEquals(3, result.size)
        assertEquals(listOf(8L, 8L, 8L), result.map { (it.length() - 44L) / 32_000L })
    }

    @Test
    fun shortTrailingSpeechIsMergedInsteadOfDecodedAlone() {
        val input = wav("short-tail.wav", seconds = 24)
        val work = File(temporaryFolder.root, "vad-tail")

        val result = PcmWavSegmenter.splitAtSpeechRanges(
            input,
            work,
            longArrayOf(
                0L, 8L * 16_000L,
                10L * 16_000L, 18L * 16_000L,
                20L * 16_000L, 22L * 16_000L,
            ),
            targetSeconds = 9,
        )

        assertEquals(2, result.size)
        assertEquals(listOf(8_000L, 10_100L), result.map { (it.length() - 44L) / 32L })
    }

    @Test
    fun preferredPauseSplitsWithoutRemovingOrOverlappingAudio() {
        val input = wav("word-boundary.wav", seconds = 20)
        val work = File(temporaryFolder.root, "word-boundary-segments")

        val result = PcmWavSegmenter.splitAtSpeechRanges(
            input,
            work,
            longArrayOf(0L, 20L * 16_000L, 10L * 16_000L, 10L * 16_000L),
            targetSeconds = 14,
        )

        assertEquals(2, result.size)
        assertEquals(listOf(10_000L, 10_000L), result.map { (it.length() - 44L) / 32L })
        assertEquals(sampleAt(input, 10_000L * 16L), sampleAt(result[1], 0L))
    }

    private fun wav(name: String, seconds: Int): File = temporaryFolder.newFile(name).apply {
        val samples = seconds * 16_000
        outputStream().buffered().use { output ->
            output.write(wavHeader(samples * 2))
            repeat(samples) { index ->
                val value = (index % Short.MAX_VALUE).toShort().toInt()
                output.write(value and 0xff)
                output.write((value ushr 8) and 0xff)
            }
        }
    }

    private fun sampleAt(file: File, index: Long): Short = RandomAccessFile(file, "r").use { input ->
        input.seek(44L + index * 2L)
        val low = input.read()
        val high = input.read()
        ((low and 0xff) or ((high and 0xff) shl 8)).toShort()
    }

    private fun wavHeader(dataBytes: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".encodeToByteArray()).putInt(dataBytes + 36).put("WAVEfmt ".encodeToByteArray())
        .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(16_000).putInt(32_000)
        .putShort(2.toShort()).putShort(16.toShort())
        .put("data".encodeToByteArray()).putInt(dataBytes).array()
}

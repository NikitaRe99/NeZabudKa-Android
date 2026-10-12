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

    @Test fun benchmark_sample_is_bounded_without_changing_the_recording() {
        val input = wav("benchmark-source.wav", seconds = 12)
        val size = input.length()
        val output = File(temporaryFolder.root, "benchmark.wav")
        PcmWavSegmenter.copyFirstSeconds(input, output, 5)
        assertEquals(44L + 5L * 32_000L, output.length())
        assertEquals(size, input.length())
        assertEquals(sampleAt(input, 79_999), sampleAt(output, 79_999))
    }

    @Test fun short_benchmark_recording_is_copied_without_padding() {
        val input = wav("benchmark-short.wav", seconds = 1)
        val output = File(temporaryFolder.root, "benchmark-copy.wav")
        PcmWavSegmenter.copyFirstSeconds(input, output, 5)
        assertTrue(input.readBytes().contentEquals(output.readBytes()))
    }

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

    @Test fun bothWhisperModesPreserveAllQuietAndUndetectedSamplesInShortRecordings() {
        val input = wav("short-weak-words.wav", seconds = 12)
        val result = PcmWavSegmenter.splitForWhisper(
            input, File(temporaryFolder.root, "precise-short"),
            longArrayOf(16000, 48000, 80000, 144000), 19,
        )
        assertSame(input, result.single())
        assertEquals(sampleAt(input, 64000), sampleAt(result.single(), 64000))
        assertEquals(sampleAt(input, 191999), sampleAt(result.single(), 191999))
    }

    @Test fun bothWhisperModesCopyQuietPrepositionsAndFinalSyllablesInLongRecordings() {
        val input = wav("long-weak-preposition.wav", seconds = 24)
        val result = PcmWavSegmenter.splitForWhisper(
            input, File(temporaryFolder.root, "precise-long"),
            longArrayOf(32000, 80000, 88000, 144000), 19,
        )
        val output = result.single()
        // Start moves from 2.0 to 1.9 s and the final syllables extend to 9.3 s.
        assertEquals(7400L, (output.length() - 44L) / 32L)
        assertEquals(sampleAt(input, 30400), sampleAt(output, 0))
        // Original audio in the 500 ms gap survives sample-for-sample.
        assertEquals(sampleAt(input, 84000), sampleAt(output, 84000 - 30400))
        assertEquals(sampleAt(input, 148799), sampleAt(output, 118399))
    }

    @Test fun extraSpeechPaddingIsClampedToFileEdges() {
        val input = wav("bounded-padding.wav", seconds = 16)
        val result = PcmWavSegmenter.splitAtSpeechRanges(
            input, File(temporaryFolder.root, "bounded-precise"), longArrayOf(0, 256000), 19,
            speechPaddingBeforeMillis = 100, speechPaddingAfterMillis = 300,
            preserveShortGapsMillis = 1000,
        )
        assertSame(input, result.single())
    }

    @Test fun protectedWhisperAudioStillRemovesLongPauses() {
        val input = wav("long-pause.wav", seconds = 20)
        val result = PcmWavSegmenter.splitForWhisper(
            input, File(temporaryFolder.root, "protected-long-pause"),
            longArrayOf(0, 4L * 16_000L, 12L * 16_000L, 16L * 16_000L), 19,
        )
        val output = result.single()
        assertEquals(8_800L, (output.length() - 44L) / 32L)
        assertEquals(sampleAt(input, 4_299L * 16L), sampleAt(output, 4_299L * 16L))
        assertEquals(0, sampleAt(output, 4_350L * 16L).toInt())
        assertEquals(sampleAt(input, 11_900L * 16L), sampleAt(output, 4_400L * 16L))
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

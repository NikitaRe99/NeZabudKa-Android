package ru.nezabudka.app.speech

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PcmWavBlockProcessorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun blockProcessingMatchesPreviousAlgorithm() {
        val source = temporaryFolder.newFile("source.wav")
        val destination = File(temporaryFolder.root, "processed.wav")
        val samples = ShortArray(65_536) { index ->
            when (index % 8) {
                0 -> -8_000
                1 -> -4_000
                2 -> -1_000
                3 -> -250
                4 -> 250
                5 -> 1_000
                6 -> 4_000
                else -> 8_000
            }.toShort()
        }
        writeWav(source, samples)

        PcmWavBlockProcessor.process(source, destination)

        assertEquals(source.length(), destination.length())
        assertArrayEquals(processInMemory(samples), readSamples(destination))
    }

    @Test
    fun processingCrossesSeveralIoBlocksWithoutChangingLength() {
        val source = temporaryFolder.newFile("large.wav")
        val destination = File(temporaryFolder.root, "large_processed.wav")
        val samples = ShortArray(200_003) { index ->
            (2_000 + (index % 4_001) - 2_000).toShort()
        }
        writeWav(source, samples)

        PcmWavBlockProcessor.process(source, destination)

        assertEquals(44L + samples.size * 2L, destination.length())
        assertEquals(samples.size, readSamples(destination).size)
    }

    @Test
    fun malformedOddPcmIsRejected() {
        val source = temporaryFolder.newFile("broken.wav")
        writeWav(source, shortArrayOf(1, 2, 3))
        FileOutputStream(source, true).use { it.write(1) }

        assertThrows(IllegalArgumentException::class.java) {
            PcmWavBlockProcessor.process(source, File(temporaryFolder.root, "unused.wav"))
        }
    }

    @Test
    fun preprocessingPreservesOriginalRecordingDuration() {
        val source = temporaryFolder.newFile("padded.wav")
        val destination = File(temporaryFolder.root, "trimmed.wav")
        val samples = ShortArray(48_000)
        for (index in 16_000 until 32_000) {
            samples[index] = if (index % 2 == 0) 3_000 else -3_000
        }
        writeWav(source, samples)

        PcmWavBlockProcessor.process(source, destination)

        assertEquals(samples.size, readSamples(destination).size)
    }

    @Test
    fun uncertainAllSilentRecordingIsPreserved() {
        val source = temporaryFolder.newFile("silence.wav")
        val destination = File(temporaryFolder.root, "silence-processed.wav")
        writeWav(source, ShortArray(32_000))

        PcmWavBlockProcessor.process(source, destination)

        assertEquals(32_000, readSamples(destination).size)
    }

    @Test
    fun quietCommandBeforeLouderSpeechIsKeptByPreRoll() {
        val source = temporaryFolder.newFile("quiet-command.wav")
        val destination = File(temporaryFolder.root, "quiet-command-processed.wav")
        val samples = ShortArray(48_000)
        for (index in 4_000 until 12_000) samples[index] = if (index % 2 == 0) 150 else -150
        for (index in 16_000 until 32_000) samples[index] = if (index % 2 == 0) 3_000 else -3_000
        writeWav(source, samples)

        PcmWavBlockProcessor.process(source, destination)

        val processed = readSamples(destination)
        assertTrue(processed.take(6_000).any { abs(it.toInt()) >= 50 })
    }

    @Test
    fun veryQuietCleanSpeechReceivesAdditionalGainWithoutClipping() {
        val source = temporaryFolder.newFile("vivo-quiet.wav")
        val destination = File(temporaryFolder.root, "vivo-quiet-processed.wav")
        val samples = ShortArray(32_000) { index ->
            when {
                index < 4_000 || index >= 28_000 -> if (index % 2 == 0) 2 else -2
                index % 2 == 0 -> 110
                else -> -110
            }.toShort()
        }
        writeWav(source, samples)

        PcmWavBlockProcessor.process(source, destination)

        val processed = readSamples(destination)
        assertTrue(processed.maxOf { abs(it.toInt()) } >= 1_000)
        assertTrue(processed.none { abs(it.toInt()) >= 32_700 })
    }

    @Test
    fun quietDeviceSilenceRemainsSilentInsteadOfReceivingAmplifiedDc() {
        val source = temporaryFolder.newFile("quiet-device-dc.wav")
        val destination = File(temporaryFolder.root, "quiet-device-dc-processed.wav")
        val samples = ShortArray(32_000)
        for (index in 4_000 until 28_000) {
            samples[index] = if (index % 2 == 0) 110 else -90
        }
        writeWav(source, samples)

        PcmWavBlockProcessor.process(source, destination)

        val processed = readSamples(destination)
        assertTrue(processed.take(4_000).all { it == 0.toShort() })
        assertTrue(processed.takeLast(4_000).all { it == 0.toShort() })
        assertTrue(processed.maxOf { abs(it.toInt()) } <= 1_400)
    }

    @Test
    fun cleanSpeechFromDifferentMicrophoneLevelsIsNormalizedToComparableRms() {
        fun process(level: Int, name: String): Double {
            val source = temporaryFolder.newFile("$name.wav")
            val destination = File(temporaryFolder.root, "$name-processed.wav")
            val samples = ShortArray(32_000) { index ->
                when {
                    index < 4_000 || index >= 28_000 -> if (index % 2 == 0) 2 else -2
                    index % 2 == 0 -> level
                    else -> -level
                }.toShort()
            }
            writeWav(source, samples)
            PcmWavBlockProcessor.process(source, destination)
            val speech = readSamples(destination).sliceArray(4_000 until 28_000)
            return sqrt(speech.sumOf { it.toDouble() * it.toDouble() } / speech.size)
        }

        val quietRms = process(110, "quiet-device")
        val normalRms = process(1_000, "normal-device")

        assertTrue(quietRms >= normalRms * 0.9)
        assertTrue(quietRms <= normalRms * 1.1)
    }

    @Test
    fun quietPhraseEndingAfterLouderWordsIsNotTrimmed() {
        val source = temporaryFolder.newFile("quiet-ending.wav")
        val destination = File(temporaryFolder.root, "quiet-ending-processed.wav")
        val samples = ShortArray(48_000) { index ->
            when (index) {
                in 12_000 until 24_000 -> if (index % 2 == 0) 800 else -800
                in 24_000 until 44_000 -> if (index % 2 == 0) 90 else -90
                else -> if (index % 2 == 0) 2 else -2
            }.toShort()
        }
        writeWav(source, samples)

        PcmWavBlockProcessor.process(source, destination)

        assertEquals(48_000, readSamples(destination).size)
    }

    private fun processInMemory(source: ShortArray): ShortArray {
        val samples = source.copyOf()
        val median = samples.sorted()[samples.size / 2].toDouble()
        val dc = median.takeIf { abs(it) >= 32.0 } ?: 0.0
        val magnitudes = samples.map { abs(it - dc) }.filter { it > 0.0 }.sorted()
        val noiseFloor = magnitudes.getOrElse((magnitudes.size * 0.20).toInt()) { 0.0 }
        val peakMagnitude = magnitudes.lastOrNull() ?: 0.0
        val gate = (noiseFloor * 2.0).coerceAtLeast(32.0)
        fun gated(centered: Double, magnitude: Double): Double =
            if (magnitude < gate) centered * (0.70 + 0.30 * (magnitude / gate)) else centered
        var allSquares = 0.0
        var voicedSquares = 0.0
        var voicedCount = 0
        samples.forEach {
            val centered = it - dc
            val magnitude = abs(centered)
            val value = gated(centered, magnitude)
            allSquares += value * value
            if (magnitude >= gate) {
                voicedSquares += value * value
                voicedCount++
            }
        }
        val speechRms = sqrt(
            if (voicedCount > 0) voicedSquares / voicedCount else allSquares / samples.size,
        )
        val limit = Short.MAX_VALUE * 0.95
        val signalToNoise = speechRms / noiseFloor.coerceAtLeast(1.0)
        val adaptiveMaximumGain = when {
            signalToNoise >= 12.0 -> 12.0
            signalToNoise >= 6.0 -> 8.0
            signalToNoise >= 3.0 -> 4.0
            else -> 2.0
        }
        val maximumGain = min(adaptiveMaximumGain, limit / peakMagnitude.coerceAtLeast(1.0)).coerceAtLeast(0.75)
        val gain = (Short.MAX_VALUE * 0.04 / speechRms.coerceAtLeast(1.0)).coerceIn(0.75, maximumGain)
        return ShortArray(samples.size) { index ->
            val centered = samples[index] - dc
            val amplified = centered * gain
            val limited = if (abs(amplified) <= limit) amplified else {
                sign(amplified) * (
                    limit + (Short.MAX_VALUE - limit) *
                        tanh((abs(amplified) - limit) / (Short.MAX_VALUE - limit))
                    )
            }
            limited.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun writeWav(file: File, samples: ShortArray) {
        FileOutputStream(file).use { output ->
            output.write(wavHeader(samples.size * 2))
            val buffer = ByteArray(8_192)
            var size = 0
            samples.forEach { sample ->
                if (size == buffer.size) {
                    output.write(buffer)
                    size = 0
                }
                buffer[size++] = sample.toInt().toByte()
                buffer[size++] = (sample.toInt() shr 8).toByte()
            }
            if (size > 0) output.write(buffer, 0, size)
        }
    }

    private fun readSamples(file: File): ShortArray {
        val result = ShortArray(((file.length() - 44L) / 2L).toInt())
        DataInputStream(FileInputStream(file).buffered()).use { input ->
            input.readFully(ByteArray(44))
            result.indices.forEach { index ->
                val low = input.readUnsignedByte()
                val high = input.readUnsignedByte()
                result[index] = (low or (high shl 8)).toShort()
            }
        }
        return result
    }

    private fun wavHeader(dataSize: Int): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".encodeToByteArray())
            .putInt(dataSize + 36)
            .put("WAVEfmt ".encodeToByteArray())
            .putInt(16)
            .putShort(1.toShort())
            .putShort(1.toShort())
            .putInt(16_000)
            .putInt(32_000)
            .putShort(2.toShort())
            .putShort(16.toShort())
            .put("data".encodeToByteArray())
            .putInt(dataSize)
            .array()
}

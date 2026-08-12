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

/**
 * Processes canonical 16 kHz mono PCM16 WAV files in fixed-size blocks.
 *
 * Sequential fixed-memory passes keep the clip-wide DC, noise-floor and RMS
 * calculations without changing the recording duration. Memory use is bounded
 * by two 65536-bin histograms and two I/O blocks.
 */
internal object PcmWavBlockProcessor {
    private const val WAV_HEADER_BYTES = 44
    private const val IO_BLOCK_BYTES = 64 * 1024
    private const val MAGNITUDE_BUCKETS = 65_536

    fun process(inputWav: File, destination: File): File {
        val sampleCount = validateAndCountSamples(inputWav)
        destination.parentFile?.mkdirs()
        if (sampleCount == 0L) {
            FileOutputStream(destination).use { it.write(wavHeader(0)) }
            return destination
        }

        val sampleCounts = LongArray(MAGNITUDE_BUCKETS)
        forEachSample(inputWav, sampleCount) { sample ->
            sampleCounts[sample - Short.MIN_VALUE.toInt()]++
        }
        val measuredDc = signedPercentile(sampleCounts, sampleCount, 50).toDouble()
        // A few PCM units are normal quantization asymmetry, not a real DC
        // offset. Subtracting that value before a large gain turns digital
        // silence into an audible constant background.
        val dc = measuredDc.takeIf { abs(it) >= MIN_DC_CORRECTION } ?: 0.0

        val magnitudes = LongArray(MAGNITUDE_BUCKETS)
        var magnitudeCount = 0L
        var peakMagnitude = 0.0
        sampleCounts.forEachIndexed { index, count ->
            if (count == 0L) return@forEachIndexed
            val sample = index + Short.MIN_VALUE.toInt()
            val magnitude = abs(sample - dc)
            if (magnitude > peakMagnitude) peakMagnitude = magnitude
            if (magnitude > 0.0) {
                magnitudes[magnitude.toInt().coerceIn(0, magnitudes.lastIndex)] += count
                magnitudeCount += count
            }
        }
        val noiseFloor = percentileBucket(magnitudes, magnitudeCount, 20)
        val gate = (noiseFloor * NOISE_GATE_MULTIPLIER).coerceAtLeast(32.0)
        var allSquares = 0.0
        var voicedSquares = 0.0
        var voicedCount = 0L
        sampleCounts.forEachIndexed { index, count ->
            if (count == 0L) return@forEachIndexed
            val sample = index + Short.MIN_VALUE.toInt()
            val centered = sample - dc
            val magnitude = abs(centered)
            val gated = softGate(centered, magnitude, gate)
            val square = gated * gated
            allSquares += square * count
            if (magnitude >= gate) {
                voicedSquares += square * count
                voicedCount += count
            }
        }
        val speechRms = sqrt(
            if (voicedCount > 0L) voicedSquares / voicedCount.toDouble()
            else allSquares / sampleCount.toDouble(),
        )
        val targetRms = Short.MAX_VALUE * 0.04
        val limit = Short.MAX_VALUE * 0.95
        val signalToNoise = speechRms / noiseFloor.coerceAtLeast(1.0)
        val adaptiveMaximumGain = when {
            signalToNoise >= 12.0 -> 12.0
            signalToNoise >= 6.0 -> 8.0
            signalToNoise >= 3.0 -> 4.0
            else -> 2.0
        }
        val headroomMaximumGain = limit / peakMagnitude.coerceAtLeast(1.0)
        val maximumGain = min(adaptiveMaximumGain, headroomMaximumGain).coerceAtLeast(0.75)
        val gain = (targetRms / speechRms.coerceAtLeast(1.0)).coerceIn(0.75, maximumGain)

        FileOutputStream(destination).use { stream ->
            stream.write(wavHeader(sampleCount * 2L))
            val output = ByteArray(IO_BLOCK_BYTES)
            var outputSize = 0
            forEachSample(inputWav, sampleCount) { sample ->
                val centered = sample - dc
                // Keep low-energy consonants and prepositions intact. The gate
                // is used only to estimate a stable speech level, never to
                // attenuate samples passed to STT.
                val amplified = centered * gain
                val limited = if (abs(amplified) <= limit) {
                    amplified
                } else {
                    sign(amplified) * (
                        limit + (Short.MAX_VALUE - limit) *
                            tanh((abs(amplified) - limit) / (Short.MAX_VALUE - limit))
                        )
                }
                val value = limited.toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                if (outputSize == output.size) {
                    stream.write(output)
                    outputSize = 0
                }
                output[outputSize++] = value.toByte()
                output[outputSize++] = (value shr 8).toByte()
            }
            if (outputSize > 0) stream.write(output, 0, outputSize)
        }
        return destination
    }

    private fun validateAndCountSamples(file: File): Long {
        require(file.isFile && file.length() >= WAV_HEADER_BYTES) { "Ожидался WAV PCM файл" }
        val header = ByteArray(WAV_HEADER_BYTES)
        DataInputStream(FileInputStream(file).buffered()).use { it.readFully(header) }
        require(header.ascii(0, 4) == "RIFF" && header.ascii(8, 4) == "WAVE") {
            "Ожидался WAV PCM файл"
        }
        require(
            header.ascii(12, 4) == "fmt " &&
                header.le16(20) == 1 &&
                header.le16(22) == 1 &&
                header.le32(24) == 16_000 &&
                header.le16(34) == 16 &&
                header.ascii(36, 4) == "data",
        ) { "Поддерживается только WAV PCM16 mono 16 кГц" }
        val dataBytes = file.length() - WAV_HEADER_BYTES
        require(dataBytes % 2L == 0L) { "WAV-файл повреждён: нечётный размер PCM" }
        return dataBytes / 2L
    }

    private inline fun forEachSample(
        file: File,
        sampleCount: Long,
        startSample: Long = 0L,
        action: (Int) -> Unit,
    ) {
        DataInputStream(FileInputStream(file).buffered(IO_BLOCK_BYTES)).use { input ->
            var bytesToSkip = WAV_HEADER_BYTES.toLong() + startSample * 2L
            while (bytesToSkip > 0L) {
                val skipped = input.skip(bytesToSkip)
                check(skipped > 0L) { "WAV закончился раньше ожидаемого" }
                bytesToSkip -= skipped
            }
            val buffer = ByteArray(IO_BLOCK_BYTES)
            var remaining = sampleCount
            while (remaining > 0L) {
                val samplesInBlock = minOf(remaining, (buffer.size / 2).toLong()).toInt()
                val bytesInBlock = samplesInBlock * 2
                input.readFully(buffer, 0, bytesInBlock)
                var offset = 0
                repeat(samplesInBlock) {
                    action(
                        ((buffer[offset].toInt() and 0xff) or
                            (buffer[offset + 1].toInt() shl 8)).toShort().toInt(),
                    )
                    offset += 2
                }
                remaining -= samplesInBlock
            }
        }
    }

    private fun percentileBucket(
        histogram: LongArray,
        count: Long,
        percentile: Int,
        additionalZeroCount: Long = 0L,
    ): Double {
        if (count == 0L) return 0.0
        val rank = count * percentile / 100L
        var cumulative = additionalZeroCount
        if (cumulative > rank) return 0.0
        for (index in histogram.indices) {
            cumulative += histogram[index]
            if (cumulative > rank) return index.toDouble()
        }
        return histogram.lastIndex.toDouble()
    }

    private fun signedPercentile(histogram: LongArray, count: Long, percentile: Int): Int {
        val rank = count * percentile / 100L
        var cumulative = 0L
        for (index in histogram.indices) {
            cumulative += histogram[index]
            if (cumulative > rank) return index + Short.MIN_VALUE.toInt()
        }
        return 0
    }

    private fun softGate(centered: Double, magnitude: Double, gate: Double): Double =
        if (magnitude < gate) centered * (MIN_NOISE_GATE_GAIN + (1.0 - MIN_NOISE_GATE_GAIN) * (magnitude / gate))
        else centered

    private fun wavHeader(dataSize: Long): ByteArray =
        ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".encodeToByteArray())
            .putInt((dataSize + 36L).toInt())
            .put("WAVEfmt ".encodeToByteArray())
            .putInt(16)
            .putShort(1.toShort())
            .putShort(1.toShort())
            .putInt(16_000)
            .putInt(32_000)
            .putShort(2.toShort())
            .putShort(16.toShort())
            .put("data".encodeToByteArray())
            .putInt(dataSize.toInt())
            .array()

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        decodeToString(offset, offset + length)

    private fun ByteArray.le16(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.le32(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private const val NOISE_GATE_MULTIPLIER = 2.0
    private const val MIN_NOISE_GATE_GAIN = 0.70
    private const val MIN_DC_CORRECTION = 32.0
}

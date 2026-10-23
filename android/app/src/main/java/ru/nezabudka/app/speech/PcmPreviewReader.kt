package ru.nezabudka.app.speech

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Read/convert each completed PCM16 sample once. Snapshots own their array. */
internal class PcmPreviewReader(private val maxSamples: Int = 20 * 60 * 16_000) {
    private var samples = FloatArray(0)
    private var size = 0

    @Synchronized fun reset() {
        samples = FloatArray(0)
        size = 0
    }

    @Synchronized fun snapshot(file: File): FloatArray? = RandomAccessFile(file, "r").use { input ->
        val available = ((input.length() - 44).coerceAtLeast(0) / 2).coerceAtMost(maxSamples.toLong()).toInt()
        if (available < size) reset()
        if (available < 8_000) return@use null
        if (available > samples.size) samples = samples.copyOf(maxOf(available, (samples.size * 2).coerceAtMost(maxSamples)))
        input.seek(44L + size * 2L)
        val bytes = ByteArray(minOf(32_768, (available - size) * 2))
        while (size < available) {
            val count = minOf(bytes.size, (available - size) * 2)
            input.readFully(bytes, 0, count)
            val pcm = ByteBuffer.wrap(bytes, 0, count).order(ByteOrder.LITTLE_ENDIAN)
            repeat(count / 2) { samples[size++] = pcm.short / 32768f }
        }
        samples.copyOf(size)
    }
}

package ru.nezabudka.app.speech

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object PcmWavSegmenter {
    private const val HEADER_BYTES = 44
    private const val BYTES_PER_SECOND = 16_000 * 2
    private const val COPY_BUFFER_BYTES = 64 * 1024

    fun splitAtSpeechRanges(
        source: File,
        workDirectory: File,
        boundaries: LongArray,
        targetSeconds: Int,
        bridgeSilenceMillis: Int = 100,
    ): List<File> {
        require(targetSeconds > 0 && bridgeSilenceMillis >= 0)
        val totalSamples = validateAndPcmBytes(source) / 2L
        val ranges = splitAtPreferredPauses(
            normalizedSpeechRanges(boundaries, totalSamples),
            preferredPauseSamples(boundaries, totalSamples),
        )
        if (ranges.isEmpty()) return listOf(source)

        val targetSamples = targetSeconds.toLong() * 16_000L
        val bridgeSamples = bridgeSilenceMillis.toLong() * 16L
        val groups = mutableListOf<MutableList<SpeechRange>>()
        var current = mutableListOf<SpeechRange>()
        var currentSamples = 0L
        ranges.forEach { range ->
            val addedSamples = range.length + if (
                current.isNotEmpty() && range.startSample > current.last().endExclusiveSample
            ) bridgeSamples else 0L
            if (current.isNotEmpty() && currentSamples + addedSamples > targetSamples) {
                groups += current
                current = mutableListOf()
                currentSamples = 0L
            }
            if (current.isNotEmpty() && range.startSample > current.last().endExclusiveSample) {
                currentSamples += bridgeSamples
            }
            current += range
            currentSamples += range.length
        }
        if (current.isNotEmpty()) groups += current
        if (groups.size > 1 && groupSamples(groups.last(), bridgeSamples) < MIN_TRAILING_SEGMENT_SAMPLES) {
            groups[groups.lastIndex - 1].addAll(groups.removeAt(groups.lastIndex))
        }

        val speechSamples = ranges.sumOf(SpeechRange::length)
        if (groups.size == 1 && totalSamples - speechSamples < MIN_REMOVED_SILENCE_SAMPLES) {
            return listOf(source)
        }

        workDirectory.deleteRecursively()
        check(workDirectory.mkdirs()) { "Не удалось создать временный каталог сегментов Whisper" }
        val zeroBuffer = ByteArray(COPY_BUFFER_BYTES)
        val copyBuffer = ByteArray(COPY_BUFFER_BYTES)
        return RandomAccessFile(source, "r").use { input ->
            groups.mapIndexed { index, group ->
                val outputSamples = groupSamples(group, bridgeSamples)
                val segment = File(workDirectory, "vad-segment-${index.toString().padStart(3, '0')}.wav")
                FileOutputStream(segment).use { output ->
                    output.write(wavHeader(outputSamples * 2L))
                    group.forEachIndexed { rangeIndex, range ->
                        if (
                            rangeIndex > 0 &&
                            range.startSample > group[rangeIndex - 1].endExclusiveSample
                        ) {
                            writeZeros(output, bridgeSamples * 2L, zeroBuffer)
                        }
                        input.seek(HEADER_BYTES + range.startSample * 2L)
                        copyBytes(input, output, range.length * 2L, copyBuffer)
                    }
                }
                segment
            }
        }
    }

    fun splitIfLong(
        source: File,
        workDirectory: File,
        thresholdSeconds: Int = 60,
        segmentSeconds: Int = 30,
        overlapSeconds: Int = 1,
    ): List<File> {
        require(segmentSeconds > overlapSeconds && overlapSeconds >= 0)
        val pcmBytes = validateAndPcmBytes(source)
        if (pcmBytes <= thresholdSeconds.toLong() * BYTES_PER_SECOND) return listOf(source)

        workDirectory.deleteRecursively()
        check(workDirectory.mkdirs()) { "Не удалось создать временный каталог сегментов Whisper" }
        val segmentBytes = segmentSeconds.toLong() * BYTES_PER_SECOND
        val stepBytes = (segmentSeconds - overlapSeconds).toLong() * BYTES_PER_SECOND
        val result = mutableListOf<File>()
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        RandomAccessFile(source, "r").use { input ->
            var start = 0L
            var index = 0
            while (start < pcmBytes) {
                val bytes = minOf(segmentBytes, pcmBytes - start)
                val segment = File(workDirectory, "segment-${index.toString().padStart(3, '0')}.wav")
                FileOutputStream(segment).use { output ->
                    output.write(wavHeader(bytes))
                    input.seek(HEADER_BYTES + start)
                    var remaining = bytes
                    while (remaining > 0L) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        check(read > 0) { "WAV закончился раньше ожидаемого" }
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                result += segment
                index++
                start += stepBytes
            }
        }
        return result
    }

    fun cleanup(segments: List<File>, original: File) {
        if (segments.size == 1 && segments.firstOrNull() == original) return
        segments.firstOrNull()?.parentFile?.deleteRecursively()
    }

    private fun normalizedSpeechRanges(boundaries: LongArray, totalSamples: Long): List<SpeechRange> {
        if (boundaries.size % 2 != 0) return emptyList()
        val sorted = boundaries.asSequence().chunked(2)
            .mapNotNull { values ->
                val start = values[0].coerceIn(0L, totalSamples)
                val end = values[1].coerceIn(start, totalSamples)
                if (end > start) SpeechRange(start, end) else null
            }
            .sortedBy(SpeechRange::startSample)
            .toList()
        val merged = mutableListOf<SpeechRange>()
        sorted.forEach { range ->
            val previous = merged.lastOrNull()
            if (previous != null && range.startSample <= previous.endExclusiveSample) {
                merged[merged.lastIndex] = previous.copy(
                    endExclusiveSample = maxOf(previous.endExclusiveSample, range.endExclusiveSample),
                )
            } else {
                merged += range
            }
        }
        return merged
    }

    private fun groupSamples(group: List<SpeechRange>, bridgeSamples: Long): Long =
        group.sumOf(SpeechRange::length) + group.zipWithNext().sumOf { (previous, next) ->
            if (next.startSample > previous.endExclusiveSample) bridgeSamples else 0L
        }

    private fun preferredPauseSamples(boundaries: LongArray, totalSamples: Long): List<Long> =
        boundaries.asSequence().chunked(2).mapNotNull { values ->
            values.takeIf { it.size == 2 && it[0] == it[1] }
                ?.first()
                ?.coerceIn(0L, totalSamples)
        }.distinct().sorted().toList()

    private fun splitAtPreferredPauses(
        ranges: List<SpeechRange>,
        pauses: List<Long>,
    ): List<SpeechRange> = buildList {
        ranges.forEach { range ->
            var start = range.startSample
            pauses.asSequence()
                .filter { it > start && it < range.endExclusiveSample }
                .forEach { pause ->
                    add(SpeechRange(start, pause))
                    start = pause
                }
            add(SpeechRange(start, range.endExclusiveSample))
        }
    }

    private fun copyBytes(
        input: RandomAccessFile,
        output: FileOutputStream,
        byteCount: Long,
        buffer: ByteArray,
    ) {
        var remaining = byteCount
        while (remaining > 0L) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(read > 0) { "WAV закончился раньше ожидаемого" }
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun writeZeros(output: FileOutputStream, byteCount: Long, buffer: ByteArray) {
        var remaining = byteCount
        while (remaining > 0L) {
            val count = minOf(buffer.size.toLong(), remaining).toInt()
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun validateAndPcmBytes(file: File): Long {
        require(file.isFile && file.length() >= HEADER_BYTES) { "Ожидался WAV PCM файл" }
        val header = ByteArray(HEADER_BYTES)
        DataInputStream(FileInputStream(file).buffered()).use { it.readFully(header) }
        require(header.ascii(0, 4) == "RIFF" && header.ascii(8, 4) == "WAVE") { "Ожидался WAV PCM файл" }
        require(
            header.ascii(12, 4) == "fmt " && header.le16(20) == 1 && header.le16(22) == 1 &&
                header.le32(24) == 16_000 && header.le16(34) == 16 && header.ascii(36, 4) == "data",
        ) { "Поддерживается только WAV PCM16 mono 16 кГц" }
        return (file.length() - HEADER_BYTES).also { require(it % 2L == 0L) { "WAV-файл повреждён" } }
    }

    private fun wavHeader(dataBytes: Long): ByteArray = ByteBuffer.allocate(HEADER_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".encodeToByteArray()).putInt((dataBytes + 36L).toInt())
        .put("WAVEfmt ".encodeToByteArray()).putInt(16).putShort(1.toShort()).putShort(1.toShort())
        .putInt(16_000).putInt(32_000).putShort(2.toShort()).putShort(16.toShort())
        .put("data".encodeToByteArray()).putInt(dataBytes.toInt()).array()

    private fun ByteArray.ascii(offset: Int, length: Int) = copyOfRange(offset, offset + length).decodeToString()
    private fun ByteArray.le16(offset: Int) =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    private fun ByteArray.le32(offset: Int) =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or ((this[offset + 3].toInt() and 0xff) shl 24)

    private data class SpeechRange(val startSample: Long, val endExclusiveSample: Long) {
        val length: Long get() = endExclusiveSample - startSample
    }

    private const val MIN_REMOVED_SILENCE_SAMPLES = 8_000L
    private const val MIN_TRAILING_SEGMENT_SAMPLES = 4L * 16_000L
}

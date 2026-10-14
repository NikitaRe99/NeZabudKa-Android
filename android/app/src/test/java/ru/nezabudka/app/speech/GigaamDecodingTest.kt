package ru.nezabudka.app.speech

import org.junit.Assert.*
import org.junit.Test

class GigaamDecodingTest {
    @Test fun ctcCollapsesFramesButPreservesRepetitionsSeparatedByBlank() {
        val vocab = listOf("а", "б", " ")
        assertEquals("ааб", decodeGigaamCtc(longArrayOf(0, 0, 3, 0, 3, 1, 1), 7, vocab))
        assertEquals("", decodeGigaamCtc(longArrayOf(3, 3, 3), 3, vocab))
        assertEquals("а", decodeGigaamCtc(longArrayOf(0, 3, 1), 1, vocab))
    }
    @Test fun longRecordingIsPartitionedAtPauseWithoutRemovingOrRepeatingSamples() {
        val audio = FloatArray(45 * 16000) { 0.1f }
        for (i in 19 * 16000 until 20 * 16000) audio[i] = 0f
        val ranges = gigaamChunkRanges(audio)
        assertEquals(0, ranges.first().first)
        assertEquals(audio.lastIndex, ranges.last().last)
        assertEquals(audio.size, ranges.sumOf { it.last - it.first + 1 })
        ranges.zipWithNext().forEach { (left, right) -> assertEquals(left.last + 1, right.first) }
        assertTrue(ranges.all { it.last - it.first + 1 <= 25 * 16000 })
        assertTrue(ranges.first().last in 19 * 16000 until 20 * 16000)
    }
    @Test fun shortRecordingsRemainWholeIncludingSilenceAndQuietEndings() {
        assertEquals(listOf(0..15999), gigaamChunkRanges(FloatArray(16000)))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidLabels() {
        decodeGigaamCtc(longArrayOf(9), 1, listOf("а"))
    }
}

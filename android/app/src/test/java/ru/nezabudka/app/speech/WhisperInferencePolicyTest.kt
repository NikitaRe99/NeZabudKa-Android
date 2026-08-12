package ru.nezabudka.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperInferencePolicyTest {
    @Test
    fun shortCommandUsesSmallBoundedContext() {
        val policy = whisperInferencePolicy(wavBytes(seconds = 3))

        assertEquals(208, policy.audioContext)
        assertEquals(96, policy.maxTokens)
        assertTrue(policy.singleSegment)
    }

    @Test
    fun contextGrowsWithRecordingWithoutCroppingIt() {
        assertEquals(208, whisperInferencePolicy(wavBytes(seconds = 4)).audioContext)
        assertEquals(288, whisperInferencePolicy(wavBytes(seconds = 5)).audioContext)
        assertEquals(384, whisperInferencePolicy(wavBytes(seconds = 7)).audioContext)
        assertEquals(544, whisperInferencePolicy(wavBytes(seconds = 10)).audioContext)
        assertEquals(800, whisperInferencePolicy(wavBytes(seconds = 15)).audioContext)
        assertEquals(1024, whisperInferencePolicy(wavBytes(seconds = 20)).audioContext)
    }

    @Test
    fun `Tiny uses a stable minimum context without changing longer recordings`() {
        assertEquals(512, whisperInferencePolicy(wavBytes(seconds = 1), tinyModel = true).audioContext)
        assertEquals(512, whisperInferencePolicy(wavBytes(seconds = 5), tinyModel = true).audioContext)
        assertEquals(512, whisperInferencePolicy(wavBytes(seconds = 7), tinyModel = true).audioContext)
        assertEquals(544, whisperInferencePolicy(wavBytes(seconds = 10), tinyModel = true).audioContext)
        assertEquals(0, whisperInferencePolicy(wavBytes(seconds = 30), tinyModel = true).audioContext)
    }

    @Test
    fun `Samsung length commands avoid oversized encoder contexts`() {
        assertEquals(320, whisperInferencePolicy(wavBytes(seconds = 5.82)).audioContext)
        assertEquals(352, whisperInferencePolicy(wavBytes(seconds = 6.50)).audioContext)
        assertEquals(416, whisperInferencePolicy(wavBytes(seconds = 7.62)).audioContext)
    }

    @Test
    fun longSegmentKeepsNativeContextAndUnboundedDecoder() {
        val policy = whisperInferencePolicy(wavBytes(seconds = 30))

        assertEquals(0, policy.audioContext)
        assertEquals(0, policy.maxTokens)
        assertFalse(policy.singleSegment)
    }

    @Test
    fun lowConfidenceNearContextLimitRetriesWithNextWindow() {
        assertEquals(256, whisperRetryAudioContext(wavBytes(seconds = 4), 208, 0.65f))
        assertEquals(320, whisperRetryAudioContext(wavBytes(seconds = 5), 256, 0.70f))
    }

    @Test
    fun reliableOrShortResultDoesNotPayForRetry() {
        assertEquals(null, whisperRetryAudioContext(wavBytes(seconds = 4), 208, 0.95f))
        assertEquals(null, whisperRetryAudioContext(wavBytes(seconds = 1), 208, 0.40f))
        assertEquals(null, whisperRetryAudioContext(wavBytes(seconds = 6.6), 384, 0.40f))
        assertEquals(null, whisperRetryAudioContext(wavBytes(seconds = 30), 0, 0.40f))
    }

    @Test
    fun vadIsSkippedForShortAndMediumPhrases() {
        assertFalse(shouldAnalyzeWhisperVad(wavBytes(seconds = 4)))
        assertFalse(shouldAnalyzeWhisperVad(wavBytes(seconds = 7)))
        assertFalse(shouldAnalyzeWhisperVad(wavBytes(seconds = 20)))
        assertTrue(shouldAnalyzeWhisperVad(wavBytes(seconds = 21)))
    }

    @Test
    fun modelIsBrieflyRetainedOnlyWhenTheDeviceHasMemoryHeadroom() {
        val gigabyte = 1024L * 1024L * 1024L
        assertEquals(20_000L, automaticWhisperIdleRetentionMillis(false, 256, gigabyte))
        assertEquals(0L, automaticWhisperIdleRetentionMillis(true, 512, 2L * gigabyte))
        assertEquals(0L, automaticWhisperIdleRetentionMillis(false, 192, 2L * gigabyte))
        assertEquals(0L, automaticWhisperIdleRetentionMillis(false, 512, gigabyte / 2L))
    }

    private fun wavBytes(seconds: Int): Long = 44L + seconds * 32_000L
    private fun wavBytes(seconds: Double): Long = 44L + (seconds * 32_000.0).toLong()
}

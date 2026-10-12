package ru.nezabudka.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperInferencePolicyTest {
    @Test fun `calendar contexts avoid the looping short window while regular commands stay fast`() {
        val short = whisperInferencePolicy(wavBytes(seconds = 3))
        assertEquals(208, short.audioContext)
        assertEquals(512, whisperPolicyForRequest(short, WhisperRequestContext.DATE_REPLY).audioContext)
        assertEquals(512, whisperPolicyForRequest(short, WhisperRequestContext.REMINDER_COMMAND).audioContext)
        assertEquals(208, whisperPolicyForRequest(short, WhisperRequestContext.COMMAND).audioContext)
        assertEquals(208, whisperPolicyForRequest(short, WhisperRequestContext.NOTE_COMMAND).audioContext)
        assertEquals(208, whisperPolicyForRequest(short, WhisperRequestContext.DICTATION).audioContext)
    }
    @Test
    fun shortCommandUsesSmallBoundedContext() {
        val policy = whisperInferencePolicy(wavBytes(seconds = 3))

        assertEquals(208, policy.audioContext)
        assertEquals(0, policy.maxTokens)
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

    @Test fun repeatedWordsRetryShortGreedyWindowsEvenWhenConfidenceWouldSkipTheNormalRetry() {
        assertEquals(null, whisperRetryAudioContext(wavBytes(0.9), 208, 0.99f))
        assertEquals(512, whisperRepetitionRetryAudioContext(208, "привет привет"))
        assertEquals(512, whisperRepetitionRetryAudioContext(384, "Напомни купить хлеб хлеб"))
        assertEquals(null, whisperRepetitionRetryAudioContext(208, "Напомни купить хлеб"))
        assertEquals(null, whisperRepetitionRetryAudioContext(512, "привет привет"))
        assertEquals(null, whisperRepetitionRetryAudioContext(0, "привет привет"))
    }

    @Test
    fun vadChecksShortAndMediumPhrases() {
        assertTrue(shouldAnalyzeWhisperVad(wavBytes(seconds = 4)))
        assertTrue(shouldAnalyzeWhisperVad(wavBytes(seconds = 7)))
        assertTrue(shouldAnalyzeWhisperVad(wavBytes(seconds = 20)))
        assertTrue(shouldAnalyzeWhisperVad(wavBytes(seconds = 21)))
    }

    @Test
    fun accurateModeUsesStableWindowAndBeamSearchForAllModels() {
        for (tiny in listOf(false, true)) for (seconds in listOf(1, 5, 30)) {
            val policy = whisperInferencePolicy(wavBytes(seconds), tiny, accurateMode = true)
            assertEquals(if (seconds == 30) 0 else maxOf(768, whisperInferencePolicy(wavBytes(seconds), tiny).audioContext), policy.audioContext)
            assertEquals(3, policy.beamSize)
            assertEquals(0, policy.maxTokens)
            assertEquals(seconds <= 12, policy.singleSegment)
            assertTrue(policy.timeLimitMs >= 20_000)
        }
    }

    @Test
    fun onlyUncertainAccurateResultsUseFullWindowRetry() {
        val policy = whisperInferencePolicy(wavBytes(3), accurateMode = true)
        assertEquals(null, whisperAccurateRetryPolicy(policy, 0.95f, false))
        val retry = whisperAccurateRetryPolicy(policy, 0.70f, false)!!
        assertEquals(0, retry.audioContext)
        assertEquals(3, retry.beamSize)
        assertEquals(policy.timeLimitMs, retry.timeLimitMs)
        assertEquals(retry, whisperAccurateRetryPolicy(policy, null, false))
        assertEquals(retry, whisperAccurateRetryPolicy(policy, 0.95f, true))
        assertEquals(null, whisperAccurateRetryPolicy(retry, 0.70f, false))
        assertEquals(null, whisperAccurateRetryPolicy(whisperInferencePolicy(wavBytes(3)), 0.70f, false))
    }

    @Test
    fun accurateAndFastTimeBudgetsAreBounded() {
        assertEquals(20_000, whisperAccurateTimeLimitMs(wavBytes(1)))
        assertEquals(60_000, whisperAccurateTimeLimitMs(wavBytes(10)))
        assertEquals(120_000, whisperAccurateTimeLimitMs(wavBytes(30)))
        assertEquals(8_000, whisperInferencePolicy(wavBytes(3)).timeLimitMs)
        assertEquals(6_000, whisperFastTimeLimitMs(wavBytes(1)))
        assertEquals(25_000, whisperFastTimeLimitMs(wavBytes(30)))
    }

    @Test
    fun pauseMarkersDoNotCountAsSpeech() {
        assertFalse(hasWhisperSpeechRanges(longArrayOf()))
        assertFalse(hasWhisperSpeechRanges(longArrayOf(0, 0, 4000, 4000)))
        assertFalse(hasWhisperSpeechRanges(longArrayOf(300, 100)))
        assertFalse(hasWhisperSpeechRanges(longArrayOf(0, 100, 200)))
        assertTrue(hasWhisperSpeechRanges(longArrayOf(0, 0, 300, 900)))
        assertFalse(shouldAnalyzeWhisperVad(44L))
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

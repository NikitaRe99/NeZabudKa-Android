package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhisperThreadBenchmarkRulesTest {
    @Test fun maximum_threads_are_not_assumed_to_be_fastest() {
        assertEquals(4, bestWhisperThreadCount(listOf(
            WhisperThreadTiming(1, 3_000), WhisperThreadTiming(4, 900), WhisperThreadTiming(8, 1_100),
        )))
    }
    @Test fun equal_times_prefer_fewer_threads() {
        assertEquals(2, bestWhisperThreadCount(listOf(WhisperThreadTiming(8, 900), WhisperThreadTiming(2, 900))))
    }
    @Test fun incomplete_or_invalid_results_are_not_applied() {
        assertNull(bestWhisperThreadCount(emptyList()))
        assertNull(bestWhisperThreadCount(listOf(WhisperThreadTiming(0, 100), WhisperThreadTiming(4, 0))))
    }
}

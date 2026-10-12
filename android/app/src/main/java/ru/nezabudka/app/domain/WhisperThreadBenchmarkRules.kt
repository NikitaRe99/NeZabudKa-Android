package ru.nezabudka.app.domain

data class WhisperThreadTiming(val threads: Int, val durationMs: Long)

fun bestWhisperThreadCount(timings: List<WhisperThreadTiming>): Int? = timings
    .filter { it.threads > 0 && it.durationMs > 0 }
    .minWithOrNull(compareBy<WhisperThreadTiming> { it.durationMs }.thenBy { it.threads })?.threads

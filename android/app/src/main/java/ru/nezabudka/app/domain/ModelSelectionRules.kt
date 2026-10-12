package ru.nezabudka.app.domain

fun restoredModelSelection(installed: List<String>, persisted: String?): String? =
    persisted?.takeIf(installed::contains) ?: installed.singleOrNull()

private const val MIB = 1024L * 1024L

fun estimatedWhisperPeakBytes(modelBytes: Long, pcmWavBytes: Long, modelWeightsRetained: Boolean = false): Long =
    // Reusing weights still needs a fresh inference workspace for every recording.
    modelBytes.coerceAtLeast(0L) * (if (modelWeightsRetained) 3L else 7L) / 4L +
        pcmWavBytes.coerceAtLeast(0L) * 4L + 160L * MIB

fun hasNativeMemoryHeadroom(estimatedPeakBytes: Long, availableBytes: Long, totalBytes: Long): Boolean =
    estimatedPeakBytes > 0L &&
        estimatedPeakBytes <= availableBytes.coerceAtLeast(0L) * 2L / 3L &&
        estimatedPeakBytes <= totalBytes.coerceAtLeast(0L) * 2L / 5L

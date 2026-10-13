package ru.nezabudka.app.speech

/** Decoder diagnostics are warning signals, not calibrated correctness percentages. */
internal data class WhisperDecodeQuality(
    val meanProbability: Float?, val meanLogProbability: Double,
    val minimumProbability: Double, val weakFraction: Double, val tokens: Int,
) {
    val hasWeakWord get() = tokens > 0 && minimumProbability < 0.25
    val needsRetry get() = tokens == 0 || meanLogProbability < -1.0 || weakFraction >= 0.2
    companion object {
        fun from(values: DoubleArray) = WhisperDecodeQuality(
            values.getOrNull(1)?.takeIf { it > 0 && it.isFinite() }?.toFloat(),
            values.getOrElse(2) { -10.0 }, values.getOrElse(3) { 0.0 },
            values.getOrElse(4) { 1.0 }, values.getOrElse(5) { 0.0 }.toInt(),
        )
    }
}

internal fun whisperTextsAgree(first: String, second: String): Boolean {
    fun words(text: String) = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase().replace('ё', 'е')).map { it.value }.toList()
    return words(first) == words(second)
}

internal fun whisperFastTimeLimitMs(wavBytes: Long): Int =
    (((wavBytes - 44L).coerceAtLeast(0L) / 32L) * 2L + 2_000L).coerceIn(6_000L, 25_000L).toInt()

internal fun whisperDeviceTimeLimitMs(wavBytes: Long, accurateMode: Boolean, limitedResources: Boolean): Int {
    val normal = if (accurateMode) whisperAccurateTimeLimitMs(wavBytes) else whisperFastTimeLimitMs(wavBytes)
    return if (limitedResources) (normal.toLong() * 4L).coerceAtMost(240_000L).toInt() else normal
}

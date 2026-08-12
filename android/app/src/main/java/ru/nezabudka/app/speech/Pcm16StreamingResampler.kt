package ru.nezabudka.app.speech

/** Stateful mono PCM16 resampler used only when a device cannot record at 16 kHz directly. */
internal class Pcm16StreamingResampler(
    private val inputSampleRate: Int,
    private val outputSampleRate: Int,
) {
    private var sourceIndex = 0L
    private var nextOutputPosition = 0L
    private var previousSample = 0
    private var hasPreviousSample = false

    init {
        require(inputSampleRate > 0 && outputSampleRate > 0)
    }

    fun process(input: ByteArray, length: Int = input.size): ByteArray {
        require(length in 0..input.size && length % 2 == 0) { "Ожидался mono PCM16" }
        if (length == 0) return ByteArray(0)
        if (inputSampleRate == outputSampleRate) return input.copyOf(length)

        val inputSamples = length / 2
        val estimatedSamples = (inputSamples.toLong() * outputSampleRate / inputSampleRate + 4L)
            .coerceAtMost(Int.MAX_VALUE.toLong() / 2L)
            .toInt()
        var output = ByteArray(estimatedSamples * 2)
        var outputSize = 0

        fun append(sample: Int) {
            if (outputSize + 2 > output.size) output = output.copyOf((output.size * 2).coerceAtLeast(2))
            val value = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[outputSize++] = value.toByte()
            output[outputSize++] = (value shr 8).toByte()
        }

        var offset = 0
        repeat(inputSamples) {
            val currentSample = (
                (input[offset].toInt() and 0xff) or
                    (input[offset + 1].toInt() shl 8)
                ).toShort().toInt()
            val currentIndex = sourceIndex++
            if (!hasPreviousSample) {
                previousSample = currentSample
                hasPreviousSample = true
                append(currentSample)
                nextOutputPosition = inputSampleRate.toLong()
            } else {
                val previousPosition = (currentIndex - 1L) * outputSampleRate
                val currentPosition = currentIndex * outputSampleRate
                while (nextOutputPosition <= currentPosition) {
                    val fractionNumerator = nextOutputPosition - previousPosition
                    val interpolated = previousSample.toLong() +
                        (currentSample - previousSample).toLong() * fractionNumerator / outputSampleRate
                    append(interpolated.toInt())
                    nextOutputPosition += inputSampleRate
                }
                previousSample = currentSample
            }
            offset += 2
        }
        return output.copyOf(outputSize)
    }
}

package ru.nezabudka.app.speech

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Quantized Base can be smaller than Tiny; identify architecture from the GGML header. */
internal fun isTinyWhisperModel(file: File): Boolean = runCatching {
    val header = ByteArray(24)
    file.inputStream().use { input ->
        var offset = 0
        while (offset < header.size) {
            val count = input.read(header, offset, header.size - offset)
            if (count < 0) return@runCatching false
            offset += count
        }
    }
    val values = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    values.getInt(0) == 0x67676d6c && values.getInt(20) == 4
}.getOrDefault(false)

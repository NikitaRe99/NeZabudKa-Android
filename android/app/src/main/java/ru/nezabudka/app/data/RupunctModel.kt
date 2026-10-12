package ru.nezabudka.app.data

import java.io.File
import java.security.MessageDigest

object RupunctModel {
    const val FILE_NAME = "rupunct_small_int8.onnx"
    const val BYTES = 29_259_673L
    const val SHA256 = "b105da023474d98aa13ba18953ae67b04b17bd0595034bc06030c17536893933"
    const val URL = "https://huggingface.co/ekhodzitsky/rupunct-small-onnx/resolve/main/rupunct_small_int8.onnx?download=true"

    fun validate(file: File) {
        require(file.length() == BYTES) { "Неверный размер модели RUPunct Small" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(hash == SHA256) { "Контрольная сумма RUPunct Small не совпала. Повторите скачивание." }
    }
}

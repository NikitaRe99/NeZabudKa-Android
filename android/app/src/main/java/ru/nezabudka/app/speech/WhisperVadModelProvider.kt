package ru.nezabudka.app.speech

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** Extracts the bundled VAD model only when local Whisper is used. */
class WhisperVadModelProvider(private val context: Context) {
    @Volatile private var cached: File? = null

    fun modelFile(): File = cached?.takeIf(::isValidWhisperVadModel) ?: synchronized(this) {
        cached?.takeIf(::isValidWhisperVadModel) ?: extract().also { cached = it }
    }

    private fun extract(): File {
        val directory = File(context.cacheDir, "native-models/vad").apply { mkdirs() }
        val destination = File(directory, MODEL_FILE_NAME)
        if (isValidWhisperVadModel(destination)) return destination

        val temporary = File(directory, "$MODEL_FILE_NAME.part")
        temporary.delete()
        context.assets.open(ASSET_PATH).use { input ->
            FileOutputStream(temporary).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        check(isValidWhisperVadModel(temporary)) { "Встроенная модель Silero VAD повреждена" }
        destination.delete()
        check(temporary.renameTo(destination)) { "Не удалось подготовить модель Silero VAD" }
        return destination
    }

    private companion object {
        const val ASSET_PATH = "models/ggml-silero-v6.2.0.bin"
        const val MODEL_FILE_NAME = "ggml-silero-v6.2.0.bin"
    }
}

internal fun isValidWhisperVadModel(file: File): Boolean =
    file.isFile && file.length() == WHISPER_VAD_MODEL_BYTES

internal const val WHISPER_VAD_MODEL_BYTES = 885_098L

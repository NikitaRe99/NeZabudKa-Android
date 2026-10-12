package ru.nezabudka.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import ru.nezabudka.app.domain.restoredModelSelection

data class ModelSelectionSnapshot(
    val installed: List<String>,
    val selectedName: String?,
)

/** Shared SAF catalog mechanics; model-format validation remains owned by each runtime. */
class AndroidModelLibrary(
    private val context: Context,
    private val storage: UserModelsStorage,
    private val kind: String,
    private val extension: String,
    preferencesName: String,
    private val defaultImportName: String,
    private val validate: (File) -> Unit,
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun snapshot(): ModelSelectionSnapshot {
        val installed = runCatching { storage.list(kind, extension) }.getOrDefault(emptyList())
        val persisted = preferences.getString(KEY_SELECTED, null)
        val selected = restoredModelSelection(installed, persisted)
        return ModelSelectionSnapshot(installed, selected)
    }

    fun selectedFile(
        missingMessage: String,
        missingException: () -> IllegalStateException = { IllegalStateException(missingMessage) },
    ): File {
        val current = snapshot()
        val selected = current.selectedName ?: throw missingException()
        return storage.materialize(kind, selected)
    }

    fun select(fileName: String): Boolean {
        require(isSafeFileName(fileName) && storage.exists(kind, fileName)) { "Модель $fileName не найдена" }
        if (snapshot().selectedName == fileName) return false
        preferences.edit().putString(KEY_SELECTED, fileName).apply()
        return true
    }

    fun import(uri: Uri): String {
        val displayName = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: defaultImportName
        val rawName = displayName.substringAfterLast('/')
        val fileName = if (rawName.endsWith(".$extension", true)) rawName else "$rawName.$extension"
        require(isSafeFileName(fileName)) { "Недопустимое имя файла модели" }
        val partial = File(context.cacheDir, "$fileName.import.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                partial.outputStream().buffered().use(input::copyTo)
            } ?: throw IOException("Не удалось открыть выбранный файл")
            validate(partial)
            storage.store(kind, fileName, partial)
            preferences.edit().putString(KEY_SELECTED, fileName).apply()
            return fileName
        } finally {
            partial.delete()
        }
    }

    private fun isSafeFileName(value: String): Boolean =
        value.isNotBlank() && value == File(value).name && !value.contains("..")

    private companion object {
        const val KEY_SELECTED = "selected_model"
    }
}

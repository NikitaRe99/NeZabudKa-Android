package ru.nezabudka.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ru.nezabudka.app.ports.ModelCacheStats
import ru.nezabudka.app.ports.UserModelsStorageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Persistent user-owned model library. Native engines receive disposable cached copies. */
class UserModelsStorage(private val context: Context) {
    private val preferences = context.getSharedPreferences("user_models_storage", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<UserModelsStorageState> = _state

    /** Validates the persisted SAF destination from a background dispatcher. */
    fun refresh() {
        _state.value = readState().copy(cache = _state.value.cache)
    }

    fun selectRoot(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        preferences.edit().putString(KEY_ROOT, uri.toString()).commit()
        ensureFolder("whisper")
        ensureFolder("nlu")
        ensureFolder("punctuation")
        migrateLegacy("whisper", "bin")
        migrateLegacy("nlu", "onnx")
        migrateLegacy("punctuation", "onnx")
        _state.value = readState().copy(cache = _state.value.cache)
    }

    fun list(kind: String, extension: String): List<String> = ensureFolder(kind).listFiles()
        .filter { it.isFile && it.name?.substringAfterLast('.', "")?.equals(extension, true) == true }
        .mapNotNull(DocumentFile::getName)
        .sorted()

    fun exists(kind: String, fileName: String): Boolean = find(kind, fileName)?.isFile == true

    fun store(kind: String, fileName: String, source: File) {
        requireSafeName(fileName)
        val folder = ensureFolder(kind)
        val old = folder.findFile(fileName)
        val temporaryName = "$fileName.part"
        folder.findFile(temporaryName)?.delete()
        val temporary = folder.createFile("application/octet-stream", temporaryName)
            ?: throw IOException("Не удалось создать файл в выбранном каталоге")
        try {
            context.contentResolver.openOutputStream(temporary.uri, "w")?.use { output ->
                source.inputStream().buffered().use { it.copyTo(output) }
            } ?: throw IOException("Нет доступа на запись в выбранный каталог")
            old?.delete()
            if (!temporary.renameTo(fileName)) {
                val destination = folder.createFile("application/octet-stream", fileName)
                    ?: throw IOException("Не удалось сохранить модель")
                context.contentResolver.openInputStream(temporary.uri)?.use { input ->
                    context.contentResolver.openOutputStream(destination.uri, "w")?.use(input::copyTo)
                        ?: throw IOException("Нет доступа на запись")
                } ?: throw IOException("Нет доступа к временному файлу")
                temporary.delete()
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    fun materialize(kind: String, fileName: String): File {
        if (!preferences.contains(KEY_ROOT)) {
            val direct = File(defaultRootFolder(), "models/$kind/$fileName")
            if (direct.isFile) return direct
        }
        val document = find(kind, fileName) ?: throw IllegalStateException("Модель $fileName не найдена")
        val cacheFolder = File(context.cacheDir, "native-models/$kind").apply { mkdirs() }
        val cached = File(cacheFolder, fileName)
        val sourceLength = document.length()
        // Some SAF providers report an unknown length as 0. Do not copy a multi-GB
        // model before every request when a valid cached copy already exists.
        if (!cached.isFile || (sourceLength > 0 && cached.length() != sourceLength)) {
            val partial = File(cacheFolder, "$fileName.part")
            try {
                context.contentResolver.openInputStream(document.uri)?.use { input ->
                    partial.outputStream().buffered().use(input::copyTo)
                } ?: throw IOException("Не удалось прочитать модель из каталога пользователя")
                if (cached.exists()) cached.delete()
                if (!partial.renameTo(cached)) throw IOException("Не удалось подготовить модель для native-движка")
            } finally { partial.delete() }
        }
        cacheFolder.listFiles()?.forEach { file ->
            if (file.isFile && file.name != fileName) file.delete()
        }
        return cached
    }

    fun description(): String = _state.value.location

    fun cleanupLegacyDownloads(): Long {
        val removed = cleanupLegacyModelDownloads(context.cacheDir) +
            deleteMeasured(File(context.cacheDir, "recordings"))
        if (!hasActiveModelDownload()) {
            cleanupStaleModelDownloadParts(context.cacheDir, STALE_DOWNLOAD_MILLIS)
        }
        refreshCacheStats()
        return removed
    }

    fun clearDisposableModelCache(): Long {
        val removed = clearDisposableModelCache(
            cacheDir = context.cacheDir,
            includeIncompleteDownloads = !hasActiveModelDownload(),
        )
        refreshCacheStats()
        return removed
    }

    fun refreshCacheStats() {
        _state.value = _state.value.copy(cache = modelCacheStats(context.cacheDir))
    }

    private fun hasActiveModelDownload(): Boolean = runCatching {
        val workManager = WorkManager.getInstance(context)
        listOf(ModelDownloadWorker.KIND_WHISPER, ModelDownloadWorker.KIND_NLU, ModelDownloadWorker.KIND_PUNCTUATION).any { kind ->
            workManager.getWorkInfosForUniqueWork(ModelDownloadWorker.uniqueWorkName(kind))
                .get(5, TimeUnit.SECONDS)
                .any { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.BLOCKED ||
                        info.state == WorkInfo.State.RUNNING
                }
        }
    }.getOrElse { true }

    fun hasDataBackup(): Boolean = findDataBackup()?.isFile == true

    fun readDataBackup(): String? {
        val document = findDataBackup()?.takeIf(DocumentFile::isFile) ?: return null
        return context.contentResolver.openInputStream(document.uri)?.bufferedReader()?.use { it.readText() }
    }

    fun writeDataBackup(content: String) {
        val folder = dataFolder()
        val temporaryName = "$DATA_BACKUP_FILE.part"
        folder.findFile(temporaryName)?.delete()
        val temporary = folder.createFile("application/json", temporaryName)
            ?: throw IOException("Не удалось создать временный файл резервной копии")
        try {
            context.contentResolver.openOutputStream(temporary.uri, "w")?.bufferedWriter()?.use { it.write(content) }
                ?: throw IOException("Нет доступа на запись резервной копии")
            val written = context.contentResolver.openInputStream(temporary.uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IOException("Не удалось проверить временную резервную копию")
            if (written != content) throw IOException("Проверка временной резервной копии завершилась ошибкой")

            val old = folder.findFile(DATA_BACKUP_FILE)?.takeIf(DocumentFile::isFile)
            val previous = if (old != null) {
                folder.findFile(PREVIOUS_DATA_BACKUP_FILE)?.let { previousFile ->
                    if (!previousFile.delete()) throw IOException("Не удалось обновить предыдущую резервную копию")
                }
                if (!old.renameTo(PREVIOUS_DATA_BACKUP_FILE)) {
                    throw IOException("SAF-провайдер не поддерживает безопасную замену резервной копии")
                }
                folder.findFile(PREVIOUS_DATA_BACKUP_FILE)
                    ?: throw IOException("Не удалось сохранить предыдущую резервную копию")
            } else null

            if (!temporary.renameTo(DATA_BACKUP_FILE)) {
                if (previous != null) {
                    previous.renameTo(DATA_BACKUP_FILE)
                    throw IOException("Не удалось заменить резервную копию; предыдущая версия сохранена")
                }
                val destination = folder.createFile("application/json", DATA_BACKUP_FILE)
                    ?: throw IOException("Не удалось сохранить резервную копию")
                copyDocument(temporary, destination)
                temporary.delete()
            }
        } catch (error: Throwable) {
            if (folder.findFile(DATA_BACKUP_FILE) == null) {
                folder.findFile(PREVIOUS_DATA_BACKUP_FILE)?.renameTo(DATA_BACKUP_FILE)
            }
            temporary.delete()
            throw error
        }
    }

    private fun findDataBackup(): DocumentFile? {
        val folder = dataFolder()
        return folder.findFile(DATA_BACKUP_FILE)?.takeIf(DocumentFile::isFile)
            ?: folder.findFile(PREVIOUS_DATA_BACKUP_FILE)?.takeIf(DocumentFile::isFile)
    }

    private fun copyDocument(source: DocumentFile, destination: DocumentFile) {
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            context.contentResolver.openOutputStream(destination.uri, "w")?.use(input::copyTo)
                ?: throw IOException("Нет доступа на запись резервной копии")
        } ?: throw IOException("Не удалось прочитать временную резервную копию")
    }

    private fun find(kind: String, fileName: String): DocumentFile? {
        requireSafeName(fileName)
        return ensureFolder(kind).findFile(fileName)
    }

    private fun ensureFolder(kind: String): DocumentFile {
        require(kind in MODEL_KINDS)
        val root = root() ?: throw IllegalStateException(
            "Сначала выберите пользовательский каталог моделей. Он не удаляется при обновлении приложения.",
        )
        val models = root.findFile("models")?.takeIf(DocumentFile::isDirectory)
            ?: root.createDirectory("models")
            ?: throw IOException("Не удалось создать папку models")
        return models.findFile(kind)?.takeIf(DocumentFile::isDirectory)
            ?: models.createDirectory(kind)
            ?: throw IOException("Не удалось создать папку models/$kind")
    }

    private fun dataFolder(): DocumentFile {
        val root = root() ?: throw IllegalStateException(
            "Сначала выберите пользовательский каталог.",
        )
        return root.findFile(DATA_FOLDER)?.takeIf(DocumentFile::isDirectory)
            ?: root.createDirectory(DATA_FOLDER)
            ?: throw IOException("Не удалось создать папку $DATA_FOLDER")
    }

    private fun migrateLegacy(kind: String, extension: String) {
        val legacy = File(context.filesDir, "models/$kind")
        legacy.listFiles { file -> file.isFile && file.extension.equals(extension, true) }?.forEach { file ->
            if (!exists(kind, file.name)) runCatching { store(kind, file.name, file) }
        }
    }

    private fun root(): DocumentFile? {
        preferences.getString(KEY_ROOT, null)?.let(Uri::parse)?.let { uri ->
            return DocumentFile.fromTreeUri(context, uri)
                ?.takeIf { it.exists() && it.canRead() && it.canWrite() }
        }
        val folder = defaultRootFolder()
        if (!folder.isDirectory && !folder.mkdirs()) return null
        return DocumentFile.fromFile(folder)
    }

    private fun defaultRootFolder(): File {
        val parent = context.getExternalFilesDir(null) ?: context.filesDir
        return migratedDefaultModelRoot(parent, DEFAULT_ROOT_FOLDER, LEGACY_ROOT_FOLDER)
    }

    private fun readState(): UserModelsStorageState {
        val root = root() ?: return UserModelsStorageState()
        return UserModelsStorageState(true, rootDescription(root))
    }

    private fun initialState(): UserModelsStorageState {
        val customRoot = preferences.getString(KEY_ROOT, null)
        return if (customRoot == null) {
            UserModelsStorageState(configured = true, location = defaultRootDescription())
        } else {
            val uri = Uri.parse(customRoot)
            UserModelsStorageState(
                configured = true,
                location = uri.lastPathSegment ?: customRoot,
            )
        }
    }

    private fun requireSafeName(value: String) {
        require(value.isNotBlank() && value == File(value).name && !value.contains("..")) { "Недопустимое имя файла" }
    }

    private fun rootDescription(root: DocumentFile): String =
        if (preferences.contains(KEY_ROOT)) {
            root.name ?: root.uri.toString()
        } else {
            defaultRootDescription()
        }

    private fun defaultRootDescription(): String = if (context.getExternalFilesDir(null) != null) {
        "Память телефона/Android/data/${context.packageName}/files/$DEFAULT_ROOT_FOLDER"
    } else {
        "Память приложения/$DEFAULT_ROOT_FOLDER"
    }

    companion object {
        private const val KEY_ROOT = "root_uri"
        private val MODEL_KINDS = setOf("whisper", "nlu", "punctuation")
        private const val DEFAULT_ROOT_FOLDER = "НеЗабудьКа"
        private const val LEGACY_ROOT_FOLDER = "LocalAssistant"
        private const val DATA_FOLDER = "data"
        private const val DATA_BACKUP_FILE = "assistant-backup.json"
        private const val PREVIOUS_DATA_BACKUP_FILE = "assistant-backup.previous.json"
        private const val STALE_DOWNLOAD_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}

internal fun migratedDefaultModelRoot(parent: File, currentName: String, legacyName: String): File {
    val current = File(parent, currentName)
    val legacy = File(parent, legacyName)
    if (!current.exists() && legacy.isDirectory) {
        // Same-volume rename preserves large user models without copying them.
        legacy.renameTo(current)
    }
    return current
}

internal fun cleanupLegacyModelDownloads(cacheDir: File): Long =
    cacheDir.listFiles()
        .orEmpty()
        .filter { file -> file.isFile && file.name.endsWith(".download.part") }
        .sumOf(::deleteMeasured)

internal fun clearDisposableModelCache(cacheDir: File, includeIncompleteDownloads: Boolean = true): Long =
    cleanupLegacyModelDownloads(cacheDir) +
        deleteMeasured(File(cacheDir, "native-models")) +
        if (includeIncompleteDownloads) deleteMeasured(File(cacheDir, "model-downloads")) else 0L

internal fun cleanupStaleModelDownloadParts(
    cacheDir: File,
    maxAgeMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Long = File(cacheDir, "model-downloads")
    .walkTopDown()
    .filter { file ->
        file.isFile && file.extension == "part" &&
            nowMillis - file.lastModified() >= maxAgeMillis
    }
    .sumOf(::deleteMeasured)

internal fun modelCacheStats(cacheDir: File): ModelCacheStats = ModelCacheStats(
    nativeCopiesBytes = measuredSize(File(cacheDir, "native-models")),
    incompleteDownloadsBytes = measuredSize(File(cacheDir, "model-downloads")),
    temporaryAudioBytes = measuredSize(File(cacheDir, "recordings")),
)

private fun measuredSize(file: File): Long = when {
    file.isFile -> file.length()
    file.isDirectory -> file.listFiles().orEmpty().sumOf(::measuredSize)
    else -> 0L
}

private fun deleteMeasured(file: File): Long {
    if (!file.exists()) return 0L
    val childrenBytes = if (file.isDirectory) {
        file.listFiles().orEmpty().sumOf(::deleteMeasured)
    } else {
        0L
    }
    val ownBytes = if (file.isFile) file.length() else 0L
    return if (file.delete()) childrenBytes + ownBytes else childrenBytes
}

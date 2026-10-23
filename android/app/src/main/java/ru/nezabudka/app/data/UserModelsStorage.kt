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

    fun selectRoot(uri: Uri) = switchRoot(uri, deletePrevious = false)

    fun useDefaultRoot(deletePrevious: Boolean = false) = switchRoot(null, deletePrevious)

    @Synchronized
    private fun switchRoot(uri: Uri?, deletePrevious: Boolean) {
        check(!hasActiveModelDownload()) { "Дождитесь завершения загрузок моделей" }
        if (uri != null) context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val oldUri = preferences.getString(KEY_ROOT, null)
        if (oldUri == uri?.toString()) { refresh(); return }
        val old = root() ?: throw IOException("Нет доступа к прежней папке")
        if (deletePrevious) {
            require(oldUri != null) { "Можно удалить только выбранную пользовательскую папку" }
            require(old.listFiles().all { it.name in setOf("models", DATA_FOLDER) }) {
                "В папке есть посторонние файлы. Их удаление не разрешено; выберите возврат к папке по умолчанию."
            }
        }
        val target = if (uri == null) DocumentFile.fromFile(defaultRootFolder())
            else DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Папка недоступна")
        require(target.canRead() && target.canWrite()) { "Нет доступа к новой папке" }
        if (target.uri == old.uri) return
        fun identity(document: DocumentFile): String {
            if (document.uri.scheme == "file") return File(requireNotNull(document.uri.path)).canonicalPath
            if (document.uri.authority == "com.android.externalstorage.documents") {
                val id = android.provider.DocumentsContract.getTreeDocumentId(document.uri)
                if (id.startsWith("primary:")) return File(android.os.Environment.getExternalStorageDirectory(), id.substringAfter(':')).canonicalPath
            }
            return document.uri.toString().trimEnd('/')
        }
        val oldPath = identity(old)
        val newPath = identity(target)
        require(oldPath != newPath && !oldPath.startsWith("$newPath/") && !newPath.startsWith("$oldPath/")) {
            "Выберите отдельную папку: она не должна совпадать с прежней, находиться внутри неё или содержать её"
        }
        val copies = mutableListOf<Pair<DocumentFile, DocumentFile>>()
        fun directory(parent: DocumentFile, name: String): DocumentFile = parent.findFile(name)?.also {
            require(it.isDirectory) { "Вместо папки $name найден файл" }
        } ?: parent.createDirectory(name) ?: throw IOException("Не удалось создать папку $name")
        val targetModels = directory(target, "models")
        for (kind in MODEL_KINDS) {
            val destination = directory(targetModels, kind)
            old.findFile("models")?.findFile(kind)?.listFiles().orEmpty().filter { it.isFile && it.name?.endsWith(".part") == false }.forEach { source ->
                val name = source.name ?: throw IOException("Не удалось прочитать имя модели")
                val existing = destination.findFile(name)
                val copied = if (existing != null) {
                    check(existing.isFile && digest(source) == digest(existing)) { "В новой папке уже есть другая модель $name. Выберите другую папку." }
                    existing
                } else copyVerified(source, destination, name)
                copies += source to copied
            }
        }
        val data = directory(target, DATA_FOLDER)
        old.findFile(DATA_FOLDER)?.listFiles().orEmpty().filter { it.isFile && it.name in setOf(DATA_BACKUP_FILE, PREVIOUS_DATA_BACKUP_FILE) }.forEach { source ->
            val name = requireNotNull(source.name)
            if (data.findFile(name) == null) copyVerified(source, data, name)
        }
        val editor = preferences.edit()
        if (uri == null) editor.remove(KEY_ROOT) else editor.putString(KEY_ROOT, uri.toString())
        check(editor.commit()) { "Не удалось сохранить выбор папки" }
        // All models have been verified before changing the root or removing any source.
        copies.forEach { (source, _) -> source.delete() }
        val deletionFailed = deletePrevious && !old.delete()
        migrateLegacy("whisper", "bin")
        migrateLegacy("gigaam", "onnx")
        migrateLegacy("nlu", "onnx")
        migrateLegacy("punctuation", "onnx")
        _state.value = readState().copy(cache = _state.value.cache)
        if (deletionFailed) throw IOException("Данные перенесены, но прежнюю папку не удалось удалить")
    }

    private fun digest(document: DocumentFile): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(document.uri)?.use { input ->
            val bytes = ByteArray(128 * 1024)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        } ?: throw IOException("Не удалось прочитать ${document.name}")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyVerified(source: DocumentFile, folder: DocumentFile, name: String): DocumentFile {
        requireSafeName(name)
        val temporary = folder.createFile("application/octet-stream", "$name.moving") ?: throw IOException("Не удалось создать $name")
        try {
            copyDocument(source, temporary)
            check(digest(source) == digest(temporary)) { "Ошибка проверки переноса $name" }
            check(temporary.renameTo(name)) { "Не удалось завершить перенос $name" }
            return folder.findFile(name) ?: throw IOException("Не удалось найти перенесённый файл $name")
        } catch (error: Throwable) { temporary.delete(); throw error }
    }

    fun list(kind: String, extension: String): List<String> = ensureFolder(kind).listFiles()
        .filter { it.isFile && it.name?.substringAfterLast('.', "")?.equals(extension, true) == true }
        .mapNotNull(DocumentFile::getName)
        .sorted()

    fun exists(kind: String, fileName: String): Boolean = find(kind, fileName)?.isFile == true

    fun deleteModel(kind: String, fileName: String) {
        require(kind in MODEL_KINDS) { "Неизвестный каталог моделей" }
        requireSafeName(fileName)
        val source = find(kind, fileName) ?: throw IOException("Модель $fileName не найдена")
        require(source.isFile) { "Можно удалить только файл модели" }
        if (!source.delete()) throw IOException("Не удалось удалить модель $fileName")
        val cached = File(context.cacheDir, "native-models/$kind/$fileName")
        if (cached.isFile) cached.delete()
        refreshCacheStats()
    }

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
        listOf(ModelDownloadWorker.KIND_WHISPER, ModelDownloadWorker.KIND_NLU, ModelDownloadWorker.KIND_PUNCTUATION, "gigaam").any { kind ->
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
        return migrateDefaultStorageLayout(parent)
    }

    private fun readState(): UserModelsStorageState {
        val root = root() ?: return UserModelsStorageState()
        return UserModelsStorageState(true, rootDescription(root), userSelectedDirectory = preferences.contains(KEY_ROOT))
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
                userSelectedDirectory = true,
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
        "Память телефона/Android/data/${context.packageName}/files"
    } else {
        "Память приложения"
    }

    companion object {
        private const val KEY_ROOT = "root_uri"
        private val MODEL_KINDS = setOf("whisper", "gigaam", "nlu", "punctuation")
        private const val DEFAULT_ROOT_FOLDER = "НеЗабудьКа"
        private const val LEGACY_ROOT_FOLDER = "LocalAssistant"
        private const val DATA_FOLDER = "data"
        private const val DATA_BACKUP_FILE = "assistant-backup.json"
        private const val PREVIOUS_DATA_BACKUP_FILE = "assistant-backup.previous.json"
        private const val STALE_DOWNLOAD_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}

@Synchronized
internal fun migrateDefaultStorageLayout(parent: File): File {
    parent.mkdirs()
    for (oldName in listOf("НеЗабудьКа", "LocalAssistant")) {
        val legacy = File(parent, oldName)
        for (kind in listOf("models", "data")) {
            val source = File(legacy, kind)
            val destination = File(parent, kind)
            if (source.isDirectory && !destination.exists()) check(source.renameTo(destination)) { "Не удалось перенести $kind" }
            else if (source.isDirectory) {
                source.walkTopDown().filter { it.isFile }.forEach { file ->
                    val target = File(destination, file.relativeTo(source).path)
                    if (!target.exists()) { target.parentFile?.mkdirs(); check(file.renameTo(target)) { "Не удалось перенести ${file.name}" } }
                }
            }
        }
        if (legacy.isDirectory) legacy.walkBottomUp().filter { it.isDirectory && it.listFiles()?.isEmpty() == true }.forEach { it.delete() }
    }
    return parent
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

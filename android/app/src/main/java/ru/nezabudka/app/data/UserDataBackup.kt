package ru.nezabudka.app.data

import android.net.Uri
import android.content.Context
import androidx.room.withTransaction
import ru.nezabudka.app.domain.AppSettings
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.ports.PortableCloudConfigurationStore
import ru.nezabudka.app.ports.PortableModelSelection
import ru.nezabudka.app.ports.PortableCloudConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BackupSyncResult(
    val restored: Boolean,
    val message: String,
)

class UserDataBackupManager(
    private val storage: UserModelsStorage,
    private val database: AppDatabase,
    private val reminders: RoomReminderRepository,
    private val notes: RoomNotesRepository,
    private val settings: SettingsStore,
    private val whisperModels: PortableModelSelection,
    private val cloudConfiguration: PortableCloudConfigurationStore,
    private val onRestoreCompleted: suspend () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            mutex.withLock {
                if (storage.state.value.userSelectedDirectory) restoreIfDatabaseEmpty()
            }
            combine(
                storage.state.map { it.userSelectedDirectory }.distinctUntilChanged(),
                storage.state.map { it.configured }.distinctUntilChanged(),
            ) { enabled, configured -> enabled && configured }
                .distinctUntilChanged()
                .flatMapLatest { active ->
                    if (!active) {
                        emptyFlow()
                    } else {
                        val applicationData = combine(
                            settings.state,
                            storage.state,
                            reminders.observeAll(),
                            notes.observeFolders(),
                            notes.observeAll(),
                        ) { appSettings, storageState, reminderValues, folderValues, noteValues ->
                            BackupObservation(
                                appSettings = appSettings,
                                storageConfigured = storageState.configured,
                                reminders = reminderValues,
                                folders = folderValues,
                                notes = noteValues,
                            )
                        }
                        combine(
                            applicationData,
                            whisperModels.selectedModelName,
                            cloudConfiguration.portableConfiguration,
                        ) { value, selectedWhisperModel, portableCloud ->
                            value.copy(
                                selectedWhisperModel = selectedWhisperModel,
                                yandexFolderId = portableCloud.yandexFolderId,
                                vkEndpoint = portableCloud.vkEndpoint,
                            )
                        }
                    }
                }
                .debounce(1_200)
                .collect { value ->
                    runCatching {
                        mutex.withLock {
                            if (storage.state.value.userSelectedDirectory) writeCurrent()
                        }
                    }
                }
        }
    }

    suspend fun selectRoot(uri: Uri): BackupSyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            storage.selectRoot(uri)
            val restored = restoreIfDatabaseEmpty()
            writeCurrent()
            BackupSyncResult(
                restored,
                if (restored) "Данные и настройки восстановлены из папки data"
                else "Каталог выбран; модели хранятся в models, данные и настройки — в data",
            )
        }
    }

    private suspend fun writeCurrent() {
        write(
            reminders.observeAll().first(),
            notes.observeFolders().first(),
            notes.observeAll().first(),
            settings.state.value.toPortableSettings(
                selectedWhisperModel = whisperModels.currentSelectedModelName(),
                yandexFolderId = cloudConfiguration.currentPortableConfiguration().yandexFolderId,
                vkEndpoint = cloudConfiguration.currentPortableConfiguration().vkEndpoint,
            ),
        )
    }

    private fun write(
        reminders: List<Reminder>,
        folders: List<NoteFolder>,
        notes: List<Note>,
        portableSettings: PortableAppSettings,
    ) {
        storage.writeDataBackup(UserDataBackupCodec.encode(snapshot(reminders, folders, notes, portableSettings)))
    }

    private fun snapshot(
        reminders: List<Reminder>, folders: List<NoteFolder>, notes: List<Note>,
        portableSettings: PortableAppSettings? = null,
    ): UserDataBackupDto = UserDataBackupDto(
            reminders = reminders.map {
                BackupReminderDto(
                    it.id, it.text, it.remindAt?.toString(), it.recurrence, it.status, it.createdAt.toString(),
                    it.favorite, it.manualOrder, it.completedAt?.toString(),
                )
            },
            folders = folders.map { BackupFolderDto(it.id, it.name, it.createdAt.toString(), it.manualOrder) },
            notes = notes.map {
                BackupNoteDto(
                    it.id, it.folderId, it.folderIds, it.text, it.createdAt.toString(), it.updatedAt.toString(),
                    it.pinned, it.archived, it.trashed, it.color, it.title, it.manualOrder,
                )
            },
            settings = portableSettings,
        )

    suspend fun exportFile(context: Context, uri: Uri, content: BackupContent) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val data = database.withTransaction {
                val reminders = if (content == BackupContent.REMINDERS) database.reminderDao().allForBackup().map {
                    BackupReminderDto(it.id, it.text, it.remindAt?.toString(), it.recurrence, it.status,
                        it.createdAt.toString(), it.favorite, it.manualOrder, it.completedAt?.toString())
                } else emptyList()
                val dao = database.notesDao()
                val folders = if (content == BackupContent.NOTES) dao.allFolders().map {
                    BackupFolderDto(it.id, it.name, it.createdAt.toString(), it.manualOrder)
                } else emptyList()
                val notes = if (content == BackupContent.NOTES) {
                    val refs = dao.allRefsForBackup().groupBy { it.noteId }
                    dao.allNotesInManualOrder().map {
                        BackupNoteDto(it.id, it.folderId, refs[it.id].orEmpty().map { ref -> ref.folderId },
                            it.text, it.createdAt.toString(), it.updatedAt.toString(), it.pinned, it.archived,
                            it.trashed, it.color, it.title, it.manualOrder)
                    }
                } else emptyList()
                UserDataBackupDto(reminders = reminders, folders = folders, notes = notes)
            }
            val encoded = UserDataBackupCodec.encode(UserDataBackupCodec.forContent(data, content))
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Не удалось открыть файл для сохранения")
            output.bufferedWriter(Charsets.UTF_8).use { it.write(encoded) }
        }
    }

    internal suspend fun readFile(context: Context, uri: Uri, content: BackupContent): BackupRestorePlan = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть выбранный файл")
        val bytes = input.use { stream ->
            val result = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(result.size() + count <= 32 * 1024 * 1024) { "Файл слишком большой: максимум 32 МБ" }
                result.write(buffer, 0, count)
            }
            result.toByteArray()
        }
        BackupRestorePlan.from(UserDataBackupCodec.forContent(UserDataBackupCodec.decode(bytes.toString(Charsets.UTF_8)), content))
    }

    internal suspend fun restoreFile(plan: BackupRestorePlan) = withContext(Dispatchers.IO) {
        mutex.withLock {
            var added = 0
            var skipped = 0
            var foldersAdded = 0
            // Generate new identifiers, so imported records cannot overwrite existing data.
            database.withTransaction {
                val dao = database.notesDao()
                val duplicates = ImportDuplicateIndex()
                val refsByNote = dao.allRefsForBackup().groupBy { it.noteId }
                dao.allNotesInManualOrder().forEach { duplicates.addNote(it, refsByNote[it.id].orEmpty().map { ref -> ref.folderId }) }
                database.reminderDao().allForBackup().forEach { duplicates.addReminder(it) }
                val existingFolders = dao.allFolders().associateBy { it.name.lowercase() }.toMutableMap()
                val folderMap = mutableMapOf<Long, Long>()
                plan.folders.forEach { folder ->
                    val existing = existingFolders[folder.name.lowercase()]
                    val id = existing?.id ?: dao.insertFolder(folder.copy(id = 0)).also { newId ->
                        check(newId > 0) { "Не удалось восстановить папку ${folder.name}" }
                        foldersAdded++
                        existingFolders[folder.name.lowercase()] = folder.copy(id = newId)
                    }
                    folderMap[folder.id] = id
                }
                plan.notes.forEach { value ->
                    val mapped = value.forImport(folderMap)
                    if (!duplicates.addNote(mapped.entity, mapped.folderIds)) { skipped++; return@forEach }
                    val noteId = dao.insertNote(mapped.entity)
                    added++
                    val refs = mapped.folderIds.map { NoteFolderRef(noteId, it) }
                    if (refs.isNotEmpty()) dao.insertRefs(refs)
                }
                plan.reminders.forEach {
                    if (duplicates.addReminder(it)) { database.reminderDao().insert(it.copy(id = 0)); added++ }
                    else skipped++
                }
            }
            ManualRestoreResult(added, skipped, added == 0 || runCatching { onRestoreCompleted() }.isSuccess, foldersAdded)
        }
    }

    private suspend fun restoreIfDatabaseEmpty(): Boolean {
        if (!storage.hasDataBackup()) return false
        if (database.reminderDao().count() != 0 || database.notesDao().noteCount() != 0 || database.notesDao().folderCount() != 0) {
            return false
        }
        val raw = storage.readDataBackup()?.takeIf(String::isNotBlank) ?: return false
        val backup = UserDataBackupCodec.decode(raw)
        val plan = BackupRestorePlan.from(backup)
        database.withTransaction {
            plan.folders.forEach { folder ->
                database.notesDao().insertFolder(folder)
            }
            plan.notes.forEach { value ->
                database.notesDao().insertNote(value.entity)
                val refs = value.folderIds.map { folderId -> NoteFolderRef(value.entity.id, folderId) }
                if (refs.isNotEmpty()) database.notesDao().insertRefs(refs)
            }
            plan.reminders.forEach { reminder ->
                database.reminderDao().restore(reminder)
            }
        }
        plan.settings?.let { portable ->
            settings.restorePortableSettings(portable)
            cloudConfiguration.restorePortableConfiguration(
                PortableCloudConfiguration(portable.yandexFolderId, portable.vkEndpoint),
            )
            portable.selectedWhisperModel?.let { fileName -> runCatching { whisperModels.selectIfAvailable(fileName) } }
        }
        onRestoreCompleted()
        return true
    }

    private data class BackupObservation(
        val appSettings: AppSettings,
        val storageConfigured: Boolean,
        val reminders: List<Reminder>,
        val folders: List<NoteFolder>,
        val notes: List<Note>,
        val selectedWhisperModel: String? = null,
        val yandexFolderId: String = "",
        val vkEndpoint: String = "",
    )
}

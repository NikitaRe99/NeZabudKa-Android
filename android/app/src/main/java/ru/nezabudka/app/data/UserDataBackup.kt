package ru.nezabudka.app.data

import android.net.Uri
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
            combine(
                settings.state.map { it.externalDataBackupEnabled }.distinctUntilChanged(),
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
                            write(
                                value.reminders,
                                value.folders,
                                value.notes,
                                value.appSettings.toPortableSettings(
                                    selectedWhisperModel = value.selectedWhisperModel,
                                    yandexFolderId = value.yandexFolderId,
                                    vkEndpoint = value.vkEndpoint,
                                ),
                            )
                        }
                    }
                }
        }
    }

    suspend fun selectRoot(uri: Uri): BackupSyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            storage.selectRoot(uri)
            val restored = restoreIfDatabaseEmpty()
            if (restored) settings.setExternalDataBackupEnabled(true)
            if (settings.state.value.externalDataBackupEnabled) writeCurrent()
            BackupSyncResult(
                restored,
                if (restored) "Данные и настройки восстановлены из папки data"
                else "Каталог выбран; модели хранятся в models, данные и настройки — в data",
            )
        }
    }

    suspend fun setEnabled(enabled: Boolean): BackupSyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!enabled) {
                settings.setExternalDataBackupEnabled(false)
                return@withLock BackupSyncResult(false, "Внешнее сохранение данных отключено")
            }
            check(storage.state.value.configured) { "Сначала выберите пользовательский каталог" }
            val restored = restoreIfDatabaseEmpty()
            settings.setExternalDataBackupEnabled(true)
            writeCurrent()
            BackupSyncResult(
                restored,
                if (restored) "Данные и настройки восстановлены, автоматическое сохранение включено"
                else "Заметки, напоминания и настройки сохраняются в папке data",
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
        val backup = UserDataBackupDto(
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
        storage.writeDataBackup(UserDataBackupCodec.encode(backup))
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

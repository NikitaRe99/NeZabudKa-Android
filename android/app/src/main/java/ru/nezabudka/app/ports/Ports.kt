package ru.nezabudka.app.ports

import ru.nezabudka.app.domain.AssistantCommand
import ru.nezabudka.app.domain.CommandContext
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.domain.DeletedNoteFolder
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.domain.AppSettings
import ru.nezabudka.app.domain.CustomThemeSettings
import ru.nezabudka.app.domain.TimeOfDaySetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDateTime

fun interface CommandUnderstandingPort {
    suspend fun understand(text: String, now: LocalDateTime, context: CommandContext): AssistantCommand
}

interface ReminderRepository {
    fun observeAll(): Flow<List<Reminder>>
    fun observe(id: Long): Flow<Reminder?> = observeAll().map { values -> values.firstOrNull { it.id == id } }
    suspend fun create(text: String, remindAt: LocalDateTime?, recurrence: String?): Reminder
    suspend fun list(start: LocalDateTime? = null, end: LocalDateTime? = null): List<Reminder>
    suspend fun find(id: Long?, text: String?, last: Boolean): Reminder?
    suspend fun update(id: Long, text: String?, remindAt: LocalDateTime?): Reminder
    suspend fun replace(id: Long, text: String, remindAt: LocalDateTime?): Reminder
    suspend fun setRecurrence(id: Long, recurrence: String?): Reminder
    suspend fun reschedule(id: Long, text: String, remindAt: LocalDateTime?): Reminder {
        var updated = replace(id, text, remindAt)
        if (updated.status == "completed") updated = setCompleted(id, false)
        return updated
    }
    suspend fun saveEditor(id: Long, text: String, remindAt: LocalDateTime?, recurrence: String?, favorite: Boolean, completed: Boolean): Reminder {
        var updated = replace(id, text, remindAt)
        if (updated.recurrence != recurrence) updated = setRecurrence(id, recurrence)
        if (updated.favorite != favorite) {
            setFavorite(id, favorite)
            updated = updated.copy(favorite = favorite)
        }
        if ((updated.status == "completed") != completed) updated = setCompleted(id, completed)
        return updated
    }
    suspend fun delete(id: Long)
    suspend fun restore(reminder: Reminder): Reminder {
        var restored = create(reminder.text, reminder.remindAt, reminder.recurrence)
        if (reminder.favorite) {
            setFavorite(restored.id, true)
            restored = restored.copy(favorite = true)
        }
        if (reminder.status == "completed") restored = setCompleted(restored.id, true)
        return restored
    }
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun setCompleted(id: Long, completed: Boolean): Reminder
    suspend fun move(id: Long, beforeId: Long?)
    suspend fun deleteCompleted()
}

interface NotesRepository {
    fun observeFolders(): Flow<List<NoteFolder>>
    fun observeNotes(folderId: Long): Flow<List<Note>>
    fun observeAll(): Flow<List<Note>>
    suspend fun create(folderName: String?, text: String): Note
    suspend fun createFolder(folderName: String): NoteFolder
    suspend fun findFolder(name: String): NoteFolder?
    suspend fun renameFolder(id: Long, name: String)
    suspend fun moveFolder(id: Long, beforeId: Long?)
    suspend fun moveNote(id: Long, beforeId: Long?)
    suspend fun deleteFolder(id: Long): DeletedNoteFolder?
    suspend fun restoreFolder(snapshot: DeletedNoteFolder): NoteFolder
    suspend fun deleteNote(id: Long)
    suspend fun deleteTrashed()
    suspend fun deleteTrashedBefore(cutoff: LocalDateTime)
    suspend fun save(id: Long?, folderNames: List<String>, title: String, text: String, pinned: Boolean, archived: Boolean, color: String?): Note
    suspend fun setPinned(id: Long, pinned: Boolean)
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun setTrashed(id: Long, trashed: Boolean)
    suspend fun setPinned(ids: List<Long>, pinned: Boolean)
    suspend fun setArchived(ids: List<Long>, archived: Boolean)
    suspend fun setTrashed(ids: List<Long>, trashed: Boolean)
    suspend fun setColor(ids: List<Long>, color: String?)
    suspend fun setFolderMembership(ids: List<Long>, folderName: String, included: Boolean)
}

interface ReminderScheduler {
    fun schedule(reminder: Reminder)
    fun cancel(reminderId: Long)
    fun isScheduled(reminderId: Long): Boolean = false
}

interface SettingsPort {
    val state: StateFlow<AppSettings>
    fun setTheme(value: String)
    fun setCustomTheme(value: CustomThemeSettings)
    fun setPopupDurationSeconds(value: Int)
    fun setLocalOnly(enabled: Boolean)
    fun setClassicNluEnabled(enabled: Boolean)
    fun setExternalDataBackupEnabled(enabled: Boolean)
    fun setTimeOfDaySettings(values: List<TimeOfDaySetting>)
    fun setSttEngine(value: String)
    fun setKeepWhisperModelLoaded(enabled: Boolean)
    fun setWhisperThreadCount(value: Int)
    fun setWhisperAccurateMode(enabled: Boolean)
    fun setNotePunctuationModel(value: String)
    fun setAudioPreprocessing(enabled: Boolean)
    fun setShowPlaybackButton(enabled: Boolean)
    fun setShowAssistantWelcomeCard(enabled: Boolean) {}
    fun setShowTechnicalLog(enabled: Boolean)
    fun setTrustStt(enabled: Boolean)
    fun setReminderSort(value: String)
    fun setNoteSort(value: String)
    fun setNoteColumns(value: Int)
    fun setNoteFolderInterface(value: String)
    fun setNoteTrashRetentionDays(value: Int)
    fun setShowNavigationLabels(enabled: Boolean)
    fun setUiPerformanceMode(value: String)
    fun setNotificationSound(uri: String)
    fun setNotificationVibrationEnabled(enabled: Boolean)
    fun setReminderAlarmMode(enabled: Boolean)
    fun setReminderAlarmPresentation(value: String)
    fun setReminderAlarmSound(uri: String)
    fun setNotificationWakeScreen(enabled: Boolean)
    fun setReminderAlarmRingSeconds(value: Int)
    fun setReminderAlarmPauseMinutes(value: Int)
    fun setReminderAlarmRepeatCount(value: Int)
}


interface SpeechToTextEngine {
    val id: String
    val displayName: String
    suspend fun transcribe(processedWav: File): SpeechRecognition
}

interface ContextualSpeechToTextEngine {
    fun setContextHints(hints: List<String>)
    fun setCommandVocabularyEnabled(enabled: Boolean) {}
    fun setAwaitingDateResponse(enabled: Boolean) {}
    fun setCommandScope(scope: CommandScope) {}
}

interface LiveSpeechToTextEngine : SpeechToTextEngine {
    fun start(
        onResult: (SpeechRecognition) -> Unit,
        onError: (Throwable) -> Unit,
    )
    fun stop()
    fun cancel()
}

data class SpeechRecognition(
    val text: String,
    val confidence: Float? = null,
    val alternatives: List<String> = emptyList(),
    val notice: String? = null,
    val requiresConfirmation: Boolean = false,
)

interface AudioPreprocessor {
    suspend fun process(inputWav: File): File
    fun lastProcessed(): File?
}

interface AudioRecorder {
    val isRecording: Boolean
    fun start()
    suspend fun stop(): File
}

interface CancellableAudioRecorder {
    fun cancel()
}

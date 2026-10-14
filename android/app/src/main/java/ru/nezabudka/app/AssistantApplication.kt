package ru.nezabudka.app

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import ru.nezabudka.app.application.Dispatcher
import ru.nezabudka.app.application.ExecuteTextCommand
import ru.nezabudka.app.application.OptionalCommandUnderstanding
import ru.nezabudka.app.application.ReminderActions
import ru.nezabudka.app.application.RestoreReminderSchedules
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.RoomNotesRepository
import ru.nezabudka.app.data.RoomReminderRepository
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.data.NoteTrashCleanupWorker
import ru.nezabudka.app.updates.GithubAppUpdater
import ru.nezabudka.app.data.UserModelsStorage
import ru.nezabudka.app.data.UserDataBackupManager
import ru.nezabudka.app.llm.ClassicNluCommandMapper
import ru.nezabudka.app.llm.ClassicNluClient
import ru.nezabudka.app.notifications.AndroidReminderScheduler
import ru.nezabudka.app.domain.RussianTemporalParser
import ru.nezabudka.app.speech.AndroidSpeechRecognizerEngine
import ru.nezabudka.app.speech.PcmAudioPreprocessor
import ru.nezabudka.app.speech.SttRegistry
import ru.nezabudka.app.speech.WavAudioRecorder
import ru.nezabudka.app.speech.WhisperCppSttEngine
import ru.nezabudka.app.speech.WhisperModelManager
import ru.nezabudka.app.speech.WhisperVadModelProvider
import ru.nezabudka.app.speech.CloudSttCredentials
import ru.nezabudka.app.speech.GoogleCloudSpeechEngine
import ru.nezabudka.app.speech.YandexSpeechKitEngine
import ru.nezabudka.app.speech.VkCloudSpeechEngine
import ru.nezabudka.app.ports.SettingsPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean

open class AssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var visibleActivities = 0
            override fun onActivityStarted(activity: Activity) {
                visibleActivities++
                container.setAppVisible(true)
            }
            override fun onActivityStopped(activity: Activity) {
                visibleActivities = (visibleActivities - 1).coerceAtLeast(0)
                if (visibleActivities == 0 && !activity.isChangingConfigurations) container.setAppVisible(false)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        ru.nezabudka.app.widget.HomeWidgets.start(this)
    }
    private val containerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uiServicesStarted = AtomicBoolean(false)
    val container: AppContainer get() = containerDelegate.value

    /** Starts UI-only observers after the first screen is already being composed. */
    open fun startUiServices() {
        if (!uiServicesStarted.compareAndSet(false, true)) return
        val activeContainer = container
        applicationScope.launch {
            delay(750)
            if (activeContainer.modelStorage.state.value.userSelectedDirectory) {
                activeContainer.startDataBackupServices()
            }
            activeContainer.runStartupMaintenanceIfNeeded()

            // Reopening the app is a useful recovery path, but rescheduling every
            // alarm must not compete with the first frame or opening an editor.
            delay(2_250)
            activeContainer.restoreReminderSchedules(skipIfSchedulesIntact = true)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!containerDelegate.isInitialized()) return
        when {
            level >= TRIM_MEMORY_BACKGROUND -> container.releaseNativeResources()
            level >= TRIM_MEMORY_UI_HIDDEN -> container.releaseClassicNlu()
        }
    }
}

class AppContainer(val app: Application) {
    private val appVisible = AtomicBoolean(false)
    fun setAppVisible(visible: Boolean) {
        appVisible.set(visible)
        if (!visible) applicationScope.launch {
            whisper.releaseWhenIdle { !appVisible.get() }
            gigaam.release()
        }
    }
    val appIcons = ru.nezabudka.app.data.AppIconManager(app)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val portableResourcesLoaded = AtomicBoolean(false)
    private val dataBackupStarted = AtomicBoolean(false)
    val database = AppDatabase.get(app)
    val reminders = RoomReminderRepository(database.reminderDao())
    val notes = RoomNotesRepository(database)
    private val settingsStore = SettingsStore(app)
    val settings: SettingsPort = settingsStore
    val scheduler = AndroidReminderScheduler(app)
    val reminderActions = ReminderActions(reminders, scheduler)
    val restoreReminderSchedules = RestoreReminderSchedules(reminders, scheduler)
    val dispatcher = Dispatcher(reminders, notes, reminderActions)
    private val temporalParser = RussianTemporalParser {
        settings.state.value.timeOfDaySettings.mapNotNull { value ->
            runCatching { value.phrase to LocalTime.parse(value.time) }.getOrNull()
        }.toMap()
    }
    val commandMapper = ClassicNluCommandMapper(temporalParser)
    val modelStorage = UserModelsStorage(app)
    val classicNlu = ClassicNluClient(app, commandMapper, modelStorage)
    val commandUnderstanding = OptionalCommandUnderstanding(classicNlu) {
        settings.state.value.classicNluEnabled
    }
    val execute = ExecuteTextCommand(
        commandUnderstanding,
        dispatcher,
        temporal = temporalParser,
    )
    val recorder = WavAudioRecorder(app, recordingTailMs = 180) { settings.state.value.audioPreprocessingEnabled }
    val preprocessor = PcmAudioPreprocessor(app)
    val notePunctuation = ru.nezabudka.app.speech.NotePunctuation(app, modelStorage)
    val whisperModels = WhisperModelManager(app, modelStorage)
    val gigaamModels = ru.nezabudka.app.speech.GigaamModelManager(app, modelStorage)
    val gigaam = ru.nezabudka.app.speech.GigaamSttEngine(app, gigaamModels,
        threads = { settings.state.value.gigaamThreadCount },
        keepLoaded = { appVisible.get() && settings.state.value.sttEngine == "gigaam" && settings.state.value.keepGigaamModelLoaded },
    )
    private val whisperVadModel = WhisperVadModelProvider(app)
    val cloudSttCredentials = CloudSttCredentials(app)
    val dataBackup = UserDataBackupManager(
        modelStorage, database, reminders, notes, settingsStore, whisperModels, cloudSttCredentials,
        onRestoreCompleted = {
            restoreReminderSchedules()
            NoteTrashCleanupWorker.configure(app, settings.state.value.noteTrashRetentionDays, runImmediately = true)
            if (!settings.state.value.keepWhisperModelLoaded) {
                whisper.release()
            }
        },
    )
    val whisper = WhisperCppSttEngine(
        models = whisperModels,
        vadModels = whisperVadModel,
        temporaryDirectory = File(app.cacheDir, "recordings/whisper-segments"),
        keepModelLoaded = {
            appVisible.get() && settings.state.value.sttEngine == "whisper" && settings.state.value.keepWhisperModelLoaded
        },
        configuredThreadCount = { settings.state.value.whisperThreadCount },
        accurateMode = { settings.state.value.whisperAccurateMode },
        rawMode = { settings.state.value.whisperRawMode },
        isAppVisible = { appVisible.get() },
    )
    val stt = SttRegistry(listOf(
        gigaam, AndroidSpeechRecognizerEngine(app), whisper,
        YandexSpeechKitEngine(cloudSttCredentials), GoogleCloudSpeechEngine(cloudSttCredentials),
        VkCloudSpeechEngine(cloudSttCredentials),
    ))

    /** Must be called from a background dispatcher. */
    fun startSettingsResources() {
        loadPortableResources()
        whisperModels.start()
        gigaamModels.start(applicationScope)
        classicNlu.startModelObserver(applicationScope)
        notePunctuation.models.start(applicationScope)
    }

    /** Must be called from a background dispatcher. */
    fun startDataBackupServices() {
        loadPortableResources()
        if (dataBackupStarted.compareAndSet(false, true)) dataBackup.start()
    }

    /** Runs filesystem cleanup and WorkManager setup once per installed version. */
    fun runStartupMaintenanceIfNeeded() {
        val preferences = app.getSharedPreferences("startup_maintenance", Application.MODE_PRIVATE)
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        val signature = "${PackageInfoCompat.getLongVersionCode(packageInfo)}:${settings.state.value.noteTrashRetentionDays}"
        if (preferences.getString("signature", null) == signature) return
        NoteTrashCleanupWorker.configure(app, settings.state.value.noteTrashRetentionDays)
        GithubAppUpdater.configure(app)
        runCatching { modelStorage.cleanupLegacyDownloads() }
            .onFailure { Log.w("LocalAssistant", "Не удалось очистить временный кэш", it) }
        preferences.edit().putString("signature", signature).apply()
    }

    private fun loadPortableResources() {
        if (!portableResourcesLoaded.compareAndSet(false, true)) return
        runCatching { cloudSttCredentials.refresh() }
            .onFailure { Log.w("LocalAssistant", "Не удалось прочитать настройки облачного STT", it) }
        runCatching { modelStorage.refresh() }
            .onFailure { Log.w("LocalAssistant", "Не удалось проверить каталог моделей", it) }
        runCatching { whisperModels.refresh() }
            .onFailure { Log.w("LocalAssistant", "Не удалось обновить список моделей Whisper", it) }
        runCatching { gigaamModels.refresh() }
            .onFailure { Log.w("LocalAssistant", "Не удалось обновить список моделей GigaAM", it) }
        runCatching { classicNlu.refreshModel() }
            .onFailure { Log.w("LocalAssistant", "Не удалось обновить состояние NLU-модели", it) }
    }

    fun setNoteTrashRetentionDays(value: Int) {
        settings.setNoteTrashRetentionDays(value)
        NoteTrashCleanupWorker.configure(
            app,
            settings.state.value.noteTrashRetentionDays,
            runImmediately = true,
        )
    }

    fun releaseNativeResources() {
        applicationScope.launch {
            classicNlu.release()
            whisper.release()
            gigaam.release()
        }
    }

    fun releaseClassicNlu() {
        applicationScope.launch { classicNlu.release() }
    }
}

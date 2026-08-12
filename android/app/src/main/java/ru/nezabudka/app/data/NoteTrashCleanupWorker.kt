package ru.nezabudka.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class NoteTrashCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val retentionDays = SettingsStore(applicationContext).state.value.noteTrashRetentionDays
        if (retentionDays > 0) {
            val cutoff = LocalDateTime.now().minusDays(retentionDays.toLong())
            AppDatabase.get(applicationContext).notesDao().deleteTrashedBefore(cutoff)
        }
        Result.success()
    }.getOrElse {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "note-trash-periodic-cleanup"
        private const val IMMEDIATE_WORK_NAME = "note-trash-immediate-cleanup"
        private const val MAX_ATTEMPTS = 3

        fun configure(context: Context, retentionDays: Int, runImmediately: Boolean = false) {
            val workManager = WorkManager.getInstance(context)
            if (retentionDays <= 0) {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                return
            }
            val periodic = PeriodicWorkRequestBuilder<NoteTrashCleanupWorker>(1, TimeUnit.DAYS).build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
            if (!runImmediately) return
            val immediate = OneTimeWorkRequestBuilder<NoteTrashCleanupWorker>().build()
            workManager.enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediate,
            )
        }
    }
}

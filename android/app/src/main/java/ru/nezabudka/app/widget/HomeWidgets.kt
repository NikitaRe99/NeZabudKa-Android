package ru.nezabudka.app.widget

import androidx.room.withTransaction

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nezabudka.app.R
import ru.nezabudka.app.application.ReminderActions
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.RoomReminderRepository
import ru.nezabudka.app.notifications.AndroidReminderScheduler
import ru.nezabudka.app.notifications.dismissReminderNotification
import java.time.LocalDate
import java.time.ZoneId

object HomeWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private var observation: Job? = null
    private var settingsPrefs: SharedPreferences? = null
    private var settingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun ids(context: Context, kind: WidgetKind): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, if (kind == WidgetKind.NOTES) NotesWidgetProvider::class.java else RemindersWidgetProvider::class.java))

    fun kind(context: Context, id: Int): WidgetKind? {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ?: return null
        return WidgetKind.entries.firstOrNull { value -> provider == ComponentName(context,
            if (value == WidgetKind.NOTES) NotesWidgetProvider::class.java else RemindersWidgetProvider::class.java) }
    }

    @Synchronized fun start(context: Context) {
        val app = context.applicationContext
        if (WidgetKind.entries.all { ids(app, it).isEmpty() }) { stop(app); return }
        if (observation?.isActive == true) return
        settingsPrefs = app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme" || key == "custom_theme") requestRefresh(app)
        }.also { settingsPrefs?.registerOnSharedPreferenceChangeListener(it) }
        observation = scope.launch {
            AppDatabase.get(app).invalidationTracker.createFlow("reminders", "notes", "note_folders", "note_folder_refs")
                .collect { tables ->
                    val kinds = buildSet {
                        if ("reminders" in tables) add(WidgetKind.REMINDERS)
                        if (tables.any { it != "reminders" }) add(WidgetKind.NOTES)
                    }
                    runCatching { refresh(app, kinds) }.onFailure { Log.w("HomeWidgets", "Не удалось обновить виджеты", it) }
                }
        }
        scheduleDayRefresh(app)
    }

    @Synchronized private fun stop(context: Context) {
        observation?.cancel(); observation = null
        settingsListener?.let { settingsPrefs?.unregisterOnSharedPreferenceChangeListener(it) }
        settingsListener = null; settingsPrefs = null
        context.getSystemService(AlarmManager::class.java).cancel(dayPendingIntent(context))
    }

    fun requestRefresh(context: Context, kinds: Set<WidgetKind> = WidgetKind.entries.toSet(), resetScrollId: Int? = null) {
        val app = context.applicationContext
        start(app)
        scope.launch { runCatching { refresh(app, kinds, resetScrollId) }.onFailure { Log.w("HomeWidgets", "Не удалось обновить виджет", it) } }
    }

    @Suppress("DEPRECATION")
    internal suspend fun refresh(context: Context, kinds: Set<WidgetKind> = WidgetKind.entries.toSet(), resetScrollId: Int? = null) = refreshLock.withLock {
        val manager = AppWidgetManager.getInstance(context)
        if (kinds.all { ids(context, it).isEmpty() }) return@withLock
        val database = AppDatabase.get(context)
        val preferences = WidgetPreferences(context)
        val folders = if (WidgetKind.NOTES in kinds) database.notesDao().allFolders() else emptyList()
        kinds.forEach { kind ->
            ids(context, kind).forEach { id ->
                val folderId = if (kind == WidgetKind.NOTES) preferences.folder(id) else null
                val folder = folders.firstOrNull { it.id == folderId }
                val missingFolder = kind == WidgetKind.NOTES && folderId != null && folder == null
                val title = when {
                    kind == WidgetKind.REMINDERS -> context.getString(R.string.widget_today)
                    missingFolder -> "Папка удалена"
                    else -> folder?.name ?: context.getString(R.string.widget_all_notes)
                }
                val reminderRows = if (kind == WidgetKind.REMINDERS) widgetReminderRows(database, preferences, LocalDate.now()) else null
                val count = when {
                    kind == WidgetKind.REMINDERS -> requireNotNull(reminderRows).activeCount
                    missingFolder -> 0
                    else -> database.notesDao().widgetCount(folderId)
                }
                val views = WidgetRenderer.frame(context, id, kind, title, count, folderId, missingFolder, id == resetScrollId,
                    if (kind == WidgetKind.REMINDERS) preferences.undo(id) else null)
                manager.updateAppWidget(id, views)
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            }
        }
    }

    internal fun receive(context: Context, block: suspend () -> Unit, finish: () -> Unit) {
        val app = context.applicationContext
        start(app)
        scope.launch {
            try { block() } catch (error: Exception) { Log.w("HomeWidgets", "Ошибка действия виджета", error) }
            finally { finish() }
        }
    }
    internal fun expireUndo(context: Context, id: Int, token: String) {
        scope.launch {
            delay(10_100)
            val prefs = WidgetPreferences(context)
            if (prefs.undo(id, includeExpired = true)?.token == token) {
                prefs.setUndo(id, null)
                refresh(context, setOf(WidgetKind.REMINDERS))
            }
        }
    }

    internal fun scheduleDayRefresh(context: Context) {
        if (WidgetKind.entries.all { ids(context, it).isEmpty() }) return
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Non-wakeup, inexact refresh: no extra exact-alarm permission or overnight
        // wakeup. Provider updates and clock/date broadcasts are additional recovery paths.
        context.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC, midnight, 10 * 60_000L, dayPendingIntent(context))
    }

    private fun dayPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(context, 901,
        Intent(context, WidgetActionReceiver::class.java).setAction(ACTION_WIDGET_DAY), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

abstract class HomeWidgetProvider(private val widgetKind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { HomeWidgets.requestRefresh(context, setOf(widgetKind)) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        HomeWidgets.requestRefresh(context, setOf(widgetKind))
    }
    override fun onDeleted(context: Context, ids: IntArray) { WidgetPreferences(context).delete(ids); HomeWidgets.start(context) }
    override fun onDisabled(context: Context) { HomeWidgets.start(context) }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        WidgetPreferences(context).restore(oldIds, newIds); HomeWidgets.requestRefresh(context, setOf(widgetKind))
    }
}

class RemindersWidgetProvider : HomeWidgetProvider(WidgetKind.REMINDERS)
class NotesWidgetProvider : HomeWidgetProvider(WidgetKind.NOTES)

internal suspend fun completeWidgetReminder(database: AppDatabase, actions: ReminderActions, id: Long,
    expectedAt: String?, expectedCreatedAt: String): Boolean = completeWidgetReminderWithUndo(database, actions, id, expectedAt, expectedCreatedAt) != null

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        HomeWidgets.receive(app, block = {
            if (intent.action == ACTION_WIDGET_COMPLETE) {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (HomeWidgets.kind(app, widgetId) != WidgetKind.REMINDERS) return@receive
                val id = intent.getLongExtra(EXTRA_ITEM_ID, 0).takeIf { it > 0 } ?: return@receive
                if (!intent.hasExtra(EXTRA_EXPECTED_AT)) return@receive
                val createdAt = intent.getStringExtra(EXTRA_CREATED_AT) ?: return@receive
                val database = AppDatabase.get(app)
                val actions = ReminderActions(RoomReminderRepository(database.reminderDao()), AndroidReminderScheduler(app))
                val undo = completeWidgetReminderWithUndo(database, actions, id, intent.getStringExtra(EXTRA_EXPECTED_AT), createdAt) { receipt ->
                    WidgetPreferences(app).apply { recordCompletion(receipt); setUndo(widgetId, receipt) }
                }
                if (undo != null) {
                    dismissReminderNotification(app, id)
                    HomeWidgets.refresh(app, setOf(WidgetKind.REMINDERS))
                    HomeWidgets.expireUndo(app, widgetId, undo.token)
                }
            } else if (intent.action == ACTION_WIDGET_UNDO) {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val prefs = WidgetPreferences(app)
                val undo = prefs.undo(widgetId, includeExpired = true) ?: return@receive
                if (undo.token != intent.getStringExtra(EXTRA_UNDO_TOKEN)) return@receive
                val database = AppDatabase.get(app)
                val actions = ReminderActions(RoomReminderRepository(database.reminderDao()), AndroidReminderScheduler(app))
                if (undoWidgetReminder(database, actions, undo)) prefs.removeCompletion(undo.token)
                prefs.setUndo(widgetId, null)
                HomeWidgets.refresh(app, setOf(WidgetKind.REMINDERS))
            } else if (intent.action == ACTION_WIDGET_RESTORE) {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (HomeWidgets.kind(app, widgetId) != WidgetKind.REMINDERS) return@receive
                val id = intent.getLongExtra(EXTRA_ITEM_ID, 0).takeIf { it > 0 } ?: return@receive
                val prefs = WidgetPreferences(app)
                val database = AppDatabase.get(app)
                val actions = ReminderActions(RoomReminderRepository(database.reminderDao()), AndroidReminderScheduler(app))
                val token = intent.getStringExtra(EXTRA_UNDO_TOKEN)
                if (token != null) {
                    val snapshot = prefs.completedOccurrences().firstOrNull { it.undo.token == token && it.undo.id == id } ?: return@receive
                    if (undoWidgetReminder(database, actions, snapshot.undo.copy(expiresAt = System.currentTimeMillis() + 10_000))) prefs.removeCompletion(token)
                } else {
                    database.withTransaction {
                        val current = database.reminderDao().byId(id) ?: return@withTransaction
                        if (current.status == "completed" && current.createdAt.toString() == intent.getStringExtra(EXTRA_CREATED_AT) &&
                            current.completedAt?.toString() == intent.getStringExtra(EXTRA_COMPLETED_AT)) actions.setCompleted(id, false)
                    }
                }
                if (prefs.undo(widgetId)?.id == id) prefs.setUndo(widgetId, null)
                HomeWidgets.refresh(app, setOf(WidgetKind.REMINDERS))
            } else {
                HomeWidgets.refresh(app)
                HomeWidgets.scheduleDayRefresh(app)
            }
        }, finish = pending::finish)
    }
}

internal const val ACTION_WIDGET_COMPLETE = "ru.nezabudka.app.widget.COMPLETE"
internal const val ACTION_WIDGET_DAY = "ru.nezabudka.app.widget.DAY"
internal const val ACTION_WIDGET_UNDO = "ru.nezabudka.app.widget.UNDO"
internal const val EXTRA_UNDO_TOKEN = "widget_undo_token"

internal const val ACTION_WIDGET_RESTORE = "ru.nezabudka.app.widget.RESTORE"

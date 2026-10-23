package ru.nezabudka.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.ReminderEntity
import ru.nezabudka.app.data.WidgetNoteRow
import java.time.LocalDate

class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = WidgetRowsFactory(applicationContext, intent)
}

internal class WidgetRowsFactory(private val context: Context, intent: Intent) : RemoteViewsService.RemoteViewsFactory {
    private val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    private val kind = runCatching { WidgetKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrDefault(WidgetKind.NOTES)
    @Volatile private var reminders: List<WidgetReminderRow> = emptyList()
    @Volatile private var notes: List<WidgetNoteRow> = emptyList()
    private var palette = WidgetPalette.read(context)
    private var today = LocalDate.now()
    private var compact = true
    override fun onCreate() { load() }
    override fun onDataSetChanged() { load() }
    private fun load() = runBlocking {
        today = LocalDate.now()
        palette = WidgetPalette.read(context)
        compact = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180) < 230
        if (!widgetFeatureEnabled(context, kind)) {
            reminders = emptyList(); notes = emptyList()
            return@runBlocking
        }
        val database = AppDatabase.get(context)
        if (kind == WidgetKind.REMINDERS) reminders = widgetReminderRows(database, WidgetPreferences(context), today).rows
        else {
            val folder = WidgetPreferences(context).folder(id)
            val exists = folder == null || database.notesDao().allFolders().any { it.id == folder }
            notes = if (exists) database.notesDao().widgetRows(folder) else emptyList()
        }
    }
    override fun onDestroy() { reminders = emptyList(); notes = emptyList() }
    override fun getCount(): Int = if (kind == WidgetKind.REMINDERS) reminders.size else notes.size
    override fun getViewAt(position: Int): RemoteViews? = if (kind == WidgetKind.REMINDERS) {
        reminders.getOrNull(position)?.let { WidgetRenderer.reminderRow(context, it.item, palette, compact, today, it.completed, it.token) }
    } else notes.getOrNull(position)?.let { WidgetRenderer.noteRow(context, it, palette, compact, today) }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = if (kind == WidgetKind.REMINDERS) reminders.getOrNull(position)?.stableId ?: -1 else notes.getOrNull(position)?.id ?: -1
    override fun hasStableIds(): Boolean = true
}

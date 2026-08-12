package ru.nezabudka.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.nezabudka.app.MainActivity
import ru.nezabudka.app.R

const val ACTION_QUICK_VOICE_NOTE = "ru.nezabudka.app.action.QUICK_VOICE_NOTE"

class VoiceNoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            val openVoiceNote = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_VOICE_NOTE
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                openVoiceNote,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.voice_note_widget).apply {
                setOnClickPendingIntent(R.id.voice_note_widget, pendingIntent)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}

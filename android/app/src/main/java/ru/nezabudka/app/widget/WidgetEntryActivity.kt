package ru.nezabudka.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle

/** NoDisplay entry for collection clicks. It always finishes in onCreate. */
class WidgetEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val kind = HomeWidgets.kind(this, widgetId) ?: return
            val item = intent.getLongExtra(EXTRA_ITEM_ID, 0).takeIf { it > 0 } ?: return
            when (intent.getStringExtra(EXTRA_ROW_ACTION)) {
                ROW_COMPLETE -> if (kind == WidgetKind.REMINDERS) {
                    sendBroadcast(Intent(this, WidgetActionReceiver::class.java).setAction(ACTION_WIDGET_COMPLETE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        .putExtra(EXTRA_ITEM_ID, item).putExtra(EXTRA_EXPECTED_AT, intent.getStringExtra(EXTRA_EXPECTED_AT))
                        .putExtra(EXTRA_CREATED_AT, intent.getStringExtra(EXTRA_CREATED_AT)))
                }
                ROW_RESTORE -> if (kind == WidgetKind.REMINDERS) {
                    sendBroadcast(Intent(intent).setClass(this, WidgetActionReceiver::class.java).setAction(ACTION_WIDGET_RESTORE))
                }
                ROW_OPEN -> startActivity(WidgetLaunchIntent.create(this,
                    WidgetLaunch(if (kind == WidgetKind.NOTES) WidgetDestination.NOTE else WidgetDestination.REMINDER,
                        if (kind == WidgetKind.NOTES) WidgetPreferences(this).folder(widgetId) else null, item), "$widgetId/item/$item"))
            }
        } finally { finish() }
    }
}

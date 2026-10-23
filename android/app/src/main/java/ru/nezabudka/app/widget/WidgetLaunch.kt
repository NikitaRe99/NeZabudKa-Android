package ru.nezabudka.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import ru.nezabudka.app.MainActivity

enum class WidgetDestination { REMINDERS, NOTES, NOTE, REMINDER, NEW_NOTE, NEW_REMINDER, SETTINGS }

data class WidgetLaunch(
    val destination: WidgetDestination,
    val folderId: Long? = null,
    val itemId: Long? = null,
    val deliveryId: Long = SystemClock.elapsedRealtimeNanos(),
)

object WidgetLaunchIntent {
    private const val ACTION = "ru.nezabudka.app.widget.OPEN"
    private const val DESTINATION = "widget_destination"
    private const val FOLDER = "widget_folder"
    private const val ITEM = "widget_item"

    fun create(context: Context, launch: WidgetLaunch, identity: String): Intent =
        Intent(context, MainActivity::class.java).setAction(ACTION)
            .setData(Uri.parse("nezabudka-widget://open/$identity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(DESTINATION, launch.destination.name)
            .putExtra(FOLDER, launch.folderId ?: 0L)
            .putExtra(ITEM, launch.itemId ?: 0L)

    fun consume(intent: Intent?): WidgetLaunch? {
        if (intent?.action != ACTION) return null
        val destination = runCatching { WidgetDestination.valueOf(intent.getStringExtra(DESTINATION).orEmpty()) }.getOrNull()
            ?: return null
        val launch = WidgetLaunch(destination,
            intent.getLongExtra(FOLDER, 0L).takeIf { it > 0 }, intent.getLongExtra(ITEM, 0L).takeIf { it > 0 })
        intent.removeExtra(DESTINATION)
        return launch
    }
}

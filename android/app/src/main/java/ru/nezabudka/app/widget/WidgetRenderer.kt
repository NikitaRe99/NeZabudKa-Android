package ru.nezabudka.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import ru.nezabudka.app.R
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.data.ReminderEntity
import ru.nezabudka.app.data.WidgetNoteRow
import ru.nezabudka.app.domain.defaultCustomTheme
import ru.nezabudka.app.domain.reminderRecurrenceDescription
import ru.nezabudka.app.domain.withAutomaticDependentColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class WidgetKind { REMINDERS, NOTES }

internal data class WidgetPalette(
    val card: Int, val text: Int, val secondary: Int, val accent: Int, val outline: Int, val selected: Int,
    val dark: Boolean, val scale: Float,
) {
    companion object {
        fun read(context: Context): WidgetPalette {
            val settings = SettingsStore(context).state.value
            val dark = settings.theme == "dark" || settings.theme == "custom" && settings.customTheme.base == "dark"
            val theme = (if (settings.theme == "custom") settings.customTheme else defaultCustomTheme(if (dark) "dark" else "light"))
                .withAutomaticDependentColors()
            return WidgetPalette(Color.parseColor(theme.card), Color.parseColor(theme.textPrimary), Color.parseColor(theme.textSecondary),
                Color.parseColor(theme.accent), Color.parseColor(theme.outline), Color.parseColor(theme.selected), dark,
                if (settings.theme == "custom") settings.customTheme.fontScale else 1f)
        }
    }
}

internal object WidgetRenderer {
    @Suppress("DEPRECATION")
    fun frame(context: Context, id: Int, kind: WidgetKind, title: String, count: Int,
              folderId: Long?, missingFolder: Boolean = false, resetScroll: Boolean = false, undo: WidgetUndo? = null): RemoteViews {
        val palette = WidgetPalette.read(context)
        val notes = kind == WidgetKind.NOTES
        val result = RemoteViews(context.packageName, R.layout.home_widget)
        result.setInt(R.id.widget_root, "setBackgroundResource", if (palette.dark) R.drawable.widget_card_dark else R.drawable.widget_card_light)
        result.setInt(R.id.widget_count, "setBackgroundResource", if (palette.dark) R.drawable.widget_count_dark else R.drawable.widget_count_light)
        if (Build.VERSION.SDK_INT >= 31) {
            result.setColorStateList(R.id.widget_root, "setBackgroundTintList", ColorStateList.valueOf(palette.card))
            result.setColorStateList(R.id.widget_count, "setBackgroundTintList", ColorStateList.valueOf(palette.selected))
        }
        result.setTextViewText(R.id.widget_title, title)
        result.setTextViewText(R.id.widget_count, count.toString())
        result.setViewVisibility(R.id.widget_count, if (notes) View.GONE else View.VISIBLE)
        result.setViewVisibility(R.id.widget_folder_chevron, if (notes) View.VISIBLE else View.GONE)
        result.setImageViewResource(R.id.widget_kind_icon, if (notes) R.drawable.ic_home_widget_folder else R.drawable.ic_home_widget_bell)
        listOf(R.id.widget_title, R.id.widget_count).forEach { result.setTextColor(it, palette.accent) }
        result.setTextColor(R.id.widget_empty, palette.secondary)
        listOf(R.id.widget_kind_icon, R.id.widget_add, R.id.widget_folder_chevron, R.id.widget_undo).forEach {
            result.setInt(it, "setColorFilter", palette.accent)
        }
        result.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 14 * palette.scale)
        result.setTextViewTextSize(R.id.widget_count, TypedValue.COMPLEX_UNIT_SP, 11 * palette.scale)
        result.setTextViewTextSize(R.id.widget_empty, TypedValue.COMPLEX_UNIT_SP, 11 * palette.scale)
        result.setViewVisibility(R.id.widget_undo, if (!notes && undo != null) View.VISIBLE else View.GONE)
        if (!notes && undo != null) result.setOnClickPendingIntent(R.id.widget_undo, PendingIntent.getBroadcast(context, id,
            Intent(context, WidgetActionReceiver::class.java).setAction(ACTION_WIDGET_UNDO)
                .setData(Uri.parse("nezabudka-widget://undo/$id")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_UNDO_TOKEN, undo.token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        result.setTextViewText(R.id.widget_empty, context.getString(when {
            missingFolder -> R.string.widget_folder_deleted
            notes -> R.string.widget_notes_empty
            else -> R.string.widget_today_empty
        }))
        result.setContentDescription(R.id.widget_add, context.getString(if (notes) R.string.widget_add_note else R.string.widget_add_reminder))
        val destination = if (notes) WidgetDestination.NOTES else WidgetDestination.REMINDERS
        val open = mainPendingIntent(context, id, "open", WidgetLaunch(destination, folderId))
        result.setOnClickPendingIntent(R.id.widget_folder_picker, open)
        result.setOnClickPendingIntent(R.id.widget_kind_icon, open)
        result.setContentDescription(R.id.widget_folder_picker, if (notes) "Открыть папку: $title" else "Открыть напоминания на сегодня")
        result.setOnClickPendingIntent(R.id.widget_add, mainPendingIntent(context, id, "add",
            WidgetLaunch(if (notes) WidgetDestination.NEW_NOTE else WidgetDestination.NEW_REMINDER, folderId)))
        if (notes) {
            result.setContentDescription(R.id.widget_folder_chevron, context.getString(R.string.widget_choose_folder))
            result.setOnClickPendingIntent(R.id.widget_folder_chevron, PendingIntent.getActivity(context, id,
                Intent(context, WidgetFolderActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("nezabudka-widget://folder/$id")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        result.setRemoteAdapter(R.id.widget_list, Intent(context, WidgetListService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_KIND, kind.name)
            .setData(Uri.parse("nezabudka-widget://list/${kind.name}/$id")))
        result.setEmptyView(R.id.widget_list, R.id.widget_empty)
        // A collection has one PendingIntent template. An invisible, immediately
        // finishing entry activity routes text clicks and check circles separately.
        // The completion broadcast holds the process while the database is updated.
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        result.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(context, id,
            Intent(context, WidgetEntryActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_KIND, kind.name).setData(Uri.parse("nezabudka-widget://row/${kind.name}/$id")),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable))
        if (resetScroll) result.setScrollPosition(R.id.widget_list, 0)
        return result
    }

    private fun mainPendingIntent(context: Context, id: Int, action: String, launch: WidgetLaunch): PendingIntent =
        PendingIntent.getActivity(context, id, WidgetLaunchIntent.create(context, launch, "$id/$action"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun reminderRow(context: Context, item: ReminderEntity, palette: WidgetPalette, compact: Boolean, today: LocalDate, completed: Boolean = false, token: String? = null): RemoteViews {
        val at = item.remindAt
        val time = at?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Без времени"
        val detail = buildString {
            if (completed) append("Выполнено · ")
            else if (at?.toLocalDate()?.isBefore(today) == true) append("Просрочено · ")
            append(time)
            if (!completed && item.recurrence != null) append(if (compact) " · ↻" else " · ${reminderRecurrenceDescription(item.recurrence)}")
        }
        return row(context, item.text.take(500), detail, palette).apply {
            if (completed) {
                setImageViewResource(R.id.widget_row_complete, R.drawable.ic_home_widget_done)
                setInt(R.id.widget_row_title, "setPaintFlags", android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG)
                setTextColor(R.id.widget_row_title, palette.secondary)
            }
            setContentDescription(R.id.widget_row_complete, if (completed) "Вернуть: ${item.text}" else "Выполнить: ${item.text}")
            setOnClickFillInIntent(R.id.widget_row_complete, Intent().putExtra(EXTRA_ROW_ACTION, if (completed) ROW_RESTORE else ROW_COMPLETE)
                .putExtra(EXTRA_ITEM_ID, item.id).putExtra(EXTRA_EXPECTED_AT, item.remindAt.toString())
                .putExtra(EXTRA_CREATED_AT, item.createdAt.toString()).putExtra(EXTRA_COMPLETED_AT, item.completedAt?.toString())
                .putExtra(EXTRA_UNDO_TOKEN, token))
            setOnClickFillInIntent(R.id.widget_row_open, Intent().putExtra(EXTRA_ROW_ACTION, ROW_OPEN).putExtra(EXTRA_ITEM_ID, item.id))
        }
    }

    fun noteRow(context: Context, item: WidgetNoteRow, palette: WidgetPalette, compact: Boolean, today: LocalDate): RemoteViews =
        row(context, widgetNoteTitle(item.title, item.text),
            if (compact) widgetNoteSnippet(item.title, item.text).ifBlank { widgetDate(item.updatedAt, today) } else widgetDate(item.updatedAt, today), palette).apply {
            setViewVisibility(R.id.widget_row_complete, View.GONE)
            setOnClickFillInIntent(R.id.widget_row_open, Intent().putExtra(EXTRA_ROW_ACTION, ROW_OPEN).putExtra(EXTRA_ITEM_ID, item.id))
        }

    private fun row(context: Context, title: String, detail: String, palette: WidgetPalette): RemoteViews =
        RemoteViews(context.packageName, R.layout.home_widget_row).apply {
            setInt(R.id.widget_row_title, "setPaintFlags", android.graphics.Paint.ANTI_ALIAS_FLAG)
            setImageViewResource(R.id.widget_row_complete, R.drawable.ic_home_widget_circle)
            setTextViewText(R.id.widget_row_title, title)
            setTextViewText(R.id.widget_row_detail, detail)
            setTextColor(R.id.widget_row_title, palette.text)
            setTextColor(R.id.widget_row_detail, palette.secondary)
            setInt(R.id.widget_row_complete, "setColorFilter", palette.accent)
            setInt(R.id.widget_row_divider, "setBackgroundColor", palette.outline)
            setTextViewTextSize(R.id.widget_row_title, TypedValue.COMPLEX_UNIT_SP, 13 * palette.scale)
            setTextViewTextSize(R.id.widget_row_detail, TypedValue.COMPLEX_UNIT_SP, 10.5f * palette.scale)
        }
}

internal const val EXTRA_KIND = "widget_kind"
internal const val EXTRA_ROW_ACTION = "widget_row_action"
internal const val EXTRA_ITEM_ID = "widget_row_item"
internal const val EXTRA_EXPECTED_AT = "widget_expected_at"
internal const val EXTRA_CREATED_AT = "widget_created_at"
internal const val ROW_COMPLETE = "complete"
internal const val ROW_OPEN = "open"

internal const val ROW_RESTORE = "restore"
internal const val EXTRA_COMPLETED_AT = "widget_completed_at"

package ru.nezabudka.app.widget

import ru.nezabudka.app.domain.Reminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun widgetNoteTitle(title: String, text: String): String =
    title.trim().ifBlank { text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
        .ifBlank { "Без названия" }.take(120)

internal fun widgetNoteSnippet(title: String, text: String): String {
    val lines = text.lineSequence().filter(String::isNotBlank).toList()
    if (title.isBlank() && lines.size <= 1 || title.trim() == text.trim()) return ""
    val body = if (title.isBlank()) lines.drop(1).joinToString(" ") else text
    return body.replace(Regex("\\s+"), " ").trim().take(180)
}

internal fun widgetDate(value: LocalDateTime, today: LocalDate = LocalDate.now()): String = when (value.toLocalDate()) {
    today -> "Сегодня, ${value.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    today.minusDays(1) -> "Вчера, ${value.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    else -> value.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")))
}

internal fun isWidgetTodayReminder(reminder: Reminder, today: LocalDate): Boolean =
    reminder.status == "active" && reminder.remindAt?.isBefore(today.plusDays(1).atStartOfDay()) == true

internal fun widgetCompletionMatches(reminder: Reminder, expectedAt: String?, expectedCreatedAt: String): Boolean =
    reminder.status == "active" && reminder.remindAt?.toString() == expectedAt && reminder.createdAt.toString() == expectedCreatedAt

internal fun widgetNoteCount(count: Int): String = "$count " + when {
    count % 100 in 11..14 -> "заметок"
    count % 10 == 1 -> "заметка"
    count % 10 in 2..4 -> "заметки"
    else -> "заметок"
}

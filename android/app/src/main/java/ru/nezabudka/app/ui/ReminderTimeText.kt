package ru.nezabudka.app.ui

import ru.nezabudka.app.domain.Reminder
import java.time.Duration
import java.time.LocalDateTime

internal fun Reminder.isOverdueAt(now: LocalDateTime): Boolean =
    status != "completed" && remindAt?.isBefore(now) == true

internal fun overdueReminderText(remindAt: LocalDateTime, now: LocalDateTime): String {
    val minutes = Duration.between(remindAt, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < MINUTES_PER_SHORT_BUCKET -> "Меньше 5 минут назад"
        minutes < MINUTES_PER_HOUR -> {
            val bucket = minutes / MINUTES_PER_SHORT_BUCKET * MINUTES_PER_SHORT_BUCKET
            ago(bucket, "минуту", "минуты", "минут")
        }
        minutes < MINUTES_PER_DAY -> ago(minutes / MINUTES_PER_HOUR, "час", "часа", "часов")
        minutes < MINUTES_PER_WEEK -> ago(minutes / MINUTES_PER_DAY, "день", "дня", "дней")
        minutes < MINUTES_PER_MONTH -> ago(minutes / MINUTES_PER_WEEK, "неделю", "недели", "недель")
        minutes < MINUTES_PER_YEAR -> ago(minutes / MINUTES_PER_MONTH, "месяц", "месяца", "месяцев")
        else -> ago(minutes / MINUTES_PER_YEAR, "год", "года", "лет")
    }
}

internal fun nextReminderUiRefreshDelayMillis(reminders: List<Reminder>, now: LocalDateTime): Long {
    val nextChange = reminders.asSequence()
        .filter { it.status != "completed" }
        .mapNotNull(Reminder::remindAt)
        .map { remindAt ->
            if (!remindAt.isBefore(now)) {
                remindAt
            } else {
                val minutes = Duration.between(remindAt, now).toMinutes().coerceAtLeast(0L)
                when {
                    minutes < MINUTES_PER_HOUR -> remindAt.plusMinutes(
                        (minutes / MINUTES_PER_SHORT_BUCKET + 1L) * MINUTES_PER_SHORT_BUCKET,
                    )
                    minutes < MINUTES_PER_DAY -> remindAt.plusHours(minutes / MINUTES_PER_HOUR + 1L)
                    minutes < MINUTES_PER_WEEK -> remindAt.plusDays(minutes / MINUTES_PER_DAY + 1L)
                    minutes < MINUTES_PER_MONTH -> remindAt.plusWeeks(minutes / MINUTES_PER_WEEK + 1L)
                    minutes < MINUTES_PER_YEAR -> remindAt.plusMonths(minutes / MINUTES_PER_MONTH + 1L)
                    else -> remindAt.plusYears(minutes / MINUTES_PER_YEAR + 1L)
                }
            }
        }
        .minOrNull()
        ?: return MAX_UI_REFRESH_DELAY_MILLIS
    return Duration.between(now, nextChange).toMillis()
        .coerceIn(MIN_UI_REFRESH_DELAY_MILLIS, MAX_UI_REFRESH_DELAY_MILLIS)
}

private fun ago(value: Long, one: String, few: String, many: String): String {
    val modulo100 = value % 100
    val form = when {
        modulo100 in 11..14 -> many
        value % 10 == 1L -> one
        value % 10 in 2..4 -> few
        else -> many
    }
    return "$value $form назад"
}

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_SHORT_BUCKET = 5L
private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
private const val MINUTES_PER_WEEK = 7L * MINUTES_PER_DAY
private const val MINUTES_PER_MONTH = 30L * MINUTES_PER_DAY
private const val MINUTES_PER_YEAR = 365L * MINUTES_PER_DAY
private const val MIN_UI_REFRESH_DELAY_MILLIS = 250L
private const val MAX_UI_REFRESH_DELAY_MILLIS = 24L * 60L * 60L * 1_000L

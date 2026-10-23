package ru.nezabudka.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class ReminderRecurrence(val storedValue: String) {
    DAILY("daily"),
    WEEKDAYS("weekdays"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly");

    companion object {
        fun fromStored(value: String?): ReminderRecurrence? = when (value?.trim()?.lowercase()) {
            "daily", "day", "every_day", "ежедневно", "каждый день" -> DAILY
            "weekdays", "weekday", "рабочие дни", "по будням" -> WEEKDAYS
            "weekly", "week", "every_week", "еженедельно", "каждую неделю" -> WEEKLY
            "monthly", "month", "every_month", "ежемесячно", "каждый месяц" -> MONTHLY
            "yearly", "annual", "year", "every_year", "ежегодно", "каждый год" -> YEARLY
            else -> null
        }
    }
}

enum class ReminderRecurrenceUnit(val storedValue: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    companion object {
        fun fromStored(value: String?): ReminderRecurrenceUnit? =
            entries.firstOrNull { it.storedValue == value }
    }
}

/**
 * Versioned representation of a user-configured repeat rule. It is kept in the
 * existing recurrence String column so older reminders and backups stay valid.
 */
data class CustomReminderRecurrence(
    val interval: Int,
    val unit: ReminderRecurrenceUnit,
    val weekdays: Set<DayOfWeek>,
    val start: LocalDateTime,
    val endDate: LocalDate? = null,
    val repeatCount: Int? = null,
) {
    fun storedValue(): String = buildString {
        append(CUSTOM_PREFIX)
        append(";interval=")
        append(interval)
        append(";unit=")
        append(unit.storedValue)
        append(";start=")
        append(start)
        if (weekdays.isNotEmpty()) {
            append(";days=")
            append(weekdays.sortedBy(DayOfWeek::getValue).joinToString(",") { it.value.toString() })
        }
        endDate?.let {
            append(";until=")
            append(it)
        }
        repeatCount?.let {
            append(";count=")
            append(it)
        }
    }

    companion object {
        private const val CUSTOM_PREFIX = "custom:v1"

        fun fromStored(value: String?): CustomReminderRecurrence? {
            val parts = value?.trim()?.split(';') ?: return null
            if (parts.firstOrNull()?.lowercase() != CUSTOM_PREFIX) return null
            val fields = parts.drop(1).mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
            }.toMap()
            val interval = fields["interval"]?.toIntOrNull()?.takeIf { it in 1..999 } ?: return null
            val unit = ReminderRecurrenceUnit.fromStored(fields["unit"]) ?: return null
            val start = fields["start"]?.let {
                runCatching { LocalDateTime.parse(it) }.getOrNull()
            } ?: return null
            val weekdays = fields["days"].orEmpty()
                .split(',')
                .filter(String::isNotBlank)
                .mapNotNull { it.toIntOrNull()?.takeIf { day -> day in 1..7 }?.let(DayOfWeek::of) }
                .toSet()
            val endDate = fields["until"]?.let {
                runCatching { LocalDate.parse(it) }.getOrNull() ?: return null
            }
            val repeatCount = fields["count"]?.let {
                it.toIntOrNull()?.takeIf { count -> count in 1..999 } ?: return null
            }
            if (endDate != null && repeatCount != null) return null
            return CustomReminderRecurrence(interval, unit, weekdays, start, endDate, repeatCount)
        }
    }
}

fun normalizeReminderRecurrence(value: String?): String? =
    ReminderRecurrence.fromStored(value)?.storedValue
        ?: CustomReminderRecurrence.fromStored(value)?.storedValue()

/** The first alarm must obey the selected weekdays too, not only subsequent repeats. */
fun alignReminderOccurrence(date: LocalDateTime, recurrence: String?): LocalDateTime {
    if (ReminderRecurrence.fromStored(recurrence) == ReminderRecurrence.WEEKDAYS) {
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date.plusDays(2)
            DayOfWeek.SUNDAY -> date.plusDays(1)
            else -> date
        }
    }
    val rule = CustomReminderRecurrence.fromStored(recurrence) ?: return date
    if (rule.unit != ReminderRecurrenceUnit.WEEK || rule.weekdays.isEmpty()) return date
    val anchor = rule.start.toLocalDate().minusDays((rule.start.dayOfWeek.value - 1).toLong())
    var candidate = maxOf(date, rule.start)
    repeat(7 * 1000) {
        val week = candidate.toLocalDate().minusDays((candidate.dayOfWeek.value - 1).toLong())
        if (candidate.dayOfWeek in rule.weekdays && ChronoUnit.WEEKS.between(anchor, week) % rule.interval == 0L) return candidate
        candidate = candidate.plusDays(1)
    }
    return date
}

fun nextReminderOccurrence(
    current: LocalDateTime,
    recurrence: String?,
    after: LocalDateTime = current,
): LocalDateTime? {
    val rule = ReminderRecurrence.fromStored(recurrence)
    if (rule == null) {
        return CustomReminderRecurrence.fromStored(recurrence)?.nextOccurrence(after)
    }
    var candidate = current
    repeat(100_000) {
        candidate = when (rule) {
            ReminderRecurrence.DAILY -> candidate.plusDays(1)
            ReminderRecurrence.WEEKDAYS -> candidate.plusDays(1).let { next ->
                when (next.dayOfWeek) {
                    DayOfWeek.SATURDAY -> next.plusDays(2)
                    DayOfWeek.SUNDAY -> next.plusDays(1)
                    else -> next
                }
            }
            ReminderRecurrence.WEEKLY -> candidate.plusWeeks(1)
            ReminderRecurrence.MONTHLY -> candidate.plusMonths(1)
            ReminderRecurrence.YEARLY -> candidate.plusYears(1)
        }
        if (candidate.isAfter(after)) return candidate
    }
    return null
}

private fun CustomReminderRecurrence.nextOccurrence(after: LocalDateTime): LocalDateTime? {
    var candidate = start
    repeat(100_000) { zeroBasedIndex ->
        if (repeatCount != null && zeroBasedIndex >= repeatCount) return null
        candidate = nextCandidate(candidate)
        if (endDate != null && candidate.toLocalDate().isAfter(endDate)) return null
        if (candidate.isAfter(after)) return candidate
    }
    return null
}

private fun CustomReminderRecurrence.nextCandidate(previous: LocalDateTime): LocalDateTime = when (unit) {
    ReminderRecurrenceUnit.DAY -> previous.plusDays(interval.toLong())
    ReminderRecurrenceUnit.WEEK -> {
        if (weekdays.isEmpty()) {
            previous.plusWeeks(interval.toLong())
        } else {
            nextSelectedWeekday(previous)
        }
    }
    ReminderRecurrenceUnit.MONTH -> previous.plusMonths(interval.toLong())
    ReminderRecurrenceUnit.YEAR -> previous.plusYears(interval.toLong())
}

private fun CustomReminderRecurrence.nextSelectedWeekday(previous: LocalDateTime): LocalDateTime {
    val anchorWeek = start.toLocalDate().minusDays((start.dayOfWeek.value - 1).toLong())
    var date = previous.toLocalDate().plusDays(1)
    repeat(100_000) {
        val candidateWeek = date.minusDays((date.dayOfWeek.value - 1).toLong())
        val weekIndex = ChronoUnit.WEEKS.between(anchorWeek, candidateWeek)
        if (weekIndex >= 0 && weekIndex % interval == 0L && date.dayOfWeek in weekdays) {
            return date.atTime(start.toLocalTime())
        }
        date = date.plusDays(1)
    }
    return previous.plusWeeks(interval.toLong())
}

package ru.nezabudka.app.domain

import ru.nezabudka.app.domain.CustomReminderRecurrence
import ru.nezabudka.app.domain.ReminderRecurrence
import ru.nezabudka.app.domain.ReminderRecurrenceUnit

fun reminderRecurrenceDescription(value: String?): String {
    return when (ReminderRecurrence.fromStored(value)) {
        ReminderRecurrence.DAILY -> "Каждый день"
        ReminderRecurrence.WEEKDAYS -> "По будням"
        ReminderRecurrence.WEEKLY -> "Каждую неделю"
        ReminderRecurrence.MONTHLY -> "Каждый месяц"
        ReminderRecurrence.YEARLY -> "Каждый год"
        null -> CustomReminderRecurrence.fromStored(value)?.label() ?: "Не повторять"
    }
}

private fun CustomReminderRecurrence.label(): String {
    val base = when (unit) {
        ReminderRecurrenceUnit.DAY ->
            if (interval == 1) "Каждый день" else "Каждые $interval ${plural(interval, "день", "дня", "дней")}"
        ReminderRecurrenceUnit.WEEK -> {
            val intervalLabel =
                if (interval == 1) "Каждую неделю" else "Каждые $interval ${plural(interval, "неделю", "недели", "недель")}"
            if (weekdays.isEmpty()) {
                intervalLabel
            } else {
                val days = weekdays.sortedBy { it.value }.joinToString(", ") {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[it.value - 1]
                }
                "$intervalLabel: $days"
            }
        }
        ReminderRecurrenceUnit.MONTH ->
            if (interval == 1) "Каждый месяц" else "Каждые $interval ${plural(interval, "месяц", "месяца", "месяцев")}"
        ReminderRecurrenceUnit.YEAR ->
            if (interval == 1) "Каждый год" else "Каждые $interval ${plural(interval, "год", "года", "лет")}"
    }
    return when {
        endDate != null -> "$base, до $endDate"
        repeatCount != null -> "$base, $repeatCount ${plural(repeatCount, "повтор", "повтора", "повторов")}"
        else -> base
    }
}

private fun plural(value: Int, one: String, few: String, many: String): String {
    val lastTwo = value % 100
    val last = value % 10
    return if (lastTwo in 11..14) many else when (last) {
        1 -> one
        in 2..4 -> few
        else -> many
    }
}

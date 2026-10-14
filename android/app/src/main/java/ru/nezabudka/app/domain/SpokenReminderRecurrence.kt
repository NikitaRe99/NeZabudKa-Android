package ru.nezabudka.app.domain

import java.time.DayOfWeek
import java.time.LocalDateTime

data class RecurringTemporalResult(val temporal: TemporalResult, val recurrence: String?)

/** Parse repeat language before calendar parsing, so recurring weekdays are not single dates. */
fun parseRecurringReminder(text: String, now: LocalDateTime, temporal: RussianTemporalParser): RecurringTemporalResult {
    val repeats = REPEAT_EXPRESSION.findAll(text).toList()
    val timeRepeats = repeats.filter { recurringNamedTime(it.value) != null }
    val calendarRepeats = repeats.filter { recurringNamedTime(it.value) == null }
    if (timeRepeats.size > 1 || calendarRepeats.size > 1) {
        throw CommandValidationException("Укажите одно правило повтора. Для утра и вечера создайте два напоминания.")
    }
    val repeat = calendarRepeats.firstOrNull() ?: timeRepeats.firstOrNull() ?: run {
        if (Regex("(?iu)(?<![\\p{L}])кажд(?:ые|ую|ый|ое)(?![\\p{L}])").containsMatchIn(text)) {
            throw CommandValidationException("Не удалось определить повтор. Укажите дни, недели, месяцы или годы; например, «каждые три дня»")
        }
        return RecurringTemporalResult(temporal.parse(text, now), null)
    }
    val expression = repeat.value.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")
    val days = WEEKDAY_WORD.findAll(expression).map { weekday(it.value) }.toSet()
    val count = REPEAT_COUNT.find(text)
    val until = REPEAT_UNTIL.find(text)
    if (count != null && until != null) throw CommandValidationException("Укажите либо число повторов, либо дату окончания")
    var clean = REPEAT_EXPRESSION.replace(text) { recurringNamedTime(it.value) ?: " " }
    count?.value?.let { clean = clean.replace(it, " ") }
    until?.value?.let { clean = clean.replace(it, " ") }
    val parsed = temporal.parse(clean, now)
    val interval = INTERVAL.find(expression)?.groupValues?.get(1)?.let(::spokenRepeatNumber) ?: 1
    val unit = when {
        days.isNotEmpty() || expression.contains("недел") -> ReminderRecurrenceUnit.WEEK
        expression.contains("месяц") || expression == "ежемесячно" -> ReminderRecurrenceUnit.MONTH
        expression.contains("год") || expression.contains("лет") -> ReminderRecurrenceUnit.YEAR
        else -> ReminderRecurrenceUnit.DAY
    }
    val weekdays = if (expression.contains("будн") || expression.contains("рабочи")) {
        DayOfWeek.entries.filter { it.value <= 5 }.toSet()
    } else if (expression.contains("выходным")) setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) else days
    val start = parsed.remindAt?.let { alignRepeatStart(it, weekdays) }
    val requestedCount = count?.groupValues?.get(1)?.let(::spokenRepeatNumber)
    val repeatCount = requestedCount?.let { if (count.value.lowercase().startsWith("всего")) it - 1 else it }
    val end = until?.groupValues?.get(1)?.let { temporal.extractDate(it, now) }
    if (until != null && end == null) throw CommandValidationException("Не удалось определить дату окончания повторов")
    if (end != null && start != null && end.isBefore(start.toLocalDate())) throw CommandValidationException("Дата окончания раньше первого напоминания")
    val simple = if (interval == 1 && count == null && until == null) when {
        expression.contains("будн") || expression.contains("рабочи") -> ReminderRecurrence.WEEKDAYS
        weekdays.isNotEmpty() -> null
        unit == ReminderRecurrenceUnit.DAY -> ReminderRecurrence.DAILY
        unit == ReminderRecurrenceUnit.WEEK -> ReminderRecurrence.WEEKLY
        unit == ReminderRecurrenceUnit.MONTH -> ReminderRecurrence.MONTHLY
        else -> ReminderRecurrence.YEARLY
    } else null
    // With no date this is only a draft held by the clarification; reanchor before dispatch.
    val recurrence = if (repeatCount == 0) null else simple?.storedValue ?: CustomReminderRecurrence(interval, if (weekdays.isNotEmpty()) ReminderRecurrenceUnit.WEEK else unit, weekdays, start ?: now, end, repeatCount).storedValue()
    return RecurringTemporalResult(parsed.copy(remindAt = start), recurrence)
}

fun reanchorSpokenRecurrence(value: String?, start: LocalDateTime): Pair<LocalDateTime, String?> {
    val custom = CustomReminderRecurrence.fromStored(value)
    val weekdays = custom?.weekdays ?: if (ReminderRecurrence.fromStored(value) == ReminderRecurrence.WEEKDAYS) DayOfWeek.entries.filter { it.value <= 5 }.toSet() else emptySet()
    val aligned = alignRepeatStart(start, weekdays)
    if (custom?.endDate?.isBefore(aligned.toLocalDate()) == true) throw CommandValidationException("Дата окончания раньше первого напоминания")
    return aligned to (custom?.copy(start = aligned)?.storedValue() ?: value)
}

private fun alignRepeatStart(start: LocalDateTime, days: Set<DayOfWeek>): LocalDateTime {
    if (days.isEmpty()) return start
    return (0L..6L).map(start::plusDays).first { it.dayOfWeek in days }
}

private fun recurringNamedTime(expression: String): String? = when (expression.lowercase().replace(Regex("\\s+"), " ")) {
    "каждое утро", "по утрам" -> "утром"
    "каждый вечер", "по вечерам" -> "вечером"
    "каждую ночь", "по ночам" -> "ночью"
    else -> null
}

private fun spokenRepeatNumber(text: String): Int {
    val value = text.toIntOrNull() ?: text.split(Regex("\\s+")).map { REPEAT_VALUES[it] ?: throw CommandValidationException("Не удалось определить число повторов: $text") }.sum()
    return value.takeIf { it in 1..999 } ?: throw CommandValidationException("Число повторов должно быть от 1 до 999")
}

private fun weekday(word: String): DayOfWeek = when {
    word.startsWith("понедельник") -> DayOfWeek.MONDAY
    word.startsWith("вторник") -> DayOfWeek.TUESDAY
    word.startsWith("сред") -> DayOfWeek.WEDNESDAY
    word.startsWith("четверг") -> DayOfWeek.THURSDAY
    word.startsWith("пятниц") -> DayOfWeek.FRIDAY
    word.startsWith("суббот") -> DayOfWeek.SATURDAY
    else -> DayOfWeek.SUNDAY
}

private val REPEAT_VALUES = mapOf(
    "один" to 1, "одну" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9,
    "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
    "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50, "шестьдесят" to 60, "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90,
    "сто" to 100, "двести" to 200, "триста" to 300, "четыреста" to 400, "пятьсот" to 500, "шестьсот" to 600, "семьсот" to 700, "восемьсот" to 800, "девятьсот" to 900,
)
private val REPEAT_UNITS = REPEAT_VALUES.filterValues { it < 10 }.keys.joinToString("|")
private val REPEAT_TEENS = REPEAT_VALUES.filterValues { it in 10..19 }.keys.joinToString("|")
private val REPEAT_TENS = REPEAT_VALUES.filterValues { it in 20..90 }.keys.joinToString("|")
private val REPEAT_HUNDREDS = REPEAT_VALUES.filterValues { it >= 100 }.keys.joinToString("|")
private val REPEAT_TAIL = "(?:(?:$REPEAT_TENS)(?:\\s+(?:$REPEAT_UNITS))?|$REPEAT_TEENS|$REPEAT_UNITS)"
private val REPEAT_NUMBER = "\\d{1,3}|(?:(?:$REPEAT_HUNDREDS)(?:\\s+$REPEAT_TAIL)?|$REPEAT_TAIL)"
private const val REPEAT_DAY = "понедельник(?:ам)?|вторник(?:ам)?|сред(?:у|ам|а)|четверг(?:ам)?|пятниц(?:у|ам|а)|суббот(?:у|ам|а)|воскресень(?:е|ям)"
private val WEEKDAY_WORD = Regex("(?:$REPEAT_DAY)(?![\\p{L}])")
private val INTERVAL = Regex("кажд(?:ые|ую|ый|ое)\\s+($REPEAT_NUMBER)\\s+")
private val REPEAT_COUNT = Regex("(?iu)(?<![\\p{L}])(?:всего|повтори|повторить)\\s+($REPEAT_NUMBER)\\s+раз(?:а)?(?![\\p{L}])")
private val REPEAT_UNTIL = Regex("(?iu)(?<![\\p{L}])до\\s+(\\d{1,2}[./]\\d{1,2}(?:[./]\\d{2,4})?|\\d{1,2}\\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)(?:\\s+\\d{4})?)(?![\\p{L}\\d])")
private val REPEAT_EXPRESSION = Regex(
    "(?iu)(?<![\\p{L}])(?:" +
        "каждое\\s+утро|каждый\\s+вечер|каждую\\s+ночь|по\\s+(?:утрам|вечерам|ночам)|" +
        "(?:кажд(?:ые|ую|ый|ое)\\s+|по\\s+)(?:$REPEAT_DAY)(?:\\s*(?:,|и)\\s*(?:$REPEAT_DAY))*|" +
        "по\\s+(?:будням|выходным|рабочим\\s+дням)|каждый\\s+(?:рабочий|будний)\\s+день|" +
        "ежедневно|еженедельно|ежемесячно|ежегодно|" +
        "раз\\s+в\\s+(?:день|неделю|месяц|год)|" +
        "кажд(?:ые|ую|ый)\\s+(?:(?:$REPEAT_NUMBER)\\s+)?(?:день|дня|дней|неделю|недели|недель|месяц|месяца|месяцев|год|года|лет)" +
        ")(?![\\p{L}])",
)

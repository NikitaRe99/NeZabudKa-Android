package ru.nezabudka.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

data class DetectedDelay(val expression: String, val value: Double, val unit: DelayUnit)
data class TemporalResult(val remindAt: LocalDateTime?, val cleanText: String, val expression: String?)
data class CalendarDateRange(val start: LocalDate, val endExclusive: LocalDate)

class RussianTemporalParser(
    private val configuredNamedTimes: () -> Map<String, LocalTime> = { DEFAULT_NAMED_TIMES },
) {
    private val numberWords = linkedMapOf(
        "один" to 1.0, "одну" to 1.0, "одна" to 1.0, "два" to 2.0, "две" to 2.0,
        "пару" to 2.0, "три" to 3.0, "четыре" to 4.0, "пять" to 5.0,
        "шесть" to 6.0, "семь" to 7.0, "восемь" to 8.0, "девять" to 9.0,
        "десять" to 10.0, "одиннадцать" to 11.0, "двенадцать" to 12.0,
        "тринадцать" to 13.0, "четырнадцать" to 14.0, "пятнадцать" to 15.0,
        "шестнадцать" to 16.0, "семнадцать" to 17.0, "восемнадцать" to 18.0,
        "девятнадцать" to 19.0, "двадцать" to 20.0, "тридцать" to 30.0,
        "сорок" to 40.0, "пятьдесят" to 50.0, "шестьдесят" to 60.0,
        "полтора" to 1.5, "полторы" to 1.5,
    )
    private val unitPattern = "минут(?:у|ы)?|минут|час(?:а|ов)?|день|дня|дней|сутки|суток|недел(?:ю|и|ь)?|месяц(?:а|ев)?|год|года|лет"
    private val wordStart = "(?<![\\p{L}\\p{N}_])"
    private val wordEnd = "(?![\\p{L}\\p{N}_])"
    private val delayRegex = Regex("${wordStart}через\\s+(?:([\\d.,]+|[а-яё]+(?:\\s+[а-яё]+){0,2})\\s+)?($unitPattern)$wordEnd", RegexOption.IGNORE_CASE)
    private val relativeDayRegex = Regex("$wordStart(послезавтра|завтра|сегодня)$wordEnd", RegexOption.IGNORE_CASE)
    private val weekdayRegex = Regex("${wordStart}(?:в\\s+|на\\s+)?(следующ(?:ий|ую|ее))?\\s*(понедельник|вторник|среду|среда|четверг|пятницу|пятница|субботу|суббота|воскресенье)$wordEnd", RegexOption.IGNORE_CASE)
    private val numericDateRegex = Regex("${wordStart}(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?$wordEnd")
    private val namedDateRegex = Regex("${wordStart}(\\d{1,2})\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)(?:\\s+(\\d{4}))?$wordEnd", RegexOption.IGNORE_CASE)
    private val wordNamedDateRegex = Regex(
        "${wordStart}(${ORDINAL_DAYS.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }})" +
            "\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)" +
            "(?:\\s+(\\d{4}))?$wordEnd",
        RegexOption.IGNORE_CASE,
    )
    private val dayOfMonthRegex = Regex(
        "${wordStart}(?:на\\s+)?(\\d{1,2})(?:-?(?:е|го))?\\s+числ(?:о|а)$wordEnd",
        RegexOption.IGNORE_CASE,
    )
    private val wordDayOfMonthRegex = Regex(
        "${wordStart}(?:на\\s+)?(${ORDINAL_DAYS.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }})" +
            "\\s+числ(?:о|а)$wordEnd",
        RegexOption.IGNORE_CASE,
    )
    private val digitTimeRegex = Regex("${wordStart}в\\s+([01]?\\d|2[0-3])(?:(?::|\\s+)([0-5]\\d))?\\s*(?:час(?:а|ов)?\\s*)?(утра|дня|вечера|ночи)?$wordEnd", RegexOption.IGNORE_CASE)
    private val timeWords = numberWords.keys.sortedByDescending { it.length }.joinToString("|")
    private val wordTimeRegex = Regex("${wordStart}в\\s+($timeWords)(?:\\s+($timeWords(?:\\s+(?:один|одну|два|две|три|четыре|пять|шесть|семь|восемь|девять))?))?\\s*(?:час(?:а|ов)?\\s*)?(утра|дня|вечера|ночи)?$wordEnd", RegexOption.IGNORE_CASE)
    private val monthPartPattern = "начал(?:о|е|а|у)|середин(?:а|е|ы|у)|кон(?:ец|це|ца|цу)"
    private val relativeMonthPartRegex = Regex(
        "${wordStart}(?:ближе\\s+к\\s+|в\\s+|на\\s+|к\\s+)?($monthPartPattern)\\s+" +
            "(?:(текущего|этого|следующего|будущего)\\s+)?месяц(?:а|е|у)?$wordEnd",
        RegexOption.IGNORE_CASE,
    )
    private val namedMonthPartRegex = Regex(
        "${wordStart}(?:ближе\\s+к\\s+|в\\s+|на\\s+|к\\s+)?($monthPartPattern)\\s+" +
            "(${MONTHS.keys.joinToString("|") { Regex.escape(it) }})(?:\\s+(\\d{4}))?$wordEnd",
        RegexOption.IGNORE_CASE,
    )
    private val reminderCreationMarkerRegex = Regex(
        "$wordStart(?:" +
            "напомни$wordEnd(?:\\s+мне$wordEnd)?[\\s,.:;!?]*|" +
            "(?:создай|добавь|поставь|сделай|установи|запланируй)$wordEnd" +
            "\\s+(?:мне\\s+)?напоминани[\\p{L}\\p{N}_]*(?:\\s+мне$wordEnd)?[\\s,.:;!?]*" +
            ")",
        RegexOption.IGNORE_CASE,
    )
    private val reminderPrefixRegex = Regex(
        "^\\s*(?:пожалуйста[\\s,]+)?(?:" +
            "(?:напомни(?:ть)?|напомню)(?:\\s+мне)?[\\s,.:;!?]*|" +
            "(?:создай|создать|добавь|добавить|поставь|поставить|сделай|сделать|установи|установить|запланируй|запланировать)" +
            "\\s+(?:мне\\s+)?напоминани[\\p{L}\\p{N}_]*(?:\\s+мне)?[\\s,.:;!?]*" +
            ")\\s*",
        RegexOption.IGNORE_CASE,
    )

    fun parse(userText: String, now: LocalDateTime): TemporalResult {
        val delay = extractDelay(userText)
        val calendarDate = findCalendarDate(userText, now)
        val day = relativeDayRegex.find(userText)
        val weekday = weekdayRegex.find(userText)
        val spokenTime = extractTime(userText)
        val remindAt = when {
            delay != null -> {
                val delayed = addDelay(now, delay.value, delay.unit)
                if (spokenTime != null && delay.unit in CALENDAR_DELAY_UNITS) {
                    LocalDateTime.of(delayed.toLocalDate(), spokenTime)
                } else delayed
            }
            calendarDate != null -> LocalDateTime.of(
                calendarDate.date,
                spokenTime ?: now.toLocalTime(),
            )
            day != null -> {
                val offset = when (day.groupValues[1].lowercase()) { "сегодня" -> 0L; "завтра" -> 1L; else -> 2L }
                LocalDateTime.of(now.toLocalDate().plusDays(offset), spokenTime ?: now.toLocalTime())
            }
            weekday != null -> LocalDateTime.of(resolveWeekday(weekday, now, spokenTime), spokenTime ?: now.toLocalTime())
            spokenTime != null -> LocalDateTime.of(
                if (spokenTime > now.toLocalTime()) now.toLocalDate() else now.toLocalDate().plusDays(1),
                spokenTime,
            )
            else -> null
        }
        val expression = delay?.expression ?: calendarDate?.expression ?: day?.value ?: weekday?.value ?: findTimeExpression(userText)
        return TemporalResult(remindAt, cleanReminderText(userText, expression), expression)
    }

    fun isReminderCreationCommand(text: String): Boolean =
        reminderCreationMarkerRegex.containsMatchIn(text) || reminderPrefixRegex.containsMatchIn(text)

    fun extractDelay(text: String): DetectedDelay? {
        val match = delayRegex.find(text) ?: return null
        val value = match.groupValues[1].takeIf { it.isNotBlank() }?.let(::parseNumber) ?: 1.0
        return DetectedDelay(match.value, value, normalizeUnit(match.groupValues[2]))
    }

    fun extractDate(text: String, now: LocalDateTime): LocalDate? {
        extractDelay(text)?.takeIf { it.unit in CALENDAR_DELAY_UNITS }?.let { delay ->
            return addDelay(now, delay.value, delay.unit).toLocalDate()
        }
        relativeDayRegex.find(text)?.let { match ->
            val offset = when (match.groupValues[1].lowercase()) { "сегодня" -> 0L; "завтра" -> 1L; else -> 2L }
            return now.toLocalDate().plusDays(offset)
        }
        weekdayRegex.find(text)?.let { return resolveWeekday(it, now, null) }
        return findCalendarDate(text, now)?.date
    }

    fun extractDateRange(text: String, now: LocalDateTime): CalendarDateRange? {
        relativeMonthPartRegex.find(text)?.let { match ->
            val modifier = match.groupValues[2].lowercase()
            val month = YearMonth.from(now).let {
                if (modifier == "следующего" || modifier == "будущего") it.plusMonths(1) else it
            }
            return monthPartRange(month, match.groupValues[1])
        }
        namedMonthPartRegex.find(text)?.let { match ->
            val explicitYear = match.groupValues[3].toIntOrNull()
            val monthNumber = MONTHS.getValue(match.groupValues[2].lowercase())
            var month = YearMonth.of(explicitYear ?: now.year, monthNumber)
            if (explicitYear == null && month.monthValue < now.monthValue) {
                month = month.plusYears(1)
            }
            return monthPartRange(month, match.groupValues[1])
        }
        return null
    }

    fun extractTime(text: String): LocalTime? {
        val lower = text.lowercase()
        if (Regex("${wordStart}в\\s+полдень$wordEnd").containsMatchIn(lower)) return LocalTime.NOON
        if (Regex("${wordStart}в\\s+полночь$wordEnd").containsMatchIn(lower)) return LocalTime.MIDNIGHT
        digitTimeRegex.find(lower)?.let {
            return LocalTime.of(applyPeriod(it.groupValues[1].toInt(), it.groupValues[3]), it.groupValues[2].ifBlank { "0" }.toInt())
        }
        wordTimeRegex.find(lower)?.let {
            val hour = parseNumber(it.groupValues[1]).toInt()
            val minute = it.groupValues[2].takeIf(String::isNotBlank)?.let(::parseNumber)?.toInt() ?: 0
            if (hour in 0..23 && minute in 0..59) return LocalTime.of(applyPeriod(hour, it.groupValues[3]), minute)
        }
        return findNamedTime(text)?.second
    }

    fun addDelay(now: LocalDateTime, value: Double, unit: DelayUnit): LocalDateTime = when (unit) {
        DelayUnit.MINUTE -> now.plusSeconds((value * 60).roundToLong())
        DelayUnit.HOUR -> now.plusSeconds((value * 3600).roundToLong())
        DelayUnit.DAY -> now.plusSeconds((value * 86400).roundToLong())
        DelayUnit.WEEK -> now.plusSeconds((value * 604800).roundToLong())
        DelayUnit.MONTH, DelayUnit.YEAR -> {
            val months = value * if (unit == DelayUnit.YEAR) 12 else 1
            if (months % 1.0 != 0.0) now.plusSeconds((value * if (unit == DelayUnit.YEAR) 31_556_952 else 2_629_746).roundToLong())
            else addCalendarMonths(now, months.toLong())
        }
    }

    fun cleanReminderText(text: String, expression: String? = null): String {
        var cleaned = text
        listOf(delayRegex, relativeDayRegex, weekdayRegex, digitTimeRegex, wordTimeRegex,
            Regex("$wordStart(?:в\\s+)?(?:полдень|полночь)$wordEnd", RegexOption.IGNORE_CASE)
        ).forEach { cleaned = it.replace(cleaned, " ") }
        findNamedTime(cleaned)?.first?.let { named -> cleaned = cleaned.replace(Regex(Regex.escape(named), RegexOption.IGNORE_CASE), " ") }
        if (!expression.isNullOrBlank()) cleaned = cleaned.replace(Regex(Regex.escape(expression), RegexOption.IGNORE_CASE), " ")
        cleaned = reminderCreationMarkerRegex.replace(cleaned, " ")
        cleaned = reminderPrefixRegex.replace(cleaned, "")
        return cleaned.replace(Regex("\\s+"), " ").trim(' ', ',', '.', '-', '—')
    }

    private fun resolveWeekday(match: MatchResult, now: LocalDateTime, time: LocalTime?): LocalDate {
        val target = when (match.groupValues[2].lowercase()) {
            "понедельник" -> DayOfWeek.MONDAY; "вторник" -> DayOfWeek.TUESDAY
            "среду", "среда" -> DayOfWeek.WEDNESDAY; "четверг" -> DayOfWeek.THURSDAY
            "пятницу", "пятница" -> DayOfWeek.FRIDAY; "субботу", "суббота" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
        if (match.groupValues[1].isNotBlank()) {
            val nextMonday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            return nextMonday.plusDays((target.value - DayOfWeek.MONDAY.value).toLong())
        }
        var date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(target))
        if (date == now.toLocalDate() && (time == null || time <= now.toLocalTime())) date = date.plusWeeks(1)
        return date
    }

    private fun findCalendarDate(text: String, now: LocalDateTime): CalendarDateExpression? {
        numericDateRegex.find(text)?.let { match ->
            val explicitYear = match.groupValues[3]
            val year = when (explicitYear.length) {
                2 -> 2000 + explicitYear.toInt()
                4 -> explicitYear.toInt()
                else -> now.year
            }
            val date = runCatching {
                LocalDate.of(year, match.groupValues[2].toInt(), match.groupValues[1].toInt())
            }.getOrNull() ?: return@let
            return CalendarDateExpression(moveToNextYearIfPast(date, now, explicitYear.isBlank()), match.value)
        }
        namedDateRegex.find(text)?.let { match ->
            val explicitYear = match.groupValues[3]
            val date = runCatching {
                LocalDate.of(
                    explicitYear.toIntOrNull() ?: now.year,
                    MONTHS.getValue(match.groupValues[2].lowercase()),
                    match.groupValues[1].toInt(),
                )
            }.getOrNull() ?: return@let
            return CalendarDateExpression(moveToNextYearIfPast(date, now, explicitYear.isBlank()), match.value)
        }
        wordNamedDateRegex.find(text)?.let { match ->
            val explicitYear = match.groupValues[3]
            val day = ORDINAL_DAYS.getValue(match.groupValues[1].lowercase().replace('ё', 'е'))
            val date = runCatching {
                LocalDate.of(
                    explicitYear.toIntOrNull() ?: now.year,
                    MONTHS.getValue(match.groupValues[2].lowercase()),
                    day,
                )
            }.getOrNull() ?: return@let
            return CalendarDateExpression(moveToNextYearIfPast(date, now, explicitYear.isBlank()), match.value)
        }
        dayOfMonthRegex.find(text)?.let { match ->
            return upcomingDayOfMonth(match.groupValues[1].toInt(), match.value, now)
        }
        wordDayOfMonthRegex.find(text)?.let { match ->
            val key = match.groupValues[1].lowercase().replace('ё', 'е')
            return upcomingDayOfMonth(ORDINAL_DAYS.getValue(key), match.value, now)
        }
        return null
    }

    private fun upcomingDayOfMonth(day: Int, expression: String, now: LocalDateTime): CalendarDateExpression {
        if (day !in 1..31) throw CommandValidationException("Некорректный день месяца: $day")
        var month = YearMonth.from(now)
        repeat(24) {
            if (day <= month.lengthOfMonth()) {
                val date = month.atDay(day)
                if (!date.isBefore(now.toLocalDate())) return CalendarDateExpression(date, expression)
            }
            month = month.plusMonths(1)
        }
        throw CommandValidationException("Не удалось определить дату для $day числа")
    }

    private fun moveToNextYearIfPast(date: LocalDate, now: LocalDateTime, yearWasOmitted: Boolean): LocalDate =
        if (yearWasOmitted && date.isBefore(now.toLocalDate())) {
            runCatching { date.plusYears(1) }.getOrElse { date.withDayOfMonth(28).plusYears(1) }
        } else date

    private fun addCalendarMonths(value: LocalDateTime, months: Long): LocalDateTime {
        val target = YearMonth.from(value).plusMonths(months)
        return value.withYear(target.year).withMonth(target.monthValue).withDayOfMonth(value.dayOfMonth.coerceAtMost(target.lengthOfMonth()))
    }

    private fun monthPartRange(month: YearMonth, rawPart: String): CalendarDateRange {
        val part = rawPart.lowercase()
        return when {
            part.startsWith("начал") -> CalendarDateRange(month.atDay(1), month.atDay(11))
            part.startsWith("середин") -> CalendarDateRange(month.atDay(11), month.atDay(21))
            else -> CalendarDateRange(month.atDay(21), month.plusMonths(1).atDay(1))
        }
    }

    private fun findTimeExpression(text: String): String? = digitTimeRegex.find(text)?.value
        ?: wordTimeRegex.find(text)?.value
        ?: Regex("$wordStart(?:в\\s+полдень|в\\s+полночь)$wordEnd", RegexOption.IGNORE_CASE).find(text)?.value
        ?: findNamedTime(text)?.first

    private fun findNamedTime(text: String): Pair<String, LocalTime>? {
        val normalizedText = text.lowercase().replace('ё', 'е')
        val entries = configuredNamedTimes().entries
            .filter { it.key.isNotBlank() }
            .sortedByDescending { it.key.length }
        entries.forEach { (phrase, time) ->
            val normalizedPhrase = phrase.trim().lowercase().replace('ё', 'е')
            val phrasePattern = normalizedPhrase.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
            val match = Regex("$wordStart$phrasePattern$wordEnd", RegexOption.IGNORE_CASE).find(normalizedText)
            if (match != null) return text.substring(match.range) to time
        }
        return null
    }

    private fun parseNumber(raw: String): Double {
        raw.replace(',', '.').toDoubleOrNull()?.let { return it }
        val values = raw.lowercase().trim().split(Regex("\\s+")).map { numberWords[it] ?: throw CommandValidationException("Не удалось разобрать число: $raw") }
        return values.sum()
    }

    private fun normalizeUnit(raw: String): DelayUnit = when {
        raw.lowercase().startsWith("минут") -> DelayUnit.MINUTE
        raw.lowercase().startsWith("час") -> DelayUnit.HOUR
        raw.lowercase() in setOf("день", "дня", "дней", "сутки", "суток") -> DelayUnit.DAY
        raw.lowercase().startsWith("недел") -> DelayUnit.WEEK
        raw.lowercase().startsWith("месяц") -> DelayUnit.MONTH
        else -> DelayUnit.YEAR
    }

    private fun applyPeriod(hour: Int, period: String): Int = when {
        period in setOf("дня", "вечера") && hour in 1..11 -> hour + 12
        period in setOf("утра", "ночи") && hour == 12 -> 0
        else -> hour
    }

    companion object {
        val DEFAULT_NAMED_TIMES = linkedMapOf(
            "утром" to LocalTime.of(9, 0),
            "днём" to LocalTime.of(14, 0),
            "вечером" to LocalTime.of(19, 0),
            "ночью" to LocalTime.MIDNIGHT,
        )
        private val CALENDAR_DELAY_UNITS = setOf(DelayUnit.DAY, DelayUnit.WEEK, DelayUnit.MONTH, DelayUnit.YEAR)
        private val MONTHS = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
            .mapIndexed { index, name -> name to index + 1 }.toMap()
        private val ORDINAL_DAY_UNITS = linkedMapOf(
            "первого" to 1, "второго" to 2, "третьего" to 3, "четвертого" to 4,
            "четвёртого" to 4, "пятого" to 5, "шестого" to 6, "седьмого" to 7,
            "восьмого" to 8, "девятого" to 9,
        )
        private val ORDINAL_DAYS = buildMap<String, Int> {
            putAll(ORDINAL_DAY_UNITS)
            listOf(
                "десятого", "одиннадцатого", "двенадцатого", "тринадцатого", "четырнадцатого",
                "пятнадцатого", "шестнадцатого", "семнадцатого", "восемнадцатого", "девятнадцатого",
            ).forEachIndexed { index, value -> put(value, index + 10) }
            put("двадцатого", 20)
            put("тридцатого", 30)
            ORDINAL_DAY_UNITS.forEach { (word, day) ->
                put("двадцать $word", 20 + day)
                if (day == 1) put("тридцать $word", 31)
            }
        }
    }

    private data class CalendarDateExpression(val date: LocalDate, val expression: String)
}

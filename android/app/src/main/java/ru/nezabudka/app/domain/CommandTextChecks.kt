package ru.nezabudka.app.domain

fun isUnsupportedMediaCommand(text: String): Boolean = UNSUPPORTED_MEDIA_COMMAND.containsMatchIn(text)
fun isCompletedActionStatement(text: String): Boolean = COMPLETED_ACTION.containsMatchIn(text)
/** Split STT "от меня" must reach the command alternative, never create a new reminder. */
fun isNonCommandSpeechStatement(text: String): Boolean = Regex("(?iu)^\\s*(?:пожалуйста[\\s,]+)?от\\s+меня(?=\\s|[,.!?]|$)").containsMatchIn(text)
fun isUnsupportedCommandMutation(text: String): Boolean = UNSUPPORTED_MUTATION.containsMatchIn(text)

private val UNSUPPORTED_MUTATION = Regex(
    "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:" +
        "(?:не\\s+(?:создавай|добавляй|сохраняй|записывай|удаляй|убирай)|отмени\\s+(?:удаление|создание|добавление))(?=\\s|$)|" +
        "(?:перекинь|перемести|перенеси|переименуй|измени)\\s+(?:эту\\s+|мою\\s+)?(?:заметк|папк)[\\p{L}]*" +
        ")",
)

fun extractStopTrackingReminderTarget(text: String): String? = STOP_TRACKING.find(text)
    ?.groupValues?.get(1)?.trim()?.trim(',', '.', '!', '?')?.trim()?.takeIf(String::isNotBlank)

/** Unambiguous cancellation syntax: keep the whole target, even if BIO predicts only its tail. */
fun extractExplicitReminderCancellation(text: String): AssistantCommand? {
    if (Regex("(?iu)^\\s*мне\\s+(?:больше\\s+)?не\\s+нужн[оа]\\s+(?:мо[её]\\s+)?последн[\\p{L}]*\\s+напоминани[\\p{L}]*\\s*[.!]?\\s*$").matches(text)) {
        return AssistantCommand(CommandIntent.DELETE_REMINDER, targetLast = true)
    }
    val target = EXPLICIT_REMINDER_CANCELLATION.firstNotNullOfOrNull { pattern ->
        pattern.matchEntire(text)?.groupValues?.get(1)
    }?.trim()?.trim('«', '»', '"', '.', '!', '?')?.trim()?.takeIf { it.any(Char::isLetterOrDigit) } ?: return null
    return AssistantCommand(CommandIntent.DELETE_REMINDER, targetText = target)
}

private val EXPLICIT_REMINDER_CANCELLATION = listOf(
    Regex("(?iu)^\\s*(?:пожалуйста[\\s,]+)?переведи\\s+напоминание\\s+(.+?)\\s+в\\s+отмен[её]нные[.!?]?\\s*$"),
    Regex("(?iu)^\\s*(?:пожалуйста[\\s,]+)?сними\\s+отметку\\s+о\\s+будущем\\s+деле\\s+(.+?)[.!?]?\\s*$"),
    Regex("(?iu)^\\s*(?:пожалуйста[\\s,]+)?останови\\s+контроль\\s+(?:задачи|напоминания)\\s+(.+?)[.!?]?\\s*$"),
)

fun extractExplicitFolderRetirement(text: String): AssistantCommand? {
    val target = Regex("(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:откажись\\s+от|больше\\s+не\\s+используй)\\s+(?:тематической\\s+|тематическую\\s+)?(?:полки|полку|папки|папку|раздела|раздел|блока\\s+материалов|блок\\s+материалов)\\s+(.+?)[.!?]?\\s*$")
        .matchEntire(text)?.groupValues?.get(1)?.trim()?.trim('«', '»', '"')?.trim()?.takeIf(String::isNotBlank) ?: return null
    return AssistantCommand(CommandIntent.DELETE_FOLDER, noteFolder = target)
}

fun extractExplicitFutureReminder(text: String, now: java.time.LocalDateTime, temporal: RussianTemporalParser): AssistantCommand? {
    val match = Regex("(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:подай\\s+(?:мне\\s+)?сигнал(?:\\s+перед\\s+тем\\s*,?\\s*как)?|мне\\s+потребуется\\s+вовремя|оставь\\s+контрольную\\s+точку|возьми\\s+на\\s+заметку\\s+как\\s+будущее\\s+дело)\\s*[:;—–-]?\\s+(.+)$").matchEntire(text) ?: return null
    val recurring = parseRecurringReminder(match.groupValues[1], now, temporal)
    val parsed = recurring.temporal
    val delay = temporal.extractDelay(match.groupValues[1])
    return AssistantCommand(CommandIntent.CREATE_REMINDER, text = parsed.cleanText, remindAt = parsed.remindAt, recurrence = recurring.recurrence,
        timeExpression = parsed.expression, delayValue = delay?.value, delayUnit = delay?.unit).validate()
}

private val UNSUPPORTED_MEDIA_COMMAND = Regex(
    "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:можешь\\s+)?(?:запиши|записать|сохрани|сохранить)\\s+(?:мне\\s+)?" +
        "(?:видео|аудио|звук|фото|голосовое\\s+сообщение)(?=\\s|[,.!?]|$)",
)
private val COMPLETED_ACTION = Regex(
    "(?iu)^\\s*(?:я|мы)\\s+(?:уже\\s+)?(?:закончил[аи]?|завершил[аи]?|записал[аи]?|сохранил[аи]?)(?=\\s|[,.!?]|$)",
)
private val STOP_TRACKING = Regex(
    "(?iu)^\\s*(?:пожалуйста[\\s,]+)?не\\s+отслеживай\\s+(?:больше\\s+)?" +
        "(?:это\\s+|эту\\s+)?(?:действие|задачу|напоминание)\\s+(?!последн)(.+)$",
)

package ru.nezabudka.app.llm

import ru.nezabudka.app.domain.AssistantCommand
import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.domain.RussianTemporalParser
import ru.nezabudka.app.domain.extractNoteParts
import java.time.LocalDateTime

/** Converts classifier output into a validated domain command without generative JSON. */
class ClassicNluCommandMapper(
    private val temporal: RussianTemporalParser = RussianTemporalParser(),
) {
    fun map(
        intent: CommandIntent,
        slots: Map<String, String>,
        userText: String,
        now: LocalDateTime,
    ): AssistantCommand {
        val command = when (intent) {
            CommandIntent.CREATE_REMINDER -> createReminder(slots, userText, now)
            CommandIntent.LIST_REMINDERS -> listReminders(userText, now)
            CommandIntent.DELETE_REMINDER -> AssistantCommand(
                intent = intent,
                targetText = if (LAST_TARGET.containsMatchIn(userText)) null else {
                    userText.afterCommandDelimiter() ?: extractReminderDeletionTarget(userText) ?:
                        confirmedSlot(slots, "TARGET", userText)
                },
                targetLast = LAST_TARGET.containsMatchIn(userText),
            )
            CommandIntent.CREATE_NOTE -> createNote(slots, userText)
            CommandIntent.CREATE_FOLDER, CommandIntent.DELETE_FOLDER -> AssistantCommand(
                intent = intent,
                noteFolder = extractFolder(intent, userText) ?: confirmedSlot(slots, "FOLDER", userText),
            )
            CommandIntent.UNKNOWN -> AssistantCommand(CommandIntent.UNKNOWN)
        }
        return command.validate()
    }

    private fun createReminder(
        slots: Map<String, String>,
        userText: String,
        now: LocalDateTime,
    ): AssistantCommand {
        val parsed = temporal.parse(userText, now)
        val delay = temporal.extractDelay(userText)
        val delimitedText = userText.afterCommandDelimiter()?.let { value ->
            temporal.parse(value, now).cleanText.cleanReminderPayload()
        }
        return AssistantCommand(
            intent = CommandIntent.CREATE_REMINDER,
            text = delimitedText ?: confirmedSlot(slots, "TEXT", parsed.cleanText) ?:
                parsed.cleanText.cleanReminderPayload(),
            remindAt = parsed.remindAt,
            timeExpression = parsed.expression,
            delayValue = delay?.value,
            delayUnit = delay?.unit,
        )
    }

    private fun listReminders(userText: String, now: LocalDateTime): AssistantCommand {
        val range = temporal.extractDateRange(userText, now)
        val date = if (range == null) temporal.extractDate(userText, now) else null
        return AssistantCommand(
            intent = CommandIntent.LIST_REMINDERS,
            periodStart = range?.start?.atStartOfDay() ?: date?.atStartOfDay(),
            periodEnd = range?.endExclusive?.atStartOfDay() ?: date?.plusDays(1)?.atStartOfDay(),
        )
    }

    private fun createNote(slots: Map<String, String>, userText: String): AssistantCommand {
        val (explicitFolder, extractedText) = extractNoteParts(userText)
        val modelText = confirmedSlot(slots, "TEXT", userText)
        val confirmedModelFolder = confirmedSlot(slots, "FOLDER", userText)
        val delimitedText = userText.afterCommandDelimiter()
        return AssistantCommand(
            intent = CommandIntent.CREATE_NOTE,
            noteText = if (explicitFolder != null && extractedText != null) {
                extractedText
            } else if (delimitedText != null) {
                delimitedText
            } else if (confirmedModelFolder != null || NOTE_NOUN.containsMatchIn(userText)) {
                modelText ?: userText.cleanNotePayload() ?: extractedText
            } else {
                userText.cleanNotePayload() ?: modelText ?: extractedText
            },
            noteFolder = explicitFolder ?: confirmedModelFolder,
        )
    }

    private fun String.clean(): String? = trim().ifBlank { null }

    private fun String.afterCommandDelimiter(): String? {
        val delimiter = DASH_DELIMITER.find(this) ?: COLON_DELIMITER.find(this)?.takeIf { match ->
            val prefix = substring(0, match.range.first)
            DELIMITER_CARRIER.containsMatchIn(prefix) || TEMPORAL_DELIMITER_END.containsMatchIn(prefix)
        } ?: return null
        return substring(delimiter.range.last + 1).trimCommandPunctuation().clean()
    }

    private fun confirmedSlot(slots: Map<String, String>, name: String, userText: String): String? =
        slots[name]?.trimCommandPunctuation()?.clean()?.takeIf { value ->
            Regex("(?iu)(?<![\\p{L}\\p{N}_])${Regex.escape(value)}(?![\\p{L}\\p{N}_])")
                .containsMatchIn(userText)
        }

    private fun String.cleanReminderPayload(): String? {
        var value = trimCommandPunctuation()
        value = REMINDER_PREFIX.replace(value, "")
        value = LEADING_CONNECTOR.replace(value, "")
        value = TRAILING_CONNECTOR.replace(value, "")
        return value.trimCommandPunctuation().clean()
    }

    private fun String.cleanNotePayload(): String? {
        val match = NOTE_PREFIX.find(this) ?: return null
        return substring(match.range.last + 1)
            .trimCommandPunctuation()
            .replace(LEADING_NOTE_CONNECTOR, "")
            .trimCommandPunctuation()
            .clean()
    }

    private fun extractReminderDeletionTarget(userText: String): String? {
        DELETE_STRUCTURED_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(userText)?.groupValues?.get(1)?.cleanDeletionTarget()
        }?.let { return it }
        DELETE_ANCHORED_TARGET.find(userText)?.groupValues?.get(1)?.cleanDeletionTarget()?.let { return it }
        DELETE_SUFFIX.find(userText)?.groupValues?.get(1)?.let { return it.cleanTarget() }
        DELETE_NOT_NEEDED_PREFIX.find(userText)?.groupValues?.get(1)?.let { return it.cleanTarget() }
        DELETE_MORE_PREFIX.find(userText)?.groupValues?.get(1)?.let { return it.cleanTarget() }
        DELETE_PREFIX.find(userText)?.groupValues?.get(1)?.let { return it.cleanTarget() }
        return null
    }

    private fun extractFolder(intent: CommandIntent, userText: String): String? {
        val patterns = if (intent == CommandIntent.CREATE_FOLDER) CREATE_FOLDER_PATTERNS else DELETE_FOLDER_PATTERNS
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(userText)?.groupValues?.get(1)?.trimCommandPunctuation()?.clean()
        }
    }

    private fun String.cleanTarget(): String? =
        replace(LEADING_TARGET_CONNECTOR, "").trimCommandPunctuation().clean()

    private fun String.cleanDeletionTarget(): String? =
        replace(LEADING_TARGET_CONNECTOR, "")
            .replace(TRAILING_DELETION_STATE, "")
            .trimCommandPunctuation()
            .clean()

    private fun String.trimCommandPunctuation(): String =
        trim().trim('«', '»', '"', ',', '.', ':', ';', '!', '?', '—', '–', '-').trim()

    private companion object {
        val LAST_TARGET = Regex("(?iu)(?<![\\p{L}\\p{N}_])последн[\\p{L}\\p{N}_]*")
        val DASH_DELIMITER = Regex("\\s+[—–-]\\s+")
        val COLON_DELIMITER = Regex("[:;]\\s+")
        val DELIMITER_CARRIER = Regex(
            "(?iu)(?:напомин|дел|задач|пункт|запис|замет|памят|текст|данн|сведен|информац|" +
                "справоч|факт|действ|план|формулиров|слова)[\\p{L}]*\\s*$",
        )
        val TEMPORAL_DELIMITER_END = Regex(
            "(?iu)(?:сегодня|завтра|послезавтра|утром|дн[её]м|вечером|ночью|" +
                "понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)\\s*$",
        )
        val REMINDER_PREFIX = Regex(
            "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:" +
                "(?:хорошо\\s+бы|главное)(?:\\s+мне)?\\s+не\\s+забыть|" +
                "(?:мне\\s+)?(?:обязательно\\s+)?(?:надо|нужно)\\s+(?:обязательно\\s+)?(?:не\\s+забыть\\s+)?|" +
                "проследи\\s*,?\\s*чтобы\\s+я\\s+не\\s+забыл(?:а)?|" +
                "не\\s+дай(?:\\s+мне)?\\s+забыть|" +
                "(?:оставь|поставь|создай|добавь|запланируй)(?:\\s+мне)?(?:\\s+напоминани[\\p{L}]*)?|" +
                "маякни(?:\\s+мне)?(?:\\s*,?\\s*чтобы)?|" +
                "напомни(?:\\s+мне)?(?:\\s+пожалуйста)?|" +
                "не\\s+забыть|обязательно" +
                ")\\s*",
        )
        val LEADING_CONNECTOR = Regex("(?iu)^\\s*(?:(?:о|об|про|на|чтобы)\\s+)+")
        val TRAILING_CONNECTOR = Regex("(?iu)\\s+(?:на|в|к)\\s*$")
        val NOTE_PREFIX = Regex(
            "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:" +
                "мне\\s+(?:важно|нужно)\\s+(?:запомнить|сохранить|записать)|" +
                "зафиксируй|запомни(?:\\s+пожалуйста)?|сохрани(?:\\s+как\\s+заметку)?(?:\\s+без\\s+изменений)?|" +
                "запиши(?:\\s+себе)?|(?:создай|добавь)\\s+заметку|оставь\\s+заметку|сделай\\s+запись|" +
                "нужно\\s+записать|добавь\\s+в\\s+заметки|пусть\\s+в\\s+заметках\\s+будет" +
                ")(?=\\s|[,:;.!?]|$)\\s*",
        )
        val LEADING_NOTE_CONNECTOR = Regex("(?iu)^\\s*(?:что\\s+)")
        val NOTE_NOUN = Regex("(?iu)(?<![\\p{L}\\p{N}_])заметк[\\p{L}]*")
        val LEADING_TARGET_CONNECTOR = Regex(
            "(?iu)^\\s*(?:(?:напоминани[\\p{L}]*|задач[\\p{L}]*)\\s+)?" +
                "(?:(?:о|об|про|с\\s+текстом|на\\s+тему)\\s+)?",
        )
        val DELETE_PREFIX = Regex(
            "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:можно\\s+)?(?:удали(?:ть)?|убери(?:ть)?|отмени(?:ть)?|сними(?:ть)?|закрой|" +
                "не\\s+напоминай(?:\\s+больше)?(?:\\s+про)?|откажись\\s+от)(?:\\s+из\\s+(?:планов|расписания))?" +
                "\\s+(?:мо[её]\\s+)?(?:напоминани[\\p{L}]*|задач[\\p{L}]*)?\\s*(.*)$",
        )
        val DELETE_MORE_PREFIX = Regex(
            "(?iu)^\\s*больше\\s+не\\s+напоминай(?:\\s+про)?\\s+(.+)$",
        )
        val DELETE_NOT_NEEDED_PREFIX = Regex(
            "(?iu)^\\s*мне\\s+(?:больше\\s+)?не\\s+нужн[оа]\\s+(?:напоминани[\\p{L}]*|задач[\\p{L}]*)?\\s*(.+)$",
        )
        val DELETE_SUFFIX = Regex(
            "(?iu)^\\s*(?:напоминани[\\p{L}]*|задач[\\p{L}]*)?\\s*(.+?)\\s+(?:больше\\s+не\\s+нужн[оа]|можно\\s+(?:удалить|убрать))\\s*[.!]?\\s*$",
        )
        val DELETE_STRUCTURED_PATTERNS = listOf(
            Regex(
                "(?iu)^\\s*(?:вычерк|удал|убер|исключ|сотр)[\\p{L}]*\\s+из\\s+" +
                    "(?:расписани[\\p{L}]*|план[\\p{L}]*|перечн[\\p{L}]*\\s+(?:дел|задач)[\\p{L}]*|" +
                    "будущ[\\p{L}]*\\s+действи[\\p{L}]*)\\s+(.+)$",
            ),
            Regex(
                "(?iu)^\\s*сним[иь][\\p{L}]*\\s+с\\s+(?:контрол[\\p{L}]*|исполнени[\\p{L}]*)\\s+" +
                    "(?:(?:задач|напоминани|пункт)[\\p{L}]*\\s+)?(.+)$",
            ),
            Regex(
                "(?iu)^\\s*помет[\\p{L}]*\\s+(?:(?:задач|напоминани|пункт)[\\p{L}]*\\s+)?" +
                    "(.+?)\\s+как\\s+(?:отмен[её]нн|неактивн)[\\p{L}]*\\s*$",
            ),
            Regex(
                "(?iu)^\\s*(?:аннулир|отмен|отзов|деактив|сотр|удал|убер|закрой|прекрат)[\\p{L}]*\\s+" +
                    "(?:(?:запланированн|ожидающ)[\\p{L}]*\\s+)?" +
                    "(?:(?:задач|напоминани|пункт|действи)[\\p{L}]*\\s+)(.+)$",
            ),
            Regex(
                "(?iu)^\\s*больше\\s+не\\s+(?:учитывай|отслеживай|держи)[\\p{L}]*" +
                    "(?:\\s+(?:в\\s+расписании|в\\s+планах))?\\s+(.+)$",
            ),
        )
        val DELETE_ANCHORED_TARGET = Regex(
            "(?iu)^.+(?:напоминани|задач|пункт|действи|дел|очеред|контрол)[\\p{L}]*\\s+(.+)$",
        )
        val TRAILING_DELETION_STATE = Regex(
            "(?iu)\\s+(?:(?:как|в)\\s+(?:отмен[её]нн|неактивн)[\\p{L}]*|из\\s+напоминани[\\p{L}]*)\\s*$",
        )
        val CREATE_FOLDER_PATTERNS = listOf(
            Regex("(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:создай|добавь|сделай|организуй|заведи)(?:\\s+(?:новую|новый))?\\s+(?:папку|раздел|каталог)\\s+(.+)$"),
            Regex("(?iu)^\\s*(?:мне\\s+нужна|хочу\\s+(?:новую\\s+)?|пусть\\s+будет)\\s*папк[ау]\\s+(.+)$"),
        )
        val DELETE_FOLDER_PATTERNS = listOf(
            Regex("(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:удали|убери|закрой|избавься\\s+от)(?:\\s+(?:папку|раздел|каталог))\\s+(.+)$"),
            Regex("(?iu)^\\s*(?:папка|раздел|каталог)\\s+(.+?)\\s+(?:больше\\s+не\\s+нуж(?:ен|на|но|ны)|можно\\s+(?:удалить|убрать))\\s*[.!]?\\s*$"),
            Regex("(?iu)^\\s*мне\\s+не\\s+нуж(?:на|ен)\\s+(?:папка|раздел|каталог)\\s+(.+)$"),
            Regex("(?iu)^\\s*можно\\s+убрать\\s+(?:папку|раздел|каталог)\\s+(.+)$"),
        )
    }
}

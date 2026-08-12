package ru.nezabudka.app.application

import ru.nezabudka.app.ports.CommandUnderstandingPort
import ru.nezabudka.app.domain.AssistantCommand
import ru.nezabudka.app.domain.CommandContext
import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.domain.CommandValidationException
import ru.nezabudka.app.domain.RussianTemporalParser
import ru.nezabudka.app.domain.extractNoteParts
import java.time.Clock
import java.time.LocalDateTime
/** Optional local classifier; all state-changing work remains in Kotlin [Dispatcher]. */
class OptionalCommandUnderstanding(
    private val delegate: CommandUnderstandingPort,
    private val enabled: () -> Boolean,
) : CommandUnderstandingPort {
    override suspend fun understand(
        text: String,
        now: LocalDateTime,
        context: CommandContext,
    ): AssistantCommand = if (enabled()) {
        delegate.understand(text, now, context)
    } else {
        AssistantCommand(CommandIntent.UNKNOWN)
    }
}

class ExecuteTextCommand(
    private val understanding: CommandUnderstandingPort,
    private val dispatcher: Dispatcher,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val temporal: RussianTemporalParser = RussianTemporalParser(),
) {
    suspend operator fun invoke(
        userText: String,
        defaultNoteFolder: String? = null,
        context: CommandContext = CommandContext(),
        reminderDateClarification: ReminderDateClarification? = null,
        alternatives: List<String> = emptyList(),
    ): ActionResult {
        require(userText.isNotBlank()) { "Введите или произнесите команду" }
        val text = userText.trim()
        val now = LocalDateTime.now(clock)
        if (reminderDateClarification != null) {
            return completeReminderDateClarification(text, now, reminderDateClarification)
        }
        val candidates = (listOf(text) + alternatives)
            .map(String::trim).filter(String::isNotBlank).distinct().take(MAX_TEXT_CANDIDATES)
        var commandText = text
        var understood = understandCandidate(commandText, now, context)
        for (candidate in candidates.drop(1)) {
            if (understood.intent != CommandIntent.UNKNOWN) break
            val candidateCommand = understandCandidate(candidate, now, context)
            if (candidateCommand.intent != CommandIntent.UNKNOWN) {
                commandText = candidate
                understood = candidateCommand
                break
            }
        }
        val dated = if (
            understood.intent == CommandIntent.LIST_REMINDERS &&
            understood.periodStart == null &&
            understood.periodEnd == null
        ) {
            temporal.extractDateRange(commandText, now)?.let { range ->
                understood.copy(
                    periodStart = range.start.atStartOfDay(),
                    periodEnd = range.endExclusive.atStartOfDay(),
                )
            } ?: temporal.extractDate(commandText, now)?.let { date ->
                understood.copy(periodStart = date.atStartOfDay(), periodEnd = date.plusDays(1).atStartOfDay())
            } ?: understood
        } else understood
        requireAllowed(dated.intent, context.scope)
        val command = if (
            dated.intent == CommandIntent.CREATE_NOTE &&
            context.scope == CommandScope.NOTES &&
            dated.noteFolder.isNullOrBlank() &&
            !defaultNoteFolder.isNullOrBlank()
        ) dated.copy(noteFolder = defaultNoteFolder) else dated
        if (command.intent == CommandIntent.CREATE_REMINDER && command.remindAt == null) {
            command.validate()
            val clarification = ReminderDateClarification(command.text.orEmpty(), command.recurrence)
            return ActionResult(
                message = "Когда напомнить о «${clarification.text}»? Назовите дату и время.",
                effect = ActionEffect.ReminderDateRequested(clarification),
                reminderDateClarification = clarification,
            )
        }
        return dispatcher.dispatch(command)
    }

    private suspend fun understandCandidate(
        text: String,
        now: LocalDateTime,
        context: CommandContext,
    ): AssistantCommand {
        val createFolder = Regex("(?iu)^\\s*(?:создай|создать|добавь|добавить)\\s+папк(?:у|а)\\s+[«\"]?(.+?)[»\"]?[.!]?\\s*$")
            .find(text)?.groupValues?.get(1)?.trim()?.trim('«', '»', '"')
        val deleteFolder = Regex("(?iu)^\\s*(?:удали|удалить)\\s+папк(?:у|а)\\s+[«\"]?(.+?)[»\"]?[.!]?\\s*$")
            .find(text)?.groupValues?.get(1)?.trim()?.trim('«', '»', '"')
        val explicitNote = NOTE_COMMAND.containsMatchIn(text)
        val (noteFolder, noteText) = if (explicitNote) extractNoteParts(text) else null to null
        val reminderDeletion = if (context.scope != CommandScope.NOTES) {
            extractDirectReminderDeletion(text)
        } else null
        val reminderList = if (context.scope != CommandScope.NOTES) {
            extractDirectReminderList(text, now)
        } else null
        val reminder = if (
            context.scope != CommandScope.NOTES &&
            temporal.isReminderCreationCommand(text)
        ) {
            val parsed = temporal.parse(text, now)
            val delay = temporal.extractDelay(text)
            AssistantCommand(
                intent = CommandIntent.CREATE_REMINDER,
                text = parsed.cleanText,
                remindAt = parsed.remindAt,
                timeExpression = parsed.expression,
                delayValue = delay?.value,
                delayUnit = delay?.unit,
            )
        } else null
        return when {
            context.scope != CommandScope.REMINDERS && !createFolder.isNullOrBlank() -> AssistantCommand(CommandIntent.CREATE_FOLDER, noteFolder = createFolder)
            context.scope != CommandScope.REMINDERS && !deleteFolder.isNullOrBlank() -> AssistantCommand(CommandIntent.DELETE_FOLDER, noteFolder = deleteFolder)
            context.scope != CommandScope.REMINDERS && !noteText.isNullOrBlank() -> AssistantCommand(
                CommandIntent.CREATE_NOTE,
                noteText = noteText,
                noteFolder = noteFolder,
            )
            reminderDeletion != null -> reminderDeletion
            reminderList != null -> reminderList
            reminder != null -> reminder
            else -> {
                val classified = understanding.understand(text, now, context)
                if (classified.intent != CommandIntent.UNKNOWN || context.scope == CommandScope.NOTES) {
                    classified
                } else {
                    extractImplicitReminder(text, now) ?: classified
                }
            }
        }
    }

    private fun extractImplicitReminder(text: String, now: LocalDateTime): AssistantCommand? {
        val parsed = temporal.parse(text, now)
        val task = IMPLICIT_REMINDER_PREFIX.matchEntire(parsed.cleanText)
            ?.groupValues?.get(1)
            ?.trim()?.trim(',', '.', ':', ';', '!', '?')?.trim()
            ?.takeIf { candidate ->
                candidate.any(Char::isLetter) &&
                    !IMPLICIT_REMINDER_QUESTION.containsMatchIn(candidate) &&
                    !IMPLICIT_META_COMMAND.containsMatchIn(candidate)
            }
            ?: return null
        val delay = temporal.extractDelay(text)
        return AssistantCommand(
            intent = CommandIntent.CREATE_REMINDER,
            text = task,
            remindAt = parsed.remindAt,
            timeExpression = parsed.expression,
            delayValue = delay?.value,
            delayUnit = delay?.unit,
        )
    }

    private suspend fun completeReminderDateClarification(
        answer: String,
        now: LocalDateTime,
        clarification: ReminderDateClarification,
    ): ActionResult {
        if (CANCEL_CLARIFICATION.matches(answer)) {
            return ActionResult("Создание напоминания отменено.")
        }
        if (WITHOUT_DATE.matches(answer)) {
            return dispatcher.dispatch(
                AssistantCommand(
                    intent = CommandIntent.CREATE_REMINDER,
                    text = clarification.text,
                    recurrence = clarification.recurrence,
                ),
            )
        }
        val parsed = temporal.parse(answer, now)
        if (parsed.remindAt == null) {
            return ActionResult(
                message = "Не удалось определить дату. Укажите её, например: «завтра в девять», либо ответьте «без даты» или «отмена».",
                effect = ActionEffect.ReminderDateRequested(clarification),
                reminderDateClarification = clarification,
            )
        }
        val delay = temporal.extractDelay(answer)
        return dispatcher.dispatch(
            AssistantCommand(
                intent = CommandIntent.CREATE_REMINDER,
                text = clarification.text,
                remindAt = parsed.remindAt,
                recurrence = clarification.recurrence,
                timeExpression = parsed.expression,
                delayValue = delay?.value,
                delayUnit = delay?.unit,
            ),
        )
    }

    private fun requireAllowed(intent: CommandIntent, scope: CommandScope) {
        val allowed = when (scope) {
            CommandScope.ASSISTANT -> ASSISTANT_INTENTS + CommandIntent.UNKNOWN
            CommandScope.REMINDERS -> REMINDER_INTENTS + CommandIntent.UNKNOWN
            CommandScope.NOTES -> NOTE_INTENTS + CommandIntent.UNKNOWN
        }
        if (intent !in allowed) throw CommandValidationException(
            when (scope) {
                CommandScope.REMINDERS -> "На вкладке «Напоминания» доступны только команды напоминаний"
                CommandScope.NOTES -> "На вкладке «Заметки» доступны только команды заметок и папок"
                CommandScope.ASSISTANT -> "Команда недоступна"
            },
        )
    }

    private companion object {
        val REMINDER_INTENTS = setOf(
            CommandIntent.CREATE_REMINDER, CommandIntent.LIST_REMINDERS, CommandIntent.DELETE_REMINDER,
        )
        val NOTE_INTENTS = setOf(CommandIntent.CREATE_NOTE, CommandIntent.CREATE_FOLDER, CommandIntent.DELETE_FOLDER)
        val ASSISTANT_INTENTS = REMINDER_INTENTS + NOTE_INTENTS
        val CANCEL_CLARIFICATION = Regex("(?iu)^\\s*(?:отмена|отмени|не надо|не нужно)\\s*[.!]?\\s*$")
        val WITHOUT_DATE = Regex("(?iu)^\\s*(?:без (?:даты|времени)|сохрани без (?:даты|времени)|создай без (?:даты|времени)|дату не указывать)\\s*[.!]?\\s*$")
        val IMPLICIT_REMINDER_PREFIX = Regex(
            "(?iu)^\\s*(?:мне\\s+)?(?:надо|нужно|необходимо)\\s+(?:бы\\s+)?" +
                "(?:обязательно\\s+)?(?:не\\s+забыть\\s+)?(.+?)\\s*[.!?]?\\s*$",
        )
        val IMPLICIT_REMINDER_QUESTION = Regex("(?iu)^ли(?:\\s|$)")
        val IMPLICIT_META_COMMAND = Regex(
            "(?iu)^(?:создать|добавить|записать|сохранить|удалить|убрать|отменить|показать|вывести|перечислить)" +
                "(?:\\s+мне)?\\s+(?:последн[\\p{L}]*\\s+)?(?:напоминани|заметк|папк)[\\p{L}]*",
        )
        const val MAX_TEXT_CANDIDATES = 4
        val NOTE_COMMAND = Regex(
            "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:создай|создать|добавь|добавить|запиши|записать|сделай|сделать|сохрани|сохранить)(?![\\p{L}\\p{N}_]).*(?<![\\p{L}\\p{N}_])заметк[\\p{L}\\p{N}_]*",
        )
        val DIRECT_REMINDER_DELETE = Regex(
            "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:удали|удалить|убери|убрать|отмени|отменить)" +
                "\\s+(?:мо[её]\\s+)?(?:(последн[\\p{L}\\p{N}_]*)\\s+)?напоминани[\\p{L}\\p{N}_]*\\s*(.*)$",
        )
        val DIRECT_LAST_REMINDER_NOT_NEEDED = Regex(
            "(?iu)^\\s*мне\\s+(?:больше\\s+)?не\\s+нужн[оа]\\s+(?:мо[её]\\s+)?" +
                "последн[\\p{L}]*\\s+напоминани[\\p{L}]*\\s*[.!]?\\s*$",
        )
        val REMINDER_LIST_PREFIX = Regex(
            "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:" +
                "(?:покажи|показать|выведи|вывести|перечисли|перечислить)(?:\\s+мне)?" +
                "(?:\\s+(?:все|мои))?\\s+(?:напоминания|напоминаний|планы|планов|расписание)|" +
                "что\\s+(?:у\\s+меня(?:\\s+запланировано)?|запланировано)|" +
                "какие(?:\\s+у\\s+меня)?\\s+(?:планы|напоминания)" +
                ")",
        )
    }

    private fun extractDirectReminderDeletion(text: String): AssistantCommand? {
        if (DIRECT_LAST_REMINDER_NOT_NEEDED.matches(text)) {
            return AssistantCommand(intent = CommandIntent.DELETE_REMINDER, targetLast = true)
        }
        val match = DIRECT_REMINDER_DELETE.find(text) ?: return null
        val targetLast = match.groupValues[1].isNotBlank()
        val target = match.groupValues[2]
            .replace(Regex("(?iu)^\\s*(?:о|об|про|с\\s+текстом|на\\s+тему)\\s+"), "")
            .trim().trim('«', '»', '"', ',', '.', ':', ';', '!', '?').trim()
            .ifBlank { null }
        if (!targetLast && target == null) return null
        return AssistantCommand(
            intent = CommandIntent.DELETE_REMINDER,
            targetText = if (targetLast) null else target,
            targetLast = targetLast,
        )
    }

    private fun extractDirectReminderList(text: String, now: LocalDateTime): AssistantCommand? {
        val match = REMINDER_LIST_PREFIX.find(text) ?: return null
        val suffix = text.substring(match.range.last + 1)
            .trim().trim(',', '.', ':', ';', '!', '?').trim()
        return if (
            suffix.isBlank() ||
            temporal.extractDateRange(text, now) != null ||
            temporal.extractDate(text, now) != null
        ) {
            AssistantCommand(CommandIntent.LIST_REMINDERS)
        } else null
    }
}

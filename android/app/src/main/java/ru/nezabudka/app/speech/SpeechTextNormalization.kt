package ru.nezabudka.app.speech

internal fun String.normalizeWhisperTranscript(): String =
    normalizeWhisperCommand().normalizeReminderTimePhrase().normalizeFolderName()

/**
 * Removes a decoder loop only when the same multi-word span occurs at least
 * three times in a row. Two repetitions and repeated individual words are
 * preserved because they can be intentional dictation.
 */
internal fun String.collapseWhisperRepetition(aggressive: Boolean = false): String {
    val standard = collapseConsecutiveWordBlocks(
        minimumWords = MIN_REPETITION_WORDS,
        minimumCount = MIN_REPETITION_COUNT,
    )
    val repeatedBlocksCollapsed = if (aggressive) {
        standard.collapseConsecutiveWordBlocks(
            minimumWords = MIN_AGGRESSIVE_REPETITION_WORDS,
            minimumCount = MIN_AGGRESSIVE_REPETITION_COUNT,
        )
    } else {
        standard
    }
    return repeatedBlocksCollapsed.removeTrailingPartialRepeat()
}

/** Removes a decoder restart such as "Напомни выбросить мусор. нап". */
private fun String.removeTrailingPartialRepeat(): String {
    val matches = TranscriptWordRegex.findAll(this).toList()
    if (matches.size < MIN_PARTIAL_REPEAT_SOURCE_WORDS + 1) return trim()
    val keys = matches.map { streamingWordKey(it.value) }
    val maxTailWords = minOf(matches.size / 2, MAX_PARTIAL_REPEAT_WORDS)
    for (tailSize in maxTailWords downTo 1) {
        val tailStart = matches.size - tailSize
        if (tailStart < MIN_PARTIAL_REPEAT_SOURCE_WORDS) continue
        val source = keys.take(tailSize)
        val tail = keys.drop(tailStart)
        val fullWordsMatch = tail.dropLast(1) == source.dropLast(1)
        val last = tail.last()
        val sourceLast = source.last()
        val lastMatches = last.length >= MIN_PARTIAL_WORD_LENGTH && sourceLast.startsWith(last)
        if (fullWordsMatch && lastMatches) {
            val startOffset = matches[tailStart].range.first
            if (last != sourceLast) {
                return substring(0, startOffset).trimEnd()
            }
        }
    }
    return trim()
}

private fun String.collapseConsecutiveWordBlocks(minimumWords: Int, minimumCount: Int): String {
    val words = trim().split(WhitespaceRegex).filter(String::isNotEmpty)
    if (words.size < minimumWords * minimumCount) return trim()

    val result = words.toMutableList()
    var start = 0
    while (start <= result.size - minimumWords * minimumCount) {
        var collapsed = false
        val maxBlockSize = (result.size - start) / minimumCount
        for (blockSize in minimumWords..maxBlockSize) {
            val block = result.subList(start, start + blockSize).map(::streamingWordKey)
            var repetitions = 1
            while (start + (repetitions + 1) * blockSize <= result.size) {
                val nextStart = start + repetitions * blockSize
                val nextBlock = result.subList(nextStart, nextStart + blockSize).map(::streamingWordKey)
                if (nextBlock != block) break
                repetitions++
            }
            if (repetitions >= minimumCount) {
                result.subList(start + blockSize, start + repetitions * blockSize).clear()
                collapsed = true
                break
            }
        }
        if (!collapsed) start++
    }
    return result.joinToString(" ")
}

internal fun speechCommandAlternatives(value: String): List<String> {
    val normalized = value.normalizeWhisperTranscript().trim()
    val corrected = normalized
        .replace(SPLIT_DELETE_COMMAND, "$1удали ")
        .replace(SPLIT_SHOW_COMMAND, "$1покажи ")
        .replace(SPLIT_NOTE_WORD, "$1заметку")
        .trim()
    return listOf(corrected, normalized).filter(String::isNotBlank).distinct()
}

internal fun contextualWhisperPrompt(hints: List<String>): String {
    val cleanHints = hints.asSequence()
        .map { hint ->
            hint.replace(CONTEXT_UNSAFE_CHARACTERS, " ")
                .replace(WhitespaceRegex, " ")
                .trim().take(MAX_CONTEXT_HINT_LENGTH)
        }
        .filter { it.length >= 2 }
        .distinctBy { it.lowercase().replace('ё', 'е') }
        .take(MAX_CONTEXT_HINTS)
        .toList()
    return if (cleanHints.isEmpty()) RUSSIAN_WHISPER_PROMPT else {
        "$RUSSIAN_WHISPER_PROMPT Слова пользователя: ${cleanHints.joinToString(", ")}."
    }
}

internal fun mergeTranscripts(current: String, incoming: String): String {
    val existing = current.trim()
    val next = incoming.trim()
    if (existing.isEmpty()) return next
    if (next.isEmpty()) return existing

    val existingWords = existing.split(WhitespaceRegex)
    val nextWords = next.split(WhitespaceRegex)
    val maxOverlap = minOf(existingWords.size, nextWords.size)
    val overlap = (maxOverlap downTo 1).firstOrNull { count ->
        existingWords.takeLast(count).map(::streamingWordKey) ==
            nextWords.take(count).map(::streamingWordKey)
    } ?: 0
    val suffix = nextWords.drop(overlap).joinToString(" ")
    val continuingText = existing.trimEnd(' ', '.', ',', ';', ':', '!', '?')
    return if (suffix.isEmpty()) existing else "$continuingText $suffix"
}

private fun streamingWordKey(value: String): String =
    value.trim { !it.isLetterOrDigit() }.lowercase().replace('ё', 'е')

private val WhitespaceRegex = Regex("\\s+")
private val TranscriptWordRegex = Regex("[\\p{L}\\p{N}]+")
private val CONTEXT_UNSAFE_CHARACTERS = Regex("[^\\p{L}\\p{N} -]+")
private val SPLIT_DELETE_COMMAND = Regex("(?iu)^(\\s*(?:пожалуйста[\\s,]+)?)(?:у\\s+дали|удали\\s+те)[\\s,.:;!?]*")
private val SPLIT_SHOW_COMMAND = Regex("(?iu)^(\\s*(?:пожалуйста[\\s,]+)?)по\\s+кажи[\\s,.:;!?]*")
private val SPLIT_NOTE_WORD = Regex("(?iu)^((?:\\s*пожалуйста[\\s,]+)?(?:создай|создать|добавь|добавить|запиши|записать|сохрани|сохранить)\\s+)за\\s+метку(?=\\s|$)")
private const val MAX_CONTEXT_HINTS = 16
private const val MAX_CONTEXT_HINT_LENGTH = 32
private const val MIN_REPETITION_WORDS = 2
private const val MIN_REPETITION_COUNT = 3
private const val MIN_AGGRESSIVE_REPETITION_WORDS = 3
private const val MIN_AGGRESSIVE_REPETITION_COUNT = 2
private const val MIN_PARTIAL_REPEAT_SOURCE_WORDS = 3
private const val MAX_PARTIAL_REPEAT_WORDS = 8
private const val MIN_PARTIAL_WORD_LENGTH = 2

internal fun String.normalizeWhisperCommand(): String {
    val match = REMIND_COMMAND_VARIANT.find(this)
    val commandNormalized = if (match != null) {
        val prefix = match.groupValues[1]
        val replacement = if (match.groupValues[2].firstOrNull()?.isUpperCase() == true) "Напомни" else "напомни"
        replaceRange(match.range, prefix + replacement + if (match.range.last < lastIndex) " " else "")
    } else {
        this
    }
    return commandNormalized
        .correctDativeName(Regex("(?iu)^(\\s*(?:пожалуйста[\\s,]+)?напомни\\s+)(катя|ваня)(?=\\s|[,.:;!?]|$)"))
        .correctDativeName(Regex("(?iu)((?<![\\p{L}\\p{N}_])(?:позвонить|написать|сказать|ответить|напомнить|отправить)\\s+)(катя|ваня)(?=\\s|[,.:;!?]|$)"))
}

private val REMIND_COMMAND_VARIANT = Regex(
    "(?iu)^(\\s*(?:пожалуйста[\\s,]+)?)" +
        "(напом\\s*(?:ни|ню|ня|не|ните)|на[\\s-]+пом(?:ни|ню|ня|не|ните))" +
        "(?=\\s|[,.:;!?]|$)[\\s,.:;!?]*",
)

private fun String.normalizeFolderName(): String {
    val folderCommand = Regex(
        "(?iu)^(\\s*(?:пожалуйста[\\s,]+)?(?:создай|создать|добавь|добавить)\\s+папк[ау]\\s+)(.+?)([.!?]?\\s*)$",
    )
    val match = folderCommand.find(this) ?: return this
    val group = match.groups[2] ?: return this
    return replaceRange(group.range, normalizeFolderWords(group.value))
}

private fun String.normalizeReminderTimePhrase(): String =
    replace(COMMA_BEFORE_RELATIVE_DATE, " ")
        .replace(MISSING_PREPOSITION_BEFORE_TIME) { match -> "${match.groupValues[1]} в " }

private fun normalizeFolderWords(value: String): String {
    val result = StringBuilder(value.length)
    var copiedUntil = 0
    Regex("[\\p{L}Ёё-]+").findAll(value).forEach { word ->
        result.append(value, copiedUntil, word.range.first)
        result.append(folderWordInNominative(word.value))
        copiedUntil = word.range.last + 1
    }
    result.append(value, copiedUntil, value.length)
    return result.toString()
}

private fun folderWordInNominative(value: String): String {
    val lower = value.lowercase()
    val corrected = FOLDER_NOMINATIVE_FORMS[lower] ?: when {
        lower.length > 3 && lower.endsWith("ую") -> lower.dropLast(2) + "ая"
        lower.length > 3 && lower.endsWith("юю") -> lower.dropLast(2) + "яя"
        else -> lower
    }
    return corrected.replaceFirstChar { if (value.first().isUpperCase()) it.uppercase() else it.toString() }
}

private val FOLDER_NOMINATIVE_FORMS = mapOf(
    "личную" to "личное",
    "развлечения" to "развлечение",
    "работу" to "работа",
    "учёбу" to "учёба",
    "учебу" to "учеба",
    "семью" to "семья",
    "кухню" to "кухня",
    "музыку" to "музыка",
    "книгу" to "книга",
    "мою" to "моя",
    "твою" to "твоя",
    "нашу" to "наша",
    "вашу" to "ваша",
)

private val COMMA_BEFORE_RELATIVE_DATE = Regex(
    "(?iu),\\s*(?=(?:через|сегодня|завтра|послезавтра)(?=\\s|$))",
)
private val MISSING_PREPOSITION_BEFORE_TIME = Regex(
    "(?iu)(?<![\\p{L}\\p{N}_])(дня|дней|недели|недель|месяца|месяцев|сегодня|завтра|послезавтра)[,;]?\\s+" +
        "(?=(?:\\d{1,2}|один|два|три|четыре|пять|шесть|семь|восемь|девять|десять|" +
        "одиннадцать|двенадцать|тринадцать|четырнадцать|пятнадцать|шестнадцать|" +
        "семнадцать|восемнадцать|девятнадцать|двадцать)\\s+(?:утра|дня|вечера|ночи)(?=\\s|[.,;:!?]|$))",
)

private fun String.correctDativeName(pattern: Regex): String {
    val match = pattern.find(this) ?: return this
    val group = match.groups[2] ?: return this
    val corrected = when (group.value.lowercase()) {
        "катя" -> "кате"
        "ваня" -> "ване"
        else -> return this
    }.replaceFirstChar { if (group.value.first().isUpperCase()) it.uppercase() else it.toString() }
    return replaceRange(group.range, corrected)
}

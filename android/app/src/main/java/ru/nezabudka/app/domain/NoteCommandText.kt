package ru.nezabudka.app.domain

fun extractNoteParts(value: String): Pair<String?, String?> {
    val createAndAddToQuotedFolder = Regex(
        "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:создай|создать|добавь|добавить|запиши|записать)" +
            "\\s+заметк[\\p{L}]*\\s+(?:и\\s+)?(?:добавь|добавить|помести|поместить)" +
            "\\s+(?:(?:е[её]|заметк[\\p{L}]*)\\s+)?в\\s+папк(?:у|е)\\s+[«\"](.+?)[»\"]" +
            "\\s*[,.:;—–-]?\\s+(.+)$",
    ).find(value)
    if (createAndAddToQuotedFolder != null) {
        return cleanQuoted(createAndAddToQuotedFolder.groupValues[1]) to
            cleanQuoted(createAndAddToQuotedFolder.groupValues[2])
    }
    val createAndAddToSingleWordFolder = Regex(
        "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:создай|создать|добавь|добавить|запиши|записать)" +
            "\\s+заметк[\\p{L}]*\\s+(?:и\\s+)?(?:добавь|добавить|помести|поместить)" +
            "\\s+(?:(?:е[её]|заметк[\\p{L}]*)\\s+)?в\\s+папк(?:у|е)\\s+([^\\s«»\"]+)\\s+(.+)$",
    ).find(value)
    if (createAndAddToSingleWordFolder != null) {
        return cleanQuoted(createAndAddToSingleWordFolder.groupValues[1]) to
            cleanQuoted(createAndAddToSingleWordFolder.groupValues[2])
    }
    val createInQuotedFolder = Regex(
        "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:создай|создать|добавь|добавить|запиши|записать)\\s+заметк[\\p{L}]*\\s+в\\s+папк(?:у|е)\\s+[«\"](.+?)[»\"]\\s*[,.:;—–-]?\\s+(.+)$",
    ).find(value)
    if (createInQuotedFolder != null) {
        return cleanQuoted(createInQuotedFolder.groupValues[1]) to createInQuotedFolder.groupValues[2].trim()
    }
    val createInFolderWithSeparator = Regex(
        "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:создай|создать|добавь|добавить|запиши|записать)\\s+заметк[\\p{L}]*\\s+в\\s+папк(?:у|е)\\s+(.+?)\\s*[.:;—–-]\\s+(.+)$",
    ).find(value)
    if (createInFolderWithSeparator != null) {
        return cleanQuoted(createInFolderWithSeparator.groupValues[1]) to createInFolderWithSeparator.groupValues[2].trim()
    }
    val createInSingleWordFolder = Regex(
        "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(?:создай|создать|добавь|добавить|запиши|записать)" +
            "\\s+заметк[\\p{L}]*\\s+в\\s+папк(?:у|е)\\s+([^\\s«»\"]+)\\s+(.+)$",
    ).find(value)
    if (createInSingleWordFolder != null) {
        return cleanQuoted(createInSingleWordFolder.groupValues[1]) to
            cleanQuoted(createInSingleWordFolder.groupValues[2])
    }
    val moveQuoted = Regex("(?iu)(?:и\\s+)?помести\\s+заметк[\\p{L}]*\\s+в\\s+папк(?:у|е)\\s+[«\"](.+?)[»\"]").find(value)
    if (moveQuoted != null) {
        val text = cleanNoteInstruction(value.removeRange(moveQuoted.range))
        return cleanQuoted(moveQuoted.groupValues[1]) to text
    }
    val moveAtEnd = Regex(
        "(?iu)(?:[,;]\\s*|\\s+и\\s+)(?:помести|поместить|добавь|добавить)" +
            "\\s+(?:(?:е[её]|заметк[\\p{L}]*)\\s+)?в\\s+папк(?:у|е)\\s+(.+?)[.!]?\\s*$",
    ).find(value)
    if (moveAtEnd != null) {
        val text = cleanNoteInstruction(value.removeRange(moveAtEnd.range))
        return cleanQuoted(moveAtEnd.groupValues[1]) to text
    }
    val moveAtStart = Regex("(?iu)^\\s*помести\\s+заметк[\\p{L}]*\\s+в\\s+папк(?:у|е)\\s+(.+?)[,;:—–-]\\s*(.+)$").find(value)
    if (moveAtStart != null) return cleanQuoted(moveAtStart.groupValues[1]) to cleanNoteInstruction(moveAtStart.groupValues[2])
    val patterns = listOf(
        Regex("(?i)^.*?(?<![\\p{L}\\p{N}_])в\\s+папк(?:у|е)\\s+[«\"]?(.+?)[»\"]?\\s+(?<![\\p{L}\\p{N}_])заметк[\\p{L}\\p{N}_]*\\s*[:—–-]?\\s*(.+)$"),
        Regex("(?i)^.*?(?<![\\p{L}\\p{N}_])заметк[\\p{L}\\p{N}_]*\\s*[:—–-]?\\s*(.+?)\\s+(?<![\\p{L}\\p{N}_])в\\s+папк(?:у|е)\\s+[«\"]?(.+?)[»\"]?\\s*$"),
        Regex("(?i)^.*?(?<![\\p{L}\\p{N}_])заметк[\\p{L}\\p{N}_]*\\s+в\\s+папк(?:у|е)\\s+[«\"]?(.+?)[»\"]?\\s*[:—–-]\\s*(.+)$"),
    )
    patterns.forEachIndexed { index, regex ->
        regex.find(value)?.let {
            return if (index == 1) cleanQuoted(it.groupValues[2]) to cleanQuoted(it.groupValues[1])
            else cleanQuoted(it.groupValues[1]) to cleanQuoted(it.groupValues[2])
        }
    }
    val text = Regex("(?i)(?<![\\p{L}\\p{N}_])заметк[\\p{L}\\p{N}_]*\\s*[:—–-]?\\s*(.+)$")
        .find(value)?.groupValues?.get(1)?.let(::cleanQuoted)
    return null to text
}

private fun cleanNoteInstruction(value: String): String? = value
    .replace(Regex("(?iu)^\\s*(?:создай|создать|добавь|добавить|запиши|записать)?\\s*заметк[\\p{L}]*\\s*[:—–-]?\\s*"), "")
    .trim().trim(',', ';', '.', '—', '–', '-').trim().ifBlank { null }

private fun cleanQuoted(value: String): String = value.trim().trim('«', '»', '"').trim()

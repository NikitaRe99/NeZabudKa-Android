package ru.nezabudka.app.domain

/** Accept grammatical request variants only at a command boundary, never inside its payload. */
fun normalizeCommandVerbForm(text: String): String {
    val match = COMMAND_VERB_FORM.find(text) ?: return text
    val verb = match.groups[1] ?: return text
    val replacement = when (verb.value.lowercase()) {
        "отобразить" -> "покажи"
        "показать" -> "покажи"
        "вывести" -> "выведи"
        "зафиксировать", "зафиксируем" -> "зафиксируй"
        "записать", "запишем" -> "запиши"
        "сохранить", "сохраним" -> "сохрани"
        else -> return text
    }
    return text.replaceRange(verb.range, replacement)
}

private val COMMAND_VERB_FORM = Regex(
    "(?iu)^\\s*(?:пожалуйста[\\s,]+)?(" +
        "(?:отобразить|показать|вывести)(?=[\\s,:;]+(?:мои\\s+)?(?:дела|задачи|напоминания|расписание|список\\s+(?:дел|задач|напоминаний))(?=\\s|[,.!?;:]|$))|" +
        "(?:зафиксировать|зафиксируем|записать|запишем|сохранить|сохраним)" +
        "(?=[\\s,:;]+(?:(?:такую|следующую)\\s+)?(?:мысль|запись|заметку)(?=\\s|[,:;]|$))" +
        ")",
)

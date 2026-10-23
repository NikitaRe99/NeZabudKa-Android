package ru.nezabudka.app.speech

import ru.nezabudka.app.domain.SpokenSymbol

/** One pass prevents replacements from becoming new spoken commands. Long phrases win. */
internal fun replaceSpokenSymbols(text: String, rules: List<SpokenSymbol>): String {
    val entries = rules.filter { it.phrase.isNotBlank() && it.symbol.isNotEmpty() }.sortedByDescending { it.phrase.length }
    if (entries.isEmpty()) return text
    val alternatives = entries.joinToString("|") { Regex.escape(it.phrase.trim()) }
    val regex = Regex("(?<![\\p{L}\\p{N}_])($alternatives)(?![\\p{L}\\p{N}_])([,.!?…])?", RegexOption.IGNORE_CASE)
    val result = regex.replace(text) { match ->
        val rule = entries.first { it.phrase.trim().equals(match.groupValues[1], true) }
        rule.symbol + if (rule.symbol.any(Char::isLetterOrDigit)) match.groupValues[2] else ""
    }
    return result.replace(Regex("[ \\t]+([,.;:!?…»)])"), "$1")
        .replace(Regex("([«(])[ \\t]+"), "$1")
        .replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
        .replace(Regex("([:;—])[,.]"), "$1")
        .replace(Regex("[ \\t]{2,}"), " ").trim()
}

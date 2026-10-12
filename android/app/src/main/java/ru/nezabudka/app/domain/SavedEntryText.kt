package ru.nezabudka.app.domain

/** Shared presentation rule at the persistence boundary, for voice and keyboard input alike. */
fun normalizeSavedEntryText(value: String): String {
    var start = 0
    while (start < value.length) {
        val character = value[start]
        val numericSign = character in "+-−" && value.getOrNull(start + 1)?.isDigit() == true
        if (numericSign || (!character.isWhitespace() && !isLeadingPunctuation(character))) break
        start++
    }
    val clean = value.substring(start).trimEnd()
    val firstLetter = clean.indexOfFirst(Char::isLetter)
    if (firstLetter < 0) return clean
    return clean.replaceRange(firstLetter, firstLetter + 1, clean[firstLetter].uppercase())
}

private fun isLeadingPunctuation(character: Char): Boolean = Character.getType(character) in setOf(
    Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
    Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt(),
)

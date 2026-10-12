package ru.nezabudka.app.speech

internal data class PunctuationWord(val value: String, val start: Int, val endExclusive: Int)

internal fun punctuationWords(text: String): List<PunctuationWord> =
    PUNCTUATION_WORD.findAll(text).map { PunctuationWord(it.value, it.range.first, it.range.last + 1) }.toList()

/** Only comma-only gaps may be rewritten. Numbers, links, quotes and other signs stay intact. */
internal fun applyPredictedCommas(text: String, words: List<PunctuationWord>, commas: List<Boolean?>): String {
    require(words.size == commas.size)
    if (words.isEmpty()) return text
    return buildString {
        append(text.substring(0, words.first().start))
        words.forEachIndexed { index, word ->
            append(word.value)
            val end = words.getOrNull(index + 1)?.start ?: text.length
            val gap = text.substring(word.endExclusive, end)
            val prediction = commas[index]
            if (index < words.lastIndex && prediction != null &&
                gap.isNotEmpty() && gap.all { it == ',' || it.isWhitespace() } &&
                !gap.contains('\n') && !gap.contains('\r')) {
                if (prediction) append(',')
                append(gap.filterNot { it == ',' }.ifEmpty { " " })
            } else append(gap)
        }
    }
}

// Treat decimal numbers, URLs and email addresses as indivisible original text.
private val PUNCTUATION_WORD = Regex(
    "https?://[^\\s]+|[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}|" +
        "\\p{N}+(?:[.,]\\p{N}+)+|[\\p{L}\\p{M}\\p{N}]+(?:[-’'][\\p{L}\\p{M}\\p{N}]+)*",
)

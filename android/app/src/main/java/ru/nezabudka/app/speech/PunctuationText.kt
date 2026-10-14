package ru.nezabudka.app.speech

internal data class PunctuationWord(val value: String, val start: Int, val endExclusive: Int)

internal data class FullPunctuationPrediction(val mark: String?)

/** Group casing variants before selecting a mark. Hyphens inside words are never inferred. */
internal fun fullPunctuationPrediction(scores: FloatArray, labels: List<String>): FullPunctuationPrediction {
    require(scores.size == labels.size)
    val maximum = scores.max()
    val probabilities = scores.map { kotlin.math.exp((it - maximum).toDouble()) }
    val total = probabilities.sum()
    val groups = mutableMapOf<String, Double>()
    labels.forEachIndexed { index, label ->
        val group = label.substringAfterLast('_')
        groups[group] = groups.getOrDefault(group, 0.0) + probabilities[index] / total
    }
    val best = groups.maxBy { it.value }
    val marks = mapOf("COMMA" to ",", "PERIOD" to ".", "QUESTION" to "?", "VOSKL" to "!",
        "DVOETOCHIE" to ":", "PERIODCOMMA" to ";", "TIRE" to "—", "QUESTIONVOSKL" to "?!", "MNOGOTOCHIE" to "…")
    val threshold = if (best.key == "COMMA") 0.55 else 0.65
    val mark = if (best.value >= threshold) marks[best.key] else null
    return FullPunctuationPrediction(mark)
}

/** Rebuild from original slices; never regenerate words from tokenizer pieces. */
internal fun applyFullPunctuation(text: String, words: List<PunctuationWord>, predictions: List<FullPunctuationPrediction?>): String {
    require(words.size == predictions.size)
    if (words.isEmpty()) return text
    var sentenceStart = true
    return buildString {
        append(text.substring(0, words.first().start))
        words.forEachIndexed { index, word ->
            val prediction = predictions[index]
            val protected = word.value.contains("://") || word.value.contains('@')
            append(if (!protected && sentenceStart && word.value.none(Char::isUpperCase)) word.value.replaceFirstChar { it.titlecase() } else word.value)
            val next = words.getOrNull(index + 1)
            val gap = text.substring(word.endExclusive, next?.start ?: text.length)
            val numericBoundary = word.value.firstOrNull()?.isDigit() == true && next?.value?.firstOrNull()?.isDigit() == true
            val replaceable = !protected && !numericBoundary && gap.all { it == ',' || it.isWhitespace() } && '\n' !in gap && '\r' !in gap
            val mark = prediction?.mark
            val terminal = mark in setOf(".", "?", "!", "?!", "…")
            if (replaceable && mark != null && (next != null || terminal)) {
                append(if (mark == "—") " —" else mark)
                if (next != null) append(gap.filterNot { it == ',' }.ifEmpty { " " }) else append(gap.filterNot { it == ',' })
                sentenceStart = terminal
            } else {
                append(gap)
                sentenceStart = gap.any { it in ".!?…\n\r" }
            }
        }
    }
}

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

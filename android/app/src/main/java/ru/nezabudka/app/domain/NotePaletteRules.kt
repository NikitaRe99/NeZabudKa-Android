package ru.nezabudka.app.domain

const val MAX_NOTE_PALETTE_SIZE = 64

/** Keep positions stable: notes store palette indices, including custom additions. */
fun normalizeNotePalette(values: List<String>, fallback: List<String>, defaultColor: String): List<String> {
    val colors = values.take(MAX_NOTE_PALETTE_SIZE).mapIndexed { index, raw ->
        val value = raw.trim().uppercase().let { if (it.startsWith('#')) it else "#$it" }
        value.takeIf { it.matches(Regex("#(?:[0-9A-F]{6}|[0-9A-F]{8})")) }
            ?: fallback.getOrElse(index) { defaultColor }
    }
    return colors + fallback.drop(colors.size)
}

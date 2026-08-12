package ru.nezabudka.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import ru.nezabudka.app.domain.DARK_NOTE_PALETTE
import ru.nezabudka.app.domain.LIGHT_NOTE_PALETTE
import ru.nezabudka.app.ui.AppTheme

internal data class NoteColorOption(
    val value: String?,
    val color: Color,
)

private const val THEME_COLOR_PREFIX = "theme-note:"

@Composable
internal fun noteColorPalette(): List<NoteColorOption> {
    val colors = AppTheme.tokens.colors
    return remember(colors) {
        listOf(NoteColorOption(null, colors.noteDefault)) +
            colors.notePalette.mapIndexed { index, color ->
                NoteColorOption("$THEME_COLOR_PREFIX$index", color)
            }
    }
}

@Composable
internal fun noteColorPaletteRows(): List<List<NoteColorOption>> {
    val palette = noteColorPalette()
    return remember(palette) { palette.chunked(4) }
}

@Composable
internal fun resolveNoteColor(value: String?): Color {
    val colors = AppTheme.tokens.colors
    val index = notePaletteIndex(value)
    return index?.let { colors.notePalette.getOrNull(it) } ?: colors.noteDefault
}

internal fun isSelectedNoteColor(storedValue: String?, optionValue: String?): Boolean =
    when {
        storedValue == null || optionValue == null -> storedValue == optionValue
        else -> notePaletteIndex(storedValue) == notePaletteIndex(optionValue)
    }

internal fun notePaletteIndex(value: String?): Int? {
    val normalized = value?.trim().orEmpty()
    if (normalized.startsWith(THEME_COLOR_PREFIX)) {
        return normalized.removePrefix(THEME_COLOR_PREFIX).toIntOrNull()
    }
    return LIGHT_NOTE_PALETTE.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        .takeIf { it >= 0 }
        ?: DARK_NOTE_PALETTE.indexOfFirst { it.equals(normalized, ignoreCase = true) }
            .takeIf { it >= 0 }
}

package ru.nezabudka.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

/** Shared scrollable picker for the editor and bulk selection. */
@Composable
internal fun NoteColorGrid(selectedValue: String?, choose: (String?) -> Unit, modifier: Modifier = Modifier) {
    val theme = AppTheme.tokens
    Column(modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        noteColorPaletteRows().forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { option ->
                    val checked = isSelectedNoteColor(selectedValue, option.value)
                    val label = option.value?.let { "Цвет заметки ${(notePaletteIndex(it) ?: 0) + 1}" } ?: "Без цвета"
                    Box(Modifier.size(48.dp).clip(CircleShape).background(option.color)
                        .border(if (checked) 3.dp else 1.dp, if (checked) theme.colors.accent else theme.colors.outline, CircleShape)
                        .clickable(role = Role.RadioButton) { choose(option.value) }
                        .semantics { contentDescription = label; selected = checked })
                }
                repeat(4 - row.size) { Box(Modifier.size(48.dp)) }
            }
        }
    }
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
        else -> notePaletteIndex(storedValue)?.let { it == notePaletteIndex(optionValue) } == true
    }

internal fun notePaletteIndex(value: String?): Int? {
    val normalized = value?.trim().orEmpty()
    if (normalized.startsWith(THEME_COLOR_PREFIX)) {
        return normalized.removePrefix(THEME_COLOR_PREFIX).toIntOrNull()?.takeIf { it >= 0 }
    }
    return LIGHT_NOTE_PALETTE.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        .takeIf { it >= 0 }
        ?: DARK_NOTE_PALETTE.indexOfFirst { it.equals(normalized, ignoreCase = true) }
            .takeIf { it >= 0 }
}

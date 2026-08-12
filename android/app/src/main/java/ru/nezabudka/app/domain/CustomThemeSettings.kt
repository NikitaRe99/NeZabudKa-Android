package ru.nezabudka.app.domain

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class CustomThemeSettings(
    val base: String = "light",
    val background: String = "#F2F6FE",
    val navigation: String = "#FFFFFF",
    val card: String = "#FFFFFF",
    val notice: String = "#E2EEFF",
    val input: String = "#FFFFFF",
    val modal: String = "#FFFFFF",
    val folderArea: String = "#EEF3FA",
    val selected: String = "#E2EEFF",
    val accent: String = "#1782F2",
    val onAccent: String = "#FFFFFF",
    val talkButton: String = "#1782F2",
    val onTalkButton: String = "#FFFFFF",
    val textPrimary: String = "#121418",
    val textSecondary: String = "#5F6875",
    val cursor: String? = null,
    val placeholder: String = "#89929E",
    val outline: String = "#D7DDE5",
    val danger: String = "#BA1A1A",
    val dangerContainer: String = "#FFDAD6",
    val onDangerContainer: String = "#410002",
    val success: String = "#2E7D32",
    val warning: String = "#FFB300",
    val scrim: String = "#75000000",
    val noteDefault: String = "#FFFFFF",
    val notePalette: List<String> = LIGHT_NOTE_PALETTE,
    val fontFamily: String = "sans",
    val fontScale: Float = 1f,
    val cornerScale: Float = 1f,
    val shadowsEnabled: Boolean = true,
)

fun defaultCustomTheme(base: String): CustomThemeSettings =
    if (base == "dark") {
        CustomThemeSettings(
            base = "dark",
            background = "#18191B",
            navigation = "#29292B",
            card = "#29292B",
            notice = "#3B3B52",
            input = "#29292B",
            modal = "#29292B",
            folderArea = "#232325",
            selected = "#3B3B52",
            accent = "#6868F4",
            onAccent = "#FFFFFF",
            talkButton = "#6868F4",
            onTalkButton = "#FFFFFF",
            textPrimary = "#F2F2F4",
            textSecondary = "#A8A8AE",
            cursor = "#F2F2F4",
            placeholder = "#77777D",
            outline = "#3B3B3F",
            danger = "#FFB4AB",
            dangerContainer = "#93000A",
            onDangerContainer = "#FFDAD6",
            success = "#81C784",
            warning = "#FFD54F",
            scrim = "#99000000",
            noteDefault = "#29292B",
            notePalette = DARK_NOTE_PALETTE,
        )
    } else {
        CustomThemeSettings(cursor = "#121418")
    }

fun automaticContentColorHex(background: String): String {
    val color = parseRgb(background) ?: return "#F7F7F7"
    val luminance = relativeLuminance(color)
    val dark = Triple(23, 23, 23)
    val light = Triple(247, 247, 247)
    val darkContrast = (luminance + 0.05) / (relativeLuminance(dark) + 0.05)
    val lightContrast = (relativeLuminance(light) + 0.05) / (luminance + 0.05)
    return if (darkContrast >= lightContrast) "#171717" else "#F7F7F7"
}

fun automaticPlaceholderHex(textSecondary: String, background: String): String {
    val foreground = parseRgb(textSecondary) ?: return textSecondary
    val surface = parseRgb(background) ?: return textSecondary
    fun blend(foregroundChannel: Int, surfaceChannel: Int): Int =
        (foregroundChannel * 0.7f + surfaceChannel * 0.3f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(
        blend(foreground.first, surface.first),
        blend(foreground.second, surface.second),
        blend(foreground.third, surface.third),
    )
}

fun CustomThemeSettings.withAutomaticDependentColors(): CustomThemeSettings = copy(
    modal = background,
    navigation = card,
    notice = selected,
    onAccent = automaticContentColorHex(accent),
    talkButton = accent,
    onTalkButton = automaticContentColorHex(accent),
    placeholder = automaticPlaceholderHex(textSecondary, background),
    cursor = textPrimary,
    noteDefault = card,
)

private fun parseRgb(value: String): Triple<Int, Int, Int>? {
    val normalized = value.trim().removePrefix("#")
    val rgb = when (normalized.length) {
        6 -> normalized
        8 -> normalized.takeLast(6)
        else -> return null
    }
    return runCatching {
        Triple(
            rgb.substring(0, 2).toInt(16),
            rgb.substring(2, 4).toInt(16),
            rgb.substring(4, 6).toInt(16),
        )
    }.getOrNull()
}

private fun relativeLuminance(color: Triple<Int, Int, Int>): Double {
    fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * linear(color.first) +
        0.7152 * linear(color.second) +
        0.0722 * linear(color.third)
}

internal val LIGHT_NOTE_PALETTE = listOf(
    "#F28B82", "#FDCB6E", "#FFF475", "#CCFF90", "#A7FFEB", "#CBF0F8",
    "#AECBFA", "#D7AEFB", "#FDCFE8", "#E6C9A8", "#E8EAED",
)

internal val DARK_NOTE_PALETTE = listOf(
    "#6F3B38", "#73572F", "#6E672F", "#46632E", "#2E625A", "#315E69",
    "#3D5575", "#594873", "#704A61", "#69523E", "#484A4E",
)

package ru.nezabudka.app.ui

import android.graphics.Color.parseColor
import android.os.Build
import android.view.Window
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import ru.nezabudka.app.domain.AppSettings
import ru.nezabudka.app.domain.CustomThemeSettings
import ru.nezabudka.app.domain.defaultCustomTheme
import ru.nezabudka.app.domain.withAutomaticDependentColors
import ru.nezabudka.app.domain.automaticContentColorHex

/** System bar colors are configurable only before Android 15's enforced edge-to-edge mode. */
@Suppress("DEPRECATION")
internal fun applyLegacySystemBarColors(window: Window, statusBar: Int, navigationBar: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        window.statusBarColor = statusBar
        window.navigationBarColor = navigationBar
    }
}

@Immutable
data class AppSemanticColors(
    val background: Color,
    val navigation: Color,
    val card: Color,
    val notice: Color,
    val input: Color,
    val modal: Color,
    val folderArea: Color,
    val selected: Color,
    val accent: Color,
    val onAccent: Color,
    val talkButton: Color,
    val onTalkButton: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cursor: Color,
    val placeholder: Color,
    val outline: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val warning: Color,
    val scrim: Color,
    val noteDefault: Color,
    val notePalette: List<Color>,
)

@Immutable
data class AppShapeTokens(
    val tag: Dp,
    val noteCard: Dp,
    val card: Dp,
    val panel: Dp,
    val action: Dp,
    val dialog: Dp,
)

@Immutable
data class AppThemeTokenSet(
    val colors: AppSemanticColors,
    val shapes: AppShapeTokens,
    val dark: Boolean,
    val shadowsEnabled: Boolean,
)

private val LocalAppThemeTokens = staticCompositionLocalOf<AppThemeTokenSet> {
    error("AppThemeTokens are not provided")
}

object AppTheme {
    val tokens: AppThemeTokenSet
        @Composable get() = LocalAppThemeTokens.current
}

internal val LocalPopupNoticesEnabled = staticCompositionLocalOf { true }

@Composable
fun appShadowElevation(defaultElevation: Dp): Dp =
    if (AppTheme.tokens.shadowsEnabled) defaultElevation else 0.dp

@Composable
fun LocalAssistantTheme(
    settings: AppSettings,
    reduceEffects: Boolean = false,
    content: @Composable () -> Unit,
) {
    val custom = settings.customTheme
    val dark = remember(settings.theme, custom.base) {
        when (settings.theme) {
            "dark" -> true
            "custom" -> custom.base == "dark"
            else -> false
        }
    }
    val baseScheme = if (dark) DarkAppColorScheme else LightAppColorScheme
    val semantic = remember(settings.theme, custom, dark) {
        if (settings.theme == "custom") {
            custom.withAutomaticDependentColors().toSemanticColors()
        } else {
            defaultSemanticColors(dark)
        }
    }
    val colorScheme = remember(semantic, baseScheme) { semantic.toColorScheme(baseScheme) }
    val typography = remember(settings.theme, custom.fontScale, custom.fontFamily) {
        scaledTypography(
            scale = if (settings.theme == "custom") custom.fontScale else 1f,
            familyName = if (settings.theme == "custom") custom.fontFamily else "sans",
        )
    }
    val cornerScale = if (settings.theme == "custom") custom.cornerScale else 1f
    val shapeTokens = remember(cornerScale) {
        AppShapeTokens(
            tag = 10.dp * cornerScale,
            noteCard = 22.dp * cornerScale,
            card = 22.dp * cornerScale,
            panel = 24.dp * cornerScale,
            action = 26.dp * cornerScale,
            dialog = 28.dp * cornerScale,
        )
    }
    val materialShapes = remember(cornerScale) {
        Shapes(
            extraSmall = RoundedCornerShape(4.dp * cornerScale),
            small = RoundedCornerShape(8.dp * cornerScale),
            medium = RoundedCornerShape(22.dp * cornerScale),
            large = RoundedCornerShape(24.dp * cornerScale),
            extraLarge = RoundedCornerShape(28.dp * cornerScale),
        )
    }
    val shadowsEnabled = !reduceEffects && custom.shadowsEnabled
    val tokens = remember(semantic, shapeTokens, dark, shadowsEnabled) {
        AppThemeTokenSet(
            colors = semantic,
            shapes = shapeTokens,
            dark = dark,
            shadowsEnabled = shadowsEnabled,
        )
    }
    val uiHaptic = rememberUiHapticFeedback(settings.uiHapticsEnabled)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPopupNoticesEnabled provides !settings.hidePopupNotices,
        androidx.compose.ui.platform.LocalHapticFeedback provides uiHaptic,
        LocalAppThemeTokens provides tokens,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = materialShapes,
            content = content,
        )
    }
}

private fun defaultSemanticColors(dark: Boolean): AppSemanticColors =
    defaultCustomThemeForUi(if (dark) "dark" else "light")
        .withAutomaticDependentColors()
        .toSemanticColors()
        .let { colors ->
            if (dark) colors else colors.copy(
                talkButton = "#3395F7".themeColor(),
                onTalkButton = automaticContentColorHex("#3395F7").themeColor(),
            )
        }

private fun defaultCustomThemeForUi(base: String): CustomThemeSettings =
    defaultCustomTheme(base)

private fun CustomThemeSettings.toSemanticColors() = AppSemanticColors(
    background = background.themeColor(),
    navigation = navigation.themeColor(),
    card = card.themeColor(),
    notice = notice.themeColor(),
    input = input.themeColor(),
    modal = modal.themeColor(),
    folderArea = folderArea.themeColor(),
    selected = selected.themeColor(),
    accent = accent.themeColor(),
    onAccent = onAccent.themeColor(),
    talkButton = talkButton.themeColor(),
    onTalkButton = onTalkButton.themeColor(),
    textPrimary = textPrimary.themeColor(),
    textSecondary = textSecondary.themeColor(),
    cursor = (cursor ?: if (base == "dark") "#FFFFFF" else "#121418").themeColor(),
    placeholder = placeholder.themeColor(),
    outline = outline.themeColor(),
    danger = danger.themeColor(),
    dangerContainer = dangerContainer.themeColor(),
    onDangerContainer = onDangerContainer.themeColor(),
    warning = warning.themeColor(),
    scrim = scrim.themeColor(),
    noteDefault = noteDefault.themeColor(),
    notePalette = notePalette.map(String::themeColor),
)

private fun AppSemanticColors.toColorScheme(base: ColorScheme): ColorScheme = base.copy(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = selected,
    onPrimaryContainer = textPrimary,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = selected,
    onSecondaryContainer = textPrimary,
    surfaceTint = accent,
    background = background,
    onBackground = textPrimary,
    surface = modal,
    onSurface = textPrimary,
    surfaceVariant = input,
    onSurfaceVariant = textSecondary,
    surfaceContainerLowest = background,
    surfaceContainerLow = card,
    surfaceContainer = navigation,
    surfaceContainerHigh = modal,
    surfaceContainerHighest = card,
    outline = outline,
    outlineVariant = outline.copy(alpha = 0.55f),
    error = danger,
    errorContainer = dangerContainer,
    onErrorContainer = onDangerContainer,
    inverseSurface = notice,
    inverseOnSurface = textPrimary,
    inversePrimary = accent,
    scrim = scrim,
)

private fun scaledTypography(scale: Float, familyName: String): Typography {
    val base = Typography(
        headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    )
    val family = when (familyName) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    return Typography(
        displayLarge = base.displayLarge.scaled(scale, family),
        displayMedium = base.displayMedium.scaled(scale, family),
        displaySmall = base.displaySmall.scaled(scale, family),
        headlineLarge = base.headlineLarge.scaled(scale, family),
        headlineMedium = base.headlineMedium.scaled(scale, family),
        headlineSmall = base.headlineSmall.scaled(scale, family),
        titleLarge = base.titleLarge.scaled(scale, family),
        titleMedium = base.titleMedium.scaled(scale, family),
        titleSmall = base.titleSmall.scaled(scale, family),
        bodyLarge = base.bodyLarge.scaled(scale, family),
        bodyMedium = base.bodyMedium.scaled(scale, family),
        bodySmall = base.bodySmall.scaled(scale, family),
        labelLarge = base.labelLarge.scaled(scale, family),
        labelMedium = base.labelMedium.scaled(scale, family),
        labelSmall = base.labelSmall.scaled(scale, family),
    )
}

private fun TextStyle.scaled(scale: Float, family: FontFamily): TextStyle = copy(
    fontFamily = family,
    fontSize = fontSize.scaled(scale),
    lineHeight = lineHeight.scaled(scale),
)

private fun TextUnit.scaled(scale: Float): TextUnit = if (isSpecified) this * scale else this

internal fun String.themeColor(): Color = Color(parseColor(this))

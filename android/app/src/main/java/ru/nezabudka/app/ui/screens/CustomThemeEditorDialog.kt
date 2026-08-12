package ru.nezabudka.app.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import ru.nezabudka.app.domain.CustomThemeSettings
import ru.nezabudka.app.domain.automaticContentColorHex
import ru.nezabudka.app.domain.automaticPlaceholderHex
import ru.nezabudka.app.domain.defaultCustomTheme
import ru.nezabudka.app.ui.AppLayout
import ru.nezabudka.app.ui.appShadowElevation
import ru.nezabudka.app.ui.themeColor
import java.util.Locale
import kotlin.math.roundToInt

private data class ThemeColorBinding(
    val label: String,
    val value: String,
    val supportsAlpha: Boolean = false,
    val update: (String) -> Unit,
)

@Composable
internal fun CustomThemeEditorDialog(
    initial: CustomThemeSettings,
    save: (CustomThemeSettings) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var editedColor by remember { mutableStateOf<ThemeColorBinding?>(null) }
    val replaceBase: (String) -> Unit = { base ->
        draft = defaultCustomTheme(base).copy(
            fontFamily = draft.fontFamily,
            fontScale = draft.fontScale,
            cornerScale = draft.cornerScale,
            shadowsEnabled = draft.shadowsEnabled,
        )
    }
    val saveAndClose = { save(draft) }

    AppDialog(
        onDismissRequest = saveAndClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        dimBackground = false,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = saveAndClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Text(
                        "Пользовательская тема",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppLayout.horizontalPadding,
                        end = AppLayout.horizontalPadding,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ThemePreview(draft)
                    }
                    item {
                        ThemeEditorSection("Основа темы") {
                            Text(
                                "Выбор основы заполняет все цвета согласованной светлой или тёмной палитрой.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = draft.base == "light",
                                    onClick = { replaceBase("light") },
                                    label = { Text("Светлая") },
                                )
                                FilterChip(
                                    selected = draft.base == "dark",
                                    onClick = { replaceBase("dark") },
                                    label = { Text("Тёмная") },
                                )
                            }
                        }
                    }
                    item {
                        ThemeEditorSection("Текст") {
                            Text("Семейство шрифта", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "sans" to "Обычный",
                                    "serif" to "С засечками",
                                    "monospace" to "Моно",
                                ).forEach { (key, title) ->
                                    FilterChip(
                                        selected = draft.fontFamily == key,
                                        onClick = { draft = draft.copy(fontFamily = key) },
                                        label = {
                                            Text(
                                                title,
                                                fontFamily = when (key) {
                                                    "serif" -> FontFamily.Serif
                                                    "monospace" -> FontFamily.Monospace
                                                    else -> FontFamily.SansSerif
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                            SliderSetting(
                                title = "Размер текста",
                                value = draft.fontScale,
                                range = 0.8f..1.4f,
                                valueText = "${(draft.fontScale * 100).roundToInt()}%",
                                onValueChange = { draft = draft.copy(fontScale = it) },
                            )
                            SliderSetting(
                                title = "Скругление элементов",
                                value = draft.cornerScale,
                                range = 0.5f..1.5f,
                                valueText = "${(draft.cornerScale * 100).roundToInt()}%",
                                onValueChange = { draft = draft.copy(cornerScale = it) },
                            )
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Тени элементов")
                                    Text(
                                        "Карточки, меню и всплывающие окна отделяются от фона тенью",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Checkbox(
                                    checked = draft.shadowsEnabled,
                                    onCheckedChange = { draft = draft.copy(shadowsEnabled = it) },
                                )
                            }
                            ThemeTypographyPreview(draft)
                        }
                    }
                    colorSection(
                        title = "Фоны и поверхности",
                        bindings = listOf(
                            ThemeColorBinding("Общий фон", draft.background) {
                                draft = draft.copy(
                                    background = it,
                                    modal = it,
                                    placeholder = automaticPlaceholderHex(draft.textSecondary, it),
                                )
                            },
                            ThemeColorBinding("Карточки и нижняя навигация", draft.card) {
                                draft = draft.copy(card = it, navigation = it, noteDefault = it)
                            },
                            ThemeColorBinding("Поля ввода и поиска", draft.input) { draft = draft.copy(input = it) },
                            ThemeColorBinding("Область папок", draft.folderArea) { draft = draft.copy(folderArea = it) },
                            ThemeColorBinding("Выбранный элемент и уведомления", draft.selected) {
                                draft = draft.copy(selected = it, notice = it)
                            },
                            ThemeColorBinding("Границы и разделители", draft.outline) { draft = draft.copy(outline = it) },
                        ),
                        edit = { editedColor = it },
                    )
                    colorSection(
                        title = "Акценты",
                        bindings = listOf(
                            ThemeColorBinding("Основной акцент и кнопка разговора", draft.accent) {
                                val contentColor = automaticContentColorHex(it)
                                draft = draft.copy(
                                    accent = it,
                                    onAccent = contentColor,
                                    talkButton = it,
                                    onTalkButton = contentColor,
                                )
                            },
                        ),
                        edit = { editedColor = it },
                    )
                    colorSection(
                        title = "Текст",
                        bindings = listOf(
                            ThemeColorBinding("Основной текст и курсор", draft.textPrimary) {
                                draft = draft.copy(textPrimary = it, cursor = it)
                            },
                            ThemeColorBinding("Второстепенный текст", draft.textSecondary) {
                                draft = draft.copy(
                                    textSecondary = it,
                                    placeholder = automaticPlaceholderHex(it, draft.background),
                                )
                            },
                        ),
                        edit = { editedColor = it },
                    )
                    colorSection(
                        title = "Состояния",
                        bindings = listOf(
                            ThemeColorBinding("Ошибка и опасное действие", draft.danger) { draft = draft.copy(danger = it) },
                            ThemeColorBinding("Фон опасного действия", draft.dangerContainer) { draft = draft.copy(dangerContainer = it) },
                            ThemeColorBinding("Текст опасного действия", draft.onDangerContainer) { draft = draft.copy(onDangerContainer = it) },
                            ThemeColorBinding("Успешное состояние", draft.success) { draft = draft.copy(success = it) },
                            ThemeColorBinding("Предупреждение и избранное", draft.warning) { draft = draft.copy(warning = it) },
                            ThemeColorBinding("Затемнение окон", draft.scrim, supportsAlpha = true) { draft = draft.copy(scrim = it) },
                        ),
                        edit = { editedColor = it },
                    )
                    item {
                        OutlinedButton(
                            onClick = { replaceBase(draft.base) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.RestartAlt, null)
                            Text("Сбросить цвета текущей основы", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }

    editedColor?.let { binding ->
        ThemeColorEditorDialog(
            binding = binding,
            dismiss = { editedColor = null },
            confirm = { color ->
                binding.update(color)
                editedColor = null
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.colorSection(
    title: String,
    bindings: List<ThemeColorBinding>,
    edit: (ThemeColorBinding) -> Unit,
) {
    item(key = "section-$title") {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
    items(bindings, key = { "$title-${it.label}" }) { binding ->
        ThemeColorRow(binding = binding, onClick = { edit(binding) })
    }
}

@Composable
private fun ThemeEditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ThemePreview(theme: CustomThemeSettings) {
    val radius = 22.dp * theme.cornerScale
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius),
        color = theme.background.themeColor(),
        shadowElevation = if (theme.shadowsEnabled) 8.dp else 0.dp,
    ) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, theme.outline.themeColor(), RoundedCornerShape(radius)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Предварительный просмотр", color = theme.textPrimary.themeColor())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(58.dp)
                    .background(theme.talkButton.themeColor(), RoundedCornerShape(26.dp * theme.cornerScale)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Говорить", color = theme.onTalkButton.themeColor())
            }
            Box(
                Modifier.size(58.dp)
                    .background(theme.selected.themeColor(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = theme.textPrimary.themeColor(), style = MaterialTheme.typography.titleLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(18.dp * theme.cornerScale),
                color = theme.card.themeColor(),
                shadowElevation = if (theme.shadowsEnabled) 4.dp else 0.dp,
            ) {
                Box(Modifier.fillMaxSize().padding(10.dp)) {
                    Text("Обычная карточка", color = theme.textPrimary.themeColor())
                }
            }
            Surface(
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(18.dp * theme.cornerScale),
                color = theme.notice.themeColor(),
                shadowElevation = if (theme.shadowsEnabled) 4.dp else 0.dp,
            ) {
                Box(Modifier.fillMaxSize().padding(10.dp)) {
                    Text("Уведомление", color = theme.textPrimary.themeColor())
                }
            }
        }
    }
    }
}

@Composable
private fun ThemeTypographyPreview(theme: CustomThemeSettings) {
    val family = previewFontFamily(theme.fontFamily)
    val inputStyle = TextStyle(
        fontFamily = family,
        fontSize = (18f * theme.fontScale).sp,
        lineHeight = (24f * theme.fontScale).sp,
    )
    val titleStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = (22f * theme.fontScale).sp,
        lineHeight = (28f * theme.fontScale).sp,
    )
    val secondaryStyle = TextStyle(
        fontFamily = family,
        fontSize = (14f * theme.fontScale).sp,
        lineHeight = (20f * theme.fontScale).sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Так будет выглядеть текст", style = titleStyle, color = theme.textPrimary.themeColor())
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp * theme.cornerScale),
            color = theme.input.themeColor(),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.outline.themeColor()),
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Новая заметка", style = inputStyle, color = theme.placeholder.themeColor())
                HorizontalDivider(color = theme.outline.themeColor().copy(alpha = 0.45f))
                Text("Купить продукты вечером", style = inputStyle, color = theme.textPrimary.themeColor())
                Text(
                    "Подсказка и введённый текст имеют одинаковый размер.",
                    style = secondaryStyle,
                    color = theme.textSecondary.themeColor(),
                )
            }
        }
    }
}

private fun previewFontFamily(value: String): FontFamily = when (value) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

@Composable
private fun ThemeColorRow(
    binding: ThemeColorBinding,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(binding.value.themeColor(), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(binding.label)
                Text(binding.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f))
            Text(valueText, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun ThemeColorEditorDialog(
    binding: ThemeColorBinding,
    dismiss: () -> Unit,
    confirm: (String) -> Unit,
) {
    var hex by remember(binding.value) { mutableStateOf(binding.value.uppercase(Locale.ROOT)) }
    var rememberedHue by remember(binding.value) {
        val values = FloatArray(3)
        val initialColor = runCatching { AndroidColor.parseColor(binding.value) }.getOrDefault(AndroidColor.BLACK)
        AndroidColor.colorToHSV(initialColor, values)
        mutableFloatStateOf(values[0])
    }
    val normalized = normalizeColor(hex, binding.supportsAlpha)
    val argb = normalized?.let { runCatching { AndroidColor.parseColor(it) }.getOrNull() }
    val alpha = argb?.ushr(24)?.and(0xFF) ?: 255
    val hsv = FloatArray(3).also { values ->
        AndroidColor.colorToHSV(argb ?: AndroidColor.BLACK, values)
    }
    val saturation = hsv[1]
    val hue = if (saturation > 0.0001f) hsv[0] else rememberedHue
    val brightness = hsv[2]
    fun updateHsv(
        a: Int = alpha,
        h: Float = hue,
        s: Float = saturation,
        v: Float = brightness,
    ) {
        rememberedHue = h.coerceIn(0f, 360f)
        val color = AndroidColor.HSVToColor(a, floatArrayOf(h, s, v))
        hex = if (binding.supportsAlpha) "#%08X".format(color)
        else "#%06X".format(color and 0xFFFFFF)
    }

    AppAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(binding.label) },
        text = {
            Column(
                Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(72.dp)
                        .background(
                            normalized?.themeColor() ?: Color.Transparent,
                            RoundedCornerShape(18.dp),
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it.take(if (binding.supportsAlpha) 9 else 7) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (binding.supportsAlpha) "Цвет #AARRGGBB" else "Цвет #RRGGBB") },
                    singleLine = true,
                    isError = normalized == null,
                )
                if (binding.supportsAlpha) {
                    HsvSlider(
                        title = "A",
                        value = alpha.toFloat(),
                        valueRange = 0f..255f,
                        valueText = alpha.toString(),
                        colors = listOf(
                            Color.Transparent,
                            Color(AndroidColor.HSVToColor(255, floatArrayOf(hue, saturation, brightness))),
                        ),
                        onValueChange = { updateHsv(a = it.roundToInt().coerceIn(0, 255)) },
                    )
                }
                HsvSlider(
                    title = "H",
                    value = hue,
                    valueRange = 0f..360f,
                    valueText = hue.roundToInt().toString(),
                    colors = (0..6).map { step ->
                        Color(AndroidColor.HSVToColor(floatArrayOf(step * 60f, 1f, 1f)))
                    },
                    onValueChange = { updateHsv(h = it) },
                )
                HsvSlider(
                    title = "S",
                    value = saturation,
                    valueRange = 0f..1f,
                    valueText = "${(saturation * 100).roundToInt()}%",
                    colors = listOf(
                        Color(AndroidColor.HSVToColor(alpha, floatArrayOf(hue, 0f, 1f))),
                        Color(AndroidColor.HSVToColor(alpha, floatArrayOf(hue, 1f, 1f))),
                    ),
                    onValueChange = { updateHsv(s = it) },
                )
                HsvSlider(
                    title = "V",
                    value = brightness,
                    valueRange = 0f..1f,
                    valueText = "${(brightness * 100).roundToInt()}%",
                    colors = listOf(
                        Color(AndroidColor.HSVToColor(alpha, floatArrayOf(hue, saturation, 0f))),
                        Color(AndroidColor.HSVToColor(alpha, floatArrayOf(hue, saturation, 1f))),
                    ),
                    onValueChange = { updateHsv(v = it) },
                )
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } },
        confirmButton = {
            TextButton(
                onClick = { normalized?.let(confirm) },
                enabled = normalized != null,
            ) { Text("Готово") }
        },
    )
}

@Composable
private fun HsvSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    colors: List<Color>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().height(8.dp)
                    .background(Brush.horizontalGradient(colors), RoundedCornerShape(4.dp)),
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

private fun normalizeColor(value: String, supportsAlpha: Boolean): String? {
    val normalized = value.trim().uppercase(Locale.ROOT).let { if (it.startsWith("#")) it else "#$it" }
    val pattern = if (supportsAlpha) ALPHA_HEX_COLOR_PATTERN else OPAQUE_HEX_COLOR_PATTERN
    return normalized.takeIf(pattern::matches)
}

private val OPAQUE_HEX_COLOR_PATTERN = Regex("#[0-9A-F]{6}")
private val ALPHA_HEX_COLOR_PATTERN = Regex("#[0-9A-F]{8}")

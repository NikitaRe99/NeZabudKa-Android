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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import ru.nezabudka.app.domain.CustomThemeSettings
import ru.nezabudka.app.domain.automaticContentColorHex
import ru.nezabudka.app.domain.automaticPlaceholderHex
import ru.nezabudka.app.domain.defaultCustomTheme
import ru.nezabudka.app.domain.withAutomaticDependentColors
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
    dismiss: () -> Unit,
    save: (CustomThemeSettings) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var editedColor by remember { mutableStateOf<ThemeColorBinding?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf<String?>(null) }
    var expandedPreview by remember { mutableStateOf(false) }
    val replaceBase: (String) -> Unit = { base ->
        draft = defaultCustomTheme(base).copy(
            fontFamily = draft.fontFamily,
            fontScale = draft.fontScale,
            cornerScale = draft.cornerScale,
            shadowsEnabled = draft.shadowsEnabled,
        )
    }
    val saveAndClose = { save(draft.withAutomaticDependentColors()) }
    val requestClose = { if (draft != initial) confirmDiscard = true else dismiss() }

    AppDialog(
        onDismissRequest = requestClose,
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
                    IconButton(onClick = requestClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Text(
                        "Пользовательская тема",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = saveAndClose) { Text("Сохранить") }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppLayout.horizontalPadding,
                        end = AppLayout.horizontalPadding,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        ThemePreview(draft.withAutomaticDependentColors(), expandedPreview) { expandedPreview = !expandedPreview }
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
                                    onClick = { if (draft.base != "light") confirmReset = "light" },
                                    label = { Text("Светлая") },
                                )
                                FilterChip(
                                    selected = draft.base == "dark",
                                    onClick = { if (draft.base != "dark") confirmReset = "dark" },
                                    label = { Text("Тёмная") },
                                )
                            }
                        }
                    }
                    item {
                        ThemeEditorSection("Шрифт и форма") {
                            Text("Семейство шрифта", style = MaterialTheme.typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            ThemeTypographyPreview(draft.withAutomaticDependentColors())
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
                            ThemeColorBinding("Карточки, сообщения и нижняя панель", draft.card) {
                                draft = draft.copy(card = it, navigation = it, noteDefault = it)
                            },
                            ThemeColorBinding("Поля ввода и поиска", draft.input) { draft = draft.copy(input = it) },
                            ThemeColorBinding("Область папок", draft.folderArea) { draft = draft.copy(folderArea = it) },
                            ThemeColorBinding("Выбранный элемент и проверка текста", draft.selected) {
                                draft = draft.copy(selected = it, notice = it)
                            },
                            ThemeColorBinding("Границы и разделители", draft.outline) { draft = draft.copy(outline = it) },
                            ThemeColorBinding("Затемнение окон", draft.scrim, supportsAlpha = true) { draft = draft.copy(scrim = it) },
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
                            ThemeColorBinding("Ошибка и удаление", draft.danger) { draft = draft.copy(danger = it) },
                            ThemeColorBinding("Избранное", draft.warning) { draft = draft.copy(warning = it) },
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
                    item {
                        ThemeEditorSection("Цвета заметок") {
                            Text("Нажмите на цвет, чтобы изменить его", style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                draft.notePalette.forEachIndexed { index, color ->
                                    Surface(onClick = {
                                        editedColor = ThemeColorBinding("Цвет заметки ${index + 1}", color) { selected ->
                                            draft = draft.copy(notePalette = draft.notePalette.mapIndexed { i, previous ->
                                                if (i == index) selected else previous
                                            })
                                        }
                                    }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Цвет заметки ${index + 1}" }, shape = CircleShape,
                                        color = color.themeColor(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                                        Box(Modifier.fillMaxSize())
                                    }
                                }
                                if (draft.notePalette.size < ru.nezabudka.app.domain.MAX_NOTE_PALETTE_SIZE) {
                                    Surface(onClick = {
                                        editedColor = ThemeColorBinding("Новый цвет заметки", draft.noteDefault) { selected ->
                                            draft = draft.copy(notePalette = draft.notePalette + selected)
                                        }
                                    }, modifier = Modifier.size(48.dp), shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Add, "Добавить цвет заметки")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { confirmReset = draft.base },
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

    if (confirmDiscard) AppAlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Сохранить изменения темы?") },
        text = { Text("Можно сохранить оформление или выйти без изменений.") },
        confirmButton = { TextButton(onClick = saveAndClose) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Не сохранять") } },
    )
    confirmReset?.let { base ->
        AppAlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("Заменить цвета темы?") },
            text = { Text("Будут восстановлены цвета выбранной основы. Шрифт, скругление и тени сохранятся.") },
            confirmButton = { TextButton(onClick = { replaceBase(base); confirmReset = null }) { Text("Заменить") } },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Отмена") } },
        )
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
        ThemeEditorSection(title) {
            if (title == "Акценты") Text("Цвет текста на кнопках и фон удаления подбираются автоматически.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            bindings.forEach { binding ->
                ThemeColorRow(binding = binding, onClick = { edit(binding) })
            }
        }
    }
}

@Composable
private fun ThemeEditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ru.nezabudka.app.ui.AppTheme.tokens.shapes.card),
        colors = CardDefaults.cardColors(containerColor = ru.nezabudka.app.ui.AppTheme.tokens.colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(4.dp)),
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
private fun ThemePreview(theme: CustomThemeSettings, expanded: Boolean, toggleExpanded: () -> Unit) {
    val radius = 22.dp * theme.cornerScale
    val family = previewFontFamily(theme.fontFamily)
    val body = TextStyle(fontFamily = family, fontSize = (14 * theme.fontScale).sp,
        lineHeight = (20 * theme.fontScale).sp)
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(radius),
        color = theme.background.themeColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.outline.themeColor()),
        shadowElevation = if (theme.shadowsEnabled) 4.dp else 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Предварительный просмотр", Modifier.weight(1f), style = body.copy(fontWeight = FontWeight.SemiBold),
                    color = theme.textPrimary.themeColor())
                TextButton(onClick = toggleExpanded) { Text(if (expanded) "Свернуть" else "Все элементы", color = theme.accent.themeColor()) }
            }
            if (expanded) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp * theme.cornerScale),
                color = theme.folderArea.themeColor()) {
                Text("Личное · Работа", Modifier.padding(12.dp), style = body, color = theme.textSecondary.themeColor())
            }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(radius), color = theme.card.themeColor(),
                shadowElevation = if (theme.shadowsEnabled) 2.dp else 0.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(20.dp).border(1.dp, theme.textSecondary.themeColor(), CircleShape))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Купить продукты вечером", style = body, color = theme.textPrimary.themeColor())
                        Text("Сегодня", style = body, color = theme.textSecondary.themeColor())
                    }
                    Icon(Icons.Default.Star, "Избранное", Modifier.size(24.dp), tint = theme.warning.themeColor())
                }
            }
            if (expanded) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp * theme.cornerScale),
                color = theme.input.themeColor(), border = androidx.compose.foundation.BorderStroke(1.dp, theme.outline.themeColor())) {
                Text("Введите команду…", Modifier.padding(12.dp), style = body, color = theme.placeholder.themeColor())
            }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp * theme.cornerScale),
                color = theme.talkButton.themeColor()) {
                Text("Удерживайте и говорите", Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = body.copy(fontWeight = FontWeight.Medium), textAlign = TextAlign.Center, color = theme.onTalkButton.themeColor())
            }
            if (expanded) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(radius), color = theme.selected.themeColor(),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.accent.themeColor().copy(alpha = 0.5f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Текст во время речи", style = body.copy(fontWeight = FontWeight.SemiBold), color = theme.textPrimary.themeColor())
                    Text("Настройте предварительный текст GigaAM", style = body, color = theme.textPrimary.themeColor())
                    Text("Открыть настройки", Modifier.align(Alignment.End), style = body, color = theme.accent.themeColor())
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(radius), color = theme.card.themeColor(),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.outline.themeColor())) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Заметка добавлена", style = body, color = theme.textPrimary.themeColor())
                    Text("Отмена", Modifier.align(Alignment.End), style = body, color = theme.accent.themeColor())
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp * theme.cornerScale),
                color = theme.dangerContainer.themeColor()) {
                Text("Удалить заметку", Modifier.padding(12.dp), style = body, color = theme.onDangerContainer.themeColor())
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(radius), color = theme.card.themeColor(),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.danger.themeColor())) {
                Text("Речь не обнаружена. Попробуйте ещё раз.", Modifier.padding(12.dp), style = body, color = theme.danger.themeColor())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                theme.notePalette.forEach { color ->
                    Box(Modifier.size(28.dp).background(color.themeColor(), CircleShape))
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
                    shape = RoundedCornerShape(16.dp),
                )
                if (binding.supportsAlpha) {
                    HsvSlider(
                        title = "Непрозрачность",
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
                    title = "Оттенок",
                    value = hue,
                    valueRange = 0f..360f,
                    valueText = hue.roundToInt().toString(),
                    colors = (0..6).map { step ->
                        Color(AndroidColor.HSVToColor(floatArrayOf(step * 60f, 1f, 1f)))
                    },
                    onValueChange = { updateHsv(h = it) },
                )
                HsvSlider(
                    title = "Насыщенность",
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
                    title = "Яркость",
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

package ru.nezabudka.app.ui.screens

import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import ru.nezabudka.app.ui.VoiceActionUiState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog as PlatformDialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.AppLayout
import ru.nezabudka.app.ui.appShadowElevation

/** Empty collections explain both the current state and the next useful step. */
@Composable
internal fun AppEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val theme = AppTheme.tokens
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(theme.shapes.card),
            color = theme.colors.selected,
            contentColor = theme.colors.accent,
        ) {
            Icon(icon, null, Modifier.padding(18.dp).size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            description,
            modifier = Modifier.widthIn(max = 320.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun Modifier.pushToTalkGesture(
    enabled: Boolean,
    hasRecognizedText: Boolean,
    beginPushToTalk: () -> Unit,
    endPushToTalk: () -> Unit,
    primaryAction: () -> Unit,
): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentHasRecognizedText by rememberUpdatedState(hasRecognizedText)
    val currentBegin by rememberUpdatedState(beginPushToTalk)
    val currentEnd by rememberUpdatedState(endPushToTalk)
    val currentPrimaryAction by rememberUpdatedState(primaryAction)
    return pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                if (!currentEnabled) return@detectTapGestures
                if (currentHasRecognizedText) {
                    if (tryAwaitRelease()) currentPrimaryAction()
                } else {
                    currentBegin()
                    try {
                        tryAwaitRelease()
                    } finally {
                        currentEnd()
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppNoticeCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    essential: Boolean = false,
    isError: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (!ru.nezabudka.app.ui.LocalPopupNoticesEnabled.current && !essential && !isError) return
    val theme = AppTheme.tokens
    val inlineClose = isError || message in setOf("Создание напоминания отменено.", "Напоминаний не найдено.", "Подходящее напоминание не найдено.")
    Card(
        modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding, vertical = 4.dp),
        shape = RoundedCornerShape(theme.shapes.card),
        colors = CardDefaults.cardColors(containerColor = containerColor ?: theme.colors.card, contentColor = if (isError) theme.colors.danger else theme.colors.textPrimary),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.talkButton),
        elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
    ) {
        if (inlineClose) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Clear, "Закрыть", Modifier.size(18.dp), tint = theme.colors.textSecondary)
                }
            }
        } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Clear, "Закрыть", Modifier.size(18.dp), tint = theme.colors.textSecondary)
                }
            }
            Text(message, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                actions()
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RowScope.AppNoticeAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color? = null) {
    if (label == "Отменить") AppNoticeTextAction("Отмена", onClick)
    else IconButton(onClick = onClick, modifier = Modifier.align(Alignment.CenterVertically).size(44.dp)) {
        Icon(icon, label, Modifier.size(22.dp), tint = tint ?: AppTheme.tokens.colors.accent)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RowScope.AppNoticeTextAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.align(Alignment.CenterVertically), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun AppSnackbarHost(state: SnackbarHostState) {
    val enabled = ru.nezabudka.app.ui.LocalPopupNoticesEnabled.current
    androidx.compose.runtime.LaunchedEffect(enabled, state.currentSnackbarData) {
        if (!enabled) state.currentSnackbarData?.dismiss()
    }
    if (!enabled) return
    SnackbarHost(state) { data ->
        val theme = AppTheme.tokens
        // Short status feedback should not occupy the space of a command result.
        Card(
            Modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding, vertical = 4.dp),
            shape = RoundedCornerShape(theme.shapes.card),
            colors = CardDefaults.cardColors(containerColor = theme.colors.card, contentColor = theme.colors.textPrimary),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(data.visuals.message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (data.visuals.withDismissAction) {
                    IconButton(onClick = data::dismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Clear, "Закрыть", Modifier.size(18.dp), tint = theme.colors.textSecondary)
                    }
                }
                data.visuals.actionLabel?.let { label ->
                    AppNoticeTextAction(if (label == "Отменить") "Отмена" else label, data::performAction)
                }
            }
        }
    }
}

/** Identical centered voice captions on the assistant and both collection tabs. */
@Composable
internal fun AppVoiceActionContent(recording: Boolean, processing: Boolean, hasText: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        if (processing) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            Icon(if (hasText) Icons.AutoMirrored.Filled.Send else Icons.Outlined.MicNone,
                null, Modifier.size(22.dp))
        }
        Text(when {
            recording -> "Отпустите для распознавания"
            processing -> "Выполняю…"
            hasText -> "Выполнить"
            else -> "Удерживайте и говорите"
        }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center)
    }
}

@Composable
internal fun RecordingPreviewCard(text: String) {
    val theme = AppTheme.tokens
    val scroll = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(text) {
        androidx.compose.runtime.withFrameNanos { }
        scroll.scrollTo(scroll.maxValue)
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding, vertical = 4.dp),
        shape = RoundedCornerShape(theme.shapes.card),
        colors = CardDefaults.cardColors(containerColor = theme.colors.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Предварительный текст", style = MaterialTheme.typography.labelMedium, color = theme.colors.textSecondary)
            Text(text.ifBlank { "Говорите…" }, style = MaterialTheme.typography.bodyMedium,
                color = theme.colors.textPrimary,
                modifier = Modifier.heightIn(max = 140.dp).verticalScroll(scroll))
        }
    }
}

@Composable
internal fun SttConfirmationCard(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = AppTheme.tokens
    Card(
        modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding, vertical = 4.dp),
        shape = RoundedCornerShape(theme.shapes.card),
        colors = CardDefaults.cardColors(containerColor = theme.colors.card, contentColor = theme.colors.textPrimary),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Проверьте распознанный текст", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, color = theme.colors.textPrimary)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(theme.shapes.tag),
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 2,
                maxLines = 4,
                trailingIcon = {
                    IconButton(onClick = { onTextChange("") }) {
                        Icon(Icons.Default.Clear, "Очистить")
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = theme.colors.input,
                    unfocusedContainerColor = theme.colors.input,
                    focusedTextColor = theme.colors.textPrimary,
                    unfocusedTextColor = theme.colors.textPrimary,
                    focusedBorderColor = theme.colors.accent,
                    unfocusedBorderColor = theme.colors.outline,
                    cursorColor = theme.colors.cursor,
                ),
            )
        }
    }
}

/**
 * A shared editor shell. Note and reminder editors intentionally use the same
 * navigation placement, top-bar height and edge-to-edge background.
 */
@Composable
internal fun AppEditorScaffold(
    containerColor: Color,
    contentColor: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    imeAware: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scaffoldModifier = modifier
        .fillMaxSize()
        .then(if (imeAware) Modifier.imePadding() else Modifier)
    Scaffold(
        modifier = scaffoldModifier,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                    Spacer(Modifier.weight(1f))
                    actions()
                }
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}

/** Shared anchored popup menu with the app's colors and rounded panel shape. */
@Composable
internal fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = AppTheme.tokens
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 224.dp, max = 340.dp),
        shape = RoundedCornerShape(theme.shapes.panel),
        containerColor = theme.colors.card,
        tonalElevation = 0.dp,
        shadowElevation = appShadowElevation(8.dp),
        content = content,
    )
}

/** Blocking modal surfaces use exactly one theme-controlled scrim. */
@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    dimBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    PlatformDialog(onDismissRequest = onDismissRequest, properties = properties) {
        AppDialogScrimEffect(dimBackground)
        content()
    }
}

@Composable
internal fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
) {
    val theme = AppTheme.tokens
    AlertDialog(
        shape = RoundedCornerShape(theme.shapes.dialog),
        containerColor = theme.colors.modal,
        titleContentColor = theme.colors.textPrimary,
        textContentColor = theme.colors.textSecondary,
        onDismissRequest = onDismissRequest,
        title = {
            AppDialogScrimEffect(dimBackground = true)
            title()
        },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}

@Composable
internal fun AppScreenHeading(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = AppTheme.tokens.colors.textPrimary)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppTheme.tokens.colors.textSecondary) }
    }
}

/** The same action sizes, labels and gesture behavior on both collection screens. */
@Composable
internal fun AppVoiceActionBar(
    ui: VoiceActionUiState,
    begin: () -> Unit,
    end: () -> Unit,
    execute: () -> Unit,
    add: () -> Unit,
    addDescription: String,
) {
    val theme = AppTheme.tokens
    Row(Modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.sectionSpacing), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(theme.shapes.action), color = theme.colors.talkButton, contentColor = theme.colors.onTalkButton,
            modifier = Modifier.weight(1f).heightIn(min = 58.dp).pushToTalkGesture(
                enabled = !ui.isProcessing, hasRecognizedText = ui.recognizedText.isNotBlank(),
                beginPushToTalk = begin, endPushToTalk = end, primaryAction = execute,
            ),
        ) {
            AppVoiceActionContent(ui.isRecording, ui.isProcessing, ui.recognizedText.isNotBlank())
        }
        Surface(shape = CircleShape, color = theme.colors.selected, contentColor = theme.colors.accent,
            modifier = Modifier.size(58.dp).clickable(onClick = add)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, addDescription) }
        }
    }
}

@Composable
private fun AppDialogScrimEffect(dimBackground: Boolean) {
    val scrimAlpha = AppTheme.tokens.colors.scrim.alpha
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.apply {
            if (dimBackground && scrimAlpha > 0f) {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(scrimAlpha)
            } else {
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
    }
}

@Composable
internal fun AppMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val theme = AppTheme.tokens
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = icon?.let { image ->
            {
                Icon(image, null)
            }
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    "Выбрано",
                    tint = theme.colors.accent,
                )
            }
        } else null,
    )
}

/** Text input for editors where placeholder and entered text must look identical. */
@Composable
internal fun AppEditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    textColor: Color,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    val cursorColor = AppTheme.tokens.colors.cursor
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle.copy(color = textColor),
        cursorBrush = SolidColor(cursorColor),
        singleLine = singleLine,
        decorationBox = { field ->
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle, color = placeholderColor)
            }
            field()
        },
    )
}

/** TextFieldValue overload used by the note editor to preserve cursor position. */
@Composable
internal fun AppEditorTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    textColor: Color,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val cursorColor = AppTheme.tokens.colors.cursor
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle.copy(color = textColor),
        cursorBrush = SolidColor(cursorColor),
        singleLine = singleLine,
        onTextLayout = onTextLayout,
        decorationBox = { field ->
            if (value.text.isEmpty()) {
                Text(placeholder, style = textStyle, color = placeholderColor)
            }
            field()
        },
    )
}

/** Shared input appearance for short creation forms. */
@Composable
internal fun AppCreationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 3,
) {
    val theme = AppTheme.tokens
    val fieldModifier = modifier.then(
        if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
    )
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier,
        textStyle = MaterialTheme.typography.titleLarge,
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.titleLarge,
                color = theme.colors.placeholder,
            )
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
    )
}

/** Search field shared by notes and reminders. */
@Composable
internal fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    reducedEffects: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val theme = AppTheme.tokens
    if (reducedEffects) {
        val shape = RoundedCornerShape(theme.shapes.dialog)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .height(56.dp)
                .background(theme.colors.input, shape)
                .border(1.dp, theme.colors.outline, shape)
                .padding(horizontal = 16.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = theme.colors.textPrimary),
            cursorBrush = SolidColor(theme.colors.accent),
            decorationBox = { innerTextField ->
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, "Поиск", tint = theme.colors.textSecondary)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, color = theme.colors.placeholder)
                        innerTextField()
                    }
                    trailingContent()
                }
            },
        )
        return
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(56.dp),
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(theme.shapes.dialog),
        leadingIcon = { Icon(Icons.Default.Search, "Поиск") },
        placeholder = { Text(placeholder) },
        trailingIcon = { Row(content = trailingContent) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = theme.colors.input,
            unfocusedContainerColor = theme.colors.input,
            unfocusedBorderColor = theme.colors.outline.copy(alpha = 0.65f),
            focusedBorderColor = theme.colors.accent,
        ),
    )
}

/** Common full-width bottom creation window used for folders and reminders. */
@Composable
internal fun CreationSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = AppTheme.tokens
    AppDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier.fillMaxSize().imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Spacer(
                Modifier.matchParentSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppLayout.horizontalPadding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(theme.shapes.panel),
                color = theme.colors.modal,
                contentColor = theme.colors.textPrimary,
                tonalElevation = 6.dp,
                shadowElevation = appShadowElevation(8.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding, vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}

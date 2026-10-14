package ru.nezabudka.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.nezabudka.app.application.ActionEffect
import ru.nezabudka.app.domain.AppSettings
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.ui.ActionNotice
import ru.nezabudka.app.ui.AppLayout
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.appShadowElevation
import ru.nezabudka.app.ui.AssistantUiState
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun AssistantScreen(
    state: AssistantUiState,
    actionNotice: ActionNotice?,
    settings: AppSettings,
    edit: (String) -> Unit,
    primaryAction: () -> Unit,
    beginPushToTalk: () -> Unit,
    endPushToTalk: () -> Unit,
    play: () -> Unit,
    editCreatedNote: (Note) -> Unit,
    changeCreatedFolder: (Note) -> Unit,
    openReminder: (Reminder) -> Unit,
    changeReminderTime: (Reminder, LocalDateTime?, String?) -> Unit,
    undoLastAction: () -> Unit,
    dismissNotice: () -> Unit,
    answerReminderDateClarification: (String) -> Unit,
    openWhisperModels: () -> Unit = {},
    hideWelcomeCard: () -> Unit = {},
    tips: List<ru.nezabudka.app.domain.AssistantTipKind> = emptyList(),
    acknowledgeTip: (ru.nezabudka.app.domain.AssistantTipKind) -> Unit = {},
    openSettings: (ru.nezabudka.app.domain.AssistantSettingsTarget) -> Unit = {},
) {
    var helpExpanded by remember { mutableStateOf(false) }
    var logExpanded by remember { mutableStateOf(false) }
    var noticeTimeReminder by remember { mutableStateOf<Reminder?>(null) }
    var bottomControlsHeight by remember { mutableIntStateOf(0) }
    val theme = AppTheme.tokens
    val assistantListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(state.error) {
        if (state.error != null) assistantListState.animateScrollToItem(0)
    }
    var previouslyVisibleTips by remember { mutableStateOf(tips.map { it.name }.toSet()) }
    LaunchedEffect(tips) {
        val added = tips.any { it.name !in previouslyVisibleTips }
        previouslyVisibleTips = tips.map { it.name }.toSet()
        if (added) assistantListState.animateScrollToItem(0)
    }
    Box(Modifier.fillMaxSize().imePadding()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().onSizeChanged { bottomControlsHeight = it.height },
            ) {
                // Keep the focused input mounted while preview text changes, otherwise
                // removing it dismisses the IME and moves the held microphone button.
                OutlinedTextField(
                    value = state.recordingPreview ?: state.recognizedText,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    onValueChange = { if (!state.isRecording && !state.isProcessing) edit(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding, vertical = 4.dp),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(theme.shapes.card),
                    label = {
                        Text(if (state.awaitingSttConfirmation) "Проверьте распознанный текст" else "Что нужно запомнить?")
                    },
                    trailingIcon = {
                        if (!state.isRecording && state.recognizedText.isNotEmpty()) IconButton(onClick = { edit("") }) {
                            Icon(Icons.Default.Clear, "Очистить текст")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.colors.input,
                        unfocusedContainerColor = theme.colors.input,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(AppLayout.sectionSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (settings.showPlaybackButton) {
                        Surface(
                            onClick = play,
                            enabled = state.lastAudio != null && !state.isRecording,
                            shape = CircleShape,
                            color = theme.colors.selected,
                            contentColor = theme.colors.textPrimary,
                            modifier = Modifier.size(58.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, "Прослушать")
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(theme.shapes.action),
                        color = if (state.isProcessing) theme.colors.input else theme.colors.talkButton,
                        contentColor = if (state.isProcessing) theme.colors.textSecondary else theme.colors.onTalkButton,
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp)
                            .pushToTalkGesture(
                            enabled = !state.isProcessing,
                            hasRecognizedText = state.recognizedText.isNotBlank(),
                            beginPushToTalk = beginPushToTalk,
                            endPushToTalk = endPushToTalk,
                            primaryAction = primaryAction,
                        ),
                    ) {
                        AppVoiceActionContent(state.isRecording, state.isProcessing, state.recognizedText.isNotBlank())
                    }
                }
            }
        },
    ) { inner -> LazyColumn(
        state = assistantListState,
        modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = AppLayout.horizontalPadding),
        contentPadding = PaddingValues(top = AppLayout.contentTopPadding, bottom = AppLayout.sectionSpacing),
        verticalArrangement = Arrangement.spacedBy(AppLayout.sectionSpacing),
    ) {
        item(key = "assistant-heading") {
            Column(
                Modifier.fillMaxWidth().padding(end = 56.dp, bottom = 8.dp).offset(y = (-4).dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppScreenHeading("НеЗабудьКа")
            }
        }
        if (state.awaitingSttConfirmation) item { Text("Проверьте распознанный текст и нажмите «Выполнить».", style = MaterialTheme.typography.bodySmall) }
        if (state.isProcessing) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Распознаю и выполняю команду…")
            }
        }
        state.error?.let { error -> item(key = "assistant-error") {
            AssistantErrorCard(error)
        } }
        state.result?.let { result -> item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(theme.shapes.card),
                colors = CardDefaults.cardColors(containerColor = theme.colors.card),
                elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
            ) { Text(result, Modifier.padding(12.dp)) }
        } }
        items(ru.nezabudka.app.domain.prioritizedAssistantTips(tips), key = { "tip:${it.name}" }) { tip ->
            AssistantTipCard(tip, openSettings, settings.sttEngine) { acknowledgeTip(tip) }
        }
        if (settings.showTechnicalLog) item(key = "technical-log") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(theme.shapes.card),
                colors = CardDefaults.cardColors(containerColor = theme.colors.card),
                elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Технический лог", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Событий в текущем сеансе: ${state.log.size}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { logExpanded = true }, modifier = Modifier.align(Alignment.End)) { Text("Открыть лог") }
                }
            }
        }
        if (settings.showAssistantWelcomeCard) item(key = "welcome") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(theme.shapes.panel),
                colors = CardDefaults.cardColors(containerColor = theme.colors.card),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MicNone, null, Modifier.size(26.dp), tint = theme.colors.accent)
                        Text("Сохраняйте идеи на лету", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Создавайте заметки, планируйте дела и собирайте идеи в папки. Удерживайте кнопку внизу или введите команду текстом.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.colors.textSecondary,
                    )
                    Text("Попробуйте команду", style = MaterialTheme.typography.labelLarge)
                    Column {
                        AssistChip(
                            onClick = { edit("Напомни завтра в 9:00 полить цветы") },
                            label = { Text("Напомни завтра полить цветы") },
                            leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)) },
                        )
                        AssistChip(
                            onClick = { edit("Создай заметку: идеи для выходных") },
                            label = { Text("Создай заметку с идеей") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(18.dp)) },
                        )
                        AssistChip(
                            onClick = { edit("Создай папку Путешествия") },
                            label = { Text("Создай папку Путешествия") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)) },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = false, role = Role.Checkbox, onValueChange = { if (it) hideWelcomeCard() }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = false, onCheckedChange = null)
                        Text("Больше не показывать", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    } }
    Surface(
        modifier = Modifier.align(Alignment.TopEnd).padding(end = AppLayout.horizontalPadding).offset(y = (-4).dp),
        shape = RoundedCornerShape(50),
        color = theme.colors.background,
    ) {
        IconButton(onClick = { helpExpanded = !helpExpanded }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, "Открыть справку по командам", Modifier.size(34.dp), tint = theme.colors.textPrimary)
        }
    }
    if (helpExpanded) AssistantHelpDialog { helpExpanded = false }
    if (logExpanded) TechnicalLogDialog(state.log) { logExpanded = false }
    actionNotice?.takeUnless { it.isError }?.let { notice ->
        AppNoticeCard(
            message = notice.message,
            onDismiss = dismissNotice,
            essential = notice.effect is ActionEffect.ReminderDateRequested,
            isError = notice.isError,
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset { IntOffset(0, -bottomControlsHeight) },
        ) {
            when (val effect = notice.effect) {
                is ActionEffect.ReminderCreated -> {
                    AppNoticeAction(Icons.Default.Edit, "Редактировать", { dismissNotice(); openReminder(effect.reminder) })
                    AppNoticeAction(Icons.Default.Schedule, "Сменить время", { noticeTimeReminder = effect.reminder })
                }
                is ActionEffect.NoteCreated -> {
                    AppNoticeAction(Icons.Default.Edit, "Редактировать", { dismissNotice(); editCreatedNote(effect.note) })
                    AppNoticeAction(Icons.Default.FolderOpen, "Сменить папку", { dismissNotice(); changeCreatedFolder(effect.note) })
                }
                is ActionEffect.ReminderDateRequested -> {
                    AppNoticeTextAction("Без времени", { answerReminderDateClarification("без даты") })
                    AppNoticeTextAction("Отмена", { answerReminderDateClarification("отмена") })
                }
                else -> Unit
            }
            if (notice.effect is ActionEffect.ReminderCreated ||
                notice.effect is ActionEffect.ReminderDeleted ||
                notice.effect is ActionEffect.NoteCreated ||
                notice.effect is ActionEffect.FolderCreated ||
                notice.effect is ActionEffect.FolderDeleted
            ) AppNoticeAction(Icons.AutoMirrored.Filled.Undo, "Отменить", undoLastAction)
        }
    }
    }
    noticeTimeReminder?.let { reminder ->
        ReminderDateTimeDialog(
            initialDate = reminder.remindAt?.toLocalDate(),
            initialTime = reminder.remindAt?.toLocalTime(),
            initialRecurrence = reminder.recurrence,
            dismiss = { noticeTimeReminder = null },
            clear = { changeReminderTime(reminder, null, null); noticeTimeReminder = null },
            confirm = { date, time ->
                changeReminderTime(reminder, date.atTime(time ?: LocalTime.of(9, 0)), reminder.recurrence)
                noticeTimeReminder = null
            },
            confirmWithRecurrence = { date, time, recurrence ->
                changeReminderTime(reminder, date.atTime(time ?: LocalTime.of(9, 0)), recurrence)
                noticeTimeReminder = null
            },
        )
    }
}

@Composable
private fun HighlightedCommandBullet(
    label: String,
    commands: List<String>,
    suffix: String = "",
) {
    Text(
        buildAnnotatedString {
            append("• ")
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("$label: ") }
            commands.forEachIndexed { index, command ->
                when {
                    index == 0 -> Unit
                    index == commands.lastIndex -> append(" или ")
                    else -> append(", ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(command) }
            }
            append(".")
            if (suffix.isNotBlank()) append(" $suffix")
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun HelpBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AssistantHelpDialog(onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Команды помощника") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
        text = {
            Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "На вкладке «Помощник» можно использовать команды для напоминаний, заметок и папок. На остальных вкладках выполняются только команды соответствующего раздела.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Стандартные команды", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Работают сразу и наиболее надёжно. Начинайте фразу с понятного действия.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Напоминания", style = MaterialTheme.typography.titleSmall)
                    HighlightedCommandBullet(
                        label = "Создание",
                        commands = listOf("«Напомни…»", "«Создай напоминание…»", "«Надо не забыть…»"),
                        suffix = "Укажите задачу, дату и время. Если даты нет, приложение попросит её уточнить.",
                    )
                    HighlightedCommandBullet(
                        label = "Просмотр",
                        commands = listOf("«Покажи напоминания…»", "«Какие планы…»", "«Что у меня…»"),
                        suffix = "Можно указать конкретный день, относительную дату либо начало, середину или конец месяца.",
                    )
                    HighlightedCommandBullet(
                        label = "Удаление",
                        commands = listOf("«Удали последнее напоминание»", "«Удали напоминание про…»", "«Отмени последнее напоминание»"),
                    )
                    HighlightedCommandBullet(
                        label = "Уточнение даты",
                        commands = listOf("«Завтра в девять»", "«Через два часа»"),
                        suffix = "Карточка останется на экране. Можно также выбрать «Отмена» или «Без времени».",
                    )
                    Text("Заметки и папки", style = MaterialTheme.typography.titleSmall)
                    HighlightedCommandBullet(
                        label = "Создание заметки",
                        commands = listOf("«Создай заметку…»"),
                        suffix = "Можно сразу указать папку.",
                    )
                    HighlightedCommandBullet("Создание папки", listOf("«Создай папку…»"))
                    HighlightedCommandBullet("Удаление папки", listOf("«Удали папку…»"))
                    Text(
                        "Если название папки состоит из нескольких слов, заключите его в кавычки или отделите от текста заметки двоеточием.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Расширенное распознавание команд", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "NLU-классификатор помогает понимать более свободные формулировки, например:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HelpBullet("Напоминание: «Главное не забыть проверить давление завтра утром».")
                    HelpBullet("Повтор: «Сигнализируй каждый вечер что нужно закрыть теплицу».")
                    HelpBullet("Просмотр дел: «Есть ли у меня задачи на завтра».")
                    HelpBullet("Заметка: «Зафиксируй мысль: запасной ключ лежит в синем футляре».")
                    HelpBullet("Папка: «Организуй папку Домашний ремонт».")
                    Text(
                        "Для таких формулировок включите настройку «Расширенное распознавание команд».",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    changeReminderTime: (Reminder, LocalDateTime?) -> Unit,
    undoLastAction: () -> Unit,
    dismissNotice: () -> Unit,
    answerReminderDateClarification: (String) -> Unit,
    onVoiceControlBounds: (Rect) -> Unit = {},
) {
    var helpExpanded by remember { mutableStateOf(false) }
    var noticeTimeReminder by remember { mutableStateOf<Reminder?>(null) }
    var bottomControlsHeight by remember { mutableIntStateOf(0) }
    val theme = AppTheme.tokens
    Box(Modifier.fillMaxSize().imePadding()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().onSizeChanged { bottomControlsHeight = it.height },
            ) {
                OutlinedTextField(
                    value = state.recognizedText,
                    onValueChange = edit,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding, vertical = 4.dp),
                    minLines = 2,
                    maxLines = 4,
                    label = {
                        Text(if (state.awaitingSttConfirmation) "Проверьте распознанный текст" else "Введите текст")
                    },
                    trailingIcon = {
                        if (state.recognizedText.isNotEmpty()) IconButton(onClick = { edit("") }) {
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
                        modifier = Modifier.weight(1f).height(58.dp)
                            .onGloballyPositioned { onVoiceControlBounds(it.boundsInRoot()) }
                            .pushToTalkGesture(
                            enabled = !state.isProcessing,
                            hasRecognizedText = state.recognizedText.isNotBlank(),
                            beginPushToTalk = beginPushToTalk,
                            endPushToTalk = endPushToTalk,
                            primaryAction = primaryAction,
                        ),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(when {
                                state.isRecording -> "Отпустите для распознавания"
                                state.isProcessing -> "Выполняю…"
                                state.recognizedText.isNotBlank() -> "Выполнить"
                                else -> "Удерживайте, чтобы говорить"
                            })
                        }
                    }
                }
            }
        },
    ) { inner -> LazyColumn(
        Modifier.fillMaxSize().padding(inner).padding(horizontal = AppLayout.horizontalPadding),
        contentPadding = PaddingValues(bottom = AppLayout.sectionSpacing),
        verticalArrangement = Arrangement.spacedBy(AppLayout.sectionSpacing),
    ) {
        item {
            Text(
                "НеЗабудьКа (название может быть временным)",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().padding(end = 56.dp),
            )
        }
        if (helpExpanded) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Доступные команды", style = MaterialTheme.typography.titleMedium)
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
                        commands = listOf("«Удали последнее напоминание»", "«Удали напоминание про…»", "«Отмени напоминание…»"),
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
                        "Локальная NLU-модель распознаёт более свободные формулировки без генерации ответа и без подключения к интернету, например:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HelpBullet("«Главное не забыть проверить давление»;")
                    HelpBullet("«Что у меня намечено на завтра?»;")
                    HelpBullet("«Сохрани мысль сначала проверить сеть»;")
                    HelpBullet("«Организуй папку Домашний ремонт»;")
                    HelpBullet("«Больше не напоминай про оплату парковки».")
                    Text(
                        "Классификатор предназначен только для команд напоминаний, заметок и папок. Это не чат и он не выполняет произвольные запросы. Короткие неоднозначные фразы могут распознаваться менее надёжно.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Для таких формулировок включите настройку «Расширенное распознавание команд». Стандартные команды работают и при выключенной модели.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (state.awaitingSttConfirmation) item { Text("Проверьте распознанный текст и нажмите «Выполнить».", style = MaterialTheme.typography.bodySmall) }
        if (state.isProcessing) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text("Распознаю и выполняю команду…")
            }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        state.result?.let { result -> item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
            ) { Text(result, Modifier.padding(12.dp)) }
        } }
        if (settings.showTechnicalLog) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Технический лог", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Событий в текущем сеансе: ${state.log.size}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SelectionContainer {
                        Text(
                            state.log.joinToString("\n").ifBlank { "Событий пока нет" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
    } }
    Surface(
        modifier = Modifier.align(Alignment.TopEnd).padding(end = AppLayout.horizontalPadding),
        shape = RoundedCornerShape(50),
        color = theme.colors.background,
    ) {
        OutlinedIconButton(onClick = { helpExpanded = !helpExpanded }) {
            Text("?", style = MaterialTheme.typography.titleLarge)
        }
    }
    actionNotice?.let { notice ->
        AppNoticeCard(
            message = notice.message,
            onDismiss = dismissNotice,
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset { IntOffset(0, -bottomControlsHeight) },
        ) {
            when (val effect = notice.effect) {
                is ActionEffect.ReminderCreated -> {
                    IconButton(onClick = { dismissNotice(); openReminder(effect.reminder) }) {
                        Icon(Icons.Default.Edit, "Редактировать")
                    }
                    IconButton(onClick = { noticeTimeReminder = effect.reminder }) {
                        Icon(Icons.Default.Schedule, "Сменить время")
                    }
                }
                is ActionEffect.NoteCreated -> {
                    TextButton(onClick = { dismissNotice(); editCreatedNote(effect.note) }) { Text("Редактировать") }
                    TextButton(onClick = { dismissNotice(); changeCreatedFolder(effect.note) }) { Text("Сменить папку") }
                }
                is ActionEffect.ReminderDateRequested -> {
                    TextButton(onClick = { answerReminderDateClarification("отмена") }) { Text("Отмена") }
                    TextButton(onClick = { answerReminderDateClarification("без даты") }) { Text("Без времени") }
                }
                else -> Unit
            }
            if (notice.effect is ActionEffect.ReminderCreated ||
                notice.effect is ActionEffect.ReminderDeleted ||
                notice.effect is ActionEffect.NoteCreated ||
                notice.effect is ActionEffect.FolderCreated ||
                notice.effect is ActionEffect.FolderDeleted
            ) TextButton(onClick = undoLastAction) { Text("Отменить") }
        }
    }
    }
    noticeTimeReminder?.let { reminder ->
        ReminderDateTimeDialog(
            initialDate = reminder.remindAt?.toLocalDate(),
            initialTime = reminder.remindAt?.toLocalTime(),
            dismiss = { noticeTimeReminder = null },
            clear = { changeReminderTime(reminder, null); noticeTimeReminder = null },
            confirm = { date, time ->
                changeReminderTime(reminder, date.atTime(time ?: LocalTime.of(9, 0)))
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

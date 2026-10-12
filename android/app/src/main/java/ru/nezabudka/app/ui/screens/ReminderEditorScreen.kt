package ru.nezabudka.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.domain.nextReminderOccurrence
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.reminderRecurrenceLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val ReminderEditorHorizontalPadding = 20.dp
private val ReminderEditorDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
private val ReminderEditorTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderEditorScreen(
    reminder: Reminder,
    back: () -> Unit,
    save: (Reminder, String, LocalDateTime?, String?, Boolean, Boolean, () -> Unit) -> Unit,
    delete: (Long, () -> Unit) -> Unit,
    openDatePickerInitially: Boolean = false,
    onTimedReminderSaved: () -> Unit = {},
) {
    val theme = AppTheme.tokens
    var text by remember(reminder.id) { mutableStateOf(reminder.text) }
    var favorite by remember(reminder.id) { mutableStateOf(reminder.favorite) }
    var date by remember(reminder.id) { mutableStateOf(reminder.remindAt?.toLocalDate()) }
    var time by remember(reminder.id) { mutableStateOf(reminder.remindAt?.toLocalTime()) }
    var recurrence by remember(reminder.id) { mutableStateOf(reminder.recurrence) }
    var dateDialog by remember(reminder.id) { mutableStateOf(openDatePickerInitially) }
    var recurrenceDialog by remember(reminder.id) { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var textEdited by remember(reminder.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    val saveAndClose = {
        val remindAt = date?.atTime(time ?: LocalTime.of(9, 0))
        val changed = text != reminder.text || remindAt != reminder.remindAt ||
            recurrence != reminder.recurrence || favorite != reminder.favorite
        when {
            text.isBlank() -> delete(reminder.id, back)
            changed -> save(reminder, text, remindAt, recurrence, favorite, reminder.status == "completed") {
                if (remindAt != null) onTimedReminderSaved()
                back()
            }
            else -> back()
        }
    }
    // The first Back while typing must only close the keyboard. Dialogs and
    // popup menus handle their own Back; committing is reserved for real exit.
    BackHandler(
        enabled = !imeVisible && !dateDialog && !recurrenceDialog && !deleteDialog && !moreMenu,
        onBack = saveAndClose,
    )
    LaunchedEffect(text) {
        if (textEdited && text.isNotBlank()) {
            delay(800)
            textEdited = false
            save(reminder, text, date?.atTime(time ?: LocalTime.of(9, 0)), recurrence, favorite, reminder.status == "completed") { }
        }
    }

    AppEditorScaffold(
        containerColor = theme.colors.background,
        contentColor = theme.colors.textPrimary,
        onBack = saveAndClose,
        actions = {
            IconButton(onClick = {
                val newFavorite = !favorite
                favorite = newFavorite
                save(reminder, text, date?.atTime(time ?: LocalTime.of(9, 0)), recurrence, newFavorite, reminder.status == "completed") { }
            }) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    if (favorite) "Убрать из избранного" else "Добавить в избранное",
                    tint = if (favorite) theme.colors.warning else theme.colors.textSecondary,
                )
            }
            Box {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                AppDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    AppMenuItem(
                        text = "Удалить",
                        icon = Icons.Default.Delete,
                        onClick = { moreMenu = false; deleteDialog = true },
                    )
                }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = ReminderEditorHorizontalPadding, vertical = 20.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        save(reminder, text, date?.atTime(time ?: LocalTime.of(9, 0)), recurrence, favorite, true, back)
                    },
                ) { Text("Выполнено") }
            }
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = ReminderEditorHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppEditorTextField(
                value = text,
                onValueChange = { text = it; textEdited = true },
                placeholder = "Текст напоминания",
                textStyle = MaterialTheme.typography.headlineSmall,
                textColor = theme.colors.textPrimary,
                placeholderColor = theme.colors.placeholder,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { dateDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Schedule, null)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        if (date == null) "Добавить дату и время"
                        else buildString {
                            append(date!!.format(ReminderEditorDateFormatter))
                            time?.let { append(", "); append(it.format(ReminderEditorTimeFormatter)) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (date != null) IconButton(onClick = {
                    date = null
                    time = null
                    recurrence = null
                    save(reminder, text, null, null, favorite, false) { }
                }) {
                    Icon(Icons.Default.Clear, "Убрать дату")
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { recurrenceDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Repeat, null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Повтор", style = MaterialTheme.typography.labelMedium)
                        Text(reminderRecurrenceLabel(recurrence))
                    }
                }
                if (recurrence != null) IconButton(onClick = {
                    recurrence = null
                    save(
                        reminder,
                        text,
                        date?.atTime(time ?: LocalTime.of(9, 0)),
                        null,
                        favorite,
                        false,
                    ) { }
                }) {
                    Icon(Icons.Default.Clear, "Убрать повтор")
                }
            }
        }
    }

    if (dateDialog) ReminderDateTimeDialog(
        initialDate = date,
        initialTime = time,
        initialRecurrence = recurrence,
        dismiss = { dateDialog = false },
        clear = {
            date = null
            time = null
            recurrence = null
            dateDialog = false
            save(reminder, text, null, null, favorite, false) { }
        },
        confirm = { newDate, newTime ->
            date = newDate
            time = newTime
            dateDialog = false
            save(reminder, text, newDate.atTime(newTime ?: LocalTime.of(9, 0)), recurrence, favorite, reminder.status == "completed") { }
        },
        confirmWithRecurrence = { newDate, newTime, newRecurrence ->
            val requestedStart = newDate.atTime(newTime ?: LocalTime.of(9, 0))
            val effectiveStart = advanceIfNewRecurrenceIsOverdue(requestedStart, recurrence, newRecurrence)
            date = effectiveStart.toLocalDate()
            time = effectiveStart.toLocalTime()
            recurrence = newRecurrence
            dateDialog = false
            save(
                reminder,
                text,
                effectiveStart,
                newRecurrence,
                favorite,
                false,
            ) { }
        },
    )
    if (recurrenceDialog) {
        val currentStart = (date ?: LocalDate.now()).atTime(time ?: LocalTime.of(9, 0))
        ReminderRecurrenceDialog(
            selected = recurrence,
            startDateTime = currentStart,
            dismiss = { recurrenceDialog = false },
            select = { newRecurrence, selectedStart ->
                val effectiveStart = advanceIfNewRecurrenceIsOverdue(
                    selectedStart ?: currentStart,
                    recurrence,
                    newRecurrence,
                )
                if (newRecurrence != null) {
                    date = effectiveStart.toLocalDate()
                    time = effectiveStart.toLocalTime()
                }
                recurrence = newRecurrence
                recurrenceDialog = false
                save(
                    reminder,
                    text,
                    date?.atTime(time ?: LocalTime.of(9, 0)),
                    newRecurrence,
                    favorite,
                    false,
                ) { }
            },
        )
    }
    if (deleteDialog) AppAlertDialog(
        onDismissRequest = { deleteDialog = false },
        title = { Text("Удалить напоминание?") },
        text = { Text("Это действие нельзя отменить.") },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Отмена") } },
        confirmButton = { TextButton(onClick = { deleteDialog = false; delete(reminder.id, back) }) { Text("Удалить") } },
    )
}

private fun advanceIfNewRecurrenceIsOverdue(
    requestedStart: LocalDateTime,
    previousRecurrence: String?,
    newRecurrence: String?,
): LocalDateTime {
    val now = LocalDateTime.now()
    return if (
        newRecurrence != null &&
        newRecurrence != previousRecurrence &&
        !requestedStart.isAfter(now)
    ) {
        nextReminderOccurrence(requestedStart, newRecurrence, now) ?: requestedStart
    } else {
        requestedStart
    }
}

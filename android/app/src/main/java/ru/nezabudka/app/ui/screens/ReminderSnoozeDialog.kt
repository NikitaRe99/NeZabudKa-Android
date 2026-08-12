package ru.nezabudka.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.nezabudka.app.domain.Reminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
internal fun ReminderSnoozeDialog(
    reminder: Reminder,
    dismiss: () -> Unit,
    snooze: (LocalDateTime) -> Unit,
) {
    var customDate by remember { mutableStateOf(false) }
    val time = reminder.remindAt?.toLocalTime() ?: LocalTime.of(9, 0)
    if (!customDate) AppAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Отложить напоминание") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { snooze(LocalDate.now().plusDays(1).atTime(time)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Update, null); Spacer(Modifier.width(12.dp)); Text("Завтра", Modifier.weight(1f))
                }
                TextButton(onClick = { snooze(LocalDate.now().plusWeeks(1).atTime(time)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DateRange, null); Spacer(Modifier.width(12.dp)); Text("На следующей неделе", Modifier.weight(1f))
                }
                TextButton(onClick = { customDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(12.dp)); Text("Выбрать своё", Modifier.weight(1f))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } },
    )
    if (customDate) ReminderDateTimeDialog(
        initialDate = reminder.remindAt?.toLocalDate() ?: LocalDate.now().plusDays(1),
        initialTime = reminder.remindAt?.toLocalTime(),
        dismiss = { customDate = false },
        clear = { customDate = false },
        confirm = { date, selectedTime -> snooze(date.atTime(selectedTime ?: time)) },
    )
}

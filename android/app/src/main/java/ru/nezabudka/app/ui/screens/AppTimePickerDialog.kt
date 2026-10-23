package ru.nezabudka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.SelectionHapticEffect
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTimePickerDialog(initialTime: LocalTime, dismiss: () -> Unit, confirm: (LocalTime) -> Unit) {
    val theme = AppTheme.tokens
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val state = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = true)
    var manual by rememberSaveable { mutableStateOf(false) }
    var hours by rememberSaveable { mutableStateOf("%02d".format(initialTime.hour)) }
    var minutes by rememberSaveable { mutableStateOf("%02d".format(initialTime.minute)) }
    val valid = !manual || (hours.toIntOrNull() in 0..23 && minutes.toIntOrNull() in 0..59)
    SelectionHapticEffect(state.hour to state.minute)
    AppDialog(onDismissRequest = dismiss) {
        Surface(shape = RoundedCornerShape(theme.shapes.dialog), color = theme.colors.modal) {
            Column(Modifier.padding(24.dp)) {
                Text("Выбор времени", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(20.dp))
                if (manual) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(2) },
                            Modifier.weight(1f), label = { Text("Часы") }, singleLine = true,
                            isError = hours.isNotEmpty() && hours.toIntOrNull() !in 0..23,
                            textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text(":", Modifier.padding(horizontal = 8.dp), fontSize = 32.sp)
                        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2) },
                            Modifier.weight(1f), label = { Text("Минуты") }, singleLine = true,
                            isError = minutes.isNotEmpty() && minutes.toIntOrNull() !in 0..59,
                            textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                } else TimePicker(state, colors = TimePickerDefaults.colors(
                    selectorColor = theme.colors.talkButton,
                    timeSelectorSelectedContainerColor = theme.colors.talkButton,
                    timeSelectorSelectedContentColor = theme.colors.onTalkButton,
                    timeSelectorUnselectedContainerColor = theme.colors.input,
                    timeSelectorUnselectedContentColor = theme.colors.textPrimary,
                ))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (manual && valid) { state.hour = hours.toInt(); state.minute = minutes.toInt() }
                        else { hours = "%02d".format(state.hour); minutes = "%02d".format(state.minute) }
                        manual = !manual
                    }) { Icon(if (manual) Icons.Default.Schedule else Icons.Default.Keyboard,
                        if (manual) "Выбрать на циферблате" else "Ввести время вручную") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = dismiss) { Text("Отмена") }
                    TextButton(enabled = valid, onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        confirm(if (manual) LocalTime.of(hours.toInt(), minutes.toInt()) else LocalTime.of(state.hour, state.minute))
                    }) { Text("ОК") }
                }
            }
        }
    }
}

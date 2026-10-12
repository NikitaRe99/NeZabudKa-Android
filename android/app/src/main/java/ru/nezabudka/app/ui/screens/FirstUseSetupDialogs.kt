package ru.nezabudka.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.nezabudka.app.domain.AppSettings
import ru.nezabudka.app.domain.FirstUseSetupKind
import ru.nezabudka.app.domain.FirstUseSetupPrompt
import ru.nezabudka.app.domain.TimeOfDaySetting
import ru.nezabudka.app.domain.parseFirstUseTime

@Composable
fun FirstUseSetupDialogs(
    prompt: FirstUseSetupPrompt?,
    settings: AppSettings,
    notificationsEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
    backgroundRunAllowed: Boolean,
    overlayWindowsAllowed: Boolean,
    alarmVendorSettingsTitle: String?,
    autoStartSettingsTitle: String?,
    confirmTimes: (Long, List<TimeOfDaySetting>) -> Unit,
    confirmFolderInterface: (Long, String) -> Unit,
    finish: (Long, Boolean) -> Unit,
    openNotifications: () -> Unit,
    openExactAlarms: () -> Unit,
    openAutoStart: () -> Unit,
    openBackground: () -> Unit,
    openOverlay: () -> Unit,
    openAlarmVendor: () -> Unit,
    nluModelInstalled: Boolean,
    enableNlu: (Long) -> Unit,
    openNluSettings: () -> Unit,
) {
    if (prompt == null) return
    key(prompt.id) {
        when (prompt.kind) {
            FirstUseSetupKind.COMMAND_UNDERSTANDING -> AppAlertDialog(
                onDismissRequest = { finish(prompt.id, false) },
                title = { Text("Включить расширенное распознавание?") },
                text = { Text(if (nluModelInstalled)
                    "Команда не распознана. NLU помогает понимать свободные формулировки и работает на телефоне. Включите его и попробуйте выполнить сохранённый текст ещё раз."
                    else "Команда не распознана. Локальная модель NLU помогает понимать свободные формулировки. Откройте настройки, скачайте или импортируйте модель и включите распознавание. Исходный текст сохранён.") },
                confirmButton = { TextButton(onClick = {
                    if (nluModelInstalled) enableNlu(prompt.id) else { finish(prompt.id, false); openNluSettings() }
                }) { Text(if (nluModelInstalled) "Включить NLU" else "Открыть настройки NLU") } },
                dismissButton = { TextButton(onClick = { finish(prompt.id, false) }) { Text("Не сейчас") } },
            )
            FirstUseSetupKind.TIME_OF_DAY -> {
                var values by remember { mutableStateOf(settings.timeOfDaySettings) }
                val valid = values.isNotEmpty() && values.all { parseFirstUseTime(it.time) != null }
                AppAlertDialog(
                    onDismissRequest = { finish(prompt.id, false) },
                    title = { Text("Во сколько — «${prompt.phrase}»?") },
                    text = {
                        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Выберите время для слов о времени суток. Эти настройки применятся и к текущему напоминанию.")
                            values.forEachIndexed { index, value ->
                                OutlinedTextField(
                                    value = value.time,
                                    onValueChange = { time -> values = values.mapIndexed { i, item -> if (i == index) item.copy(time = time) else item } },
                                    label = { Text(value.phrase) },
                                    placeholder = { Text("09:00") },
                                    singleLine = true,
                                    isError = parseFirstUseTime(value.time) == null,
                                    supportingText = { if (parseFirstUseTime(value.time) == null) Text("Введите время от 00:00 до 23:59") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(enabled = valid, onClick = {
                            confirmTimes(prompt.id, values.map { it.copy(time = parseFirstUseTime(it.time)!!.toString()) })
                        }) { Text("Сохранить и продолжить") }
                    },
                    dismissButton = { TextButton(onClick = { finish(prompt.id, true) }) { Text("Оставить как есть") } },
                )
            }
            FirstUseSetupKind.FOLDER_INTERFACE -> {
                var selected by remember { mutableStateOf(settings.noteFolderInterface) }
                AppAlertDialog(
                    onDismissRequest = { finish(prompt.id, false) },
                    title = { Text("Как показывать папки?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Папка «${prompt.folderName}» создана. Выберите удобный способ переключения папок. Его можно изменить в настройках.")
                            listOf(
                                Triple("carousel", "Карусель", "Папки над заметками"),
                                Triple("drawer", "Боковая панель", "Список папок открывается свайпом"),
                            ).forEach { (value, title, description) ->
                                Row(Modifier.fillMaxWidth().clickable { selected = value }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selected == value, onClick = { selected = value })
                                    Column { Text(title); Text(description, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { confirmFolderInterface(prompt.id, selected) }) { Text("Выбрать") } },
                    dismissButton = { TextButton(onClick = { finish(prompt.id, false) }) { Text("Оставить как есть") } },
                )
            }
            FirstUseSetupKind.REMINDER_DELIVERY -> AppAlertDialog(
                onDismissRequest = { finish(prompt.id, false) },
                title = { Text("Чтобы напоминания приходили вовремя") },
                text = {
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (prompt.requiresExactTime) "Напоминание сохранено. Проверьте разрешения, чтобы телефон мог показать его в нужное время."
                            else "Напоминание сохранено без даты. Чтобы получить уведомление, добавьте дату и время. Для доставки также нужны разрешения телефона.")
                        Text(if (notificationsEnabled) "Уведомления разрешены." else "Уведомления выключены — напоминание может не появиться на экране.")
                        if (!notificationsEnabled) TextButton(onClick = openNotifications) { Text("Включить уведомления") }
                        if (prompt.requiresExactTime) {
                            Text(if (exactAlarmsAllowed) "Точные сигналы разрешены." else "Разрешите точные сигналы, чтобы напоминания приходили в выбранное время.")
                            if (!exactAlarmsAllowed) TextButton(onClick = openExactAlarms) { Text("Разрешить точные сигналы") }
                        }
                        if (autoStartSettingsTitle != null) {
                            Text("На вашем телефоне автозапуск может влиять на доставку после перезапуска. Проверьте, разрешён ли он для Незабудки.")
                            TextButton(onClick = openAutoStart) { Text(autoStartSettingsTitle) }
                        }
                        if (!overlayWindowsAllowed) {
                            Text("Разрешите плавающие окна, если хотите видеть окно будильника поверх других приложений.")
                            TextButton(onClick = openOverlay) { Text("Разрешить плавающие окна") }
                        }
                        if (alarmVendorSettingsTitle != null) {
                            Text("В разрешениях телефона проверьте «Всплывающие окна в фоновом режиме» и «Отображать на заблок. экране». Это помогает открыть окно будильника, когда приложение закрыто.")
                            TextButton(onClick = openAlarmVendor) { Text("Проверить всплывающие окна") }
                        }
                        if (!backgroundRunAllowed) {
                            Text("Если уведомления задерживаются, проверьте ограничения работы в фоне и батареи.")
                            TextButton(onClick = openBackground) { Text("Проверить работу в фоне") }
                        }
                        Text("Эти настройки доступны и позже в разделе «Напоминания» в настройках приложения.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { finish(prompt.id, false) }) { Text("Готово") } },
            )
        }
    }
}

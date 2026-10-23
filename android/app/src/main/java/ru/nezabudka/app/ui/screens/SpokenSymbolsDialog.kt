package ru.nezabudka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.nezabudka.app.domain.SpokenSymbol

@Composable
internal fun SpokenSymbolsDialog(values: List<SpokenSymbol>, dismiss: () -> Unit, save: (List<SpokenSymbol>) -> Unit) {
    var draft by remember { mutableStateOf(values.map { it.copy(symbol = it.symbol.replace("\n", "\\n")) }) }
    val valid = draft.all { it.phrase.isNotBlank() && it.symbol.isNotEmpty() } && draft.map { it.phrase.trim().lowercase() }.distinct().size == draft.size
    AppAlertDialog(onDismissRequest = dismiss, title = { Text("Слова в символы") }, text = {
        Column {
            Text("Фразы заменяются после распознавания. Для новой строки укажите \\n.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(draft) { index, rule ->
                    Column {
                        OutlinedTextField(rule.phrase, { value -> draft = draft.toMutableList().also { it[index] = rule.copy(phrase = value.take(100)) } }, Modifier.fillMaxWidth(), label = { Text("Произнести") }, singleLine = true)
                        Row {
                            OutlinedTextField(rule.symbol, { value -> draft = draft.toMutableList().also { it[index] = rule.copy(symbol = value.take(40)) } }, Modifier.weight(1f), label = { Text("Вставить") }, singleLine = true)
                            TextButton(onClick = { draft = draft.toMutableList().also { it.removeAt(index) } }) { Text("Удалить") }
                        }
                    }
                }
                item { TextButton(enabled = draft.size < 100, onClick = { draft = draft + SpokenSymbol("", "") }) { Text("Добавить замену") } }
            }
            if (!valid) Text("Заполните обе строки; фразы не должны повторяться", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { save(draft.map { it.copy(symbol = it.symbol.replace("\\n", "\n")) }); dismiss() }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } })
}

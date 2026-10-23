package ru.nezabudka.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.nezabudka.app.domain.SpokenSymbol
import ru.nezabudka.app.ui.AppTheme

@Composable
internal fun SpokenSymbolsDialog(values: List<SpokenSymbol>, dismiss: () -> Unit, save: (List<SpokenSymbol>) -> Unit) {
    val theme = AppTheme.tokens
    var draft by remember { mutableStateOf(values.map { it.copy(symbol = it.symbol.replace("\n", "\\n")) }) }
    val valid = draft.all { it.phrase.isNotBlank() && it.symbol.isNotEmpty() } && draft.map { it.phrase.trim().lowercase() }.distinct().size == draft.size
    AppAlertDialog(onDismissRequest = dismiss, title = { Text("Слова в символы") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Произносите фразу — приложение вставит указанный символ.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(draft) { index, rule ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(rule.phrase, { value -> draft = draft.toMutableList().also { it[index] = rule.copy(phrase = value.take(100)) } },
                            Modifier.weight(1f), label = { Text("Фраза") }, singleLine = true,
                            shape = RoundedCornerShape(theme.shapes.action), textStyle = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(rule.symbol, { value -> draft = draft.toMutableList().also { it[index] = rule.copy(symbol = value.take(40)) } },
                            Modifier.width(76.dp), label = { Text("Символ") }, singleLine = true,
                            shape = RoundedCornerShape(theme.shapes.action), textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center))
                        IconButton(onClick = { draft = draft.toMutableList().also { it.removeAt(index) } }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, "Удалить замену «${rule.phrase}»", tint = theme.colors.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                item { TextButton(enabled = draft.size < 100, onClick = { draft = draft + SpokenSymbol("", "") }) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Добавить замену")
                } }
            }
            if (!valid) Text("Заполните оба поля. Фразы не должны повторяться.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { save(draft.map { it.copy(symbol = it.symbol.replace("\\n", "\n")) }); dismiss() }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } })
}

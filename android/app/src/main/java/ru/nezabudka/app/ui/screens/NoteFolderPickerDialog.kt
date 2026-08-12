package ru.nezabudka.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.ui.AppLayout
import ru.nezabudka.app.ui.AppTheme

@Composable
internal fun NoteFolderPickerDialog(
    folders: List<NoteFolder>,
    selectedNames: Set<String>,
    dismiss: () -> Unit,
    toggle: (NoteFolder, Boolean) -> Unit,
    createFolder: (String, (NoteFolder) -> Unit) -> Unit,
) {
    val theme = AppTheme.tokens
    var query by remember { mutableStateOf("") }
    val overrides = remember { mutableStateMapOf<Long, Boolean>() }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    AppDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
        dimBackground = false,
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = dismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    AppEditorTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Название папки",
                        textStyle = MaterialTheme.typography.titleLarge,
                        textColor = theme.colors.textPrimary,
                        placeholderColor = theme.colors.placeholder,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).focusRequester(focus),
                        singleLine = true,
                    )
                }
                val matching = folders.filter { it.name.contains(query, true) }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding),
                ) {
                    if (query.isNotBlank() && folders.none { it.name.equals(query.trim(), true) }) item {
                        TextButton(
                            onClick = {
                                val requested = query.trim()
                                createFolder(requested) { created ->
                                    overrides[created.id] = true
                                    toggle(created, true)
                                    query = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Icon(Icons.Default.Add, null); Text("Создать \"${query.trim()}\"") }
                    }
                    items(matching, key = { it.id }) { folder ->
                        val checked = overrides[folder.id] ?: (folder.name in selectedNames)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null)
                            Text(folder.name, Modifier.weight(1f).padding(horizontal = 12.dp))
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value -> overrides[folder.id] = value; toggle(folder, value) },
                            )
                        }
                    }
                }
            }
        }
    }
}

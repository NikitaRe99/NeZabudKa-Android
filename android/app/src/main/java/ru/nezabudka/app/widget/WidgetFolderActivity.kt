package ru.nezabudka.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.NoteFolderEntity
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.ui.LocalAssistantTheme

class WidgetFolderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (HomeWidgets.kind(this, id) != WidgetKind.NOTES) { finish(); return }
        val prefs = WidgetPreferences(this)
        val selected = prefs.folder(id)
        val settings = SettingsStore(this).state.value
        setContent {
            LocalAssistantTheme(settings) {
                var folders by remember { mutableStateOf<List<NoteFolderEntity>?>(null) }
                LaunchedEffect(Unit) { folders = withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).notesDao().allFolders() } }
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Папка виджета", style = MaterialTheme.typography.titleLarge)
                        if (folders == null) Text("Загрузка папок…", style = MaterialTheme.typography.bodyMedium)
                        else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                            item(key = "all") { FolderChoice("Все заметки", selected == null) { choose(id, null) } }
                            items(folders.orEmpty(), key = { it.id }) { folder -> FolderChoice(folder.name, selected == folder.id) { choose(id, folder.id) } }
                        }
                        TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.End)) { Text("Отмена") }
                    }
                }
            }
        }
        window.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun choose(id: Int, folder: Long?) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { WidgetPreferences(applicationContext).setFolder(id, folder) }
            HomeWidgets.requestRefresh(applicationContext, setOf(WidgetKind.NOTES), resetScrollId = id)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun FolderChoice(title: String, selected: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = choose).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = choose)
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

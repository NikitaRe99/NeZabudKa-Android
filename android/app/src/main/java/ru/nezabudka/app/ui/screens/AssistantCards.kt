package ru.nezabudka.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.nezabudka.app.domain.AssistantSettingsTarget
import ru.nezabudka.app.domain.AssistantTipKind
import ru.nezabudka.app.ui.AppLayout
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.appShadowElevation

@Composable
internal fun AssistantTipCard(kind: AssistantTipKind, open: (AssistantSettingsTarget) -> Unit, engine: String = "whisper", acknowledge: () -> Unit) {
    val description = if (kind == AssistantTipKind.PUNCTUATION && engine == "gigaam")
        "GigaAM распознаёт текст без знаков препинания. Чтобы добавлять их автоматически, выберите и включите модель пунктуации."
        else kind.description
    AssistantExplanationCard(kind.title, description, kind.target.path, kind.button,
        { open(kind.target) }, acknowledge, tip = kind)
}

@Composable
internal fun AssistantErrorCard(message: String) {
    val theme = AppTheme.tokens
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(theme.shapes.card),
        border = BorderStroke(1.dp, theme.colors.danger),
        colors = CardDefaults.cardColors(containerColor = theme.colors.card)) {
        Text(message, Modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.bodyMedium, color = theme.colors.danger)
    }
}

@Composable
private fun AssistantExplanationCard(title: String, body: String, path: String?, button: String?,
    action: () -> Unit, acknowledge: () -> Unit, error: Boolean = false, tip: AssistantTipKind? = null) {
    val theme = AppTheme.tokens
    var newlyShown by rememberSaveable(tip?.name) { mutableStateOf(tip != null) }
    LaunchedEffect(tip) { if (newlyShown) { kotlinx.coroutines.delay(12000); newlyShown = false } }
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, if (error) theme.colors.danger else theme.colors.accent.copy(alpha = 0.5f)), shape = RoundedCornerShape(theme.shapes.card),
        colors = CardDefaults.cardColors(containerColor = if (error) theme.colors.card else theme.colors.selected),
        elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tip != null) Text("Новая подсказка", Modifier.alpha(if (newlyShown) 1f else 0f),
                style = MaterialTheme.typography.labelSmall, color = theme.colors.accent)
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (error) theme.colors.danger else theme.colors.textPrimary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = theme.colors.textPrimary)
            path?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = theme.colors.textSecondary) }
            if (!error) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = acknowledge) { Text("Понятно") }
                button?.let { TextButton(onClick = action) { Text(it) } }
            }
        }
    }
}

@Composable
internal fun TechnicalLogDialog(lines: List<String>, close: () -> Unit) {
    val context = LocalContext.current
    val theme = AppTheme.tokens
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(lines) { copied = false }
    AppDialog(onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = theme.colors.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Column(Modifier.weight(1f)) {
                        Text("Технический лог", style = MaterialTheme.typography.titleMedium)
                        Text("Событий в текущем сеансе: ${lines.size}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(enabled = lines.isNotEmpty(), onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Технический лог", lines.joinToString("\n")))
                        copied = true
                    }) { Text(if (copied) "Скопировано" else "Копировать") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(AppLayout.horizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (lines.isEmpty()) item { Text("Событий пока нет") }
                        itemsIndexed(lines) { _, line ->
                            Text(line, Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
            }
        }
    }
}

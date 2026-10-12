package ru.nezabudka.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import ru.nezabudka.app.ui.WhisperThreadBenchmarkUiState

@Composable
internal fun WhisperThreadBenchmarkDialog(
    state: WhisperThreadBenchmarkUiState,
    canApply: Boolean,
    start: () -> Unit,
    cancel: () -> Unit,
    apply: () -> Unit,
    dismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Подбор потоков Whisper") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Проверка использует до 5 секунд последней записи и выбранную модель. Лучше предварительно записать обычную фразу длительностью 3–5 секунд.")
                Text("Сравним все доступные значения дважды. Проверка может занять несколько минут; распознавание временно будет занято.")
                if (state.running) {
                    Text(state.stage)
                    if (state.total > 0) LinearProgressIndicator(
                        progress = { state.completed.toFloat() / state.total }, modifier = Modifier.fillMaxWidth(),
                    ) else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!state.running && state.stage.isNotBlank()) Text(state.stage)
                state.bestThreads?.let { best ->
                    Text("Самый быстрый вариант: $best ${threadCountSuffix(best)}", style = MaterialTheme.typography.titleMedium)
                    state.modelName?.let { Text("Модель: $it", style = MaterialTheme.typography.bodySmall) }
                    Text("Среднее время двух замеров:", style = MaterialTheme.typography.bodySmall)
                    state.timings.forEach { timing ->
                        Text("${timing.threads} ${threadCountSuffix(timing.threads)} — ${String.format(Locale.ROOT, "%.2f", timing.durationMs / 1000.0)} с")
                    }
                    Text("Результат зависит от модели, записи и нагрева телефона. Количество потоков определяет скорость, а не точность.", style = MaterialTheme.typography.bodySmall)
                    if (!canApply) Text("Модель изменилась. Повторите проверку для выбранной модели.")
                }
            }
        },
        confirmButton = {
            if (!state.running && state.bestThreads != null && canApply) {
                TextButton(onClick = apply) { Text("Применить ${state.bestThreads}") }
            } else if (!state.running) {
                TextButton(onClick = start) { Text("Начать проверку") }
            }
        },
        dismissButton = {
            TextButton(onClick = if (state.running) cancel else dismiss) { Text(if (state.running) "Остановить" else "Закрыть") }
            if (!state.running && state.bestThreads != null) TextButton(onClick = start) { Text("Повторить") }
        },
    )
}

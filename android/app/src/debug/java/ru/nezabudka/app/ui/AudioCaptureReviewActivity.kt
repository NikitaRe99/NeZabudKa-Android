package ru.nezabudka.app.ui

import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.speech.WavAudioRecorder
import ru.nezabudka.app.speech.PcmWavBlockProcessor
import java.io.File

/** Debug-only isolated capture: never executes a command or overwrites the last recording. */
class AudioCaptureReviewActivity : ComponentActivity() {
    private var recorder: WavAudioRecorder? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsSnapshot = SettingsStore(this).state.value
        val dateOnly = intent.getBooleanExtra("date_only", false)
        val holdout = intent.getBooleanExtra("context_holdout", false)
        val prefix = if (holdout) "capture-context-holdout" else if (dateOnly) "capture-date-audit" else "capture-audit-044"
        val count = if (dateOnly || holdout) 1 else 3
        val phrase = if (holdout) "Придумайте новую команду для заметок со своим содержимым, которую ещё не проверяли. После записи пришлите точный произнесённый текст в чат."
            else if (dateOnly) "Завтра в десять двадцать пять" else "Напомни завтра утром сходить к врачу и купить бумаги"
        setContent {
            var index by remember { mutableIntStateOf(0) }
            var recording by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf("") }
            val results = remember { JSONArray() }
            val profiles = listOf("Обычная запись", "Без эхоподавления", "Необработанный микрофон")
            LocalAssistantTheme(settingsSnapshot) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 70.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Проверка микрофона", style = MaterialTheme.typography.headlineSmall)
                        Text("${index.coerceAtMost(count - 1) + 1} из $count · ${profiles[index.coerceAtMost(2)]}")
                        Text("Произнесите:\n«$phrase»")
                        Text("Нажмите начать, дождитесь надписи «Говорите», произнесите фразу и нажмите остановить сразу после последнего слова.")
                        Text(message)
                        Button(enabled = !busy && index < count, onClick = {
                            if (!recording) {
                                runCatching {
                                    val raw = File(filesDir, "$prefix-$index-raw.wav")
                                    recorder = WavAudioRecorder(this@AudioCaptureReviewActivity,
                                        captureSources = if (index == 2) intArrayOf(MediaRecorder.AudioSource.UNPROCESSED) else intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION),
                                        echoCancellationEnabled = index == 0, recordingTailMs = 180, outputFile = raw)
                                    recorder!!.start()
                                }.onSuccess { recording = true; message = "Говорите" }
                                    .onFailure { message = it.message.orEmpty() }
                            } else {
                                busy = true
                                val releaseTime = SystemClock.elapsedRealtime()
                                lifecycleScope.launch {
                                    runCatching {
                                        val capture = recorder!!
                                        val raw = capture.stop()
                                        val processed = File(filesDir, "$prefix-$index.wav")
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            if (index < 2) PcmWavBlockProcessor.process(raw, processed) else raw.copyTo(processed, overwrite = true)
                                        }
                                        results.put(JSONObject().put("file", processed.name).put("raw", raw.name)
                                            .put("profile", profiles[index]).put("capture", capture.lastCaptureInfo)
                                            .put("stopDurationMs", SystemClock.elapsedRealtime() - releaseTime))
                                        File(filesDir, "$prefix.json").writeText(results.toString(2))
                                    }.onSuccess { index++; message = if (index == count) "Готово. Все записи сохранены." else "Запись сохранена. Повторите фразу для следующего режима." }
                                        .onFailure { message = it.message.orEmpty() }
                                    recording = false; busy = false; recorder = null
                                }
                            }
                        }) { Text(if (recording) "Остановить" else "Начать запись") }
                        TextButton(onClick = { recorder?.cancel(); finish() }) { Text("Завершить проверку") }
                    }
                }
            }
        }
    }
    override fun onDestroy() { recorder?.cancel(); super.onDestroy() }
}

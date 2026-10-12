package ru.nezabudka.app.speech

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.AssistantApplication
import ru.nezabudka.app.data.UserModelsStorage
import java.io.File
import java.security.MessageDigest

/** Explicit opt-in. Raw decoder comparison; no text editing, commands or setting changes. */
@RunWith(AndroidJUnit4::class)
class WhisperRepetitionCauseTest {
    @Test fun compare_prompt_on_identical_recordings() = runBlocking(Dispatchers.Default) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("run_repetition_cause") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        assumeTrue(manager.state.value.selectedName != null)
        val hints = (context as AssistantApplication).container.notes.observeFolders().first()
            .map { it.name }.filter(String::isNotBlank).take(8)
        val realPrompt = contextualWhisperPrompt(hints)
        val commaPrompt = WHISPER_COMMAND_VOCABULARY.split(" ").joinToString(", ") + "."
        val commaWithHints = commaPrompt + if (hints.isEmpty()) "" else " " + hints.joinToString(", ") + "."
        val names = args.getString("reference_files")?.split(",") ?: listOf(
            "whisper-repeat-reference.wav", "whisper-repeat-current-20261012.wav", "whisper-count-reference-audit.wav",
        )
        val threads = manager.recommendedThreadCount()
        val vad = WhisperVadModelProvider(context).modelFile()
        val directory = File(context.cacheDir, "repetition-cause-${System.nanoTime()}").apply { mkdirs() }
        val rows = JSONArray()
        val report = JSONObject().put("threads", threads).put("results", rows)
        data class Candidate(val name: String, val prompt: String, val context: Int? = null, val beam: Int = 1)
        val candidates = listOf(
            Candidate("current-with-folder-hints", realPrompt),
            Candidate("vocabulary-only", contextualWhisperPrompt(emptyList())),
            Candidate("no-prompt", ""),
            Candidate("comma-vocabulary", commaPrompt),
            Candidate("comma-with-folder-hints", commaWithHints),
            Candidate("folder-hints-only", hints.joinToString(", ")),
        ).filter { args.getString("candidate_names")?.split(",")?.contains(it.name) ?: true }
        try {
            for (name in names) {
                require(name.matches(Regex("[a-zA-Z0-9_-]+\\.wav")))
                val source = File(context.filesDir, name)
                require(source.isFile)
                val copy = source.copyTo(File(directory, name))
                val model = manager.selectedModel(pcmWavBytes = copy.length())
                val tiny = model.length() <= 96L * 1024L * 1024L
                val ranges = WhisperNative.detectSpeechSegments(vad.absolutePath, copy.absolutePath, threads, 19f)
                require(hasWhisperSpeechRanges(ranges))
                val segments = PcmWavSegmenter.splitForWhisper(copy, File(directory, "segments"), ranges, 19)
                val hash = MessageDigest.getInstance("SHA-256").digest(copy.readBytes()).joinToString("") { "%02x".format(it) }
                try {
                    // Weight loading is excluded; every call still uses a fresh native decoder state.
                    val warm = whisperInferencePolicy(segments.first().length(), tiny)
                    WhisperNative.transcribeWav(model.absolutePath, segments.first().absolutePath, "", "ru", "", threads,
                        warm.audioContext, 0, warm.singleSegment, 1, 20_000)
                    for (candidate in candidates) {
                        val start = SystemClock.elapsedRealtime()
                        val confidences = JSONArray()
                        val decoded = runCatching {
                            segments.joinToString(" ") { segment ->
                                val policy = whisperInferencePolicy(segment.length(), tiny)
                                WhisperNative.transcribeWav(model.absolutePath, segment.absolutePath, "", "ru", candidate.prompt,
                                    threads, candidate.context ?: policy.audioContext, 0, policy.singleSegment, candidate.beam, 20_000)
                                    .trim().also { confidences.put(WhisperNative.lastConfidence()) }
                            }
                        }
                        val row = JSONObject().put("file", name).put("sha256", hash).put("candidate", candidate.name)
                            .put("durationMs", SystemClock.elapsedRealtime() - start).put("segments", segments.size)
                            .put("audioSeconds", (copy.length() - 44L) / 32_000.0).put("text", decoded.getOrNull())
                            .put("error", decoded.exceptionOrNull()?.message).put("confidence", confidences)
                        rows.put(row)
                        File(context.filesDir, "whisper-repetition-cause-report.json").writeText(report.toString(2))
                        println("REPETITION_CAUSE $row")
                    }
                } finally { PcmWavSegmenter.cleanup(segments, copy) }
            }
        } finally {
            WhisperNative.release()
            directory.deleteRecursively()
        }
    }
}

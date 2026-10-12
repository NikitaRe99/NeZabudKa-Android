package ru.nezabudka.app.speech

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.UserModelsStorage
import java.io.File
import java.security.MessageDigest

/** Opt-in comparison: identical copied audio, no command execution or setting changes. */
@RunWith(AndroidJUnit4::class)
class WhisperWordBoundariesTest {
    @Test fun compare_audio_preservation_prompts_and_token_limit() = runBlocking(Dispatchers.Default) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("run_whisper_word_boundaries") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Never silently substitute the most recent recording for a known reference.
        val sourceName = arguments.getString("reference_file").orEmpty()
        require(sourceName.matches(Regex("[a-zA-Z0-9_-]+\\.wav"))) { "Provide a copied WAV name in filesDir" }
        val source = File(context.filesDir, sourceName)
        require(source.isFile && source.length() in 6_444L..960_044L) { "Reference must contain 0.2–30 seconds" }
        val expected = arguments.getString("expected_transcript").orEmpty().trim()
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        assumeTrue("Install a Whisper model first", manager.state.value.selectedName != null)
        val model = manager.selectedModel(pcmWavBytes = source.length())
        val threads = manager.recommendedThreadCount()
        val tiny = model.length() <= 96L * 1024L * 1024L
        val directory = File(context.cacheDir, "whisper-word-boundaries-${System.nanoTime()}").apply { mkdirs() }
        val copy = File(directory, "reference.wav").also { source.copyTo(it) }
        val sourceHash = MessageDigest.getInstance("SHA-256").digest(copy.readBytes())
            .joinToString("") { "%02x".format(it) }
        val results = JSONArray()
        data class Candidate(
            val name: String, val preserve: Boolean, val vocabulary: Boolean,
            val accurate: Boolean, val cap: Boolean,
            val audioContext: Int? = null, val promptOverride: String? = null,
        )
        val candidates = listOf(
            Candidate("fast-0.2.19", false, false, false, true),
            Candidate("fast-audio-only", true, false, false, true),
            Candidate("fast-prompt-only", false, true, false, true),
            Candidate("fast-0.2.20", true, true, false, false),
            Candidate("accurate-0.2.19", true, true, true, true),
            Candidate("accurate-0.2.20", true, true, true, false),
            Candidate("fast-window-512", true, true, false, false, audioContext = 512),
            Candidate("fast-window-768", true, true, false, false, audioContext = 768),
            Candidate("fast-window-full", true, true, false, false, audioContext = 0),
            Candidate("fast-window-512-no-prompt", true, true, false, false, audioContext = 512, promptOverride = ""),
        )
        try {
            val vad = WhisperVadModelProvider(context).modelFile()
            val ranges = WhisperNative.detectSpeechSegments(vad.absolutePath, copy.absolutePath, threads, 19f)
            require(hasWhisperSpeechRanges(ranges)) { "Reference contains no detected speech" }
            // Exclude initial weight loading from the candidate timings.
            val warmup = whisperInferencePolicy(copy.length(), tiny)
            WhisperNative.transcribeWav(model.absolutePath, copy.absolutePath, "", "ru", "",
                threads, warmup.audioContext, 96, warmup.singleSegment, 1, 60_000)
            for (candidate in candidates) {
                val segments = if (candidate.preserve) {
                    PcmWavSegmenter.splitForWhisper(copy, File(directory, "segments"), ranges, 19)
                } else {
                    PcmWavSegmenter.splitAtSpeechRanges(copy, File(directory, "segments"), ranges, 19)
                }
                try {
                    val started = SystemClock.elapsedRealtime()
                    val decoded = runCatching {
                        segments.map { segment ->
                            val policy = whisperInferencePolicy(segment.length(), tiny, candidate.accurate)
                            val cap = if (candidate.cap && segment.length() <= 44L + 12L * 32_000L) 96 else 0
                            val prompt = candidate.promptOverride ?: when {
                                candidate.accurate && candidate.cap -> WHISPER_COMMAND_VOCABULARY.replace(" отмени", "")
                                candidate.vocabulary -> contextualWhisperPrompt(emptyList())
                                else -> RUSSIAN_WHISPER_PROMPT
                            }
                            WhisperNative.transcribeWav(model.absolutePath, segment.absolutePath, "", "ru", prompt,
                                threads, candidate.audioContext ?: policy.audioContext, cap, policy.singleSegment, policy.beamSize,
                                policy.timeLimitMs.takeIf { it > 0 } ?: 60_000)
                                .trim()
                        }.joinToString(" ")
                    }
                    val result = JSONObject().put("candidate", candidate.name)
                        .put("durationMs", SystemClock.elapsedRealtime() - started)
                        .put("decodedAudioSeconds", segments.sumOf { (it.length() - 44L) / 32_000.0 })
                        .put("text", decoded.getOrNull()).put("error", decoded.exceptionOrNull()?.message)
                    if (decoded.isSuccess && expected.isNotEmpty()) {
                        val referenceWords = words(expected)
                        val errors = editDistance(referenceWords, words(decoded.getOrThrow()))
                        result.put("wordErrors", errors).put("referenceWords", referenceWords.size)
                            .put("wer", errors.toDouble() / referenceWords.size.coerceAtLeast(1))
                    }
                    results.put(result)
                } finally { PcmWavSegmenter.cleanup(segments, copy) }
            }
            val report = JSONObject().put("reference", sourceName).put("referenceSha256", sourceHash)
                .put("comparison", "Raw first decode: identical native library, no Kotlin retries or transcript correction")
                .put("expectedTranscript", expected.takeIf { it.isNotEmpty() })
                .put("model", model.name).put("threads", threads).put("vadBoundaries", JSONArray(ranges.toList()))
                .put("results", results)
            File(context.filesDir, "whisper-word-boundaries-report.json").writeText(report.toString(2))
            println("WHISPER_WORD_BOUNDARIES_REPORT $report")
        } finally {
            WhisperNative.release()
            directory.deleteRecursively()
        }
    }

    private fun words(text: String): List<String> = Regex("[\\p{L}\\p{N}]+")
        .findAll(text.lowercase().replace('ё', 'е')).map { it.value }.toList()

    private fun editDistance(reference: List<String>, actual: List<String>): Int {
        var previous = IntArray(actual.size + 1) { it }
        reference.forEachIndexed { row, word ->
            val current = IntArray(actual.size + 1)
            current[0] = row + 1
            actual.forEachIndexed { column, other ->
                current[column + 1] = minOf(current[column] + 1, previous[column + 1] + 1,
                    previous[column] + if (word == other) 0 else 1)
            }
            previous = current
        }
        return previous.last()
    }
}

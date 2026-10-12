package ru.nezabudka.app.speech

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
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
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.domain.RussianTemporalParser
import ru.nezabudka.app.ui.InterfaceReviewActivity
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime

/** Opt-in diagnosis on a copied user recording. Reference is used only after decoding. */
@RunWith(AndroidJUnit4::class)
class WhisperShortDateLoopAuditTest {
    @Test fun production_contexts_keep_the_short_date_once() = runBlocking(Dispatchers.Default) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_short_date_loop_audit") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.filesDir, "whisper-poslezavtra-repeat-20261013.wav")
        require(source.isFile)
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        val foreground = ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
            .putExtra("gallery_message", "Whisper: контроль фразы о послезавтра"))
        foreground.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        Thread.sleep(750)
        val engine = WhisperCppSttEngine(manager, WhisperVadModelProvider(context), File(context.cacheDir, "short-date-production"),
            keepModelLoaded = { true }, configuredThreadCount = { 6 })
        val rows = JSONArray()
        try {
            for ((name, scope, dateReply) in listOf(
                Triple("date-reply", CommandScope.ASSISTANT, true),
                Triple("reminder-tab", CommandScope.REMINDERS, false),
                Triple("assistant", CommandScope.ASSISTANT, false),
            )) {
                engine.setCommandScope(scope)
                engine.setAwaitingDateResponse(dateReply)
                val started = SystemClock.elapsedRealtime()
                val result = engine.transcribe(source)
                val row = JSONObject().put("context", name).put("text", result.text)
                    .put("durationMs", SystemClock.elapsedRealtime() - started)
                    .put("passes", engine.runtime.value.nativePasses)
                    .put("repetitionRetry", engine.runtime.value.usedRepetitionRetry)
                    .put("requiresConfirmation", result.requiresConfirmation)
                rows.put(row)
                File(context.filesDir, "short-date-loop-production.json").writeText(rows.toString(2))
                val now = LocalDateTime.of(2026, 10, 13, 12, 0)
                val parsed = RussianTemporalParser().parse(result.text, now)
                require(parsed.remindAt == now.toLocalDate().plusDays(2).atTime(20, 0)) { "$name: ${result.text}" }
                require(Regex("[\\p{L}]+").findAll(result.text).count() <= 8) { "$name repeats: ${result.text}" }
            }
        } finally { engine.release(); foreground.close() }
    }

    @Test fun compare_prompts_and_windows_on_same_short_date() = runBlocking(Dispatchers.Default) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_short_date_loop_audit") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.filesDir, "whisper-poslezavtra-repeat-20261013.wav")
        require(source.isFile)
        val manager = WhisperModelManager(context, UserModelsStorage(context)).apply { refresh() }
        require(manager.state.value.selectedName != null)
        val foreground = ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
            .putExtra("gallery_message", "Whisper: проверка повторов даты"))
        foreground.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        Thread.sleep(750)
        val directory = File(context.cacheDir, "short-date-loop-audit").apply { mkdirs() }
        val copy = source.copyTo(File(directory, source.name), overwrite = true)
        val model = manager.selectedModel(pcmWavBytes = copy.length())
        val threads = manager.recommendedThreadCount(6)
        val policy = whisperInferencePolicy(copy.length(), model.length() <= 96L * 1024L * 1024L)
        val cases = listOf(
            Triple("date-208", WhisperRequestContext.DATE_REPLY, 208),
            Triple("general-208", WhisperRequestContext.COMMAND, 208),
            Triple("reminders-208", WhisperRequestContext.REMINDER_COMMAND, 208),
            Triple("no-prompt-208", WhisperRequestContext.DICTATION, 208),
            Triple("date-512", WhisperRequestContext.DATE_REPLY, 512),
            Triple("reminders-512", WhisperRequestContext.REMINDER_COMMAND, 512),
            Triple("no-prompt-512", WhisperRequestContext.DICTATION, 512),
            Triple("date-768-beam3", WhisperRequestContext.DATE_REPLY, 768),
        )
        val rows = JSONArray()
        val report = JSONObject()
            .put("sha256", MessageDigest.getInstance("SHA-256").digest(copy.readBytes()).joinToString("") { "%02x".format(it) })
            .put("audioSeconds", (copy.length() - 44L) / 32_000.0)
            .put("model", model.name).put("threads", threads).put("results", rows)
        try {
            val vad = WhisperVadModelProvider(context).modelFile()
            val boundaries = WhisperNative.detectSpeechSegments(vad.absolutePath, copy.absolutePath, threads, 19f)
            require(hasWhisperSpeechRanges(boundaries))
            val segments = PcmWavSegmenter.splitForWhisper(copy, File(directory, "segments"), boundaries, 19)
            report.put("segments", segments.size)
            require(segments.size == 1)
            for ((name, scope, window) in cases) {
                val beam = if (name.endsWith("beam3")) 3 else 1
                val prompt = whisperRequestPrompt(emptyList(), scope, window)
                val started = SystemClock.elapsedRealtime()
                val result = runCatching {
                    WhisperNative.transcribeWav(model.absolutePath, segments.single().absolutePath, "", "ru",
                        prompt, threads, window, policy.maxTokens, policy.singleSegment, beam, 20_000, 0f)
                }
                val row = JSONObject().put("candidate", name).put("durationMs", SystemClock.elapsedRealtime() - started)
                    .put("prompt", prompt).put("rawText", result.getOrNull())
                    .put("collapsedText", result.getOrNull()?.collapseWhisperRepetition())
                    .put("error", result.exceptionOrNull()?.message)
                rows.put(row)
                File(context.filesDir, "short-date-loop-audit.json").writeText(report.toString(2))
            }
        } finally {
            WhisperNative.release()
            directory.deleteRecursively()
            foreground.close()
        }
    }
}

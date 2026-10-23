package ru.nezabudka.app.speech

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.nezabudka.app.llm.ClassicWordPieceTokenizer
import ru.nezabudka.app.data.UserModelsStorage
import java.nio.LongBuffer
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.exp

const val NOTE_PUNCTUATION_WHISPER = "whisper"
const val NOTE_PUNCTUATION_RUPUNCT = "rupunct_small"

fun normalizeNotePunctuationModel(value: String): String =
    if (value == NOTE_PUNCTUATION_RUPUNCT) value else NOTE_PUNCTUATION_WHISPER

/** Reuse a session briefly between foreground requests, without changing inference. */
class NotePunctuation(context: Context, storage: UserModelsStorage = UserModelsStorage(context),
                      private val canRetain: () -> Boolean = { false }) {
    private val app = context.applicationContext
    val models = RupunctModelManager(app, storage)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleRelease: Job? = null
    private var session: OrtSession? = null
    private var modelIdentity: String? = null
    private val tokenizer by lazy {
        val vocabulary = app.assets.open("punctuation/vocab.txt").bufferedReader().use { it.readLines() }
        ClassicWordPieceTokenizer(vocabulary, MAX_TOKENS)
    }
    private val labels by lazy {
        val map = JSONObject(app.assets.open("punctuation/config.json").bufferedReader().use { it.readText() }).getJSONObject("id2label")
        List(map.length()) { map.getString(it.toString()) }
    }
    private val commaLabels by lazy { labels.indices.filter { labels[it].endsWith("_COMMA") } }

    suspend fun release() = mutex.withLock { idleRelease?.cancel(); close() }
    private fun close() { session?.close(); session = null; modelIdentity = null }

    private fun retainOrRelease() {
        idleRelease?.cancel()
        if (session == null || !canRetain()) { close(); return }
        idleRelease = scope.launch { delay(60_000); mutex.withLock { close() } }
    }

    suspend fun restore(text: String, model: String, fullPunctuation: Boolean = false): String = withContext(Dispatchers.Default) {
        if (model != NOTE_PUNCTUATION_RUPUNCT || text.isBlank()) return@withContext text
        val words = punctuationWords(text)
        if (words.isEmpty() || (!fullPunctuation && words.size < 2)) return@withContext text
        mutex.withLock {
            idleRelease?.cancel()
            try {
                val environment = OrtEnvironment.getEnvironment()
                val modelFile = withContext(Dispatchers.IO) { models.modelFile() }
                val predictions = MutableList<Boolean?>(words.size) { null }
                val fullPredictions = MutableList<FullPunctuationPrediction?>(words.size) { null }
                val identity = "${modelFile.absolutePath}:${modelFile.length()}:${modelFile.lastModified()}"
                if (modelIdentity != identity) {
                    close()
                    session = OrtSession.SessionOptions().use { options ->
                        options.setIntraOpNumThreads((Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4))
                        options.setInterOpNumThreads(1)
                        options.addConfigEntry("session.intra_op.allow_spinning", "0")
                        options.addConfigEntry("session.inter_op.allow_spinning", "0")
                        environment.createSession(modelFile.absolutePath, options)
                    }
                    modelIdentity = identity
                }
                val active = requireNotNull(session)
                var firstUnprocessed = 0
                while (firstUnprocessed < words.size) {
                    coroutineContext.ensureActive()
                    // Overlapping text context, but each original word is rendered just once.
                    val start = (firstUnprocessed - CONTEXT_WORDS).coerceAtLeast(0)
                    var end = start
                    var tokenCount = 0
                    while (end < words.size) {
                        val count = tokenizer.encode(words[end].value.lowercase(Locale.ROOT)).pieces.count { it != null }
                        if (tokenCount + count > MAX_TOKENS - 2) break
                        tokenCount += count
                        end++
                    }
                    check(end > firstUnprocessed) { "Слишком длинное слово для модели пунктуации" }
                    val processUntil = if (end == words.size) end else (end - CONTEXT_WORDS).coerceAtLeast(firstUnprocessed + 1)
                    val windowWords = words.subList(start, end)
                    val input = windowWords.joinToString(" ") { it.value.lowercase(Locale.ROOT) }
                    val offsets = mutableListOf<Int>()
                    var offset = 0
                    windowWords.forEach { offsets += offset; offset += it.value.lowercase(Locale.ROOT).length + 1 }
                    val tokens = tokenizer.encode(input)
                    val shape = longArrayOf(1, MAX_TOKENS.toLong())
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.inputIds), shape).use { ids ->
                        OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.attentionMask), shape).use { mask ->
                            OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.tokenTypeIds), shape).use { types ->
                                active.run(mapOf("input_ids" to ids, "attention_mask" to mask, "token_type_ids" to types)).use { result ->
                                    @Suppress("UNCHECKED_CAST")
                                    val logits = (result[0].value as Array<Array<FloatArray>>)[0]
                                    for (index in firstUnprocessed until processUntil) {
                                        val tokenIndex = tokens.pieces.indexOfFirst { it?.start == offsets[index - start] }
                                        if (tokenIndex < 0 || tokens.pieces[tokenIndex]?.value == "[UNK]") continue
                                        val scores = logits[tokenIndex]
                                        val maximum = scores.max()
                                        val weights = scores.map { exp((it - maximum).toDouble()) }
                                        val probability = commaLabels.sumOf { weights[it] } / weights.sum()
                                        predictions[index] = probability >= COMMA_THRESHOLD
                                        if (fullPunctuation) fullPredictions[index] = fullPunctuationPrediction(scores, labels)
                                    }
                                }
                            }
                        }
                    }
                    firstUnprocessed = processUntil
                }
                if (fullPunctuation) applyFullPunctuation(text, words, fullPredictions)
                else applyPredictedCommas(text, words, predictions)
            } catch (error: Throwable) {
                close()
                throw error
            } finally { retainOrRelease() }
        }
    }

    private companion object {
        const val MAX_TOKENS = 128
        const val CONTEXT_WORDS = 8
        const val COMMA_THRESHOLD = 0.55
    }
}

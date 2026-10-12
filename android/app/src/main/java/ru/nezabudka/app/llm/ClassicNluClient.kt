package ru.nezabudka.app.llm

import android.content.Context
import android.net.Uri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ru.nezabudka.app.domain.AssistantCommand
import ru.nezabudka.app.domain.CommandContext
import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.domain.isUnsupportedMediaCommand
import ru.nezabudka.app.domain.isCompletedActionStatement
import ru.nezabudka.app.domain.extractStopTrackingReminderTarget
import ru.nezabudka.app.ports.CommandUnderstandingPort
import ru.nezabudka.app.data.AndroidModelLibrary
import ru.nezabudka.app.data.ModelDownloadWorker
import ru.nezabudka.app.data.UserModelsStorage
import java.nio.LongBuffer
import java.time.LocalDateTime
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class ClassicNluModelState(
    val installed: Boolean = false,
    val installedModels: List<String> = emptyList(),
    val selectedName: String? = null,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val error: String? = null,
)

data class ClassicNluIntentScore(val intent: String, val confidence: Double)

data class ClassicNluDiagnostic(
    val requestId: Long = 0,
    val inputText: String = "",
    val modelAlreadyLoaded: Boolean = false,
    val elapsedMs: Long = 0,
    val tokenPieces: List<String> = emptyList(),
    val bioLabels: List<String> = emptyList(),
    val intentScores: List<ClassicNluIntentScore> = emptyList(),
    val selectedIntent: String? = null,
    val confidence: Double? = null,
    val requiredConfidence: Double? = null,
    val slots: Map<String, String> = emptyMap(),
    val decision: String = "Модель ещё не запускалась",
    val error: String? = null,
)

private data class ClassicNluInference(
    val command: AssistantCommand,
    val tokenPieces: List<String>,
    val bioLabels: List<String>,
    val intentScores: List<ClassicNluIntentScore>,
    val selectedIntent: String,
    val confidence: Double,
    val requiredConfidence: Double,
    val slots: Map<String, String>,
    val decision: String,
)

/**
 * Local Siri-style NLU: one encoder predicts an intent and BIO slots. It never
 * changes application data directly; validated commands still go through Dispatcher.
 */
class ClassicNluClient(
    context: Context,
    private val mapper: ClassicNluCommandMapper,
    private val storage: UserModelsStorage,
) : CommandUnderstandingPort {
    private val applicationContext = context.applicationContext
    private val environment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OrtEnvironment.getEnvironment()
    }
    private val mutex = Mutex()
    private var tokenizer: ClassicWordPieceTokenizer? = null
    private var session: OrtSession? = null
    private val modelLibrary = AndroidModelLibrary(
        applicationContext,
        storage,
        ModelDownloadWorker.KIND_NLU,
        "onnx",
        "nlu_models",
        MODEL_FILE,
    ) { file -> require(file.length() > 1_000_000L) { "Файл слишком мал и не похож на NLU-модель" } }
    private val workManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManager.getInstance(applicationContext)
    }
    private val modelObserverStarted = AtomicBoolean(false)
    private val _modelState = MutableStateFlow(scanModel())
    val modelState: StateFlow<ClassicNluModelState> = _modelState
    private val _diagnostic = MutableStateFlow(ClassicNluDiagnostic())
    val diagnostic: StateFlow<ClassicNluDiagnostic> = _diagnostic

    override suspend fun understand(
        text: String,
        now: LocalDateTime,
        context: CommandContext,
    ): AssistantCommand = withContext(Dispatchers.Default) {
        mutex.withLock {
            val requestId = _diagnostic.value.requestId + 1
            val modelAlreadyLoaded = session != null
            val started = System.nanoTime()
            try {
                val inference = infer(text, now, context)
                _diagnostic.value = ClassicNluDiagnostic(
                    requestId = requestId,
                    inputText = text,
                    modelAlreadyLoaded = modelAlreadyLoaded,
                    elapsedMs = (System.nanoTime() - started) / 1_000_000,
                    tokenPieces = inference.tokenPieces,
                    bioLabels = inference.bioLabels,
                    intentScores = inference.intentScores,
                    selectedIntent = inference.selectedIntent,
                    confidence = inference.confidence,
                    requiredConfidence = inference.requiredConfidence,
                    slots = inference.slots,
                    decision = inference.decision,
                )
                inference.command
            } catch (error: Exception) {
                _diagnostic.value = ClassicNluDiagnostic(
                    requestId = requestId,
                    inputText = text,
                    modelAlreadyLoaded = modelAlreadyLoaded,
                    elapsedMs = (System.nanoTime() - started) / 1_000_000,
                    decision = "Ошибка выполнения NLU",
                    error = "${error::class.simpleName}: ${error.message.orEmpty()}",
                )
                AssistantCommand(CommandIntent.UNKNOWN)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun infer(text: String, now: LocalDateTime, context: CommandContext): ClassicNluInference {
        val activeTokenizer = tokenizer ?: loadTokenizer().also { tokenizer = it }
        val activeSession = session ?: loadSession().also { session = it }
        val tokens = activeTokenizer.encode(text)
        val shape = longArrayOf(1, MAX_LENGTH.toLong())
        OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.inputIds), shape).use { inputIds ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.attentionMask), shape).use { attentionMask ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.tokenTypeIds), shape).use { tokenTypeIds ->
                    activeSession.run(
                        mapOf(
                            "input_ids" to inputIds,
                            "attention_mask" to attentionMask,
                            "token_type_ids" to tokenTypeIds,
                        ),
                    ).use { output ->
                        val intentLogits = output.get("intent_logits").orElseThrow().value
                            .let { it as Array<FloatArray> }[0]
                        val slotLogits = output.get("slot_logits").orElseThrow().value
                            .let { it as Array<Array<FloatArray>> }[0]
                        return decode(text, now, context, tokens, intentLogits, slotLogits)
                    }
                }
            }
        }
    }

    private fun decode(
        text: String,
        now: LocalDateTime,
        context: CommandContext,
        tokens: ClassicNluTokens,
        intentLogits: FloatArray,
        slotLogits: Array<FloatArray>,
    ): ClassicNluInference {
        val intentIndex = intentLogits.indices.maxByOrNull(intentLogits::get) ?: error("Пустой intent tensor")
        val intent = CommandIntent.fromWireName(INTENT_LABELS[intentIndex])
        val probabilities = softmaxProbabilities(intentLogits)
        val confidence = probabilities[intentIndex]
        val requiredConfidence = when {
            intent.isDestructive() && !EXPLICIT_DELETION.containsMatchIn(text) -> DESTRUCTIVE_THRESHOLD
            intent.isDestructive() -> EXPLICIT_DESTRUCTIVE_THRESHOLD
            else -> INTENT_THRESHOLDS[intent] ?: INTENT_THRESHOLD
        }
        val slots = extractSlots(text, tokens, slotLogits)
        val tokenPieces = tokens.pieces.mapNotNull { it?.value }
        val bioLabels = tokens.pieces.mapIndexedNotNull { index, piece ->
            if (piece == null || index >= slotLogits.size) null else {
                val labelIndex = slotLogits[index].indices.maxByOrNull(slotLogits[index]::get) ?: 0
                "${piece.value}:${SLOT_LABELS[labelIndex]}"
            }
        }
        val intentScores = probabilities.indices
            .sortedByDescending(probabilities::get)
            .take(3)
            .map { index -> ClassicNluIntentScore(INTENT_LABELS[index], probabilities[index]) }
        fun result(command: AssistantCommand, decision: String) = ClassicNluInference(
            command = command,
            tokenPieces = tokenPieces,
            bioLabels = bioLabels,
            intentScores = intentScores,
            selectedIntent = intent.wireName,
            confidence = confidence,
            requiredConfidence = requiredConfidence,
            slots = slots,
            decision = decision,
        )
        mapper.explicitCommand(text, now, context.scope)?.let {
            return result(it, "Принято: явная команда ${it.intent.wireName}")
        }
        if (intent == CommandIntent.UNKNOWN) return result(unknown(), "Отклонено: модель выбрала unknown")
        if (intent !in allowedIntents(context.scope)) {
            return result(unknown(), "Отклонено: intent недоступен на вкладке ${context.scope.name.lowercase()}")
        }
        if (confidence < requiredConfidence) {
            return result(unknown(), "Отклонено: уверенность ниже порога")
        }
        if (!passesIntentGuard(intent, text, slots)) {
            return result(unknown(), "Отклонено защитным фильтром intent")
        }

        return try {
            val cleanSlots = slots.toMutableMap().apply {
                this["TARGET"] = this["TARGET"]?.cleanTarget().orEmpty()
            }.filterValues(String::isNotBlank)
            result(mapper.map(intent, cleanSlots, text, now), "Принято и передано Dispatcher")
        } catch (error: Exception) {
            result(unknown(), "Отклонено преобразованием команды: ${error.message.orEmpty()}")
        }
    }

    private fun extractSlots(
        text: String,
        tokens: ClassicNluTokens,
        slotLogits: Array<FloatArray>,
    ): Map<String, String> {
        data class Segment(
            val type: String,
            var start: Int,
            var endExclusive: Int,
            var tokenCount: Int,
            var score: Float,
        )
        val segments = mutableListOf<Segment>()
        var current: Segment? = null
        fun finishCurrent() {
            current?.let(segments::add)
            current = null
        }
        tokens.pieces.forEachIndexed { index, piece ->
            if (piece == null || index >= slotLogits.size) return@forEachIndexed
            val labelIndex = slotLogits[index].indices.maxByOrNull(slotLogits[index]::get) ?: return@forEachIndexed
            val label = SLOT_LABELS[labelIndex]
            if (label == "O") {
                if (current != null && text.substring(piece.start, piece.endExclusive).all(::isInternalSlotGap)) {
                    return@forEachIndexed
                }
                finishCurrent()
                return@forEachIndexed
            }
            val prefix = label.substringBefore('-')
            val type = label.substringAfter('-')
            val previous = current
            val whitespaceGap = previous != null && piece.start >= previous.endExclusive &&
                text.substring(previous.endExclusive, piece.start).all(::isInternalSlotGap)
            if (previous != null && prefix == "I" && previous.type == type && whitespaceGap) {
                previous.endExclusive = piece.endExclusive
                previous.tokenCount += 1
                previous.score += slotLogits[index][labelIndex]
            } else {
                finishCurrent()
                current = Segment(type, piece.start, piece.endExclusive, 1, slotLogits[index][labelIndex])
            }
        }
        finishCurrent()
        return segments.groupBy(Segment::type).mapValues { (_, candidates) ->
            val segment = candidates.maxWith(
                compareBy<Segment> { it.tokenCount }.thenBy { it.score / it.tokenCount },
            )
            text.substring(segment.start, segment.endExclusive)
                .trim().trim(',', '.', ':', ';', '!', '?', '«', '»', '"').trim()
        }.filterValues(String::isNotBlank)
    }

    private fun isInternalSlotGap(char: Char): Boolean =
        char.isWhitespace() || char in ".,:/+-"

    private fun loadTokenizer(): ClassicWordPieceTokenizer {
        val vocabulary = applicationContext.assets.open(VOCAB_ASSET).bufferedReader().use { it.readLines() }
        return ClassicWordPieceTokenizer(vocabulary, MAX_LENGTH)
    }

    private fun loadSession(): OrtSession {
        val model = modelLibrary.selectedFile(
            "NLU-модель не установлена в пользовательском каталоге models/nlu",
        )
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads((Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4))
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return try {
            environment.createSession(model.absolutePath, options)
        } finally {
            options.close()
        }
    }

    private fun passesIntentGuard(
        intent: CommandIntent,
        text: String,
        slots: Map<String, String>,
    ): Boolean = !ru.nezabudka.app.domain.isUnsupportedCommandMutation(text) &&
        !ru.nezabudka.app.domain.isNonCommandSpeechStatement(text) && when (intent) {
        CommandIntent.CREATE_REMINDER ->
            !REMINDER_MUTATION_MARKER.containsMatchIn(text) &&
                !NON_COMMAND_QUESTION.containsMatchIn(text) &&
                (!EXPLICIT_DELETION.containsMatchIn(text) || POSITIVE_REMINDER_MARKER.containsMatchIn(text))
        CommandIntent.LIST_REMINDERS ->
            !NOTE_CONTENT_NOUN.containsMatchIn(text) &&
                !REMINDER_MUTATION_MARKER.containsMatchIn(text) && !EXPLICIT_DELETION.containsMatchIn(text)
        CommandIntent.CREATE_FOLDER ->
            CREATE_FOLDER_MARKER.containsMatchIn(text) &&
                (FOLDER_NOUN.containsMatchIn(text) || hasConfirmedSlot(slots, "FOLDER", text))
        CommandIntent.DELETE_REMINDER ->
            EXPLICIT_DELETION.containsMatchIn(text) &&
                (extractStopTrackingReminderTarget(text) != null || LAST_TARGET.containsMatchIn(text) ||
                    ((REMINDER_CONTEXT.containsMatchIn(text) || REMINDER_ONLY_ACTION.containsMatchIn(text) ||
                        (SHORT_DELETE_ACTION.containsMatchIn(text) && !NON_REMINDER_DELETE_OBJECT.containsMatchIn(text))) &&
                        (hasConfirmedSlot(slots, "TARGET", text) || hasMeaningfulArgument(text))))
        CommandIntent.DELETE_FOLDER ->
            EXPLICIT_DELETION.containsMatchIn(text) && FOLDER_NOUN.containsMatchIn(text) &&
                (hasConfirmedSlot(slots, "FOLDER", text) || DELETE_FOLDER_ARGUMENT.containsMatchIn(text) ||
                    hasMeaningfulArgument(text))
        CommandIntent.CREATE_NOTE -> !isUnsupportedMediaCommand(text) && !isCompletedActionStatement(text)
        else -> true
    }

    private fun hasConfirmedSlot(slots: Map<String, String>, name: String, text: String): Boolean {
        val value = slots[name]?.trim()?.trim(',', '.', ':', ';', '!', '?', '«', '»', '"')?.trim()
            ?.takeIf { it.isNotBlank() && !NON_ARGUMENT_SLOT.matches(it) } ?: return false
        return Regex("(?iu)(?<![\\p{L}\\p{N}_])${Regex.escape(value)}(?![\\p{L}\\p{N}_])")
            .containsMatchIn(text)
    }

    private fun hasMeaningfulArgument(text: String): Boolean =
        text.split(Regex("[^\\p{L}\\p{N}]+"), limit = 0).count(String::isNotBlank) >= 3

    private fun allowedIntents(scope: CommandScope): Set<CommandIntent> = when (scope) {
        CommandScope.ASSISTANT -> REMINDER_INTENTS + NOTE_INTENTS
        CommandScope.REMINDERS -> REMINDER_INTENTS
        CommandScope.NOTES -> NOTE_INTENTS
    }

    private fun softmaxProbabilities(values: FloatArray): DoubleArray {
        val maximum = values.maxOrNull()?.toDouble() ?: return DoubleArray(0)
        val denominator = values.sumOf { exp(it.toDouble() - maximum) }
        return DoubleArray(values.size) { index -> exp(values[index].toDouble() - maximum) / denominator }
    }

    suspend fun release() = withContext(Dispatchers.Default) {
        mutex.withLock {
            session?.close()
            session = null
            tokenizer = null
        }
    }

    fun startModelObserver(scope: kotlinx.coroutines.CoroutineScope) {
        if (!modelObserverStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            workManager.getWorkInfosForUniqueWorkFlow(
                ModelDownloadWorker.uniqueWorkName(ModelDownloadWorker.KIND_NLU),
            ).collectLatest(::applyModelWorkState)
        }
    }

    fun refreshModel() {
        _modelState.value = scanModel()
    }

    suspend fun downloadModel(url: String) = withContext(Dispatchers.IO) {
        require(url.isNotBlank()) { "Адрес NLU-модели ещё не настроен" }
        _modelState.value = scanModel().copy(downloading = true, progress = 0f)
        ModelDownloadWorker.enqueue(
            applicationContext,
            ModelDownloadWorker.KIND_NLU,
            "classic-nlu-v7",
            "Расширенное распознавание команд",
            MODEL_FILE,
            url,
        )
    }

    suspend fun importModel(uri: Uri) = withContext(Dispatchers.IO) {
        mutex.withLock {
            session?.close()
            session = null
            tokenizer = null
            modelLibrary.import(uri)
            refreshModel()
        }
    }

    suspend fun selectModel(fileName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (modelLibrary.select(fileName)) {
                session?.close()
                session = null
                tokenizer = null
            }
            refreshModel()
        }
    }

    private fun scanModel(): ClassicNluModelState {
        val snapshot = modelLibrary.snapshot()
        return ClassicNluModelState(installed = snapshot.selectedName != null, installedModels = snapshot.installed, selectedName = snapshot.selectedName)
    }

    private fun applyModelWorkState(values: List<WorkInfo>) {
        val work = values.firstOrNull {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED || it.state == WorkInfo.State.RUNNING
        } ?: values.lastOrNull() ?: return
        _modelState.value = when (work.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> scanModel().copy(downloading = true, progress = 0f)
            WorkInfo.State.RUNNING -> {
                val copied = work.progress.getLong(ModelDownloadWorker.KEY_COPIED, 0L)
                val total = work.progress.getLong(ModelDownloadWorker.KEY_TOTAL, -1L)
                scanModel().copy(
                    downloading = true,
                    progress = if (total > 0L) copied.toFloat() / total else null,
                )
            }
            WorkInfo.State.SUCCEEDED -> scanModel()
            WorkInfo.State.FAILED -> scanModel().copy(
                error = work.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Ошибка загрузки NLU-модели",
            )
            WorkInfo.State.CANCELLED -> scanModel()
        }
    }

    private fun unknown() = AssistantCommand(CommandIntent.UNKNOWN)

    private fun CommandIntent.isDestructive() =
        this == CommandIntent.DELETE_REMINDER || this == CommandIntent.DELETE_FOLDER

    private fun String.cleanTarget(): String =
        replace(Regex("(?iu)^\\s*(?:о|об|про|с\\s+текстом|на\\s+тему)\\s+"), "").trim()

    private companion object {
        const val MAX_LENGTH = 64
        const val MODEL_FILE = "classic_nlu_v7_int8.onnx"
        const val VOCAB_ASSET = "nlu/vocab.txt"
        const val INTENT_THRESHOLD = 0.72
        const val EXPLICIT_DESTRUCTIVE_THRESHOLD = 0.50
        const val DESTRUCTIVE_THRESHOLD = 0.90
        val INTENT_LABELS = listOf(
            "create_reminder", "list_reminders", "delete_reminder", "create_note",
            "create_folder", "delete_folder", "unknown",
        )
        val SLOT_LABELS = listOf(
            "O", "B-TEXT", "I-TEXT", "B-TARGET", "I-TARGET", "B-FOLDER", "I-FOLDER",
            "B-DATE", "I-DATE", "B-TIME", "I-TIME", "B-RECURRENCE", "I-RECURRENCE",
        )
        val REMINDER_INTENTS = setOf(
            CommandIntent.CREATE_REMINDER, CommandIntent.LIST_REMINDERS, CommandIntent.DELETE_REMINDER,
        )
        val NOTE_INTENTS = setOf(
            CommandIntent.CREATE_NOTE, CommandIntent.CREATE_FOLDER, CommandIntent.DELETE_FOLDER,
        )
        val INTENT_THRESHOLDS = mapOf(CommandIntent.CREATE_REMINDER to 0.65)
        val EXPLICIT_DELETION = Regex(
            "(?iu)(?:удал|убер|убир|убра|отмен|аннул|исключ|вычерк|сотр|ликвид|расформ|упразд|прекрат|" +
                "отзов|деактив|останов|не\\s+отслеж|не\\s+использ|больше\\s+не\\s+использ|" +
                "(?<![\\p{L}\\p{N}_])не\\s+нуж|избав|закрой|сним|откаж|не\\s+напоминай|" +
                "не\\s+актуал|не\\s+действ|утрат[\\p{L}]*\\s+актуальност)",
        )
        val FOLDER_NOUN = Regex(
            "(?iu)(?:папк|раздел|каталог|категор|коллекц|секци|хранилищ|подборк|рубр|групп|блок|полк)[\\p{L}]*",
        )
        val REMINDER_CONTEXT = Regex(
            "(?iu)(?:(?:напомин|задач|план|распис)[\\p{L}]*|" +
                "(?:будущ|запланирован|календар|контрол|отслежив)[\\p{L}]*\\s+" +
                "(?:пункт|действ|дел|обязательств|очеред)[\\p{L}]*|" +
                "(?:пункт|действ|дел|обязательств|очеред)[\\p{L}]*\\s+" +
                "(?:будущ|запланирован|календар|контрол|отслежив)[\\p{L}]*)",
        )
        val REMINDER_ONLY_ACTION = Regex(
            "(?iu)(?:отмен[\\p{L}]*|не\\s+напоминай|больше\\s+не\\s+напоминай)",
        )
        val SHORT_DELETE_ACTION = Regex("(?iu)^\\s*(?:удал[иь][\\p{L}]*|убер[иь][\\p{L}]*)\\s+")
        val NON_REMINDER_DELETE_OBJECT = Regex(
            "(?iu)(?:документ|файл|экран|окно|страниц|журнал|истори|загрузк|форм[ауеы]|запрос|" +
                "сообщени|заметк|папк|каталог|рубр|групп|блок|контакт|фото|видео)[\\p{L}]*",
        )
        val DELETE_FOLDER_ARGUMENT = Regex(
            "(?iu)(?:^(?:удал|убер|закрой|ликвид|расформ|исключ|сотр|упразд)[\\p{L}]*\\s+" +
                "(?:папк|раздел|каталог|категор|коллекц|секци|хранилищ|подборк|рубр|групп|блок)[\\p{L}]*\\s+.+|" +
                "^(?:папк|раздел|каталог|категор|коллекц|секци|хранилищ|подборк|рубр|групп|блок)[\\p{L}]*\\s+.+\\s+" +
                "(?:не\\s+нуж|можно\\s+(?:удал|убр)|пора\\s+удал|утрат[\\p{L}]*\\s+актуальност))",
        )
        val CREATE_FOLDER_MARKER = Regex(
            "(?iu)(?:созд|добав|сдел|организ|завед|сформ|подготов|собер|выдел|учред|откр|размест|нужна\\s+папк|" +
                "хочу\\s+(?:новую\\s+)?папк|пусть\\s+будет\\s+папк|нов(?:ая|ый|ую)\\s+(?:папк|раздел))",
        )
        val REMINDER_MUTATION_MARKER = Regex("(?iu)^\\s*(?:измен|перенес|передвин|отлож)")
        val NON_COMMAND_QUESTION = Regex(
            "(?iu)^\\s*(?:стоит\\s+ли|можно\\s+ли|как|почему|зачем|кто|я\\s+уже|не\\s+хочу|объясни)",
        )
        val POSITIVE_REMINDER_MARKER = Regex(
            "(?iu)(?:не\\s+забыть|не\\s+дай(?:\\s+мне)?\\s+забыть|не\\s+забыл[а-яё]*|" +
                "(?<![\\p{L}\\p{N}_])напомни(?![\\p{L}\\p{N}_])|" +
                "(?:создай|добавь|поставь|оставь)\\s+(?:мне\\s+)?напоминани[\\p{L}]*)",
        )
        val NOTE_CONTENT_NOUN = Regex("(?iu)(?:заметк|папк|каталог|рубр|групп)[\\p{L}]*")
        val LAST_TARGET = Regex("(?iu)(?<![\\p{L}\\p{N}_])последн[\\p{L}\\p{N}_]*")
        val NON_ARGUMENT_SLOT = Regex("(?iu)(?:это|вс[её]|его|е[её]|их|текущ(?:ее|ий|ую)|данн(?:ое|ый|ую))")
    }
}

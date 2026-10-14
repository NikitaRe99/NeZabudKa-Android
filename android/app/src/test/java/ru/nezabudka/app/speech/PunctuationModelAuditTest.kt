package ru.nezabudka.app.speech

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.nezabudka.app.llm.ClassicWordPieceTokenizer

/** Optional real ONNX audit: production tokenizer and renderer, no model training. */
class PunctuationModelAuditTest {
    private val root = File("../../.gigaam-cache")
    private val cases = listOf(
        "если завтра будет дождь я останусь дома когда погода улучшится мы пойдём гулять",
        "ты уже отправил документы когда ждать ответ",
        "список покупок молоко хлеб и яблоки",
        "ура мы закончили работу",
        "главное не забыть ключи кошелёк и телефон",
        "я думаю что встречу лучше перенести",
        "температура 36.6 давление 120 на 80",
        "письмо отправьте на team@example.ru сайт https://example.org/a",
        "он сказал «вернусь завтра» потом ушёл",
        "Здравствуйте! Документы готовы? Да, всё готово.",
        "напомни завтра в десять проверить документы в синей папке",
        "привет привет очень очень важно",
        "когда начнётся встреча",
        "почему дверь осталась открытой",
        "как пройти к ближайшей станции",
        "сколько времени осталось до отправления",
        "пожалуйста закрой окно",
        "победа мы справились",
    )
    @Test fun prepareTokens() {
        assumeTrue(System.getenv("PUNCT_AUDIT") == "prepare")
        val tokenizer = ClassicWordPieceTokenizer(File("src/main/assets/punctuation/vocab.txt").readLines(), 128)
        val rows = cases.map { text ->
            val words = punctuationWords(text)
            val input = words.joinToString(" ") { it.value.lowercase() }
            val tokens = tokenizer.encode(input)
            var offset = 0
            val positions = words.map { word ->
                val position = tokens.pieces.indexOfFirst { it?.start == offset }
                offset += word.value.lowercase().length + 1
                position
            }
            buildJsonObject {
                put("text", text)
                put("positions", JsonArray(positions.map(::JsonPrimitive)))
                put("input_ids", JsonArray(tokens.inputIds.map(::JsonPrimitive)))
                put("attention_mask", JsonArray(tokens.attentionMask.map(::JsonPrimitive)))
                put("token_type_ids", JsonArray(tokens.tokenTypeIds.map(::JsonPrimitive)))
            }
        }
        File(root, "punct-tokens.json").writeText(JsonArray(rows).toString())
    }
    @Test fun evaluateRealPredictions() {
        assumeTrue(System.getenv("PUNCT_AUDIT") == "evaluate")
        val source = Json.parseToJsonElement(File(root, "punct-logits.json").readText()).jsonArray
        val labels = Json.parseToJsonElement(File("src/main/assets/punctuation/config.json").readText()).jsonObject.getValue("id2label").jsonObject
        val labelList = List(labels.size) { labels.getValue(it.toString()).jsonPrimitive.content }
        val report = source.map { item ->
            val row = item.jsonObject
            val text = row.getValue("text").jsonPrimitive.content
            val words = punctuationWords(text)
            val positions = row.getValue("positions").jsonArray.map { it.jsonPrimitive.int }
            val logits = row.getValue("logits").jsonArray
            val predictions = positions.map { index ->
                if (index < 0) null else fullPunctuationPrediction(logits[index].jsonArray.map { it.jsonPrimitive.float }.toFloatArray(), labelList)
            }
            val output = applyFullPunctuation(text, words, predictions)
            assertEquals("No words may be changed or lost", words.map { it.value.lowercase() }, punctuationWords(output).map { it.value.lowercase() })
            buildJsonObject { put("input", text); put("output", output) }
        }
        File("../../docs/gigaam/punctuation-audit-0.2.82.json").writeText(Json { prettyPrint = true }.encodeToString(JsonArray(report)))
    }
}

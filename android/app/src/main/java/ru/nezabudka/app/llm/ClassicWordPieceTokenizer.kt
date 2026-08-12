package ru.nezabudka.app.llm

internal data class ClassicNluToken(
    val id: Long,
    val value: String,
    val start: Int,
    val endExclusive: Int,
)

internal data class ClassicNluTokens(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
    val pieces: List<ClassicNluToken?>,
)

/** Minimal BERT-compatible tokenizer retaining offsets required by BIO slot extraction. */
internal class ClassicWordPieceTokenizer(
    vocabulary: List<String>,
    private val maxLength: Int,
) {
    private val tokenIds = vocabulary.withIndex().associate { it.value to it.index.toLong() }
    private val unknownId = tokenIds.getValue("[UNK]")
    private val classificationId = tokenIds.getValue("[CLS]")
    private val separatorId = tokenIds.getValue("[SEP]")
    private val paddingId = tokenIds.getValue("[PAD]")

    fun encode(text: String): ClassicNluTokens {
        val content = buildList {
            basicTokens(text).forEach { token -> addAll(wordPieces(token, text)) }
        }.take(maxLength - 2)
        val ids = LongArray(maxLength) { paddingId }
        val mask = LongArray(maxLength)
        val types = LongArray(maxLength)
        val pieces = MutableList<ClassicNluToken?>(maxLength) { null }

        ids[0] = classificationId
        mask[0] = 1
        content.forEachIndexed { index, piece ->
            val outputIndex = index + 1
            ids[outputIndex] = piece.id
            mask[outputIndex] = 1
            pieces[outputIndex] = piece
        }
        val separatorIndex = content.size + 1
        ids[separatorIndex] = separatorId
        mask[separatorIndex] = 1
        return ClassicNluTokens(ids, mask, types, pieces)
    }

    private data class BasicToken(val value: String, val start: Int, val endExclusive: Int)

    private fun basicTokens(text: String): List<BasicToken> = buildList {
        var tokenStart = -1
        fun finish(end: Int) {
            if (tokenStart >= 0) {
                add(BasicToken(text.substring(tokenStart, end), tokenStart, end))
                tokenStart = -1
            }
        }
        text.forEachIndexed { index, character ->
            when {
                character.isWhitespace() || character.isISOControl() -> finish(index)
                isPunctuation(character) -> {
                    finish(index)
                    add(BasicToken(character.toString(), index, index + 1))
                }
                tokenStart < 0 -> tokenStart = index
            }
        }
        finish(text.length)
    }

    private fun wordPieces(token: BasicToken, original: String): List<ClassicNluToken> {
        tokenIds[token.value]?.let { id ->
            return listOf(ClassicNluToken(id, token.value, token.start, token.endExclusive))
        }
        val result = mutableListOf<ClassicNluToken>()
        var start = 0
        while (start < token.value.length) {
            var end = token.value.length
            var matchId: Long? = null
            var matchEnd = start
            while (end > start) {
                val value = token.value.substring(start, end).let { if (start == 0) it else "##$it" }
                tokenIds[value]?.let { id ->
                    matchId = id
                    matchEnd = end
                }
                if (matchId != null) break
                end--
            }
            if (matchId == null) {
                return listOf(ClassicNluToken(unknownId, "[UNK]", token.start, token.endExclusive))
            }
            val pieceStart = token.start + start
            val pieceEnd = token.start + matchEnd
            if (original.regionMatches(pieceStart, token.value, start, matchEnd - start)) {
                result += ClassicNluToken(matchId!!, token.value.substring(start, matchEnd).let { if (start == 0) it else "##$it" }, pieceStart, pieceEnd)
            } else {
                result += ClassicNluToken(matchId!!, token.value.substring(start, matchEnd).let { if (start == 0) it else "##$it" }, token.start, token.endExclusive)
            }
            start = matchEnd
        }
        return result
    }

    private fun isPunctuation(value: Char): Boolean {
        if (value in '!'..'/' || value in ':'..'@' || value in '['..'`' || value in '{'..'~') return true
        return when (Character.getType(value)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt() -> true
            else -> false
        }
    }
}

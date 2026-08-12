package ru.nezabudka.app.llm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassicWordPieceTokenizerTest {
    private val vocabulary = listOf(
        "[PAD]", "[UNK]", "[CLS]", "[SEP]", "напомни", "позвон", "##ить", ",",
    )

    @Test
    fun `encodes word pieces and preserves source offsets`() {
        val encoded = ClassicWordPieceTokenizer(vocabulary, maxLength = 8)
            .encode("напомни позвонить")

        assertArrayEquals(longArrayOf(2, 4, 5, 6, 3, 0, 0, 0), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 1, 0, 0, 0), encoded.attentionMask)
        assertEquals(listOf("напомни", "позвон", "##ить"), encoded.pieces.mapNotNull { it?.value })
        assertEquals("напомни", "напомни позвонить".substring(encoded.pieces[1]!!.start, encoded.pieces[1]!!.endExclusive))
        assertEquals("позвон", "напомни позвонить".substring(encoded.pieces[2]!!.start, encoded.pieces[2]!!.endExclusive))
        assertEquals("ить", "напомни позвонить".substring(encoded.pieces[3]!!.start, encoded.pieces[3]!!.endExclusive))
    }

    @Test
    fun `keeps punctuation separate and unknown word as one source range`() {
        val encoded = ClassicWordPieceTokenizer(vocabulary, maxLength = 8)
            .encode("напомни, врачу")

        assertArrayEquals(longArrayOf(2, 4, 7, 1, 3, 0, 0, 0), encoded.inputIds)
        assertEquals(listOf("напомни", ",", "[UNK]"), encoded.pieces.mapNotNull { it?.value })
        assertEquals(",", "напомни, врачу".substring(encoded.pieces[2]!!.start, encoded.pieces[2]!!.endExclusive))
        assertEquals("врачу", "напомни, врачу".substring(encoded.pieces[3]!!.start, encoded.pieces[3]!!.endExclusive))
    }
}

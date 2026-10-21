package com.offlinekeyboard.mt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compares the Kotlin tokenizer with the official SentencePiece library. The fixtures were produced by
 * python `sentencepiece` 0.2.x from a unigram model trained on tools/mt/phrases (see docs/TRANSLATION.md).
 */
class SentencePieceTest {

    private val model = SentencePieceModel.parse(javaClass.getResourceAsStream("/spm/toy.model")!!.readBytes())

    @Test
    fun matchesOfficialTokenizer() {
        val lines = javaClass.getResourceAsStream("/spm/toy_cases.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
        assertTrue(lines.size > 200)
        val mismatches = lines.mapNotNull { line ->
            val (text, pieces, decoded) = line.split('\t')
            val expected = if (pieces.isEmpty()) emptyList() else pieces.split(' ')
            val actual = model.encode(text)
            when {
                actual != expected -> "encode '$text': expected $expected, got $actual"
                model.decode(actual) != decoded -> "decode '$text': expected '$decoded', got '${model.decode(actual)}'"
                else -> null
            }
        }
        assertEquals(mismatches.joinToString("\n"), 0, mismatches.size)
    }

    @Test
    fun segmenterKeepsWhitespace() {
        val text = "Привіт! Як справи?\n\nДобре.  Дякую"
        val segments = TextSegmenter.split(text)
        assertEquals(text, segments.joinToString("") { it.text })
        assertEquals(listOf("Привіт!", "Як справи?", "Добре.", "Дякую"), segments.filter { it.isTranslatable }.map { it.text })
        assertEquals(listOf("12:30"), TextSegmenter.split("12:30").map { it.text })
        assertTrue(TextSegmenter.split("12:30").none { it.isTranslatable })
    }
}

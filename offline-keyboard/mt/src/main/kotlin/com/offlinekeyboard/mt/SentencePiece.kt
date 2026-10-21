package com.offlinekeyboard.mt

import java.io.File
import java.text.Normalizer

/**
 * Reader for SentencePiece `.spm` model files and a unigram (Viterbi) tokenizer equivalent to
 * `SentencePieceProcessor.encode(text, out_type=str)`. OPUS-MT (Marian) models ship such files.
 * Only the parts of the protobuf the tokenizer needs are parsed.
 */
class SentencePieceModel private constructor(
    val pieces: List<Piece>,
    val modelType: Int,
    private val addDummyPrefix: Boolean,
    private val removeExtraWhitespaces: Boolean,
    private val escapeWhitespaces: Boolean,
    private val nfkc: Boolean,
) {
    data class Piece(val text: String, val score: Float, val type: Int)

    private val index: Map<String, Int> = HashMap<String, Int>().also { map ->
        pieces.forEachIndexed { i, p -> if (p.type == TYPE_NORMAL || p.type == TYPE_USER_DEFINED) map.putIfAbsent(p.text, i) }
    }
    private val maxPieceLength = pieces.maxOfOrNull { it.text.length } ?: 1
    private val unkScore = (pieces.filter { it.type == TYPE_NORMAL }.minOfOrNull { it.score } ?: 0f) - UNK_PENALTY

    val unkPiece: String = pieces.firstOrNull { it.type == TYPE_UNKNOWN }?.text ?: "<unk>"

    fun normalize(text: String): String {
        var t = if (nfkc) Normalizer.normalize(text, Normalizer.Form.NFKC) else text
        if (removeExtraWhitespaces) t = t.trim().replace(Regex("[ \\t\\r\\n\\u3000]+"), " ")
        if (t.isEmpty()) return t
        if (escapeWhitespaces) t = t.replace(' ', SPACE)
        if (addDummyPrefix) t = SPACE + t
        return t
    }

    /** Text → pieces, like `SentencePieceProcessor.encode(text, out_type=str)`. */
    fun encode(text: String): List<String> {
        check(modelType == MODEL_UNIGRAM) { "Only unigram SentencePiece models are supported (got type $modelType)" }
        val s = normalize(text)
        val n = s.length
        if (n == 0) return emptyList()
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val from = IntArray(n + 1) { -1 }
        val isUnk = BooleanArray(n + 1)
        best[0] = 0f
        var start = 0
        while (start < n) {
            if (best[start] == Float.NEGATIVE_INFINITY) { start = nextBoundary(s, start); continue }
            var end = nextBoundary(s, start)
            while (end <= n && end - start <= maxPieceLength) {
                val id = index[s.substring(start, end)]
                if (id != null) {
                    val p = pieces[id]
                    // User-defined symbols always win over pieces they overlap, as in SentencePiece.
                    val score = if (p.type == TYPE_USER_DEFINED) 0f else p.score
                    if (best[start] + score > best[end]) {
                        best[end] = best[start] + score
                        from[end] = start
                        isUnk[end] = false
                    }
                }
                if (end == n) break
                end = nextBoundary(s, end)
            }
            // Like SentencePiece: a character with no single-character piece gets an <unk> node.
            val charEnd = nextBoundary(s, start)
            if (index[s.substring(start, charEnd)] == null && best[start] + unkScore > best[charEnd]) {
                best[charEnd] = best[start] + unkScore
                from[charEnd] = start
                isUnk[charEnd] = true
            }
            start = nextBoundary(s, start)
        }
        val result = ArrayList<String>()
        var pos = n
        val reversed = ArrayList<Pair<String, Boolean>>()
        while (pos > 0) {
            val prev = from[pos]
            reversed += s.substring(prev, pos) to isUnk[pos]
            pos = prev
        }
        // Unknown characters keep their surface text (a run of them becomes one piece), like SentencePiece;
        // the vocabulary lookup later maps them to <unk>.
        var previousUnk = false
        for ((piece, unk) in reversed.asReversed()) {
            if (unk && previousUnk) result[result.lastIndex] = result.last() + piece else result += piece
            previousUnk = unk
        }
        return result
    }

    /** Pieces → text, the inverse of [encode] (unknowns stay as ⁇ like SentencePiece). */
    fun decode(pieces: List<String>): String {
        val sb = StringBuilder()
        for (p in pieces) sb.append(if (p == unkPiece) " ⁇ " else p)
        var text = sb.toString().replace(SPACE, ' ')
        if (addDummyPrefix && text.startsWith(' ')) text = text.substring(1)
        return text
    }

    private fun nextBoundary(s: String, i: Int): Int =
        if (i < s.length && Character.isHighSurrogate(s[i]) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])) i + 2 else i + 1

    companion object {
        const val SPACE = '▁'
        const val TYPE_NORMAL = 1
        const val TYPE_UNKNOWN = 2
        const val TYPE_CONTROL = 3
        const val TYPE_USER_DEFINED = 4
        const val MODEL_UNIGRAM = 1
        private const val UNK_PENALTY = 10f

        fun load(file: File): SentencePieceModel = parse(file.readBytes())

        fun parse(bytes: ByteArray): SentencePieceModel {
            val pieces = ArrayList<Piece>()
            var modelType = MODEL_UNIGRAM
            var addDummyPrefix = true
            var removeExtra = true
            var escape = true
            var normalizerName = "nmt_nfkc"
            val r = ProtoReader(bytes)
            while (r.hasMore()) {
                val (field, wire) = r.tag()
                when {
                    field == 1 && wire == 2 -> pieces += parsePiece(r.bytes())
                    field == 2 && wire == 2 -> {
                        val t = ProtoReader(r.bytes())
                        while (t.hasMore()) {
                            val (f, w) = t.tag()
                            if (f == 3 && w == 0) modelType = t.varint().toInt() else t.skip(w)
                        }
                    }
                    field == 3 && wire == 2 -> {
                        val t = ProtoReader(r.bytes())
                        while (t.hasMore()) {
                            val (f, w) = t.tag()
                            when {
                                f == 1 && w == 2 -> normalizerName = String(t.bytes(), Charsets.UTF_8)
                                f == 3 && w == 0 -> addDummyPrefix = t.varint() != 0L
                                f == 4 && w == 0 -> removeExtra = t.varint() != 0L
                                f == 5 && w == 0 -> escape = t.varint() != 0L
                                else -> t.skip(w)
                            }
                        }
                    }
                    else -> r.skip(wire)
                }
            }
            return SentencePieceModel(pieces, modelType, addDummyPrefix, removeExtra, escape, normalizerName.contains("nfkc"))
        }

        private fun parsePiece(bytes: ByteArray): Piece {
            val r = ProtoReader(bytes)
            var text = ""
            var score = 0f
            var type = TYPE_NORMAL
            while (r.hasMore()) {
                val (f, w) = r.tag()
                when {
                    f == 1 && w == 2 -> text = String(r.bytes(), Charsets.UTF_8)
                    f == 2 && w == 5 -> score = Float.fromBits(r.fixed32())
                    f == 3 && w == 0 -> type = r.varint().toInt()
                    else -> r.skip(w)
                }
            }
            return Piece(text, score, type)
        }
    }
}

/** Minimal protobuf wire-format reader. */
internal class ProtoReader(private val buf: ByteArray) {
    private var pos = 0

    fun hasMore() = pos < buf.size

    fun tag(): Pair<Int, Int> {
        val v = varint()
        return (v ushr 3).toInt() to (v and 7).toInt()
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = buf[pos++].toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun fixed32(): Int {
        val v = (buf[pos].toInt() and 0xff) or ((buf[pos + 1].toInt() and 0xff) shl 8) or
            ((buf[pos + 2].toInt() and 0xff) shl 16) or ((buf[pos + 3].toInt() and 0xff) shl 24)
        pos += 4
        return v
    }

    fun bytes(): ByteArray {
        val len = varint().toInt()
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun skip(wire: Int) {
        when (wire) {
            0 -> varint()
            1 -> pos += 8
            2 -> {
                val len = varint().toInt() // read before updating pos: `pos += varint()` would use the old pos
                pos += len
            }
            5 -> pos += 4
            else -> error("Unsupported protobuf wire type $wire")
        }
    }
}

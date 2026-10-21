package com.offlinekeyboard.mt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.offlinekeyboard.core.speech.EngineAvailability
import com.offlinekeyboard.core.translation.Glossary
import com.offlinekeyboard.core.translation.Phrasebook
import com.offlinekeyboard.core.translation.TranslationCheck
import com.offlinekeyboard.core.translation.TranslationDirection
import com.offlinekeyboard.core.translation.TranslationEngine
import com.offlinekeyboard.core.translation.TranslationResult
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Properties

/**
 * An unpacked OPUS-MT (Marian) model package produced by `tools/mt/export_opus_mt.py`:
 *
 * ```
 * model.properties        direction, special token ids, max length
 * source.spm, target.spm  SentencePiece models
 * vocab.txt               one token per line, line number = id
 * encoder_model.onnx, decoder_model.onnx, decoder_with_past_model.onnx (int8)
 * ```
 */
class MarianPackage(val dir: File) {
    val properties = Properties().apply { File(dir, "model.properties").inputStream().use { load(it) } }
    val direction: TranslationDirection = TranslationDirection.valueOf(properties.getProperty("direction"))
    val modelName: String = properties.getProperty("model", dir.name)
    val license: String = properties.getProperty("license", "")

    fun int(key: String): Int = properties.getProperty(key)?.trim()?.toInt() ?: error("model.properties: missing $key")

    companion object {
        val requiredFiles = listOf(
            "model.properties", "source.spm", "target.spm", "vocab.txt", "encoder_model.onnx", "decoder_model.onnx",
        )

        fun isValid(dir: File): Boolean = requiredFiles.all { File(dir, it).isFile }
    }
}

/** Token ids ↔ text for one direction, matching Hugging Face `MarianTokenizer`. */
class MarianTokenizer(pkg: MarianPackage) {
    private val source = SentencePieceModel.load(File(pkg.dir, "source.spm"))
    private val target = SentencePieceModel.load(File(pkg.dir, "target.spm"))
    private val vocab: List<String> = File(pkg.dir, "vocab.txt").readLines(Charsets.UTF_8)
    private val ids: Map<String, Int> = HashMap<String, Int>(vocab.size * 2).also { m ->
        vocab.forEachIndexed { i, t -> m.putIfAbsent(t, i) }
    }
    val eosId = pkg.int("eos_token_id")
    val padId = pkg.int("pad_token_id")
    val unkId = pkg.int("unk_token_id")
    private val sourcePrefix: String = pkg.properties.getProperty("source_prefix", "").trim()

    fun encode(text: String): LongArray {
        val pieces = buildList {
            if (sourcePrefix.isNotEmpty()) add(sourcePrefix)
            addAll(source.encode(text))
        }
        val out = LongArray(pieces.size + 1)
        pieces.forEachIndexed { i, p -> out[i] = (ids[p] ?: unkId).toLong() }
        out[pieces.size] = eosId.toLong()
        return out
    }

    fun decode(tokenIds: LongArray): String {
        val pieces = tokenIds.map { it.toInt() }
            .filter { it != eosId && it != padId }
            .map { vocab.getOrElse(it) { target.unkPiece } }
            .filterNot { it.startsWith(">>") && it.endsWith("<<") }
        return target.decode(pieces).trim()
    }
}

/**
 * Greedy decoding on ONNX Runtime. Uses the decoder-with-past model (key/value cache) when present, so each
 * step only processes the newest token.
 */
class MarianOnnxModel(pkg: MarianPackage, threads: Int = 2) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val encoder = env.createSession(File(pkg.dir, "encoder_model.onnx").path, options)
    private val decoder = env.createSession(File(pkg.dir, "decoder_model.onnx").path, options)
    private val decoderWithPast = File(pkg.dir, "decoder_with_past_model.onnx").takeIf { it.isFile }
        ?.let { env.createSession(it.path, options) }

    private val decoderStartId = pkg.int("decoder_start_token_id").toLong()
    private val eosId = pkg.int("eos_token_id")
    private val padId = pkg.int("pad_token_id")
    val maxLength = pkg.properties.getProperty("max_length", "512").trim().toInt()

    fun generate(inputIds: LongArray, maxNewTokens: Int): LongArray {
        val n = inputIds.size.toLong()
        val toClose = ArrayList<AutoCloseable>()
        try {
            val idsTensor = tensor(inputIds, longArrayOf(1, n)).also(toClose::add)
            val maskTensor = tensor(LongArray(inputIds.size) { 1L }, longArrayOf(1, n)).also(toClose::add)
            val encoderResult = encoder.run(mapOf("input_ids" to idsTensor, "attention_mask" to maskTensor)).also(toClose::add)
            val hidden = encoderResult.get("last_hidden_state").orElseGet { encoderResult.get(0) } as OnnxTensor

            val output = ArrayList<Long>()
            output += decoderStartId
            // Latest key/value cache by suffix ("0.decoder.key" ...). The first decoder result also holds the
            // encoder cache, so it stays open; later results are closed once the next step replaces them.
            val presents = HashMap<String, OnnxValue>()
            var firstResult: OrtSession.Result? = null
            var previousResult: OrtSession.Result? = null
            for (step in 0 until maxNewTokens) {
                val useCache = decoderWithPast != null && step > 0
                val session = if (useCache) decoderWithPast!! else decoder
                val decoderIds = if (useCache) longArrayOf(output.last()) else output.toLongArray()
                val result = tensor(decoderIds, longArrayOf(1, decoderIds.size.toLong())).use { decIdsTensor ->
                    val inputs = HashMap<String, OnnxTensor>()
                    for (name in session.inputNames) {
                        when {
                            name == "input_ids" -> inputs[name] = decIdsTensor
                            name == "encoder_attention_mask" || name == "attention_mask" -> inputs[name] = maskTensor
                            name == "encoder_hidden_states" -> inputs[name] = hidden
                            name.startsWith("past_key_values.") ->
                                inputs[name] = presents[name.removePrefix("past_key_values.")] as? OnnxTensor
                                    ?: error("Missing cache tensor for $name")
                            else -> error("Unexpected decoder input $name")
                        }
                    }
                    session.run(inputs)
                }
                for ((name, value) in result) {
                    if (name.startsWith("present.")) presents[name.removePrefix("present.")] = value
                }
                if (firstResult == null) {
                    firstResult = result
                    toClose += result
                } else {
                    previousResult?.takeIf { it !== firstResult }?.close()
                }
                previousResult = result
                val next = argmaxLast(result.get("logits").get() as OnnxTensor)
                if (next == eosId) break
                output += next.toLong()
            }
            previousResult?.takeIf { it !== firstResult }?.close()
            return output.drop(1).toLongArray()
        } finally {
            toClose.asReversed().forEach { runCatching { it.close() } }
        }
    }

    /**
     * Beam search like Hugging Face `generate(num_beams=B)` (length_penalty 1.0): keeps the B best partial
     * translations, so one unlucky word choice does not derail the sentence. Beams run as one batch; the
     * decoder's key/value cache is reordered after each step. Needs decoder_with_past_model.onnx.
     */
    fun generateBeam(inputIds: LongArray, maxNewTokens: Int, beams: Int, lengthPenalty: Double = 1.0): LongArray {
        val withPast = decoderWithPast
        if (beams <= 1 || withPast == null) return generate(inputIds, maxNewTokens)
        val n = inputIds.size
        val toClose = ArrayList<AutoCloseable>()
        var owned: List<OnnxTensor> = emptyList() // reordered decoder cache of the current step
        try {
            val idsTensor = tensor(inputIds, longArrayOf(1, n.toLong())).also(toClose::add)
            val mask1 = tensor(LongArray(n) { 1L }, longArrayOf(1, n.toLong())).also(toClose::add)
            val encoderResult = encoder.run(mapOf("input_ids" to idsTensor, "attention_mask" to mask1)).also(toClose::add)
            val hidden1 = encoderResult.get("last_hidden_state").orElseGet { encoderResult.get(0) } as OnnxTensor
            val hShape = hidden1.info.shape
            val one = FloatArray((hShape[1] * hShape[2]).toInt()).also { hidden1.floatBuffer.get(it) }
            val hiddenB = FloatArray(one.size * beams).also { arr -> repeat(beams) { b -> one.copyInto(arr, b * one.size) } }
            val hiddenTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(hiddenB), longArrayOf(beams.toLong(), hShape[1], hShape[2]))
                .also(toClose::add)
            val maskB = tensor(LongArray(beams * n) { 1L }, longArrayOf(beams.toLong(), n.toLong())).also(toClose::add)

            var seqs = List(beams) { LongArray(0) }
            var scores = DoubleArray(beams) { if (it == 0) 0.0 else Double.NEGATIVE_INFINITY }
            val finished = ArrayList<Pair<LongArray, Double>>()
            val presents = HashMap<String, OnnxTensor>()
            var firstResult: OrtSession.Result? = null

            for (step in 0 until maxNewTokens) {
                val session = if (step == 0) decoder else withPast
                val lastTokens = LongArray(beams) { b -> if (step == 0) decoderStartId else seqs[b].last() }
                val result = tensor(lastTokens, longArrayOf(beams.toLong(), 1)).use { ids ->
                    val inputs = HashMap<String, OnnxTensor>()
                    for (name in session.inputNames) {
                        when {
                            name == "input_ids" -> inputs[name] = ids
                            name == "encoder_attention_mask" || name == "attention_mask" -> inputs[name] = maskB
                            name == "encoder_hidden_states" -> inputs[name] = hiddenTensor
                            name.startsWith("past_key_values.") ->
                                inputs[name] = presents[name.removePrefix("past_key_values.")] ?: error("Missing cache tensor for $name")
                            else -> error("Unexpected decoder input $name")
                        }
                    }
                    session.run(inputs)
                }
                if (firstResult == null) {
                    firstResult = result
                    toClose += result
                }

                // Candidate (beam, token, score) — keep the 2B best over all beams.
                val logits = result.get("logits").get() as OnnxTensor
                val vocab = logits.info.shape[2].toInt()
                val buf = logits.floatBuffer
                val candBeam = IntArray(2 * beams)
                val candToken = IntArray(2 * beams)
                val candScore = DoubleArray(2 * beams) { Double.NEGATIVE_INFINITY }
                for (b in 0 until beams) {
                    if (scores[b] == Double.NEGATIVE_INFINITY) continue
                    val off = b * vocab
                    var max = Float.NEGATIVE_INFINITY
                    for (v in 0 until vocab) if (v != padId) max = maxOf(max, buf.get(off + v))
                    var sum = 0.0
                    for (v in 0 until vocab) if (v != padId) sum += Math.exp((buf.get(off + v) - max).toDouble())
                    val logZ = max + Math.log(sum)
                    for (v in 0 until vocab) {
                        if (v == padId) continue
                        val sc = scores[b] + (buf.get(off + v) - logZ)
                        if (sc <= candScore[candScore.size - 1]) continue
                        var i = candScore.size - 1
                        while (i > 0 && candScore[i - 1] < sc) {
                            candScore[i] = candScore[i - 1]; candBeam[i] = candBeam[i - 1]; candToken[i] = candToken[i - 1]
                            i--
                        }
                        candScore[i] = sc; candBeam[i] = b; candToken[i] = v
                    }
                }

                val newSeqs = ArrayList<LongArray>(beams)
                val newScores = ArrayList<Double>(beams)
                val sourceBeam = ArrayList<Int>(beams)
                for (rank in candScore.indices) {
                    if (candScore[rank] == Double.NEGATIVE_INFINITY) break
                    val b = candBeam[rank]
                    val tok = candToken[rank]
                    if (tok == eosId) {
                        // Like Hugging Face: an end-of-sentence only counts if it is among the B best.
                        if (rank < beams) finished += seqs[b] to candScore[rank] / Math.pow(seqs[b].size + 1.0, lengthPenalty)
                    } else {
                        newSeqs += seqs[b] + tok.toLong()
                        newScores += candScore[rank]
                        sourceBeam += b
                    }
                    if (newSeqs.size == beams) break
                }

                // Reorder the decoder cache to follow the surviving beams (encoder cache is the same for all).
                val reordered = ArrayList<OnnxTensor>()
                for ((name, value) in result) {
                    if (!name.startsWith("present.")) continue
                    val key = name.removePrefix("present.")
                    if (".encoder." in key && step > 0) continue
                    val t = value as OnnxTensor
                    if (".encoder." in key) {
                        presents[key] = t // from the first result, kept open
                        continue
                    }
                    val shape = t.info.shape
                    val block = (shape[1] * shape[2] * shape[3]).toInt()
                    val src = FloatArray(block * beams).also { t.floatBuffer.get(it) }
                    val dst = FloatArray(block * beams)
                    for (i in 0 until beams) {
                        val from = sourceBeam.getOrElse(i) { sourceBeam.firstOrNull() ?: 0 }
                        src.copyInto(dst, i * block, from * block, from * block + block)
                    }
                    val nt = OnnxTensor.createTensor(env, FloatBuffer.wrap(dst), shape)
                    reordered += nt
                    presents[key] = nt
                }
                owned.forEach { it.close() }
                owned = reordered
                if (result !== firstResult) result.close()

                if (newSeqs.isEmpty()) break
                while (newSeqs.size < beams) { newSeqs += newSeqs.last(); newScores += Double.NEGATIVE_INFINITY; sourceBeam += 0 }
                seqs = newSeqs
                scores = newScores.toDoubleArray()

                // Done when B hypotheses are finished and no live beam can still beat the best of them.
                if (finished.size >= beams) {
                    val bestFinished = finished.maxOf { it.second }
                    val bestAlive = scores.max() / Math.pow(seqs[0].size.toDouble(), lengthPenalty)
                    if (bestAlive <= bestFinished) break
                }
            }
            if (finished.isEmpty()) {
                for (b in 0 until beams) {
                    if (scores[b] > Double.NEGATIVE_INFINITY) finished += seqs[b] to scores[b] / Math.pow(seqs[b].size.toDouble(), lengthPenalty)
                }
            }
            return finished.maxByOrNull { it.second }?.first ?: LongArray(0)
        } finally {
            owned.forEach { runCatching { it.close() } }
            toClose.asReversed().forEach { runCatching { it.close() } }
        }
    }

    private fun argmaxLast(logits: OnnxTensor): Int {
        val shape = logits.info.shape // [1, t, vocab]
        val vocab = shape[2].toInt()
        val t = shape[1].toInt()
        val buf = logits.floatBuffer
        val offset = (t - 1) * vocab
        var best = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until vocab) {
            if (i == padId) continue // Marian never generates <pad>
            val v = buf.get(offset + i)
            if (v > bestScore) { bestScore = v; best = i }
        }
        return best
    }

    private fun tensor(values: LongArray, shape: LongArray) = OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)

    override fun close() {
        decoderWithPast?.close()
        decoder.close()
        encoder.close()
        options.close()
    }
}

/** Offline translation of one direction from an unpacked package. Thread-safe for sequential use only. */
class MarianTranslationEngine(private val pkg: MarianPackage, threads: Int = 2) : TranslationEngine, AutoCloseable {
    private val tokenizer = MarianTokenizer(pkg)
    private val model = MarianOnnxModel(pkg, threads)

    /** 1 = greedy (fast, for live previews); 4 = beam search (more faithful, for the text that is inserted). */
    @Volatile
    var beams: Int = DEFAULT_BEAMS

    override fun availability(direction: TranslationDirection) =
        if (direction == pkg.direction) EngineAvailability.READY else EngineAvailability.MODEL_MISSING

    override fun translate(text: String, direction: TranslationDirection): TranslationResult {
        if (direction != pkg.direction) return TranslationResult.Failure("No model for $direction")
        return try {
            val out = TextSegmenter.split(text).joinToString("") { seg ->
                if (seg.isTranslatable) translateSentence(seg.text) else seg.text
            }
            TranslationResult.Success(out)
        } catch (e: Exception) {
            TranslationResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    fun translate(text: String, direction: TranslationDirection, beams: Int): TranslationResult = synchronized(this) {
        val previous = this.beams
        this.beams = beams
        try {
            translate(text, direction)
        } finally {
            this.beams = previous
        }
    }

    /** The user's corrections; terms in it are put into the sentence in the target language first. */
    @Volatile
    var glossary: Glossary = Glossary.EMPTY

    @Synchronized
    fun translateSentence(sentence: String): String {
        val glossary = glossary
        glossary.sentence(sentence, pkg.direction)?.let { return it }
        Phrasebook.lookup(sentence, pkg.direction)?.let { return it }
        val prepared = glossary.prepare(sentence, pkg.direction)
        // The models were trained on whole sentences; an unfinished phrase without a final mark makes them
        // invent text. Translate it as a sentence and drop the added full stop again.
        val needsStop = prepared.last() !in SENTENCE_MARKS
        val input = if (needsStop) "$prepared." else prepared
        val ids = tokenizer.encode(input)
        val limit = minOf(model.maxLength, ids.size * 2 + 10)
        val tokens = if (beams > 1) model.generateBeam(ids, limit, beams) else model.generate(ids, limit)
        var out = cleanUp(tokenizer.decode(tokens), sentence)
        if (needsStop && out.endsWith('.') && !out.endsWith("..")) out = out.dropLast(1)
        return TranslationCheck.repair(sentence, out)
    }

    fun translateSentence(sentence: String, beams: Int): String = synchronized(this) {
        val previous = this.beams
        this.beams = beams
        try { translateSentence(sentence) } finally { this.beams = previous }
    }

    /** Plain decoding without the keyboard's adjustments; used to compare with Hugging Face in CI. */
    @Synchronized
    fun translateRaw(text: String, beams: Int): String {
        val ids = tokenizer.encode(text)
        val limit = minOf(model.maxLength, ids.size * 3 + 16)
        return tokenizer.decode(if (beams > 1) model.generateBeam(ids, limit, beams) else model.generate(ids, limit))
    }

    /** Removes artefacts the models add from subtitle-style training data (leading "- ", "/"). */
    private fun cleanUp(translation: String, source: String): String {
        var t = translation.trim()
        if (!source.trimStart().startsWith("-")) t = t.removePrefix("- ").removePrefix("– ")
        if ("/" !in source) t = t.replace("/", "")
        return t.trim()
    }

    companion object {
        const val DEFAULT_BEAMS = 4
        private const val SENTENCE_MARKS = ".!?…:;"
    }

    fun encodeForTest(text: String): LongArray = tokenizer.encode(text)

    override fun close() = model.close()
}

/** Splits text into sentences so long input is translated piece by piece; whitespace and line breaks are kept. */
object TextSegmenter {
    data class Segment(val text: String, val isTranslatable: Boolean)

    private val sentenceEnd = Regex("(?<=[.!?…])\\s+|\\n+")

    fun split(text: String): List<Segment> {
        val out = ArrayList<Segment>()
        var last = 0
        for (m in sentenceEnd.findAll(text)) {
            addChunk(out, text.substring(last, m.range.first))
            out += Segment(m.value, false)
            last = m.range.last + 1
        }
        addChunk(out, text.substring(last))
        return out
    }

    private fun addChunk(out: MutableList<Segment>, chunk: String) {
        if (chunk.isEmpty()) return
        val lead = chunk.takeWhile { it.isWhitespace() }
        val trail = chunk.takeLastWhile { it.isWhitespace() }
        val core = chunk.substring(lead.length, chunk.length - trail.length)
        if (lead.isNotEmpty()) out += Segment(lead, false)
        if (core.isNotEmpty()) out += Segment(core, core.any { it.isLetter() })
        if (trail.isNotEmpty() && core.isNotEmpty()) out += Segment(trail, false)
    }
}

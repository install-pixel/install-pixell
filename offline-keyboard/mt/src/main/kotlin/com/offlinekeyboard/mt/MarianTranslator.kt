package com.offlinekeyboard.mt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.offlinekeyboard.core.speech.EngineAvailability
import com.offlinekeyboard.core.translation.TranslationDirection
import com.offlinekeyboard.core.translation.TranslationEngine
import com.offlinekeyboard.core.translation.TranslationResult
import java.io.File
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

    @Synchronized
    fun translateSentence(sentence: String): String {
        val ids = tokenizer.encode(sentence)
        val limit = minOf(model.maxLength, ids.size * 3 + 16)
        return tokenizer.decode(model.generate(ids, limit))
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

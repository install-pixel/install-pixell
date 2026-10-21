package com.offlinekeyboard.mt

import com.offlinekeyboard.core.translation.TranslationCheck
import com.offlinekeyboard.core.translation.TranslationResult
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the Kotlin translator on real exported model packages (directory from MT_MODELS_DIR, set by CI) and
 * compares it with the Hugging Face tokenizer and reference translations made by tools/mt/export_opus_mt.py.
 * Writes a review report (all phrases, both directions, timings) to MT_REPORT_DIR.
 */
class MarianModelTest {

    private val modelsDir = System.getProperty("mt.modelsDir")?.let(::File)
    private val reportDir = System.getProperty("mt.reportDir")?.let(::File)

    private fun packages(): List<MarianPackage> {
        assumeTrue("MT_MODELS_DIR not set: skipping real-model tests", modelsDir?.isDirectory == true)
        return modelsDir!!.listFiles()!!.filter { MarianPackage.isValid(it) }.map { MarianPackage(it) }
            .also { assertTrue("no model packages in $modelsDir", it.isNotEmpty()) }
    }

    private fun tsv(file: File): List<Pair<String, String>> =
        file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { it.substringBefore('\t') to it.substringAfter('\t') }

    @Test
    fun tokenizerMatchesHuggingFace() {
        for (pkg in packages()) {
            val tokenizer = MarianTokenizer(pkg)
            val cases = tsv(File(pkg.dir, "tokenizer_check.tsv"))
            val mismatches = cases.filter { (text, ids) -> tokenizer.encode(text).joinToString(" ") != ids }
            assertTrue(
                "${pkg.direction}: ${mismatches.size}/${cases.size} tokenizer mismatches, e.g. " +
                    mismatches.take(3).joinToString { (t, ids) -> "'$t' expected [$ids] got [${tokenizer.encode(t).joinToString(" ")}]" },
                mismatches.isEmpty(),
            )
        }
    }

    /** Same decoding algorithm as Hugging Face must give (nearly) the same output. */
    private fun matchRate(engine: MarianTranslationEngine, cases: List<Pair<String, String>>, beams: Int): Pair<Int, Long> {
        var exact = 0
        val t0 = System.nanoTime()
        for ((source, reference) in cases) {
            val ours = engine.translateRaw(source, beams)
            if (ours == reference) exact++ else println("  ≠ beams=$beams  $source  →  $ours   [HF: $reference]")
        }
        return exact to (System.nanoTime() - t0) / 1_000_000 / cases.size
    }

    @Test
    fun translationsMatchReference() {
        val report = StringBuilder("# Offline translation check\n\n")
        val problems = ArrayList<String>()
        val summaries = ArrayList<String>()
        for (pkg in packages()) {
            val greedyCases = tsv(File(pkg.dir, "reference.tsv"))
            val beamFile = File(pkg.dir, "reference_beam.tsv")
            MarianTranslationEngine(pkg, threads = 2).use { engine ->
                engine.translateRaw(greedyCases.first().first, 1) // warm-up

                val (greedyExact, greedyMs) = matchRate(engine, greedyCases, 1)
                val (beamExact, beamMs) = if (beamFile.isFile) matchRate(engine, tsv(beamFile), 4) else (-1 to 0L)

                // What the keyboard inserts: beam search + sentence handling + artefact clean-up.
                var valuesLost = 0
                val rows = ArrayList<String>()
                var keyboardMs = 0L
                for ((source, _) in greedyCases) {
                    val t0 = System.nanoTime()
                    val result = engine.translate(source, pkg.direction, MarianTranslationEngine.DEFAULT_BEAMS)
                    keyboardMs += (System.nanoTime() - t0) / 1_000_000
                    val ours = (result as? TranslationResult.Success)?.text ?: "ERROR: $result"
                    val lost = TranslationCheck.missing(source, ours)
                    if (lost.isNotEmpty()) valuesLost++
                    println("${pkg.direction} ${if (lost.isEmpty()) "=" else "⚠$lost"} $source  →  $ours")
                    rows += "| ${source.md()} | ${ours.md()} | ${lost.joinToString()} |"
                }
                val n = greedyCases.size
                val summary = "${pkg.direction} (${pkg.modelName}): greedy = HF $greedyExact/$n (${greedyMs} ms/phrase); " +
                    "beam4 = HF $beamExact/$n (${beamMs} ms/phrase); keyboard output: numbers/codes changed $valuesLost/$n, " +
                    "avg ${keyboardMs / n} ms"
                println(summary)
                report.append("## ${pkg.direction} — ${pkg.modelName} (license: ${pkg.license})\n\n$summary\n\n")
                    .append("| Source | Keyboard translation (beam 4) | Values changed |\n|---|---|---|\n")
                rows.forEach { report.append(it).append('\n') }
                report.append('\n')
                summaries += summary
                // Small differences come from ONNX Runtime versions (Java vs Python) on quantized weights;
                // a broken tokenizer or decoder would match far less.
                if (greedyExact * 100 / n < GREEDY_MIN_PERCENT) problems += "${pkg.modelName}: greedy $greedyExact/$n"
                if (beamExact >= 0 && beamExact * 100 / n < BEAM_MIN_PERCENT) problems += "${pkg.modelName}: beam $beamExact/$n"
            }
        }
        reportDir?.let {
            it.mkdirs()
            File(it, "translation-report.md").writeText("## Summary\n\n" + summaries.joinToString("\n\n") + "\n\n" + report)
        }
        summaries.forEach { println("SUMMARY $it") }
        assertTrue("decoding differs from Hugging Face too often: $problems", problems.isEmpty())
    }

    private companion object {
        const val GREEDY_MIN_PERCENT = 70
        const val BEAM_MIN_PERCENT = 60
    }

    private fun String.md() = replace("|", "\\|").replace("\n", " ")
}

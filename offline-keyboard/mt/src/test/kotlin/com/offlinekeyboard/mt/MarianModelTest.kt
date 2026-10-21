package com.offlinekeyboard.mt

import com.offlinekeyboard.core.translation.TranslationResult
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the Kotlin translator on real exported model packages (directory from MT_MODELS_DIR, set by CI) and
 * compares it with the Hugging Face tokenizer and the reference translations made by tools/mt/export_opus_mt.py.
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

    @Test
    fun translationsMatchReference() {
        val report = StringBuilder("# Offline translation check\n\n")
        for (pkg in packages()) {
            val cases = tsv(File(pkg.dir, "reference.tsv"))
            MarianTranslationEngine(pkg, threads = 2).use { engine ->
                engine.translateSentence(cases.first().first) // warm-up
                var exact = 0
                val rows = ArrayList<String>()
                val start = System.nanoTime()
                var slowestMs = 0L
                for ((source, reference) in cases) {
                    val t0 = System.nanoTime()
                    val result = engine.translate(source, pkg.direction)
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    slowestMs = maxOf(slowestMs, ms)
                    val ours = (result as? TranslationResult.Success)?.text ?: "ERROR: $result"
                    // The reference is sentence-by-sentence only when the phrase is one sentence.
                    val same = ours == reference
                    if (same) exact++
                    rows += "| ${source.md()} | ${ours.md()} | ${if (same) "=" else reference.md()} | $ms |"
                }
                val totalMs = (System.nanoTime() - start) / 1_000_000
                val rate = exact * 100 / cases.size
                report.append("## ${pkg.direction} — ${pkg.modelName} (license: ${pkg.license})\n\n")
                    .append("Exact match with Hugging Face greedy reference: $exact/${cases.size} ($rate%). ")
                    .append("Total ${totalMs} ms, average ${totalMs / cases.size} ms, slowest $slowestMs ms (CI CPU, 2 threads).\n\n")
                    .append("| Source | Translation (keyboard) | Reference if different | ms |\n|---|---|---|---|\n")
                rows.forEach { report.append(it).append('\n') }
                report.append('\n')
                println("${pkg.direction}: exact $exact/${cases.size}, avg ${totalMs / cases.size} ms")
                // Multi-sentence phrases are split by the keyboard and may legitimately differ; most must match.
                assertTrue("${pkg.direction}: only $rate% match the reference", rate >= 85)
            }
        }
        reportDir?.let {
            it.mkdirs()
            File(it, "translation-report.md").writeText(report.toString())
        }
    }

    private fun String.md() = replace("|", "\\|").replace("\n", " ")
}

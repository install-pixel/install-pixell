package com.offlinekeyboard.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.translation.TranslationDirection
import com.offlinekeyboard.mt.MarianPackage
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.zip.ZipInputStream

data class InstalledModel(val name: String, val sizeBytes: Long, val dir: File)

/**
 * Offline models imported from local zip files (Storage Access Framework). Nothing is downloaded:
 * the user copies the zip to the phone and picks it in Settings. Models are kept in no-backup storage.
 *
 * Speech: a Vosk model zip as published by alphacephei.com (folder with am/, conf/, graph/ ...).
 * Translation: a package from tools/mt/export_opus_mt.py (model.properties says the direction).
 */
class ModelStore(private val context: Context) {

    private val root = File(context.noBackupFilesDir, "models")

    fun speechDir(language: Language) = File(root, "speech/${language.name}")
    fun translationDir(direction: TranslationDirection) = File(root, "translation/${direction.name}")

    fun speechModel(language: Language): InstalledModel? = installed(speechDir(language)) { isVoskModel(it) }

    fun translationModel(direction: TranslationDirection): InstalledModel? =
        installed(translationDir(direction)) { MarianPackage.isValid(it) }

    fun deleteSpeech(language: Language) = speechDir(language).deleteRecursively()
    fun deleteTranslation(direction: TranslationDirection) = translationDir(direction).deleteRecursively()

    /** Blocking; run off the main thread. */
    fun importSpeech(uri: Uri, language: Language): InstalledModel =
        import(uri, speechDir(language), ::isVoskModel, "Not a Vosk speech model (no am/ and conf/ folders)")

    /** Blocking; run off the main thread. The direction comes from the package itself. */
    fun importTranslation(uri: Uri): Pair<TranslationDirection, InstalledModel> {
        val staging = unzipToStaging(uri)
        try {
            val modelDir = findDir(staging) { MarianPackage.isValid(it) }
                ?: throw IOException("Not a translation package (model.properties, *.spm, *.onnx missing)")
            val direction = MarianPackage(modelDir).direction
            return direction to install(modelDir, translationDir(direction), displayName(uri))
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun import(uri: Uri, target: File, valid: (File) -> Boolean, error: String): InstalledModel {
        val staging = unzipToStaging(uri)
        try {
            val modelDir = findDir(staging, valid) ?: throw IOException(error)
            return install(modelDir, target, displayName(uri))
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun install(modelDir: File, target: File, name: String): InstalledModel {
        target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!modelDir.renameTo(target)) {
            modelDir.copyRecursively(target, overwrite = true)
        }
        val size = target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        Properties().apply {
            setProperty("name", name)
            setProperty("size", size.toString())
            File(target, INFO_FILE).outputStream().use { store(it, null) }
        }
        return InstalledModel(name, size, target)
    }

    private fun installed(dir: File, valid: (File) -> Boolean): InstalledModel? {
        val info = File(dir, INFO_FILE)
        if (!info.isFile || !valid(dir)) return null
        val props = Properties().apply { info.inputStream().use { load(it) } }
        return InstalledModel(props.getProperty("name", dir.name), props.getProperty("size", "0").toLong(), dir)
    }

    private fun unzipToStaging(uri: Uri): File {
        val staging = File(root, "staging-${System.currentTimeMillis()}")
        staging.mkdirs()
        val base = staging.canonicalPath + File.separator
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
            ZipInputStream(input.buffered()).use { zip ->
                var entries = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    val out = File(staging, entry.name)
                    // Zip-slip protection: every entry must stay inside the staging folder.
                    if (!out.canonicalPath.startsWith(base)) throw IOException("Unsafe path in zip: ${entry.name}")
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
                if (entries == 0) throw IOException("The file is not a zip archive")
            }
            return staging
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw if (e is IOException) e else IOException(e.message ?: e.javaClass.simpleName, e)
        }
    }

    /** The folder (the zip root or one level below) that holds the model. */
    private fun findDir(staging: File, valid: (File) -> Boolean): File? {
        if (valid(staging)) return staging
        return staging.listFiles()?.filter { it.isDirectory }?.firstOrNull(valid)
    }

    private fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.removeSuffix(".zip") ?: uri.lastPathSegment ?: "model"

    companion object {
        private const val INFO_FILE = "keyboard-model-info.properties"

        fun isVoskModel(dir: File): Boolean = File(dir, "am").isDirectory && File(dir, "conf").isDirectory
    }
}

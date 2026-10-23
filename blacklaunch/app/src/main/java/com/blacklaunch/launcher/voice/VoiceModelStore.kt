package com.blacklaunch.launcher.voice

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** Офлайн-моделі Vosk у внутрішній пам'яті BlackLaunch: `files/voice-models/<uk|pl|en>/`. */
class VoiceModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "voice-models")

    private val _installed = MutableStateFlow(scan())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    fun dir(language: String): File = File(root, language.substringBefore('-'))

    fun isInstalled(language: String): Boolean = language in _installed.value

    /** Розпаковує архів, визначає мову й атомарно замінює стару модель цієї мови. */
    suspend fun import(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val temp = File(appContext.cacheDir, "voice-import-${System.nanoTime()}")
        try {
            val paths = mutableListOf<String>()
            var marker: String? = null
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open $uri")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!ModelPackage.isSafe(entry.name)) throw IOException("Unsafe path ${entry.name}")
                    val out = File(temp, entry.name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                        paths += entry.name
                        if (entry.name.substringAfterLast('/') == ModelPackage.MARKER) marker = out.readText()
                    }
                }
            }
            val language = ModelPackage.detectLanguage(marker, paths)
                ?: throw IOException("Unknown model language")
            val modelRoot = ModelPackage.modelRoot(paths) ?: throw IOException("Not a Vosk model")
            val target = dir(language)
            val old = File(root, "${target.name}.old")
            root.mkdirs()
            old.deleteRecursively()
            if (target.exists() && !target.renameTo(old)) throw IOException("Cannot replace model")
            if (!File(temp, modelRoot).renameTo(target)) {
                old.renameTo(target)
                throw IOException("Cannot install model")
            }
            old.deleteRecursively()
            _installed.value = scan()
            Result.success(language)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        } finally {
            temp.deleteRecursively()
        }
    }

    fun delete(language: String) {
        dir(language).deleteRecursively()
        _installed.value = scan()
    }

    private fun scan(): Set<String> =
        VoiceLanguages.ALL.filter { language ->
            val dir = dir(language)
            File(dir, "am").isDirectory || File(dir, "conf/model.conf").isFile
        }.toSet()

    companion object {
        /** Ті самі моделі Vosk, що й у клавіатурі Offlingo, — одне місце для всіх проєктів. */
        private const val RELEASE = "https://github.com/install-pixel/install-pixell/releases/download/keyboard-models"

        private val files = mapOf(
            VoiceLanguages.UKRAINIAN to "vosk-model-small-uk-v3-small.zip",
            VoiceLanguages.POLISH to "vosk-model-small-pl-0.22.zip",
            VoiceLanguages.ENGLISH to "vosk-model-small-en-us-0.15.zip",
        )

        fun downloadUrl(language: String): String = "$RELEASE/${files.getValue(language)}"
    }
}

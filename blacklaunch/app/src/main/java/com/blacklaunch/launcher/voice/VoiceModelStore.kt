package com.blacklaunch.launcher.voice

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Чому не вдалося встановити модель — показується користувачу. */
enum class ImportError { READ, NOT_MODEL, LANGUAGE, NO_SPACE, OTHER }

class ModelImportException(val reason: ImportError, cause: Throwable? = null) : IOException(reason.name, cause)

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
            val input = try {
                appContext.contentResolver.openInputStream(uri)
            } catch (e: IOException) {
                throw ModelImportException(ImportError.READ, e)
            } catch (e: SecurityException) {
                throw ModelImportException(ImportError.READ, e)
            } ?: throw ModelImportException(ImportError.READ)
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!ModelPackage.isSafe(entry.name)) throw ModelImportException(ImportError.NOT_MODEL)
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
            val modelRoot = ModelPackage.modelRoot(paths) ?: throw ModelImportException(ImportError.NOT_MODEL)
            val language = ModelPackage.detectLanguage(marker, paths)
                ?: throw ModelImportException(ImportError.LANGUAGE)
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
        } catch (e: ModelImportException) {
            Log.w(TAG, "Import failed: ${e.reason}", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.w(TAG, "Import failed", e)
            val reason = when {
                e is ZipException -> ImportError.NOT_MODEL
                e.message.orEmpty().contains("ENOSPC") || temp.usableSpace < MIN_FREE -> ImportError.NO_SPACE
                else -> ImportError.OTHER
            }
            Result.failure(ModelImportException(reason, e))
        } catch (e: SecurityException) {
            Log.w(TAG, "Import failed", e)
            Result.failure(ModelImportException(ImportError.READ, e))
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
        private const val TAG = "VoiceModelStore"
        private const val MIN_FREE = 50L * 1024 * 1024

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

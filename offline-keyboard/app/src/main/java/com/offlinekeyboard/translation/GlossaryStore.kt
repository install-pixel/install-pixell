package com.offlinekeyboard.translation

import android.content.Context
import com.offlinekeyboard.core.translation.Glossary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The user's translation glossary: one text file in no-backup storage, shared by the settings screen and
 * the keyboard (same process). The text is kept exactly as written; [glossary] is its parsed form.
 */
class GlossaryStore private constructor(context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    private val _text = MutableStateFlow(runCatching { if (file.isFile) file.readText(Charsets.UTF_8) else "" }.getOrDefault(""))
    val text: StateFlow<String> = _text

    @Volatile
    var glossary: Glossary = Glossary.parse(_text.value)
        private set

    /** Saves and applies at once; the next translation uses it. */
    fun save(text: String) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        glossary = Glossary.parse(text)
        _text.value = text
    }

    companion object {
        private const val FILE_NAME = "translation_glossary.txt"
        const val EXAMPLE_ASSET = "warehouse.glossary"

        @Volatile
        private var instance: GlossaryStore? = null

        fun get(context: Context): GlossaryStore =
            instance ?: synchronized(this) { instance ?: GlossaryStore(context.applicationContext).also { instance = it } }
    }
}

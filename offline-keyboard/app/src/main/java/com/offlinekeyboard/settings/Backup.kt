package com.offlinekeyboard.settings

import android.content.Context
import android.net.Uri
import com.offlinekeyboard.core.dictionary.WordListFile
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.dictionary.UserDictionaryStore
import com.offlinekeyboard.translation.GlossaryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Files the user saves or opens through the system file picker: the own dictionary, and a full backup
 * (settings with own emoji and shortcuts, the dictionary, the translation glossary). Nothing leaves the
 * phone unless the user puts the file somewhere.
 */
internal object Backup {

    private const val FORMAT = "offkey-backup"
    private const val VERSION = 1

    suspend fun exportDictionary(context: Context, uri: Uri) {
        val store = UserDictionaryStore.get(context)
        write(context, uri, WordListFile.toJson(store.dictionary))
    }

    /** Words of a .txt file go to [plainTextLanguage]; a JSON file names its languages. */
    suspend fun importDictionary(context: Context, uri: Uri, plainTextLanguage: Language): WordListFile.Result {
        val text = read(context, uri)
        val store = UserDictionaryStore.get(context)
        val result = WordListFile.import(store.dictionary, WordListFile.parse(text, plainTextLanguage), System.currentTimeMillis())
        store.changed()
        store.saveNow()
        return result
    }

    suspend fun exportAll(context: Context, uri: Uri) {
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("settings", SettingsRepository(context).exportJson())
            .put("dictionary", UserDictionaryStore.get(context).dictionary.serialize())
            .put("glossary", GlossaryStore.get(context).text.value)
        write(context, uri, json.toString(2))
    }

    /** Returns false when the file is not an OffKey backup; nothing is changed then. */
    suspend fun importAll(context: Context, uri: Uri): Boolean {
        val json = runCatching { JSONObject(read(context, uri)) }.getOrNull() ?: return false
        if (json.optString("format") != FORMAT) return false
        json.optJSONObject("settings")?.let { SettingsRepository(context).importJson(it) }
        json.optString("dictionary", null)?.let {
            val store = UserDictionaryStore.get(context)
            store.dictionary.replaceWith(it)
            store.changed()
            store.saveNow()
        }
        json.optString("glossary", null)?.let { GlossaryStore.get(context).save(it) }
        return true
    }

    private suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
    }

    private suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates an existing file; some file providers only know "w".
        val stream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri)
        stream?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        Unit
    }
}

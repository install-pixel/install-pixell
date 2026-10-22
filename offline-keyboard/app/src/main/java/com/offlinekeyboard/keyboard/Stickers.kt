package com.offlinekeyboard.keyboard

import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.offlinekeyboard.core.layout.EmojiUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Who Lingo is in the character stickers; the other stickers (heart, fire, ...) are shared. */
enum class StickerCharacter(val folder: String) { BOY("boy"), GIRL("girl") }

/**
 * The stickers: the keyboard's own animated ones (drawn by `tools/stickers/make_stickers.py`, shipped in
 * `assets/stickers/` as animated WebP and GIF) and the user's own pictures, copied into the app's
 * storage. A sticker is named "folder/name": "girl/01-hi", "common/12-heart" or "custom/<file>".
 *
 * A sticker is sent the way Android keyboards send images: the app the user types in says which image
 * types it accepts, and the keyboard hands it a read-only file link. Apps that accept no images get
 * nothing (the keyboard says so instead).
 */
object Stickers {

    private const val DIR = "stickers"
    private const val CUSTOM = "custom"
    private const val CUSTOM_DIR = "stickers_custom"
    private const val PREFS = "stickers"
    private const val KEY_USAGE = "usage"
    private const val KEY_FAVORITES = "favorites"
    private const val MAX_IMPORT_BYTES = 8L * 1024 * 1024

    private val _version = MutableStateFlow(0)

    /** Changes whenever own stickers or favourites change, so open screens can refresh. */
    val version: StateFlow<Int> = _version

    private val extensions = mapOf("gif" to "image/gif", "webp" to "image/webp", "png" to "image/png", "jpg" to "image/jpeg")

    fun isCustom(name: String) = name.startsWith("$CUSTOM/")

    private fun customDir(context: Context) = File(context.filesDir, CUSTOM_DIR)

    fun customFile(context: Context, name: String) = File(customDir(context), name.substringAfter('/'))

    /** The user's own stickers, oldest first. */
    fun custom(context: Context): List<String> =
        customDir(context).listFiles().orEmpty()
            .filter { it.extension.lowercase() in extensions }
            .sortedBy { it.name }
            .map { "$CUSTOM/${it.name}" }

    /** Built-in stickers for [character] in their drawn order. */
    private fun builtIn(context: Context, character: StickerCharacter): List<String> =
        listOf(character.folder, "common").flatMap { folder ->
            context.assets.list("$DIR/$folder").orEmpty().filter { it.endsWith(".webp") }.map { "$folder/${it.removeSuffix(".webp")}" }
        }.sortedBy { it.substringAfter('/') }

    /**
     * Panel order: favourites first, then the most used, then the user's own stickers, then the rest.
     */
    fun ordered(context: Context, character: StickerCharacter): List<String> {
        val all = custom(context) + builtIn(context, character)
        val usage = usage(context)
        val favorites = favorites(context)
        return all.withIndex().sortedWith(
            compareByDescending<IndexedValue<String>> { it.value in favorites }
                .thenByDescending { usage.count(it.value) }
                .thenBy { it.index },
        ).map { it.value }
    }

    fun previewPath(name: String) = "$DIR/$name.webp"

    fun stillPath(name: String) = "$DIR/$name.gif"

    // ------------------------------------------------------------------ favourites and usage

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun usage(context: Context) = EmojiUsage.decode(prefs(context).getString(KEY_USAGE, null))

    fun favorites(context: Context): Set<String> = prefs(context).getStringSet(KEY_FAVORITES, emptySet()).orEmpty()

    fun recordUse(context: Context, name: String) {
        val usage = usage(context)
        usage.record(name)
        prefs(context).edit().putString(KEY_USAGE, usage.encode()).apply()
    }

    /** Adds or removes a favourite; returns true when [name] is now a favourite. */
    fun toggleFavorite(context: Context, name: String): Boolean {
        val set = favorites(context).toMutableSet()
        val now = if (name in set) { set.remove(name); false } else { set.add(name); true }
        prefs(context).edit().putStringSet(KEY_FAVORITES, set).apply()
        _version.value++
        return now
    }

    // ------------------------------------------------------------------ own stickers

    /** Copies a picture the user picked; false when it is not a GIF, WebP, PNG or JPEG or is too big. */
    fun import(context: Context, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val type = resolver.getType(uri)?.lowercase()
        val extension = extensions.entries.firstOrNull { it.value == type }?.key
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.let { if (it == "jpeg") "jpg" else it }
                ?.takeIf { it in extensions }
            ?: return false
        val dir = customDir(context).apply { mkdirs() }
        val file = File(dir, "u${System.currentTimeMillis()}-${(1000..9999).random()}.$extension")
        val copied = resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) return@use false
                    output.write(buffer, 0, n)
                }
                true
            }
        } ?: false
        if (!copied) file.delete()
        if (copied) _version.value++
        copied
    }.getOrDefault(false)

    fun delete(context: Context, name: String) {
        if (!isCustom(name)) return
        customFile(context, name).delete()
        val set = favorites(context) - name
        prefs(context).edit().putStringSet(KEY_FAVORITES, set).apply()
        _version.value++
    }

    // ------------------------------------------------------------------ sending

    private fun accepted(editorInfo: EditorInfo?): List<String> =
        editorInfo?.let { EditorInfoCompat.getContentMimeTypes(it) }.orEmpty().filterNotNull()

    /** The image type to send for the built-in stickers, or null when the app takes no stickers. */
    fun mimeFor(editorInfo: EditorInfo?): String? {
        val accepted = accepted(editorInfo)
        fun matches(type: String) = accepted.any { ClipDescription.compareMimeTypes(type, it) }
        // GIF first: chat apps play it (Telegram shows an animated WebP from a keyboard as a still picture).
        return when {
            matches("image/gif") -> "image/gif"
            matches("image/webp") -> "image/webp"
            else -> null
        }
    }

    /** Sends sticker [name] to the app; false when the app does not take it. */
    fun send(context: Context, connection: InputConnection?, editorInfo: EditorInfo?, name: String, description: String): Boolean {
        connection ?: return false
        editorInfo ?: return false
        val file: File
        val mime: String
        if (isCustom(name)) {
            file = customFile(context, name)
            mime = extensions[file.extension.lowercase()] ?: return false
            if (accepted(editorInfo).none { ClipDescription.compareMimeTypes(mime, it) }) return false
        } else {
            mime = mimeFor(editorInfo) ?: return false
            val extension = if (mime == "image/gif") "gif" else "webp"
            file = File(File(context.cacheDir, DIR).apply { mkdirs() }, "${name.replace('/', '-')}.$extension")
            // Copied every time, so an updated keyboard never sends an old drawing.
            context.assets.open("$DIR/$name.$extension").use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.stickers", file)
        val content = InputContentInfoCompat(uri, ClipDescription(description, arrayOf(mime)), null)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        } else {
            0
        }
        val sent = InputConnectionCompat.commitContent(connection, editorInfo, content, flags, null)
        if (sent) recordUse(context, name)
        return sent
    }
}

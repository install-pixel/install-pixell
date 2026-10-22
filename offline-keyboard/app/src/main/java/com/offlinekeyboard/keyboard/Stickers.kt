package com.offlinekeyboard.keyboard

import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

/**
 * The keyboard's own animated stickers: drawn by `tools/stickers/make_stickers.py`, shipped in
 * `assets/stickers/` as animated WebP (soft edges) and GIF (for apps that only take GIF).
 *
 * A sticker is sent the way Android keyboards send images: the app the user types in says which image
 * types it accepts, and the keyboard hands it a read-only file link. Apps that accept no images get
 * nothing (the keyboard says so instead).
 */
object Stickers {

    private const val DIR = "stickers"

    /** Sticker names in panel order, e.g. "01-hi". */
    fun names(context: Context): List<String> =
        context.assets.list(DIR).orEmpty().filter { it.endsWith(".webp") }.map { it.removeSuffix(".webp") }.sorted()

    fun previewPath(name: String) = "$DIR/$name.webp"

    fun stillPath(name: String) = "$DIR/$name.gif"

    /** The image type to send to the app described by [editorInfo], or null when it takes no stickers. */
    fun mimeFor(editorInfo: EditorInfo?): String? {
        val accepted = editorInfo?.let { EditorInfoCompat.getContentMimeTypes(it) }.orEmpty()
        fun explicit(type: String) = accepted.any { it.equals(type, ignoreCase = true) }
        fun matches(type: String) = accepted.any { ClipDescription.compareMimeTypes(type, it) }
        return when {
            explicit("image/webp") -> "image/webp"
            explicit("image/gif") -> "image/gif"
            // "image/*": GIF is the type every chat app animates.
            matches("image/gif") -> "image/gif"
            matches("image/webp") -> "image/webp"
            else -> null
        }
    }

    /** Sends sticker [name] to the app; false when the app does not take it. */
    fun send(context: Context, connection: InputConnection?, editorInfo: EditorInfo?, name: String, description: String): Boolean {
        connection ?: return false
        val mime = mimeFor(editorInfo) ?: return false
        val extension = if (mime == "image/gif") "gif" else "webp"
        val file = File(File(context.cacheDir, DIR).apply { mkdirs() }, "$name.$extension")
        // Copied every time, so an updated keyboard never sends an old drawing.
        context.assets.open("$DIR/$name.$extension").use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.stickers", file)
        val content = InputContentInfoCompat(uri, ClipDescription(description, arrayOf(mime)), null)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        } else {
            0
        }
        return InputConnectionCompat.commitContent(connection, editorInfo!!, content, flags, null)
    }
}

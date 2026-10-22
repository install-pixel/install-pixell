package com.offlinekeyboard.debug

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

/** Plain fields of the kinds the keyboard treats differently; used by the emulator UI tests. */
class KeyboardTestActivity : Activity() {

    lateinit var plain: EditText
    lateinit var multiLine: EditText
    lateinit var password: EditText
    /** A field that takes images, like a chat app's message box. */
    lateinit var chat: EditText

    /** What [chat] received: "mime size-in-bytes uri". */
    @Volatile var receivedSticker: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        plain = field(InputType.TYPE_CLASS_TEXT, "plain")
        multiLine = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, "multi")
        password = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "password")
        chat = ChatField().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "chat"
            contentDescription = "chat"
        }
        listOf(plain, multiLine, password, chat).forEach { root.addView(it) }
        setContentView(root)
        plain.requestFocus()
    }

    private inner class ChatField : EditText(this@KeyboardTestActivity) {
        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val connection = super.onCreateInputConnection(outAttrs) ?: return null
            EditorInfoCompat.setContentMimeTypes(outAttrs, arrayOf("image/gif", "image/webp"))
            return InputConnectionCompat.createWrapper(connection, outAttrs) { info, flags, _ ->
                if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                    runCatching { info.requestPermission() }
                }
                val size = runCatching {
                    contentResolver.openInputStream(info.contentUri)!!.use { it.readBytes().size }
                }.getOrDefault(-1)
                receivedSticker = "${info.description.getMimeType(0)} $size ${info.contentUri}"
                true
            }
        }
    }

    private fun field(type: Int, name: String) = EditText(this).apply {
        inputType = type
        hint = name
        contentDescription = name
    }
}

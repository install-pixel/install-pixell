package com.offlinekeyboard.debug

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

/** Plain fields of the kinds the keyboard treats differently; used by the emulator UI tests. */
class KeyboardTestActivity : Activity() {

    lateinit var plain: EditText
    lateinit var multiLine: EditText
    lateinit var password: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        plain = field(InputType.TYPE_CLASS_TEXT, "plain")
        multiLine = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, "multi")
        password = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "password")
        listOf(plain, multiLine, password).forEach { root.addView(it) }
        setContentView(root)
        plain.requestFocus()
    }

    private fun field(type: Int, name: String) = EditText(this).apply {
        inputType = type
        hint = name
        contentDescription = name
    }
}

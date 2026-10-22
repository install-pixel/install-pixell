package com.offlinekeyboard.keyboard

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy

/**
 * A text field behind an [InputConnection], enough for composing-text tests. [reportsComposing] = false
 * imitates apps (many messengers, browsers) that report candidatesStart = -1 to the keyboard.
 */
class FakeEditor(initial: String = "", private val reportsComposing: Boolean = true) {
    val text = StringBuilder(initial)
    var cursor = initial.length
    private var compStart = -1
    private var compEnd = -1

    /** Cursor updates the keyboard would receive: (selStart, selEnd, candidatesStart, candidatesEnd). */
    val updates = ArrayList<IntArray>()

    private fun replaceRegion(start: Int, end: Int, value: CharSequence) {
        text.replace(start, end, value.toString())
        cursor = start + value.length
    }

    private fun report() {
        updates += if (reportsComposing) intArrayOf(cursor, cursor, compStart, compEnd) else intArrayOf(cursor, cursor, -1, -1)
    }

    fun moveCursor(to: Int) {
        cursor = to
        report()
    }

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "setComposingText" -> {
                val value = args[0] as CharSequence
                val start = if (compStart >= 0) compStart else cursor
                val end = if (compStart >= 0) compEnd else cursor
                replaceRegion(start, end, value)
                if (value.isEmpty()) { compStart = -1; compEnd = -1 } else { compStart = start; compEnd = start + value.length }
                report()
                true
            }
            "commitText" -> {
                val value = args[0] as CharSequence
                val start = if (compStart >= 0) compStart else cursor
                val end = if (compStart >= 0) compEnd else cursor
                replaceRegion(start, end, value)
                compStart = -1; compEnd = -1
                report()
                true
            }
            "finishComposingText" -> { compStart = -1; compEnd = -1; report(); true }
            "getTextBeforeCursor" -> text.substring(maxOf(0, cursor - (args[0] as Int)), cursor)
            "beginBatchEdit", "endBatchEdit" -> true
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                else -> null
            }
        }
    } as InputConnection
}

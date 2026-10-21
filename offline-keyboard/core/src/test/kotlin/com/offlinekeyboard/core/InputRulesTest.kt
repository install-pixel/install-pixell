package com.offlinekeyboard.core

import com.offlinekeyboard.core.input.Direction
import com.offlinekeyboard.core.input.EnterLabel
import com.offlinekeyboard.core.input.EnterResolution
import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.input.FieldInfo.Companion.IME_ACTION_DONE
import com.offlinekeyboard.core.input.FieldInfo.Companion.IME_ACTION_SEND
import com.offlinekeyboard.core.input.FieldInfo.Companion.IME_FLAG_NO_ENTER_ACTION
import com.offlinekeyboard.core.input.FieldInfo.Companion.IME_FLAG_NO_PERSONALIZED_LEARNING
import com.offlinekeyboard.core.input.FieldInfo.Companion.TYPE_CLASS_NUMBER
import com.offlinekeyboard.core.input.FieldInfo.Companion.TYPE_CLASS_TEXT
import com.offlinekeyboard.core.input.FieldInfo.Companion.TYPE_NUMBER_VARIATION_PASSWORD
import com.offlinekeyboard.core.input.FieldInfo.Companion.TYPE_TEXT_FLAG_MULTI_LINE
import com.offlinekeyboard.core.input.FieldInfo.Companion.TYPE_TEXT_VARIATION_PASSWORD
import com.offlinekeyboard.core.input.FieldInfo.Companion.TYPE_TEXT_VARIATION_WEB_PASSWORD
import com.offlinekeyboard.core.input.SelectionMath
import com.offlinekeyboard.core.input.TabResolution
import com.offlinekeyboard.core.input.TextBoundaries
import com.offlinekeyboard.core.input.TextSelection
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.SpecialKey
import com.offlinekeyboard.core.speech.EngineAvailability
import com.offlinekeyboard.core.speech.VoiceDecision
import com.offlinekeyboard.core.speech.VoiceGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputRulesTest {

    private val singleLine = FieldInfo(TYPE_CLASS_TEXT, 0)
    private val multiLine = FieldInfo(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE, 0)

    @Test
    fun `enter resolution`() {
        assertEquals(EnterResolution.KeyEvent, singleLine.resolveEnter(false))
        assertEquals(EnterResolution.Newline, multiLine.resolveEnter(false))
        val chat = FieldInfo(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE, IME_ACTION_SEND)
        assertEquals(EnterResolution.EditorAction(IME_ACTION_SEND), chat.resolveEnter(false))
        assertEquals(EnterLabel.SEND, chat.enterLabel)
        assertEquals(EnterResolution.Newline, chat.resolveEnter(forceNewline = true))
        val noAction = FieldInfo(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE, IME_ACTION_DONE or IME_FLAG_NO_ENTER_ACTION)
        assertEquals(EnterResolution.Newline, noAction.resolveEnter(false))
        assertEquals(EnterLabel.NEWLINE, noAction.enterLabel)
        assertEquals(EnterResolution.KeyEvent, FieldInfo(0, 0).resolveEnter(true))
    }

    @Test
    fun `tab resolution`() {
        assertEquals(TabResolution.InsertTab, multiLine.resolveTab(false))
        assertEquals(TabResolution.KeyEvent, multiLine.resolveTab(true))
        assertEquals(TabResolution.KeyEvent, singleLine.resolveTab(false))
    }

    @Test
    fun `sensitive fields block voice`() {
        assertTrue(FieldInfo(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD, 0).isSensitive)
        assertTrue(FieldInfo(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_WEB_PASSWORD, 0).isSensitive)
        assertTrue(FieldInfo(TYPE_CLASS_NUMBER or TYPE_NUMBER_VARIATION_PASSWORD, 0).isSensitive)
        assertTrue(FieldInfo(TYPE_CLASS_TEXT, IME_FLAG_NO_PERSONALIZED_LEARNING).isSensitive)
        assertFalse(singleLine.isSensitive)
        val pw = FieldInfo(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD, 0)
        assertEquals(VoiceDecision.BLOCKED_SENSITIVE_FIELD, VoiceGate.check(pw, EngineAvailability.READY, true))
        assertEquals(VoiceDecision.START, VoiceGate.check(singleLine, EngineAvailability.READY, true))
        assertEquals(VoiceDecision.PERMISSION_NEEDED, VoiceGate.check(singleLine, EngineAvailability.READY, false))
    }

    @Test
    fun `backspace removes whole graphemes`() {
        assertEquals(1, TextBoundaries.previousGraphemeLength("abc"))
        assertEquals(2, TextBoundaries.previousGraphemeLength("a😀"))
        val family = "👨‍👩‍👧"
        assertEquals(family.length, TextBoundaries.previousGraphemeLength("x$family"))
        val flag = "🇺🇦"
        assertEquals(flag.length, TextBoundaries.previousGraphemeLength("x$flag"))
        assertEquals(2, TextBoundaries.previousGraphemeLength("aï")) // i + combining diaeresis
        assertEquals(2, TextBoundaries.nextGraphemeLength("😀b"))
        assertEquals(0, TextBoundaries.previousGraphemeLength(""))
    }

    @Test
    fun `word boundaries`() {
        assertEquals("world  ".length, TextBoundaries.previousWordLength("hello world  "))
        assertEquals("Привіт".length, TextBoundaries.previousWordLength("Привіт"))
        assertEquals("ім'я".length, TextBoundaries.previousWordLength("моє ім'я"))
        assertEquals(1, TextBoundaries.previousWordLength("end."))
        assertEquals("hello ".length, TextBoundaries.nextWordLength("hello world"))
        assertEquals(1, TextBoundaries.nextWordLength("\nabc"))
    }

    @Test
    fun `selection math`() {
        val text = "first line\nsecond"
        val atSecond = TextSelection(13, 13)
        assertEquals(TextSelection(11, 11), SelectionMath.move(text, atSecond, Direction.HOME, false))
        assertEquals(TextSelection(13, text.length), SelectionMath.move(text, atSecond, Direction.END, true))
        assertEquals(TextSelection(0, 0), SelectionMath.move(text, atSecond, Direction.HOME, false, toDocumentEdge = true))
        assertEquals(TextSelection(13, 12), SelectionMath.move(text, atSecond, Direction.LEFT, true))
        val sel = TextSelection(2, 5)
        assertEquals(TextSelection(2, 2), SelectionMath.move(text, sel, Direction.LEFT, false))
        assertEquals(TextSelection(5, 5), SelectionMath.move(text, sel, Direction.RIGHT, false))
        assertEquals(TextSelection(3, 3), SelectionMath.move("a😀b", TextSelection(1, 1), Direction.RIGHT, false))
        assertEquals(TextSelection(6, 6), SelectionMath.move("first line", TextSelection(0, 0), Direction.RIGHT, false, byWord = true))
        assertEquals(TextSelection(0, 0), SelectionMath.move("abc", TextSelection(0, 0), Direction.LEFT, false))
    }

    @Test
    fun `labels`() {
        val ctx = LabelContext(shift = false, capsLock = false, language = Language.UK, mode = KeyboardMode.MOBILE)
        assertEquals("Українська", KeyLabels.label(Key(KeyAction.Special(SpecialKey.SPACE)), ctx))
        assertEquals("Укр", KeyLabels.label(Key(KeyAction.Special(SpecialKey.LANGUAGE)), ctx))
        assertEquals("!", KeyLabels.hint(Key(KeyAction.Text("1", "!")), ctx))
        assertEquals("Ї", KeyLabels.label(Key(KeyAction.Text("ї")), ctx.copy(shift = true)))
    }
}

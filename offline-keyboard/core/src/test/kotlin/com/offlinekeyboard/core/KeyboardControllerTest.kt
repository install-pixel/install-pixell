package com.offlinekeyboard.core

import com.offlinekeyboard.core.input.Direction
import com.offlinekeyboard.core.input.EditAction
import com.offlinekeyboard.core.input.EditorCommand
import com.offlinekeyboard.core.input.KeyMods
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardControllerTest {

    private var now = 10_000L
    private fun controller(mode: KeyboardMode = KeyboardMode.COMPUTER, language: Language = Language.EN) =
        KeyboardController(mode, language) { now }

    private fun KeyboardController.key(text: String): Key =
        layout(false).allKeys().first { (it.action as? KeyAction.Text)?.text == text }

    private fun KeyboardController.press(special: SpecialKey): List<EditorCommand> {
        now += 1000
        return onKey(Key(KeyAction.Special(special)))
    }

    private fun KeyboardController.type(text: String) = onKey(key(text))

    @Test
    fun `letters respect shift and caps lock`() {
        val c = controller()
        assertEquals(listOf(EditorCommand.CommitText("a")), c.type("a"))
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.CommitText("A")), c.type("a"))
        assertEquals(listOf(EditorCommand.CommitText("a")), c.type("a"))
        c.press(SpecialKey.CAPS_LOCK)
        assertEquals(listOf(EditorCommand.CommitText("A")), c.type("a"))
        assertEquals(listOf(EditorCommand.CommitText("1")), c.type("1"))
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.CommitText("a")), c.type("a"))
    }

    @Test
    fun `shift gives PC symbols on the digit row`() {
        val c = controller()
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.CommitText("!")), c.type("1"))
        val uk = controller(language = Language.UK)
        uk.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.CommitText("№")), uk.type("3"))
    }

    @Test
    fun `ukrainian capitals`() {
        val c = controller(language = Language.UK)
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.CommitText("Ї")), c.type("ї"))
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.CommitText("Ґ")), c.type("ґ"))
    }

    @Test
    fun `ctrl shortcuts use edit actions even on cyrillic keys`() {
        val c = controller(language = Language.UK)
        c.press(SpecialKey.CTRL)
        assertEquals(listOf(EditorCommand.Edit(EditAction.COPY)), c.type("с"))
        c.press(SpecialKey.CTRL)
        assertEquals(listOf(EditorCommand.Edit(EditAction.PASTE)), c.type("м"))
        c.press(SpecialKey.CTRL)
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.Edit(EditAction.REDO)), c.type("я"))
        // one-shot modifiers are used up
        assertEquals(listOf(EditorCommand.CommitText("я")), c.type("я"))
    }

    @Test
    fun `other ctrl combinations become key events`() {
        val c = controller()
        c.press(SpecialKey.CTRL)
        assertEquals(listOf(EditorCommand.SendKey("F", KeyMods(ctrl = true))), c.type("f"))
    }

    @Test
    fun `polish alt gives diacritics`() {
        val c = controller(language = Language.PL)
        c.press(SpecialKey.ALT)
        assertEquals(listOf(EditorCommand.CommitText("ą")), c.type("a"))
        c.press(SpecialKey.ALT)
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.CommitText("Ż")), c.type("z"))
    }

    @Test
    fun `locked ctrl stays for several keys`() {
        val c = controller()
        c.press(SpecialKey.CTRL)
        now += 100
        c.onKey(Key(KeyAction.Special(SpecialKey.CTRL)))
        assertEquals(listOf(EditorCommand.Move(Direction.LEFT, byWord = true)), c.press(SpecialKey.LEFT))
        assertEquals(listOf(EditorCommand.Move(Direction.LEFT, byWord = true)), c.press(SpecialKey.LEFT))
        assertEquals(listOf(EditorCommand.DeleteWordBackward), c.press(SpecialKey.BACKSPACE))
    }

    @Test
    fun `navigation keys`() {
        val c = controller()
        assertEquals(listOf(EditorCommand.Move(Direction.HOME)), c.press(SpecialKey.HOME))
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.Move(Direction.END, select = true)), c.press(SpecialKey.END))
        c.press(SpecialKey.CTRL)
        assertEquals(listOf(EditorCommand.Move(Direction.END, toDocumentEdge = true)), c.press(SpecialKey.END))
        assertEquals(listOf(EditorCommand.SendKey("F5")), c.press(SpecialKey.F5))
        assertEquals(listOf(EditorCommand.SendKey("INSERT")), c.press(SpecialKey.INSERT))
    }

    @Test
    fun `enter tab and escape`() {
        val c = controller()
        assertEquals(listOf(EditorCommand.Enter(false)), c.press(SpecialKey.ENTER))
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.Enter(true)), c.press(SpecialKey.ENTER))
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.Tab(true)), c.press(SpecialKey.TAB))
        c.press(SpecialKey.ALT)
        assertEquals(emptyList<EditorCommand>(), c.press(SpecialKey.ESC)) // first Esc drops Alt
        assertEquals(listOf(EditorCommand.SendKey("ESCAPE")), c.press(SpecialKey.ESC))
    }

    @Test
    fun `language cycles through enabled languages only`() {
        val c = KeyboardController(KeyboardMode.MOBILE, Language.UK, listOf(Language.UK, Language.PL)) { now }
        c.press(SpecialKey.LANGUAGE)
        assertEquals(Language.PL, c.language)
        c.press(SpecialKey.LANGUAGE)
        assertEquals(Language.UK, c.language)
        c.enabledLanguages = listOf(Language.EN)
        assertEquals(Language.EN, c.language)
    }

    @Test
    fun `switching mode keeps language and caps lock`() {
        val c = controller(language = Language.PL)
        c.press(SpecialKey.CAPS_LOCK)
        c.press(SpecialKey.LAYER_FUNCTION)
        var changedTo: KeyboardMode? = null
        c.listener = object : KeyboardController.Listener {
            override fun onModeChanged(mode: KeyboardMode) { changedTo = mode }
        }
        c.press(SpecialKey.MODE)
        assertEquals(KeyboardMode.MOBILE, changedTo)
        assertEquals(Language.PL, c.language)
        assertEquals(Layer.LETTERS, c.layer)
        assertTrue(c.modifiers.capsLock)
        c.press(SpecialKey.MODE)
        assertEquals(KeyboardMode.COMPUTER, c.mode)
    }

    @Test
    fun `layer keys toggle in computer mode`() {
        val c = controller()
        c.press(SpecialKey.LAYER_FUNCTION)
        assertEquals(Layer.FUNCTION, c.layer)
        c.press(SpecialKey.LAYER_FUNCTION)
        assertEquals(Layer.LETTERS, c.layer)
    }

    @Test
    fun `numeric field opens digits in mobile mode`() {
        val c = controller(mode = KeyboardMode.MOBILE)
        c.onStartField(numeric = true)
        assertEquals(Layer.SYMBOLS, c.layer)
        c.onStartField(numeric = false)
        assertEquals(Layer.LETTERS, c.layer)
    }

    @Test
    fun `auto shift capitalises one letter in mobile mode only`() {
        val c = controller(mode = KeyboardMode.MOBILE)
        c.setAutoShift(true)
        assertEquals(listOf(EditorCommand.CommitText("H")), c.type("h"))
        assertEquals(listOf(EditorCommand.CommitText("i")), c.type("i"))
        val pc = controller(mode = KeyboardMode.COMPUTER)
        pc.setAutoShift(true)
        assertEquals(listOf(EditorCommand.CommitText("h")), pc.type("h"))
    }

    @Test
    fun `long press alternative follows shift`() {
        val c = controller(mode = KeyboardMode.MOBILE, language = Language.PL)
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.CommitText("Ś")), c.onAlternative("ś"))
        assertEquals(listOf(EditorCommand.CommitText("ś")), c.onAlternative("ś"))
    }
}

package com.offlinekeyboard.core

import com.offlinekeyboard.core.dictionary.UserDictionary
import com.offlinekeyboard.core.dictionary.WordRules
import com.offlinekeyboard.core.input.EditAction
import com.offlinekeyboard.core.input.EditorCommand
import com.offlinekeyboard.core.input.FieldInfo
import com.offlinekeyboard.core.layout.Key
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.SpecialKey
import com.offlinekeyboard.core.speech.VoiceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {

    private var now = 10_000L

    private fun controller(language: Language = Language.UK, mode: KeyboardMode = KeyboardMode.COMPUTER) =
        KeyboardController(mode, language) { now }

    private fun KeyboardController.press(special: SpecialKey): List<EditorCommand> {
        now += 1000
        return onKey(Key(KeyAction.Special(special)))
    }

    private fun KeyboardController.type(text: String) =
        onKey(layout(false).allKeys().first { (it.action as? KeyAction.Text)?.text == text })

    // ------------------------------------------------------------ shortcuts

    @Test
    fun `modifier only shortcuts wait for the pause`() {
        val c = controller()
        c.shortcuts = listOf(Shortcut(alt = true, shift = true, action = ShortcutAction.SWITCH_LANGUAGE))
        c.press(SpecialKey.ALT)
        assertEquals(emptyList<EditorCommand>(), c.press(SpecialKey.SHIFT))
        assertEquals(Language.UK, c.language) // waits for "release"
        assertEquals(emptyList<EditorCommand>(), c.firePendingShortcut())
        assertEquals(Language.PL, c.language)
        assertFalse(c.modifiers.isActive(ModifierKey.ALT))
    }

    @Test
    fun `a key after alt shift cancels the language switch`() {
        val c = controller(language = Language.PL)
        c.shortcuts = listOf(Shortcut(alt = true, shift = true, action = ShortcutAction.SWITCH_LANGUAGE))
        c.press(SpecialKey.ALT)
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.CommitText("Ż")), c.type("z"))
        assertEquals(emptyList<EditorCommand>(), c.firePendingShortcut())
        assertEquals(Language.PL, c.language)
    }

    @Test
    fun `only win space is a default`() {
        assertEquals(1, Shortcut.defaults.size)
        val c = controller()
        c.press(SpecialKey.ALT)
        c.press(SpecialKey.SHIFT)
        c.firePendingShortcut()
        assertEquals(Language.UK, c.language)
    }

    @Test
    fun `terminals receive ctrl letters as keys`() {
        val c = controller(language = Language.EN)
        c.rawKeyField = true
        c.press(SpecialKey.CTRL)
        assertEquals(listOf(EditorCommand.SendKey("X", com.offlinekeyboard.core.input.KeyMods(ctrl = true))), c.type("x"))
        c.rawKeyField = false
        c.press(SpecialKey.CTRL)
        assertEquals(listOf(EditorCommand.Edit(EditAction.CUT)), c.type("x"))
    }

    @Test
    fun `russian letters on long press`() {
        val keys = com.offlinekeyboard.core.layout.LayoutProvider.layout(KeyboardMode.MOBILE, Language.UK, com.offlinekeyboard.core.layout.Layer.LETTERS).allKeys()
        fun alts(t: String) = keys.first { (it.action as? KeyAction.Text)?.text == t }.alternatives
        assertTrue("ы" in alts("и"))
        assertTrue("э" in alts("е") && "ё" in alts("е"))
        assertTrue("ъ" in alts("ь"))
    }

    @Test
    fun `win space switches language and ctrl shift keeps selecting`() {
        val c = controller()
        c.press(SpecialKey.META)
        c.press(SpecialKey.SPACE)
        assertEquals(Language.PL, c.language)
        // Ctrl+Shift is not a default shortcut: Ctrl+Shift+Z still means redo.
        c.press(SpecialKey.CTRL)
        c.press(SpecialKey.SHIFT)
        assertEquals(Language.PL, c.language)
        assertEquals(listOf(EditorCommand.Edit(EditAction.REDO)), c.type("z"))
    }

    @Test
    fun `custom shortcuts on cyrillic keys use latin positions`() {
        val c = controller()
        c.shortcuts = listOf(
            Shortcut(ctrl = true, alt = true, key = "T", action = ShortcutAction.INSERT_TEXT, text = "Дякую!"),
            Shortcut(ctrl = true, key = "M", action = ShortcutAction.SWITCH_MODE),
            Shortcut(ctrl = true, key = "F2", action = ShortcutAction.VOICE_INPUT),
            Shortcut(ctrl = true, shift = true, action = ShortcutAction.TOGGLE_TRANSLATE),
        )
        c.press(SpecialKey.CTRL); c.press(SpecialKey.ALT)
        assertEquals(listOf(EditorCommand.CommitText("Дякую!")), c.type("е")) // е is on the T position
        c.press(SpecialKey.CTRL)
        assertEquals(listOf(EditorCommand.StartVoice), c.press(SpecialKey.F2))
        c.press(SpecialKey.CTRL)
        c.press(SpecialKey.SHIFT)
        assertEquals(listOf(EditorCommand.ToggleTranslate), c.firePendingShortcut())
        c.press(SpecialKey.CTRL)
        c.type("ь") // ь is on the M position
        assertEquals(KeyboardMode.MOBILE, c.mode)
    }

    @Test
    fun `shortcut encoding round trip and validation`() {
        val list = listOf(
            Shortcut(ctrl = true, shift = true, key = "L", action = ShortcutAction.SWITCH_LANGUAGE),
            Shortcut(alt = true, key = "1", action = ShortcutAction.INSERT_TEXT, text = "a|b\nc, ąę ї"),
        )
        assertEquals(list, Shortcut.decode(Shortcut.encode(list)))
        assertEquals(emptyList<Shortcut>(), Shortcut.decode("garbage\n|||"))
        assertFalse(Shortcut(shift = true, key = "A", action = ShortcutAction.SWITCH_LANGUAGE).isValid)
        assertFalse(Shortcut(ctrl = true, action = ShortcutAction.SWITCH_LANGUAGE).isValid)
        assertFalse(Shortcut(ctrl = true, key = "A", action = ShortcutAction.INSERT_TEXT).isValid)
        assertTrue(Shortcut(ctrl = true, key = "C", action = ShortcutAction.SWITCH_LANGUAGE).overridesBuiltIn)
        assertEquals("Ctrl+Shift+L", list[0].describe())
    }

    @Test
    fun `shortcuts do nothing in mobile mode`() {
        val c = controller(mode = KeyboardMode.MOBILE)
        c.press(SpecialKey.SHIFT)
        c.firePendingShortcut()
        assertEquals(Language.UK, c.language)
    }

    // ------------------------------------------------------------ dictionary

    @Test
    fun `dictionary learns and suggests with case`() {
        val d = UserDictionary()
        repeat(3) { d.learn(Language.PL, "Warszawa", now++) }
        d.learn(Language.PL, "warsztat", now++)
        assertFalse(d.learn(Language.PL, "wa", now++)) // too short to learn
        assertEquals(listOf("warszawa", "warsztat"), d.suggestions(Language.PL, "wa"))
        assertEquals(listOf("Warszawa", "Warsztat"), d.suggestions(Language.PL, "Wa"))
        assertEquals(listOf("WARSZAWA", "WARSZTAT"), d.suggestions(Language.PL, "WA"))
        assertEquals(emptyList<String>(), d.suggestions(Language.UK, "wa"))
        assertEquals(emptyList<String>(), d.suggestions(Language.PL, "warszawa")) // exact word is not suggested
    }

    @Test
    fun `dictionary rejects codes and keeps acronyms`() {
        val d = UserDictionary()
        assertFalse(d.learn(Language.EN, "a", 0))
        assertFalse(d.learn(Language.EN, "AB12", 0))
        assertFalse(d.learn(Language.EN, "123", 0))
        assertFalse(d.learn(Language.EN, "user@mail", 0))
        assertTrue(d.learn(Language.EN, "NATO", 0))
        assertTrue(d.learn(Language.UK, "пʼятниця", 0))
        assertEquals(listOf("NATO"), d.suggestions(Language.EN, "na"))
        assertEquals(listOf("пʼятниця"), d.suggestions(Language.UK, "пʼя"))
    }

    @Test
    fun `dictionary serialization and eviction`() {
        val d = UserDictionary(maxWordsPerLanguage = 2)
        d.learn(Language.UK, "палета", 1); d.learn(Language.UK, "палета", 2)
        d.learn(Language.UK, "пакет", 3)
        d.learn(Language.UK, "папір", 4) // evicts the least used, oldest word
        assertEquals(2, d.size(Language.UK))
        assertEquals(listOf("палета", "папір"), d.suggestions(Language.UK, "па", limit = 5))
        val copy = UserDictionary.parse(d.serialize())
        assertEquals(d.entries(Language.UK), copy.entries(Language.UK))
        assertTrue(copy.remove(Language.UK, "Палета"))
        assertEquals(1, copy.size(Language.UK))
    }

    @Test
    fun `short words are not learned and once-typed words are forgotten after a while`() {
        val d = UserDictionary()
        assertFalse(d.learn(Language.UK, "так", 0))
        assertTrue(d.learn(Language.UK, "палета", 0))
        d.learn(Language.UK, "накладна", 0); d.learn(Language.UK, "накладна", 1)
        assertEquals(0, d.forgetStale(UserDictionary.FORGET_ONCE_TYPED_MS))
        assertEquals(1, d.forgetStale(UserDictionary.FORGET_ONCE_TYPED_MS + 1))
        assertEquals(listOf("накладна"), d.entries(Language.UK).map { it.word })
    }

    @Test
    fun `trailing word extraction`() {
        assertEquals("Ware", WordRules.trailingWord("Hello Ware"))
        assertEquals("", WordRules.trailingWord("Hello "))
        assertEquals("", WordRules.trailingWord("ABC12"))
        assertEquals("ім'я", WordRules.trailingWord("моє ім'я"))
        assertTrue(WordRules.continuesWord("abc"))
        assertFalse(WordRules.continuesWord(" abc"))
    }

    @Test
    fun `learning only in ordinary text fields`() {
        assertTrue(FieldInfo(FieldInfo.TYPE_CLASS_TEXT, 0).allowsLearning)
        assertFalse(FieldInfo(FieldInfo.TYPE_CLASS_TEXT or FieldInfo.TYPE_TEXT_VARIATION_PASSWORD, 0).allowsLearning)
        assertFalse(FieldInfo(FieldInfo.TYPE_CLASS_TEXT or FieldInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0).allowsLearning)
        assertFalse(FieldInfo(FieldInfo.TYPE_CLASS_TEXT or FieldInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0).allowsLearning)
        assertFalse(FieldInfo(FieldInfo.TYPE_CLASS_TEXT, FieldInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).allowsLearning)
        assertFalse(FieldInfo(FieldInfo.TYPE_CLASS_NUMBER, 0).allowsLearning)
    }

    // ------------------------------------------------------------ voice text

    @Test
    fun `voice punctuation and capitals`() {
        assertEquals("Привіт, як справи?", VoiceText.format("привіт кома як справи знак питання", Language.UK, ""))
        assertEquals(" ładna pogoda. Idziemy", VoiceText.format("ładna pogoda kropka idziemy", Language.PL, "Dziś"))
        assertEquals("Hello.\nHere", VoiceText.format("hello period new line here", Language.EN, "Text. "))
        assertEquals("кома", VoiceText.format("кома", Language.UK, "x ", commandsEnabled = false))
        assertEquals("", VoiceText.format("   ", Language.UK, ""))
    }

    @Test
    fun `sentence start detection`() {
        assertTrue(VoiceText.startsSentence(""))
        assertTrue(VoiceText.startsSentence("Done. "))
        assertTrue(VoiceText.startsSentence("line\n"))
        assertFalse(VoiceText.startsSentence("word "))
        assertNull(null)
    }
}

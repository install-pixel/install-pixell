package com.offlinekeyboard.core

import com.offlinekeyboard.core.layout.EmojiSet
import com.offlinekeyboard.core.layout.EmojiUsage
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.LayoutProvider
import com.offlinekeyboard.core.layout.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiUsageTest {

    @Test
    fun `most used come first and survive encoding`() {
        val usage = EmojiUsage()
        repeat(3) { usage.record("👍") }
        usage.record("😀")
        repeat(5) { usage.record("❤️") }
        assertEquals(listOf("❤️", "👍", "😀"), usage.top(10))
        val again = EmojiUsage.decode(usage.encode())
        assertEquals(listOf("❤️", "👍"), again.top(2))
        assertEquals(3, again.count("👍"))
        // Text smileys with "=" inside are kept whole.
        again.record("=)")
        assertEquals(1, EmojiUsage.decode(again.encode()).count("=)"))
        assertEquals(0, EmojiUsage.decode("garbage").top(5).size)
    }

    @Test
    fun `usage keeps a bounded number of items`() {
        val usage = EmojiUsage()
        repeat(EmojiUsage.MAX_ITEMS + 20) { usage.record("e$it") }
        assertTrue(usage.top(1000).size <= EmojiUsage.MAX_ITEMS)
    }

    @Test
    fun `frequent and own pages come before the built-in ones`() {
        val pages = EmojiSet.pagesWith(listOf("👍", "😀"), EmojiSet.parseOwn("🦄  🐙\n:-) 🦄"))
        assertEquals(EmojiSet.FREQUENT_TITLE, pages[0].title)
        assertEquals(listOf("👍", "😀"), pages[0].items)
        assertEquals(EmojiSet.OWN_TITLE, pages[1].title)
        assertEquals(listOf("🦄", "🐙", ":-)"), pages[1].items)
        assertEquals(4, pages[1].columns)
        assertEquals(EmojiSet.pages.size + 2, pages.size)
        assertEquals(EmojiSet.pages, EmojiSet.pagesWith(emptyList(), emptyList()))

        val c = KeyboardController(KeyboardMode.MOBILE, Language.UK, Language.entries)
        c.emojiPages = pages
        c.onKey(c.layout(false).allKeys().first { (it.action as? KeyAction.Special)?.key == SpecialKey.LAYER_EMOJI })
        val first = c.layout(false).allKeys().first { it.action is KeyAction.Text }
        assertEquals("👍", (first.action as KeyAction.Text).text)
    }

    @Test
    fun `apostrophe is on a long press of the period and the space bar is wide`() {
        for (language in Language.entries) {
            val layout = LayoutProvider.layout(KeyboardMode.MOBILE, language, Layer.LETTERS)
            val keys = layout.allKeys()
            assertFalse("$language has no apostrophe key", keys.any { (it.action as? KeyAction.Text)?.text == "'" })
            val period = keys.first { (it.action as? KeyAction.Text)?.text == "." }
            assertEquals("'", period.alternatives.first())
            val space = keys.first { (it.action as? KeyAction.Special)?.key == SpecialKey.SPACE }
            assertTrue(space.width >= 3f)
            assertEquals(layout.rows[0].sumOf { it.width.toDouble() }, layout.rows.last().sumOf { it.width.toDouble() }, 0.01)
        }
    }
}

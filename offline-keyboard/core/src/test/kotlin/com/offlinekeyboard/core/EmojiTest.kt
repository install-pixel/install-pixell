package com.offlinekeyboard.core

import com.offlinekeyboard.core.layout.EmojiSet
import com.offlinekeyboard.core.layout.KeyAction
import com.offlinekeyboard.core.layout.KeyboardMode
import com.offlinekeyboard.core.layout.Language
import com.offlinekeyboard.core.layout.Layer
import com.offlinekeyboard.core.layout.LayoutProvider
import com.offlinekeyboard.core.layout.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiTest {

    private fun special(key: SpecialKey) = { k: com.offlinekeyboard.core.layout.Key -> (k.action as? KeyAction.Special)?.key == key }

    @Test
    fun `every mobile letters layout opens emoji from a long press of the comma`() {
        for (language in Language.entries) {
            val layout = LayoutProvider.layout(KeyboardMode.MOBILE, language, Layer.LETTERS)
            val comma = layout.allKeys().first { (it.action as? KeyAction.Text)?.text == "," }
            assertEquals("$language", LayoutProvider.EMOJI_OPTION, comma.alternatives.first())
            // The space bar sits in the middle of the bottom row.
            val bottom = layout.rows.last()
            val space = bottom.indexOfFirst(special(SpecialKey.SPACE))
            val left = bottom.take(space).sumOf { it.width.toDouble() }
            val right = bottom.drop(space + 1).sumOf { it.width.toDouble() }
            assertEquals("$language", left, right, 0.01)
            // The Shift/Backspace row and the bottom row are as wide as the widest one.
            for (row in layout.rows.takeLast(2)) assertEquals("$language", layout.maxRowUnits.toDouble(), row.sumOf { it.width.toDouble() }, 0.01)
        }
        val c = KeyboardController(KeyboardMode.MOBILE, Language.UK, Language.entries)
        assertEquals(emptyList<Any>(), c.onAlternative(LayoutProvider.EMOJI_OPTION))
        assertEquals(Layer.EMOJI, c.layer)
    }

    @Test
    fun `emoji panel has a stickers key and its bottom row is as wide as the emoji rows`() {
        val layout = LayoutProvider.layout(KeyboardMode.MOBILE, Language.UK, Layer.EMOJI)
        assertTrue(layout.allKeys().any(special(SpecialKey.STICKERS)))
        assertEquals(8f, layout.rows.last().sumOf { it.width.toDouble() }.toFloat(), 0.01f)
        val c = KeyboardController(KeyboardMode.MOBILE, Language.UK, Language.entries)
        c.onAlternative(LayoutProvider.EMOJI_OPTION)
        val commands = c.onKey(c.layout(false).allKeys().first(special(SpecialKey.STICKERS)))
        assertEquals(listOf(com.offlinekeyboard.core.input.EditorCommand.OpenStickers), commands)
    }

    @Test
    fun `pages fit the panel and have no duplicates`() {
        val all = EmojiSet.pages.flatMap { it.items }
        assertEquals(all.size, all.toSet().size)
        for (page in EmojiSet.pages) {
            assertTrue(page.title, page.items.size <= 32)
            assertEquals(page.title, 0, page.items.size % page.columns)
        }
        assertTrue(all.size >= 200)
    }

    @Test
    fun `the comma's emoji option opens the panel, arrows turn pages, emoji are typed, ABC goes back`() {
        val c = KeyboardController(KeyboardMode.MOBILE, Language.UK, Language.entries)
        fun press(key: SpecialKey) = c.onKey(c.layout(false).allKeys().first(special(key)))
        c.onAlternative(LayoutProvider.EMOJI_OPTION)
        assertEquals(Layer.EMOJI, c.layer)
        val first = c.layout(false).allKeys().first { it.action is KeyAction.Text }
        assertEquals(EmojiSet.pages[0].items[0], (first.action as KeyAction.Text).text)
        press(SpecialKey.EMOJI_NEXT)
        assertEquals(1, c.emojiPage)
        press(SpecialKey.EMOJI_PREVIOUS)
        press(SpecialKey.EMOJI_PREVIOUS)
        assertEquals(EmojiSet.pages.size - 1, c.emojiPage)
        val typed = c.onKey(c.layout(false).allKeys().first { it.action is KeyAction.Text })
        assertTrue(typed.toString(), typed.toString().contains(":)"))
        assertEquals(Layer.EMOJI, c.layer)
        press(SpecialKey.LAYER_LETTERS)
        assertEquals(Layer.LETTERS, c.layer)
    }
}

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
    fun `every mobile letters layout has an emoji key`() {
        for (language in Language.entries) {
            val keys = LayoutProvider.layout(KeyboardMode.MOBILE, language, Layer.LETTERS).allKeys()
            assertTrue("$language", keys.any(special(SpecialKey.LAYER_EMOJI)))
        }
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
    fun `emoji key opens the panel, arrows turn pages, emoji are typed, ABC goes back`() {
        val c = KeyboardController(KeyboardMode.MOBILE, Language.UK, Language.entries)
        fun press(key: SpecialKey) = c.onKey(c.layout(false).allKeys().first(special(key)))
        press(SpecialKey.LAYER_EMOJI)
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

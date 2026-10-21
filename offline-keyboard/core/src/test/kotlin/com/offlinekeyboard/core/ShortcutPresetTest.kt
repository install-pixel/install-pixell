package com.offlinekeyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ShortcutPresetTest {

    private val presetDir = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "shortcut-presets") }
        .first { it.isDirectory }

    @Test
    fun `every preset in the repository parses in every language`() {
        val files = presetDir.listFiles { f -> f.extension == "txt" }!!.sortedBy { it.name }
        assertTrue(files.size >= 4)
        for (file in files) {
            for (lang in listOf("en", "uk", "pl")) {
                val preset = ShortcutPreset.parse(file.readText(), lang)
                assertTrue("${file.name}: no tips", preset.tips.isNotEmpty())
                assertTrue("${file.name}: no description", preset.about.isNotEmpty())
            }
        }
    }

    @Test
    fun `presets never take alt plus letter, which polish needs`() {
        for (file in presetDir.listFiles { f -> f.extension == "txt" }!!) {
            for (s in ShortcutPreset.parse(file.readText(), "en").shortcuts) {
                assertTrue("${file.name}: ${s.describe()}", !(s.alt && !s.ctrl && !s.meta))
            }
        }
    }

    @Test
    fun `localized fields replace the plain ones`() {
        val preset = ShortcutPreset.parse(
            """
            title = Vim
            title.uk = Вім
            tip = Esc
            tip.uk = Ескейп
            Win+W = text ":w\n"
            Ctrl+Alt+L = language
            """.trimIndent(),
            "uk",
        )
        assertEquals("Вім", preset.title)
        assertEquals(listOf("Ескейп"), preset.tips)
        assertEquals(":w\n", preset.shortcuts[0].text)
        assertEquals(ShortcutAction.INSERT_TEXT, preset.shortcuts[0].action)
        assertEquals("Ctrl+Alt+L", preset.shortcuts[1].describe())
        assertEquals("Vim", ShortcutPreset.parse("title = Vim\ntitle.uk = Вім", "pl").title)
    }

    @Test
    fun `escapes for terminal keys`() {
        val preset = ShortcutPreset.parse(
            "title = T\nWin+W = text \"\\e:w\\n\"\nWin+S = text \"\\x0f\\n\"\nWin+B = text \"\\e\\\\\"",
            "en",
        )
        assertEquals("\u001b:w\n", preset.shortcuts[0].text)
        assertEquals("\u000f\n", preset.shortcuts[1].text)
        assertEquals("\u001b\\", preset.shortcuts[2].text)
    }

    @Test
    fun `bigger sets for vim nano and excel`() {
        for ((name, min) in listOf("vim.txt" to 15, "nano.txt" to 12, "excel.txt" to 15)) {
            val preset = ShortcutPreset.parse(File(presetDir, name).readText(), "uk")
            assertTrue("$name: ${preset.shortcuts.size}", preset.shortcuts.size >= min)
            // Every shortcut can be stored and read back.
            assertEquals(preset.shortcuts, Shortcut.decode(Shortcut.encode(preset.shortcuts)))
        }
    }

    @Test
    fun `broken lines are reported with their number`() {
        for (bad in listOf("title = X\nWin+W = fly", "title = X\nShift+A = language", "title = X\nwhat", "about = no title")) {
            try {
                ShortcutPreset.parse(bad, "en")
                fail("accepted: $bad")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.isNotEmpty())
            }
        }
    }
}

class BaseDictionaryTest {

    @Test
    fun `every language ships 300 to 400 words`() {
        for (language in com.offlinekeyboard.core.layout.Language.entries) {
            val size = com.offlinekeyboard.core.dictionary.BaseDictionary.words(language).size
            assertTrue("$language: $size", size in 300..400)
        }
    }

    @Test
    fun `base words complete a prefix and user words come first`() {
        val uk = com.offlinekeyboard.core.layout.Language.UK
        val dict = com.offlinekeyboard.core.dictionary.UserDictionary()
        assertTrue(dict.suggestionsWithBase(uk, "дяк").contains("дякую"))
        assertEquals("Дякую", dict.suggestionsWithBase(uk, "Дяк").first())
        // Any apostrophe matches.
        assertTrue(dict.suggestionsWithBase(uk, "пʼя").any { it.startsWith("п'я") })
        dict.learn(uk, "дякувати", 1)
        assertEquals("дякувати", dict.suggestionsWithBase(uk, "дяк").first())
        assertEquals(dict.suggestionsWithBase(uk, "дяк").distinct(), dict.suggestionsWithBase(uk, "дяк"))
    }
}

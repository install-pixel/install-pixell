package com.blacklaunch.launcher.global

import com.blacklaunch.launcher.search.PreparedQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    private fun ids(q: String) = SettingsCatalog.search(PreparedQuery(q)).map { it.entryId }

    @Test fun ukrainianWords() {
        assertEquals("display", ids("дисплей").first())
        assertEquals("display", ids("яскравість").first())
        assertEquals("bluetooth", ids("блютуз").first())
        assertEquals("battery", ids("батарея").first())
    }

    @Test fun polishAndEnglish() {
        assertEquals("display", ids("jasność").first())
        assertEquals("wifi", ids("wifi").first())
        assertEquals("sound", ids("volume").first())
    }

    @Test fun prefixWhileTyping() = assertEquals("display", ids("диспл").first())

    @Test fun shortOrUnrelatedQueriesShowNothing() {
        assertEquals(emptyList<String>(), ids("ди"))
        assertEquals(emptyList<String>(), ids("telegram"))
    }

    @Test fun idsAreUnique() {
        val all = SettingsCatalog.entries.map { it.id }
        assertTrue(all.size == all.toSet().size)
    }
}

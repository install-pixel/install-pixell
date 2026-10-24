package com.blacklaunch.launcher.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinnedEditsTest {
    private val keys = listOf("a", "b", "c")

    @Test fun addAppendsOnce() {
        assertEquals(listOf("a", "b", "c", "d"), PinnedEdits.add(keys, "d"))
        assertEquals(keys, PinnedEdits.add(keys, "b"))
    }

    @Test fun removeDropsKey() = assertEquals(listOf("a", "c"), PinnedEdits.remove(keys, "b"))

    @Test fun moveUpAndDown() {
        assertEquals(listOf("b", "a", "c"), PinnedEdits.move(keys, "b", -1))
        assertEquals(listOf("a", "c", "b"), PinnedEdits.move(keys, "b", 1))
    }

    @Test fun moveAtEdgesKeepsOrder() {
        assertEquals(keys, PinnedEdits.move(keys, "a", -1))
        assertEquals(keys, PinnedEdits.move(keys, "c", 1))
        assertEquals(keys, PinnedEdits.move(keys, "missing", 1))
    }

    @Test fun neighbourWrapsAround() {
        val profiles = listOf(LauncherProfile("w", "W"), LauncherProfile("h", "H"), LauncherProfile("r", "R"))
        assertEquals("h", profiles.neighbour("w", 1)?.id)
        assertEquals("w", profiles.neighbour("r", 1)?.id)
        assertEquals("r", profiles.neighbour("w", -1)?.id)
        assertEquals("h", profiles.neighbour("unknown", 1)?.id)
        assertNull(emptyList<LauncherProfile>().neighbour("w", 1))
    }
}

package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.data.ListDrop
import org.junit.Assert.assertEquals
import org.junit.Test

class ListDropTest {
    @Test
    fun middleIsIntoEdgesAreBetween() {
        assertEquals(ListDrop.Kind.Before, ListDrop.decide(105f, 100f, 100f, canInto = true))
        assertEquals(ListDrop.Kind.Into, ListDrop.decide(150f, 100f, 100f, canInto = true))
        assertEquals(ListDrop.Kind.After, ListDrop.decide(195f, 100f, 100f, canInto = true))
        // У папці «в рядок» не можна — лише над чи під.
        assertEquals(ListDrop.Kind.Before, ListDrop.decide(140f, 100f, 100f, canInto = false))
        assertEquals(ListDrop.Kind.After, ListDrop.decide(160f, 100f, 100f, canInto = false))
    }

    @Test
    fun moveBeforeAndAfter() {
        val keys = listOf("f1", "c1", "c2", "c3")
        assertEquals(listOf("c3", "f1", "c1", "c2"), ListDrop.move(keys, "c3", "f1", after = false))
        assertEquals(listOf("c1", "f1", "c2", "c3"), ListDrop.move(keys, "f1", "c1", after = true))
        assertEquals(keys, ListDrop.move(keys, "c1", "c1", after = false))
        assertEquals(keys, ListDrop.move(keys, "x", "c1", after = false))
    }
}

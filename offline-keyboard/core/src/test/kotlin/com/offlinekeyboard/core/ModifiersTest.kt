package com.offlinekeyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifiersTest {

    @Test
    fun `single tap is one shot, double tap locks, tap unlocks`() {
        val m = Modifiers(doubleTapMillis = 400)
        m.tap(ModifierKey.SHIFT, 1000)
        assertEquals(LatchState.ONCE, m.state(ModifierKey.SHIFT))
        m.tap(ModifierKey.SHIFT, 1200)
        assertEquals(LatchState.LOCKED, m.state(ModifierKey.SHIFT))
        m.consumeOneShot()
        assertEquals(LatchState.LOCKED, m.state(ModifierKey.SHIFT))
        m.tap(ModifierKey.SHIFT, 3000)
        assertEquals(LatchState.OFF, m.state(ModifierKey.SHIFT))
    }

    @Test
    fun `slow second tap cancels instead of locking`() {
        val m = Modifiers(doubleTapMillis = 400)
        m.tap(ModifierKey.CTRL, 1000)
        m.tap(ModifierKey.CTRL, 2000)
        assertEquals(LatchState.OFF, m.state(ModifierKey.CTRL))
    }

    @Test
    fun `auto shift never overrides a user choice`() {
        val m = Modifiers()
        m.setAutoShift(true)
        assertTrue(m.shiftIsAutomatic)
        m.setAutoShift(false)
        assertEquals(LatchState.OFF, m.state(ModifierKey.SHIFT))

        m.tap(ModifierKey.SHIFT, 0)
        m.setAutoShift(false)
        assertEquals(LatchState.ONCE, m.state(ModifierKey.SHIFT))
        assertFalse(m.shiftIsAutomatic)
    }

    @Test
    fun `release all keeps caps lock`() {
        val m = Modifiers()
        m.toggleCapsLock()
        m.tap(ModifierKey.ALT, 0)
        assertTrue(m.releaseAll())
        assertTrue(m.capsLock)
        assertFalse(m.releaseAll())
    }
}

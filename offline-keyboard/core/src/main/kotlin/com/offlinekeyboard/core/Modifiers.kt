package com.offlinekeyboard.core

import java.util.EnumMap

enum class ModifierKey { SHIFT, CTRL, ALT, META }

/** OFF → tap → ONCE (applies to the next key) → quick second tap → LOCKED → tap → OFF. */
enum class LatchState { OFF, ONCE, LOCKED }

class Modifiers(private val doubleTapMillis: Long = 400L) {
    private val states = EnumMap<ModifierKey, LatchState>(ModifierKey::class.java).apply {
        ModifierKey.entries.forEach { put(it, LatchState.OFF) }
    }
    private val lastTap = EnumMap<ModifierKey, Long>(ModifierKey::class.java)

    var capsLock: Boolean = false
        private set

    /** True while Shift is ONCE only because of sentence auto-capitalisation. */
    var shiftIsAutomatic: Boolean = false
        private set

    fun state(key: ModifierKey): LatchState = states.getValue(key)

    fun isActive(key: ModifierKey): Boolean = state(key) != LatchState.OFF

    val anyShortcutModifier: Boolean
        get() = isActive(ModifierKey.CTRL) || isActive(ModifierKey.ALT) || isActive(ModifierKey.META)

    fun tap(key: ModifierKey, nowMillis: Long) {
        val previousTap = lastTap[key]
        val next = when (state(key)) {
            LatchState.OFF -> LatchState.ONCE
            LatchState.ONCE ->
                if (previousTap != null && nowMillis - previousTap <= doubleTapMillis) LatchState.LOCKED else LatchState.OFF
            LatchState.LOCKED -> LatchState.OFF
        }
        states[key] = next
        lastTap[key] = nowMillis
        if (key == ModifierKey.SHIFT) shiftIsAutomatic = false
    }

    fun toggleCapsLock() {
        capsLock = !capsLock
    }

    /** Sets or clears automatic sentence-start Shift without touching a Shift the user chose. */
    fun setAutoShift(enabled: Boolean) {
        val shift = state(ModifierKey.SHIFT)
        if (enabled) {
            if (shift == LatchState.OFF && !capsLock) {
                states[ModifierKey.SHIFT] = LatchState.ONCE
                shiftIsAutomatic = true
            }
        } else if (shiftIsAutomatic && shift == LatchState.ONCE) {
            states[ModifierKey.SHIFT] = LatchState.OFF
            shiftIsAutomatic = false
        }
    }

    /** Called after a non-modifier key: one-shot modifiers are used up, locked ones stay. */
    fun consumeOneShot(): Boolean {
        var changed = false
        for (key in ModifierKey.entries) {
            if (states[key] == LatchState.ONCE) {
                states[key] = LatchState.OFF
                changed = true
            }
        }
        shiftIsAutomatic = false
        return changed
    }

    /** Releases every modifier (one-shot and locked). Caps Lock is kept. Returns true if anything was active. */
    fun releaseAll(): Boolean {
        val wasActive = ModifierKey.entries.any { isActive(it) }
        ModifierKey.entries.forEach { states[it] = LatchState.OFF }
        shiftIsAutomatic = false
        return wasActive
    }
}

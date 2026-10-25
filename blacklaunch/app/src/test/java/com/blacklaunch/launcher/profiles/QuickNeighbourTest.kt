package com.blacklaunch.launcher.profiles

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickNeighbourTest {
    private val all = listOf("home", "rest", "work", "sport", "road").map { LauncherProfile(it, it) }
    private val quick = setOf("home", "work", "road")

    private fun next(from: String?, delta: Int = 1, chosen: Set<String> = quick) =
        all.quickNeighbour(from, delta) { it in chosen }?.id

    @Test fun cyclesOnlyChosen() {
        assertEquals("work", next("home"))
        assertEquals("road", next("work"))
        assertEquals("home", next("road"))
        assertEquals("road", next("home", -1))
    }

    @Test fun fromNotChosenGoesToNearestChosen() {
        assertEquals("work", next("rest"))
        assertEquals("home", next("rest", -1))
        assertEquals("road", next("sport"))
    }

    @Test fun noneChosenMeansAll() {
        assertEquals("rest", next("home", chosen = emptySet()))
    }
}

package com.blacklaunch.launcher.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRulesTest {
    private val start = 1_000_000L
    private val session = FocusSession("focus", "home", start, start + 30 * 60_000L, strict = false)

    @Test fun noSessionNoDelaySwitchesFreely() {
        assertEquals(FocusGate.Allowed, FocusRules.switchGate("home", "work", null, 0, start))
        assertEquals(FocusGate.Allowed, FocusRules.settingsGate(null, 0, start))
    }

    @Test fun exitDelayAsksForCountdownToLeaveAndForSettings() {
        assertEquals(FocusGate.Delay(30), FocusRules.switchGate("focus", "home", null, 30, start))
        assertEquals(FocusGate.Delay(30), FocusRules.settingsGate(null, 30, start))
        // Той самий режим — нічого не перемикається, відліку не треба.
        assertEquals(FocusGate.Allowed, FocusRules.switchGate("focus", "focus", null, 30, start))
    }

    @Test fun runningTimerLocksEverySwitchAwayAndSettings() {
        val now = start + 10 * 60_000L
        assertEquals(FocusGate.Locked(20 * 60_000L), FocusRules.switchGate("focus", "home", session, 0, now))
        assertEquals(FocusGate.Locked(20 * 60_000L), FocusRules.switchGate("focus", "home", session, 60, now))
        assertEquals(FocusGate.Locked(20 * 60_000L), FocusRules.settingsGate(session, 0, now))
        // Повернутися в режим таймера можна завжди.
        assertEquals(FocusGate.Allowed, FocusRules.switchGate("home", "focus", session, 0, now))
    }

    @Test fun finishedTimerNoLongerLocks() {
        val after = session.endsAt
        assertFalse(FocusRules.running(session, after))
        assertEquals(FocusGate.Allowed, FocusRules.switchGate("focus", "home", session, 0, after))
        assertEquals(FocusGate.Delay(10), FocusRules.switchGate("focus", "home", session, 10, after))
    }

    @Test fun automationsIgnoreExitDelayButNotTimer() {
        assertTrue(FocusRules.automationMaySwitch("focus", "home", null, start))
        assertFalse(FocusRules.automationMaySwitch("focus", "home", session, start + 1))
        assertTrue(FocusRules.automationMaySwitch("home", "focus", session, start + 1))
        assertTrue(FocusRules.automationMaySwitch("focus", "home", session, session.endsAt))
    }

    @Test fun minutesRoundUpAndClockFormats() {
        assertEquals(1, FocusRules.minutesLeft(1))
        assertEquals(23, FocusRules.minutesLeft(22 * 60_000L + 1))
        assertEquals(0, FocusRules.minutesLeft(-5))
        assertEquals("23:41", FocusRules.clock(23 * 60_000L + 41_000L))
        assertEquals("1:05:00", FocusRules.clock(65 * 60_000L))
        assertEquals("00:00", FocusRules.clock(-1))
    }

    @Test fun summaryCountsEarlyEnd() {
        val done = FocusRules.summary(session.copy(attempts = 2), "Focus", session.endsAt)
        assertEquals(FocusSummary("Focus", 30, 30, 2, early = false), done)
        val early = FocusRules.summary(session, "Focus", start + 12 * 60_000L + 30_000L)
        assertEquals(12, early.minutes)
        assertTrue(early.early)
    }

    @Test fun sessionRoundTrips() {
        val strict = session.copy(strict = true, attempts = 3)
        assertEquals(strict, FocusSession.decode(strict.encode()))
        val noPrevious = session.copy(previousId = null)
        assertEquals(noPrevious, FocusSession.decode(noPrevious.encode()))
        assertNull(FocusSession.decode("broken"))
        assertNull(FocusSession.decode(null))
    }
}

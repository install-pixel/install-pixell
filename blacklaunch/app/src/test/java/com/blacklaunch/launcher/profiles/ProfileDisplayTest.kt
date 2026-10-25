package com.blacklaunch.launcher.profiles

import com.blacklaunch.launcher.system.GlanceParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileDisplayTest {
    @Test fun encodeDecodeRoundTrip() {
        val quiet = ProfileDisplay(glance = false, profileLabel = false, hideStatusBar = true, suggestions = false, recentQueries = false, allAppsButton = false)
        assertEquals(quiet, ProfileDisplay.decode(quiet.encode()))
        assertEquals(ProfileDisplay(), ProfileDisplay.decode(ProfileDisplay().encode()))
        val hidden = ProfileDisplay(quickSwitch = false, showApps = false)
        assertEquals(hidden, ProfileDisplay.decode(hidden.encode()))
        // Збережене старими версіями (6 і 7 значень) — нові налаштування за замовчуванням.
        assertEquals(ProfileDisplay(glance = true), ProfileDisplay.decode("1,1,1,1,1,1"))
        assertEquals(ProfileDisplay(quickSwitch = false), ProfileDisplay.decode("0,1,1,1,1,1,0"))
        val ownGlance = ProfileDisplay(glance = true, ownGlance = GlanceParts(clock = true, alarm = false, event = false, battery = true))
        assertEquals(ownGlance, ProfileDisplay.decode(ownGlance.encode()))
        assertNull(ProfileDisplay.decode("1,0"))
        assertNull(ProfileDisplay.decode(null))
    }
}

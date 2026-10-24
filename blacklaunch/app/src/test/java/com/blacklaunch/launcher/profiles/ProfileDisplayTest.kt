package com.blacklaunch.launcher.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileDisplayTest {
    @Test fun encodeDecodeRoundTrip() {
        val quiet = ProfileDisplay(glance = false, profileLabel = false, hideStatusBar = true, suggestions = false, recentQueries = false, allAppsButton = false)
        assertEquals(quiet, ProfileDisplay.decode(quiet.encode()))
        assertEquals(ProfileDisplay(), ProfileDisplay.decode(ProfileDisplay().encode()))
        assertNull(ProfileDisplay.decode("1,0"))
        assertNull(ProfileDisplay.decode(null))
    }
}

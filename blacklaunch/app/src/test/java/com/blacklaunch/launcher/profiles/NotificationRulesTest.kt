package com.blacklaunch.launcher.profiles

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {
    private val allowed = setOf("pl.sklado.app")
    private fun hide(pkg: String, category: String? = null, ongoing: Boolean = false) =
        NotificationRules.hide(pkg, "com.blacklaunch.launcher", allowed, category, ongoing)

    @Test fun appsOutsideTheProfileAreHidden() {
        assertTrue(hide("org.telegram.messenger"))
        assertFalse(hide("pl.sklado.app"))
    }

    @Test fun callsAlarmsSystemAndOngoingAreNeverHidden() {
        assertFalse(hide("com.google.android.dialer", Notification.CATEGORY_CALL))
        assertFalse(hide("com.google.android.deskclock", Notification.CATEGORY_ALARM))
        assertFalse(hide("com.android.systemui"))
        assertFalse(hide("com.spotify.music", ongoing = true))
        assertFalse(hide("com.blacklaunch.launcher"))
    }
}

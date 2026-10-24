package com.blacklaunch.launcher.profiles

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileModesTest {
    private val work = LauncherProfile("work", "Робота", soundMode = SoundMode.VIBRATE)
    private val rest = LauncherProfile("rest", "Відпочинок", soundMode = SoundMode.SILENT, notificationMode = NotificationMode.DO_NOT_DISTURB)
    private val home = LauncherProfile("home", "Дім", soundMode = SoundMode.NORMAL)

    @Test fun vibrateWorksWithoutAccessAndLeavesDndAlone() {
        assertEquals(ProfileModes.Plan(dnd = null, ringer = SoundMode.VIBRATE, needsAccess = false), ProfileModes.plan(work, hasAccess = false, enabledByUs = false))
    }

    @Test fun silentAndDndNeedAccess() {
        assertEquals(ProfileModes.Plan(dnd = null, ringer = null, needsAccess = true), ProfileModes.plan(rest, hasAccess = false, enabledByUs = false))
        assertEquals(ProfileModes.Plan(dnd = true, ringer = SoundMode.SILENT, needsAccess = false), ProfileModes.plan(rest, hasAccess = true, enabledByUs = false))
    }

    @Test fun dndIsTurnedOffOnlyIfWeTurnedItOn() {
        assertEquals(false, ProfileModes.plan(home, hasAccess = true, enabledByUs = true).dnd)
        assertEquals(null, ProfileModes.plan(home, hasAccess = true, enabledByUs = false).dnd)
    }
}

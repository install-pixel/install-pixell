package com.blacklaunch.launcher.profiles

import com.blacklaunch.launcher.global.GroupKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalAccessTest {
    private val all = GroupKind.entries.toSet()
    private val roles = RolePackages(
        dialer = setOf("com.google.android.dialer"),
        contacts = setOf("com.google.android.contacts"),
        sms = setOf("com.google.android.apps.messaging"),
        calendar = setOf("com.google.android.calendar"),
        settings = setOf("com.android.settings"),
    )

    @Test fun byAppsFollowsTheProfile() {
        val work = GlobalAccess.of(GlobalMode.BY_APPS, all, setOf("pl.sklado.app", "com.google.android.dialer"), roles)
        assertTrue(work.canCall)
        assertFalse(work.canMessage)
        assertEquals(setOf(GroupKind.CALCULATOR, GroupKind.PROFILES, GroupKind.SHORTCUTS, GroupKind.CONTACTS), work.kinds)
    }

    @Test fun messagesAppAddsSmsAndMessageButton() {
        val access = GlobalAccess.of(GlobalMode.BY_APPS, all, setOf("com.google.android.apps.messaging"), roles)
        assertTrue(access.canMessage)
        assertTrue(GroupKind.SMS in access.kinds && GroupKind.CONTACTS in access.kinds)
    }

    @Test fun contactsAppGivesContactSearchWithCall() {
        val access = GlobalAccess.of(GlobalMode.BY_APPS, all, setOf("com.google.android.contacts"), roles)
        assertTrue(access.canCall)
        assertTrue(GroupKind.CONTACTS in access.kinds)
        assertFalse(GroupKind.SMS in access.kinds)
    }

    @Test fun offAndOn() {
        assertEquals(setOf(GroupKind.CALCULATOR, GroupKind.PROFILES), GlobalAccess.of(GlobalMode.OFF, all, emptySet(), roles).kinds)
        assertEquals(all, GlobalAccess.of(GlobalMode.ON, all, emptySet(), roles).kinds)
    }
}

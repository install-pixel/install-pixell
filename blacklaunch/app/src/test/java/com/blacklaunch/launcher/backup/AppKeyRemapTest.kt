package com.blacklaunch.launcher.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class AppKeyRemapTest {
    @Test fun sameAppWithAnotherUserNumberIsFoundMissingIsSkipped() {
        val remap = AppKeyRemap(listOf("org.telegram.messenger/Main/0", "pl.sklado.app/Main/0"))
        assertEquals(
            listOf("org.telegram.messenger/Main/0", "pl.sklado.app/Main/0"),
            remap.mapAll(listOf("org.telegram.messenger/Main/0", "pl.sklado.app/Main/10", "com.gone/Main/0")),
        )
        assertEquals(1, remap.skipped)
    }

    @Test fun missingAppsAreKeptForLater() {
        val remap = AppKeyRemap(listOf("pl.sklado.app/Main/0"))
        assertEquals(listOf("pl.sklado.app/Main/0", "com.later/Main/0"), remap.mapAllOrKeep(listOf("pl.sklado.app/Main/10", "com.later/Main/0", "com.later/Main/0")))
        assertEquals(1, remap.waiting)
    }
}

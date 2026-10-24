package com.blacklaunch.launcher.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun newestEntryFirstAndOnlyLastThreeKept() {
        var text = ""
        (1..5).forEach { text = CrashEntries.append(text, "entry $it") }
        assertEquals(listOf("entry 5", "entry 4", "entry 3"), text.split(CrashEntries.SEPARATOR))
        assertEquals("entry 5", CrashEntries.latest(text))
    }

    @Test fun formatHasHeaderThreadAndTrimmedStack() {
        val entry = CrashEntries.format("BlackLaunch 1.0", "main", "x".repeat(CrashEntries.MAX_STACK_CHARS + 100))
        assertTrue(entry.startsWith("BlackLaunch 1.0\nThread: main\n\n"))
        assertTrue(entry.length < CrashEntries.MAX_STACK_CHARS + 100)
    }

    @Test fun recordSurvivesRestartAndClearRemovesIt() {
        val file = folder.newFile("crashes.txt").apply { delete() }
        val log = CrashLog(file) { "BlackLaunch test" }
        assertNull(log.text.value)
        log.record("main", IllegalStateException("boom"))
        assertTrue(log.text.value!!.contains("IllegalStateException: boom"))
        assertTrue(CrashLog(file) { "" }.text.value!!.contains("boom"))
        log.clear()
        assertNull(log.text.value)
        assertNull(CrashLog(file) { "" }.text.value)
    }
}

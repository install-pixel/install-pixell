package com.blacklaunch.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchHistoryTest {
    private val app = FakeAppSource.app("Telegram")

    @Test fun keepsOnlyLatestRecords() {
        val history = InMemoryLaunchHistory(maxRecords = 3)
        (1L..5L).forEach { history.record(app, it) }
        assertEquals(listOf(3L, 4L, 5L), history.records.value.map { it.timeMillis })
    }

    @Test fun clearRemovesAll() {
        val history = InMemoryLaunchHistory()
        history.record(app, 1)
        history.clear()
        assertEquals(emptyList<LaunchRecord>(), history.records.value)
    }

    @Test fun encodeDecodeRoundTrip() {
        val records = listOf(LaunchRecord("a.b/Main/0", 10), LaunchRecord("c.d/.Act/10", 20))
        assertEquals(records, PrefsLaunchHistory.decode(PrefsLaunchHistory.encode(records)))
    }

    @Test fun decodeSkipsBrokenLines() {
        assertEquals(
            listOf(LaunchRecord("a.b/Main/0", 5)),
            PrefsLaunchHistory.decode("garbage\n5|a.b/Main/0\n|\nx|y"),
        )
        assertEquals(emptyList<LaunchRecord>(), PrefsLaunchHistory.decode(null))
    }
}

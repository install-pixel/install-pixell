package com.blacklaunch.launcher.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncompletePhraseTest {
    @Test fun onlyTheStartOfACommand() {
        assertTrue(IncompletePhrase.isIncomplete("відкрий"))
        assertTrue(IncompletePhrase.isIncomplete("Otwórz"))
        assertTrue(IncompletePhrase.isIncomplete("маршрут до"))
        assertTrue(IncompletePhrase.isIncomplete("режим"))
    }

    @Test fun completeOrEmpty() {
        assertFalse(IncompletePhrase.isIncomplete("відкрий телеграм"))
        assertFalse(IncompletePhrase.isIncomplete("ліхтарик"))
        assertFalse(IncompletePhrase.isIncomplete(""))
    }
}

package com.blacklaunch.launcher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionSuggestTest {
    @Test fun fullTextCommandIsSuggested() {
        assertEquals(listOf(VoiceAction.Panel(SystemPanel.WIFI)), VoiceActionParser.suggest("wifi"))
        assertEquals(listOf(VoiceAction.Timer(600)), VoiceActionParser.suggest("таймер 10 хвилин"))
    }

    @Test fun startOfSimpleCommandIsSuggested() {
        assertEquals(listOf(VoiceAction.Flashlight(null)), VoiceActionParser.suggest("ліхт"))
        assertEquals(listOf(VoiceAction.Volume(VolumeChange.UP)), VoiceActionParser.suggest("гучн"))
    }

    @Test fun shortOrUnrelatedTextGivesNothing() {
        assertTrue(VoiceActionParser.suggest("лі").isEmpty())
        assertTrue(VoiceActionParser.suggest("телеграм").isEmpty())
        assertTrue(VoiceActionParser.suggest("").isEmpty())
    }
}

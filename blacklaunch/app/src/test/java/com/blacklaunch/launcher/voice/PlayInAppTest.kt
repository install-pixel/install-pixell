package com.blacklaunch.launcher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayInAppTest {
    @Test fun appNameInThreeLanguages() {
        assertEquals("Symfonium", PlayInApp.parse("увімкни музику на Symfonium"))
        assertEquals("Spotify", PlayInApp.parse("грай у Spotify"))
        assertEquals("YouTube Music", PlayInApp.parse("play music on YouTube Music"))
        assertEquals("Spotify", PlayInApp.parse("włącz muzykę w Spotify"))
    }

    @Test fun otherPhrasesAreNotIt() {
        assertNull(PlayInApp.parse("увімкни музику"))
        assertNull(PlayInApp.parse("відкрий Symfonium"))
        assertNull(PlayInApp.parse("грай музику"))
        assertNull(PlayInApp.parse("увімкни ліхтарик"))
    }
}

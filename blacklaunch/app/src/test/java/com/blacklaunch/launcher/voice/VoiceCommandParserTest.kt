package com.blacklaunch.launcher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {
    private fun parse(text: String) = VoiceCommandParser.parse(text)

    @Test fun plainNameIsOpen() = assertEquals(VoiceCommand.Open("ютуб"), parse("Ютуб"))

    @Test fun ukrainianOpenVerb() = assertEquals(VoiceCommand.Open("телеграм"), parse("Відкрий телеграм"))

    @Test fun launchVerbWithPoliteness() =
        assertEquals(VoiceCommand.Open("калькулятор"), parse("запусти калькулятор будь ласка"))

    @Test fun englishOpenVerb() = assertEquals(VoiceCommand.Open("youtube"), parse("Open YouTube"))

    @Test fun appNounIsDropped() =
        assertEquals(VoiceCommand.Open("монобанк"), parse("відкрий додаток монобанк"))

    @Test fun openLastUkrainian() {
        assertEquals(VoiceCommand.OpenLast, parse("відкрий останній"))
        assertEquals(VoiceCommand.OpenLast, parse("Відкрий останню програму"))
        assertEquals(VoiceCommand.OpenLast, parse("попередній додаток"))
    }

    @Test fun openLastEnglish() {
        assertEquals(VoiceCommand.OpenLast, parse("open last app"))
        assertEquals(VoiceCommand.OpenLast, parse("previous"))
    }

    @Test fun lastWordInsideAppNameIsNotCommand() =
        assertEquals(VoiceCommand.Open("last pass"), parse("open last pass"))

    @Test fun profileCommand() {
        assertEquals(VoiceCommand.SwitchProfile("робота"), parse("Режим робота"))
        assertEquals(VoiceCommand.SwitchProfile("home"), parse("profile home"))
    }

    @Test fun verbAloneIsNothing() = assertNull(parse("відкрий"))

    @Test fun blankIsNothing() = assertNull(parse("   "))

    @Test fun appNounAloneStaysQuery() = assertEquals(VoiceCommand.Open("додаток"), parse("відкрий додаток"))

    @Test fun polishOpenVerb() {
        assertEquals(VoiceCommand.Open("telegram"), parse("Otwórz Telegram"))
        assertEquals(VoiceCommand.Open("kalkulator"), parse("uruchom aplikację kalkulator proszę"))
    }

    @Test fun polishOpenLast() {
        assertEquals(VoiceCommand.OpenLast, parse("otwórz ostatnią aplikację"))
        assertEquals(VoiceCommand.OpenLast, parse("poprzednia"))
    }

    @Test fun polishProfile() = assertEquals(VoiceCommand.SwitchProfile("praca"), parse("tryb praca"))
}

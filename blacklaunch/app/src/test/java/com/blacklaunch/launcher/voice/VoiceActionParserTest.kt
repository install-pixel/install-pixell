package com.blacklaunch.launcher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceActionParserTest {
    private fun action(text: String): VoiceAction? = (VoiceCommandParser.parse(text) as? VoiceCommand.Action)?.action

    @Test fun alarmWithDigitsAndWordsInThreeLanguages() {
        assertEquals(VoiceAction.Alarm(7, 30), action("Будильник на 7:30"))
        assertEquals(VoiceAction.Alarm(7, 30), action("постав будильник на сім тридцять"))
        assertEquals(VoiceAction.Alarm(19, 0), action("будильник на 7 вечора"))
        assertEquals(VoiceAction.Alarm(6, 45), action("budzik na 6:45"))
        assertEquals(VoiceAction.Alarm(18, 15), action("set an alarm for 6 15 pm"))
        assertEquals(VoiceAction.Alarm(7, 5), action("alarm 705"))
    }

    @Test fun alarmWithoutTimeOpensClockInstead() {
        assertNull(action("будильник"))
    }

    @Test fun timerDurations() {
        assertEquals(VoiceAction.Timer(600), action("таймер 10 хвилин"))
        assertEquals(VoiceAction.Timer(30), action("таймер на 30 секунд"))
        assertEquals(VoiceAction.Timer(60), action("таймер на хвилину"))
        assertEquals(VoiceAction.Timer(1800), action("таймер на півгодини"))
        assertEquals(VoiceAction.Timer(5400), action("timer 1 hour 30 minutes"))
        assertEquals(VoiceAction.Timer(300), action("minutnik pięć minut"))
        assertEquals(VoiceAction.Timer(1500), action("таймер двадцять п'ять хвилин"))
        assertEquals(VoiceAction.Timer(600), action("таймер 10"))
        assertNull(action("таймер"))
    }

    @Test fun flashlight() {
        assertEquals(VoiceAction.Flashlight(null), action("ліхтарик"))
        assertEquals(VoiceAction.Flashlight(true), action("увімкни ліхтарик"))
        assertEquals(VoiceAction.Flashlight(false), action("вимкни ліхтарик"))
        assertEquals(VoiceAction.Flashlight(false), action("wyłącz latarkę"))
        assertEquals(VoiceAction.Flashlight(true), action("flashlight on"))
    }

    @Test fun mediaAndVolume() {
        assertEquals(VoiceAction.Media(MediaCommand.PAUSE), action("пауза"))
        assertEquals(VoiceAction.Media(MediaCommand.NEXT), action("наступна пісня"))
        assertEquals(VoiceAction.Media(MediaCommand.PREVIOUS), action("previous song"))
        assertEquals(VoiceAction.Media(MediaCommand.PLAY), action("graj"))
        assertEquals(VoiceAction.Volume(VolumeChange.UP), action("гучніше"))
        assertEquals(VoiceAction.Volume(VolumeChange.DOWN), action("ciszej"))
        assertEquals(VoiceAction.Volume(VolumeChange.MUTE), action("mute"))
    }

    @Test fun previousAloneIsStillTheLastApp() {
        assertEquals(VoiceCommand.OpenLast, VoiceCommandParser.parse("попередній додаток"))
    }

    @Test fun panels() {
        assertEquals(VoiceAction.Panel(SystemPanel.WIFI), action("вай-фай"))
        assertEquals(VoiceAction.Panel(SystemPanel.WIFI), action("увімкни wi-fi"))
        assertEquals(VoiceAction.Panel(SystemPanel.BLUETOOTH), action("bluetooth"))
        assertEquals(VoiceAction.Panel(SystemPanel.INTERNET), action("мобільний інтернет"))
    }

    @Test fun navigationKeepsOriginalPlace() {
        assertEquals(VoiceAction.Navigate("Хрещатик 22"), action("маршрут до Хрещатик 22"))
        assertEquals(VoiceAction.Navigate("Warszawa Centralna"), action("nawiguj do Warszawa Centralna"))
        assertEquals(VoiceAction.Navigate("the airport"), action("navigate to the airport"))
    }

    @Test fun callContact() {
        assertEquals(VoiceAction.Call("марії"), action("Подзвони Марії"))
        assertEquals(VoiceAction.Call("мами"), action("зателефонуй до мами"))
        assertEquals(VoiceAction.Call("mom"), action("call mom"))
        assertEquals(VoiceAction.Call("mamy"), action("zadzwoń do mamy"))
        assertNull(action("подзвони"))
    }

    @Test fun smsWithRecipientAndOriginalText() {
        assertEquals(VoiceAction.Sms("марії", "Буду пізніше!"), action("Напиши SMS Марії, що Буду пізніше!"))
        assertEquals(VoiceAction.Sms("марії", "буду о 7"), action("напиши смс Марії буду о 7"))
        assertEquals(VoiceAction.Sms("mom", "I'm on my way"), action("send a text to mom saying I'm on my way".replace(" a ", " ")))
        assertEquals(VoiceAction.Sms("mamy", ""), action("napisz sms do mamy"))
    }

    @Test fun timeQuestion() {
        assertEquals(VoiceAction.Time, action("Котра година?"))
        assertEquals(VoiceAction.Time, action("która godzina"))
    }

    @Test fun appNamesStayAppCommands() {
        assertEquals(VoiceCommand.Open("телеграм"), VoiceCommandParser.parse("відкрий телеграм"))
        assertEquals(VoiceCommand.Open("камера"), VoiceCommandParser.parse("камера"))
    }
}

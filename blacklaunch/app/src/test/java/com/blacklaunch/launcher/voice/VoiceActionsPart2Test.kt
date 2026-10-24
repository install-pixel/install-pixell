package com.blacklaunch.launcher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceActionsPart2Test {
    private fun action(text: String): VoiceAction? = (VoiceCommandParser.parse(text) as? VoiceCommand.Action)?.action

    @Test fun brightness() {
        assertEquals(VoiceAction.BrightnessLevel(50), action("яскравість 50"))
        assertEquals(VoiceAction.BrightnessLevel(30), action("яскравість на тридцять відсотків"))
        assertEquals(VoiceAction.BrightnessLevel(100), action("brightness max"))
        assertEquals(VoiceAction.BrightnessLevel(0), action("jasność minimum"))
        assertEquals(VoiceAction.BrightnessStep(up = true), action("яскравіше"))
        assertEquals(VoiceAction.BrightnessStep(up = false), action("ciemniej"))
        assertEquals(VoiceAction.BrightnessStep(up = false), action("зменш яскравість"))
        assertNull(action("яскравість"))
    }

    @Test fun whatsApp() {
        assertEquals(VoiceAction.WhatsApp("мамі", "Я вже їду"), action("напиши в ватсап мамі, що Я вже їду"))
        assertEquals(VoiceAction.WhatsApp("john", "see you soon"), action("whats app John see you soon".lowercase()))
        assertEquals(VoiceAction.WhatsApp("mamy", ""), action("napisz na whatsapp do mamy"))
        assertNull(action("ватсап"))
    }

    @Test fun calendarEvent() {
        assertEquals(VoiceAction.CalendarEvent("Зустріч з лікарем", 1, 15, 0), action("подія завтра о 15 зустріч з лікарем"))
        assertEquals(VoiceAction.CalendarEvent("Стоматолог", 0, 9, 30), action("додай в календар стоматолог о 9:30"))
        assertEquals(VoiceAction.CalendarEvent("Dentist", 2, 10, 0), action("add event dentist day after tomorrow at 10"))
        assertEquals(VoiceAction.CalendarEvent("Urodziny mamy", 1, null, 0), action("dodaj wydarzenie jutro urodziny mamy"))
    }

    @Test fun calendarAppNameStaysAppCommand() {
        assertNull(action("календар"))
        assertEquals(VoiceCommand.Open("календар", explicit = true), VoiceCommandParser.parse("відкрий календар"))
    }
}

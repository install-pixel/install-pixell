package com.blacklaunch.launcher.automation

import com.blacklaunch.launcher.voice.VoiceCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationsTest {
    @Test fun bluetoothMatchesOnlyChosenDevice() {
        val a = Automation.create(TriggerKind.BLUETOOTH_CONNECTED, "cmd", "AA:BB:CC:DD:EE:FF", "Навушники")
        assertTrue(a.matches(TriggerKind.BLUETOOTH_CONNECTED, "aa:bb:cc:dd:ee:ff"))
        assertFalse(a.matches(TriggerKind.BLUETOOTH_CONNECTED, "11:22:33:44:55:66"))
        assertFalse(a.matches(TriggerKind.BLUETOOTH_DISCONNECTED, "AA:BB:CC:DD:EE:FF"))
        assertFalse(a.copy(enabled = false).matches(TriggerKind.BLUETOOTH_CONNECTED, "AA:BB:CC:DD:EE:FF"))
        assertTrue(Automation.create(TriggerKind.POWER_CONNECTED, "cmd", null, null).matches(TriggerKind.POWER_CONNECTED, null))
    }

    @Test fun encodeDecodeRoundTrip() {
        val list = listOf(
            Automation.create(TriggerKind.BLUETOOTH_CONNECTED, "c1", "AA:BB", "Авто\tмагнітола"),
            Automation.create(TriggerKind.POWER_DISCONNECTED, "c2", null, null).copy(enabled = false),
        )
        val back = InMemoryAutomationStore.decode(InMemoryAutomationStore.encode(list))
        assertEquals(list.map { it.copy(deviceName = it.deviceName?.replace('\t', ' ')) }, back)
    }

    @Test fun hiddenStepsAreOnlySafeOnes() {
        assertTrue(BackgroundSteps.canRunHidden(VoiceCommandParser.parse("грай музику")))
        assertTrue(BackgroundSteps.canRunHidden(VoiceCommandParser.parse("яскравість 30")))
        assertTrue(BackgroundSteps.canRunHidden(VoiceCommandParser.parse("режим дорога")))
        assertFalse(BackgroundSteps.canRunHidden(VoiceCommandParser.parse("відкрий музику")))
        assertFalse(BackgroundSteps.canRunHidden(VoiceCommandParser.parse("вайфай")))
    }
}

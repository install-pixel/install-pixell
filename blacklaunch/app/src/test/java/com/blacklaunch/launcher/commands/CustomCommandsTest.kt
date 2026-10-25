package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class CustomCommandsTest {
    private val home = CustomCommand("1", "Я вдома", listOf("вайфай", "яскравість 30", "режим дім"))
    private val sleep = CustomCommand("2", "На ніч", listOf("яскравість 0", "без звуку"))

    @Test fun exactMatchIgnoresCaseAndPunctuation() {
        assertEquals(home, CustomCommandMatcher.exact(listOf(home, sleep), "я вдома!"))
        assertNull(CustomCommandMatcher.exact(listOf(home, sleep), "я вдома зараз"))
    }

    @Test fun suggestByStartOfPhrase() {
        assertEquals(listOf(home), CustomCommandMatcher.suggest(listOf(home, sleep), "я вд"))
        assertEquals(emptyList<CustomCommand>(), CustomCommandMatcher.suggest(listOf(home, sleep), "на"))
    }

    @Test fun encodeDecodeRoundTripAndEdit() {
        val store = InMemoryCustomCommandStore()
        store.save(home)
        store.save(sleep)
        store.save(home.copy(steps = listOf("вайфай")))
        val decoded = InMemoryCustomCommandStore.decode(InMemoryCustomCommandStore.encode(store.commands.value))
        assertEquals(listOf(home.copy(steps = listOf("вайфай")), sleep), decoded)
        store.delete("2")
        assertEquals(listOf("1"), store.commands.value.map { it.id })
    }

    @Test fun triggersAndConditionSurviveEncoding() {
        val music = CustomCommand(
            "3", "Навушники", listOf("грай музику"),
            triggers = listOf(CommandTrigger(TriggerKind.BLUETOOTH_CONNECTED, "AA:BB", "Мої навушники"), CommandTrigger(TriggerKind.POWER_CONNECTED)),
            condition = CommandCondition(ConditionKind.BLUETOOTH_DEVICE, "AA:BB", "Мої навушники"),
        )
        val auto = CustomCommand("4", "", listOf("пауза"), triggers = listOf(CommandTrigger(TriggerKind.HEADSET_UNPLUGGED)))
        val all = listOf(home, music, auto)
        assertEquals(all, InMemoryCustomCommandStore.decode(InMemoryCustomCommandStore.encode(all)))
        // Старий формат (до 0.32.0): три поля.
        assertEquals(listOf(home), InMemoryCustomCommandStore.decode("1\tЯ вдома\tвайфай\u001Fяскравість 30\u001Fрежим дім"))
    }

    @Test fun samePhraseWithConditionGoesFirst() {
        val always = CustomCommand("5", "Музика", listOf("грай музику"))
        val withPhones = always.copy(id = "6", steps = listOf("відкрий музику"), condition = CommandCondition(ConditionKind.WIRED_HEADSET))
        assertEquals(listOf(withPhones, always), CustomCommandMatcher.exactAll(listOf(always, withPhones), "музика"))
        assertEquals(1, CustomCommandMatcher.suggest(listOf(always, withPhones), "муз").size)
        val noPhrase = CustomCommand("7", "", listOf("пауза"), triggers = listOf(CommandTrigger(TriggerKind.POWER_CONNECTED)))
        assertEquals(emptyList<CustomCommand>(), CustomCommandMatcher.suggest(listOf(noPhrase), "пау"))
    }

    @Test fun bluetoothTriggerMatchesChosenDevice() {
        val t = CommandTrigger(TriggerKind.BLUETOOTH_CONNECTED, "AA:BB:CC:DD:EE:FF", "Навушники")
        assertTrue(t.matches(TriggerKind.BLUETOOTH_CONNECTED, "aa:bb:cc:dd:ee:ff"))
        assertFalse(t.matches(TriggerKind.BLUETOOTH_CONNECTED, "11:22:33:44:55:66"))
        assertFalse(t.matches(TriggerKind.BLUETOOTH_DISCONNECTED, "AA:BB:CC:DD:EE:FF"))
    }

    @Test fun emptyStepsAreDropped() {
        assertEquals(listOf("вайфай", "режим дім"), CustomCommand.create(" Я вдома ", listOf(" вайфай ", "", "режим дім")).steps)
    }
}

package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class CustomCommandsTest {
    private fun steps(vararg texts: String) = texts.map { CommandStep(it) }
    private val home = CustomCommand("1", "Я вдома", steps("вайфай", "яскравість 30", "режим дім"))
    private val sleep = CustomCommand("2", "На ніч", steps("яскравість 0", "без звуку"))

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
        store.save(home.copy(steps = steps("вайфай")))
        val decoded = InMemoryCustomCommandStore.decode(InMemoryCustomCommandStore.encode(store.commands.value))
        assertEquals(listOf(home.copy(steps = steps("вайфай")), sleep), decoded)
        store.delete("2")
        assertEquals(listOf("1"), store.commands.value.map { it.id })
    }

    @Test fun triggersAndConditionSurviveEncoding() {
        val music = CustomCommand(
            "3", "Навушники", steps("грай музику"),
            triggers = listOf(CommandTrigger(TriggerKind.BLUETOOTH_CONNECTED, "AA:BB", "Мої навушники"), CommandTrigger(TriggerKind.POWER_CONNECTED)),
            conditions = listOf(CommandCondition(ConditionKind.BLUETOOTH_DEVICE, "AA:BB", "Мої навушники")),
        )
        val auto = CustomCommand("4", "", steps("пауза"), triggers = listOf(CommandTrigger(TriggerKind.HEADSET_UNPLUGGED)))
        val all = listOf(home, music, auto)
        assertEquals(all, InMemoryCustomCommandStore.decode(InMemoryCustomCommandStore.encode(all)))
        // Старий формат (до 0.32.0): три поля.
        assertEquals(listOf(home), InMemoryCustomCommandStore.decode("1\tЯ вдома\tвайфай\u001Fяскравість 30\u001Fрежим дім"))
        // Формат 0.32–1.1: одна умова з трьох частин.
        val old = "3\tНавушники\tграй музику\t\tBLUETOOTH_DEVICE\u001DAA:BB\u001DМої навушники"
        assertEquals(listOf(CommandCondition(ConditionKind.BLUETOOTH_DEVICE, "AA:BB", "Мої навушники")), InMemoryCustomCommandStore.decode(old).single().conditions)
    }

    @Test fun severalConditionsAndStepConditionsSurviveEncoding() {
        val road = CustomCommand(
            "8", "Дорога",
            listOf(
                CommandStep("режим дорога"),
                CommandStep("грай музику", listOf(CommandCondition(ConditionKind.BLUETOOTH_DEVICE, "AA:BB", "Навушники"))),
                CommandStep("маршрут до роботи", listOf(CommandCondition(ConditionKind.TIME, from = 420, to = 600, days = 31), CommandCondition(ConditionKind.CHARGING, negate = true))),
            ),
            conditions = listOf(
                CommandCondition(ConditionKind.BATTERY_ABOVE, percent = 20),
                CommandCondition(ConditionKind.PROFILE, profile = "Дім"),
                CommandCondition(ConditionKind.WIFI, negate = true),
            ),
        )
        assertEquals(listOf(road), InMemoryCustomCommandStore.decode(InMemoryCustomCommandStore.encode(listOf(road))))
    }

    @Test fun timeWindowAndDays() {
        val morning = CommandCondition(ConditionKind.TIME, from = 7 * 60, to = 10 * 60)
        assertTrue(ConditionRules.timeMatches(morning, 7 * 60, 1))
        assertTrue(ConditionRules.timeMatches(morning, 9 * 60 + 59, 3))
        assertFalse(ConditionRules.timeMatches(morning, 10 * 60, 3))
        assertFalse(ConditionRules.timeMatches(morning, 6 * 60 + 59, 3))
        // Через північ: 22:00–06:00.
        val night = CommandCondition(ConditionKind.TIME, from = 22 * 60, to = 6 * 60)
        assertTrue(ConditionRules.timeMatches(night, 23 * 60, 5))
        assertTrue(ConditionRules.timeMatches(night, 5 * 60, 5))
        assertFalse(ConditionRules.timeMatches(night, 12 * 60, 5))
        // Лише будні (пн–пт = біти 0–4).
        val weekdays = CommandCondition(ConditionKind.TIME, days = 0b0011111)
        assertTrue(ConditionRules.timeMatches(weekdays, 0, 1))
        assertTrue(ConditionRules.timeMatches(weekdays, 0, 5))
        assertFalse(ConditionRules.timeMatches(weekdays, 0, 6))
        assertFalse(ConditionRules.timeMatches(weekdays, 0, 7))
    }

    @Test fun samePhraseWithConditionGoesFirst() {
        val always = CustomCommand("5", "Музика", steps("грай музику"))
        val withPhones = always.copy(id = "6", steps = steps("відкрий музику"), conditions = listOf(CommandCondition(ConditionKind.WIRED_HEADSET)))
        assertEquals(listOf(withPhones, always), CustomCommandMatcher.exactAll(listOf(always, withPhones), "музика"))
        assertEquals(1, CustomCommandMatcher.suggest(listOf(always, withPhones), "муз").size)
        val noPhrase = CustomCommand("7", "", steps("пауза"), triggers = listOf(CommandTrigger(TriggerKind.POWER_CONNECTED)))
        assertEquals(emptyList<CustomCommand>(), CustomCommandMatcher.suggest(listOf(noPhrase), "пау"))
    }

    @Test fun bluetoothTriggerMatchesChosenDevice() {
        val t = CommandTrigger(TriggerKind.BLUETOOTH_CONNECTED, "AA:BB:CC:DD:EE:FF", "Навушники")
        assertTrue(t.matches(TriggerKind.BLUETOOTH_CONNECTED, "aa:bb:cc:dd:ee:ff"))
        assertFalse(t.matches(TriggerKind.BLUETOOTH_CONNECTED, "11:22:33:44:55:66"))
        assertFalse(t.matches(TriggerKind.BLUETOOTH_DISCONNECTED, "AA:BB:CC:DD:EE:FF"))
    }

    @Test fun emptyStepsAreDropped() {
        assertEquals(listOf("вайфай", "режим дім"), CustomCommand.create(" Я вдома ", listOf(" вайфай ", "", "режим дім")).stepTexts)
    }
}

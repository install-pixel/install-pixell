package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandCheckTest {
    private val apps = setOf("музика", "карти")
    private val profiles = listOf("Дорога", "Робота", "Дім")

    private fun kinds(command: CustomCommand, canOpenDirectly: Boolean = true) =
        CommandCheck.check(command, { q -> apps.any { it.take(4) == q.lowercase().take(4) } }, profiles, canOpenDirectly).map { it.kind to it.step }

    private fun steps(vararg t: String) = t.map { CommandStep(it) }

    @Test fun goodCommandHasNoProblems() {
        val road = CustomCommand("1", "Дорога", steps("режим дорога", "відкрий музику", "грай музику", "зачекай 10 хвилин", "котра година", "скажи Поспіши"))
        assertEquals(emptyList<Pair<CommandProblemKind, Int>>(), kinds(road))
    }

    @Test fun unknownAppProfileAndWaitAtEnd() {
        val bad = CustomCommand("2", "Тест", steps("відкрий тракторист", "режим відпустка", "зачекай 5 хвилин"))
        assertEquals(
            listOf(CommandProblemKind.UNKNOWN_APP to 1, CommandProblemKind.UNKNOWN_PROFILE to 2, CommandProblemKind.WAIT_AT_END to 3),
            kinds(bad),
        )
    }

    @Test fun screenStepsInAutomaticCommandWithoutOverlay() {
        val auto = CustomCommand("3", "", steps("грай музику", "маршрут до роботи"), triggers = listOf(CommandTrigger(TriggerKind.POWER_CONNECTED)))
        assertEquals(listOf(CommandProblemKind.NEEDS_SCREEN to 2), kinds(auto, canOpenDirectly = false))
        assertEquals(emptyList<Pair<CommandProblemKind, Int>>(), kinds(auto, canOpenDirectly = true))
    }

    @Test fun contradictions() {
        val charging = CommandCondition(ConditionKind.CHARGING)
        assertTrue(CommandCheck.contradicts(listOf(charging, charging.copy(negate = true))))
        assertTrue(CommandCheck.contradicts(listOf(CommandCondition(ConditionKind.PROFILE, profile = "Дім"), CommandCondition(ConditionKind.PROFILE, profile = "Робота"))))
        assertTrue(CommandCheck.contradicts(listOf(CommandCondition(ConditionKind.BATTERY_ABOVE, percent = 60), CommandCondition(ConditionKind.BATTERY_BELOW, percent = 40))))
        assertFalse(CommandCheck.contradicts(listOf(CommandCondition(ConditionKind.BATTERY_ABOVE, percent = 20), CommandCondition(ConditionKind.BATTERY_BELOW, percent = 80))))
        assertTrue(CommandCheck.contradicts(listOf(CommandCondition(ConditionKind.WIFI), CommandCondition(ConditionKind.MOBILE_DATA))))
        // Відключили зарядку, а умова — «заряджається».
        assertTrue(CommandCheck.contradicts(listOf(charging), listOf(CommandTrigger(TriggerKind.POWER_DISCONNECTED))))
        // Увімкнено «Дорога», а умова — профіль «Робота».
        assertTrue(CommandCheck.contradicts(listOf(CommandCondition(ConditionKind.PROFILE, profile = "Робота")), listOf(CommandTrigger(TriggerKind.PROFILE_ON, profile = "Дорога"))))
        assertFalse(CommandCheck.contradicts(listOf(CommandCondition(ConditionKind.PROFILE, profile = "дорога")), listOf(CommandTrigger(TriggerKind.PROFILE_ON, profile = "Дорога"))))
        // О 11:20, а умова часу — з 18:00 до 19:00.
        val lunch = listOf(CommandTrigger(TriggerKind.TIME, minute = 11 * 60 + 20))
        assertTrue(CommandCheck.contradicts(listOf(CommandCondition(ConditionKind.TIME, from = 18 * 60, to = 19 * 60)), lunch))
        assertFalse(CommandCheck.contradicts(listOf(CommandCondition(ConditionKind.TIME, from = 11 * 60, to = 12 * 60)), lunch))
    }

    @Test fun neverAndStepNeverButPhraseKeepsCommandAlive() {
        val onlyEvent = CustomCommand(
            "4", "", steps("скажи Обід", "скажи Кінець зміни"),
            triggers = listOf(CommandTrigger(TriggerKind.TIME, minute = 11 * 60 + 20, days = 31), CommandTrigger(TriggerKind.TIME, minute = 18 * 60 + 20, days = 31)),
            conditions = listOf(CommandCondition(ConditionKind.PROFILE, profile = "Робота")),
        )
        assertEquals(emptyList<Pair<CommandProblemKind, Int>>(), kinds(onlyEvent))
        val withNeverStep = onlyEvent.copy(steps = onlyEvent.steps + CommandStep("пауза", listOf(CommandCondition(ConditionKind.PROFILE, profile = "Дім"))))
        assertEquals(listOf(CommandProblemKind.STEP_NEVER to 3), kinds(withNeverStep))
        // З фразою — голосом можна запустити будь-коли, тож подія не робить умову «ніколи».
        val phrase = CustomCommand("5", "Обід", steps("скажи Обід"), triggers = listOf(CommandTrigger(TriggerKind.POWER_DISCONNECTED)), conditions = listOf(CommandCondition(ConditionKind.CHARGING)))
        assertEquals(emptyList<Pair<CommandProblemKind, Int>>(), kinds(phrase))
        assertEquals(listOf(CommandProblemKind.NEVER to 0), kinds(phrase.copy(phrase = "")))
    }

    @Test fun severalModesInOneCondition() {
        val roadOrHome = CommandCondition(ConditionKind.PROFILE, profile = "Дорога|Дім")
        assertEquals(listOf("Дорога", "Дім"), roadOrHome.profileNames)
        // Спільний режим є — не суперечать; немає — суперечать.
        assertFalse(CommandCheck.contradicts(listOf(roadOrHome, CommandCondition(ConditionKind.PROFILE, profile = "Дім|Робота"))))
        assertTrue(CommandCheck.contradicts(listOf(roadOrHome, CommandCondition(ConditionKind.PROFILE, profile = "Робота"))))
        // Увімкнено «Дім», дозволено «Дорога|Дім» — норм; увімкнено «Робота» — ніколи.
        assertFalse(CommandCheck.contradicts(listOf(roadOrHome), listOf(CommandTrigger(TriggerKind.PROFILE_ON, profile = "Дім"))))
        assertTrue(CommandCheck.contradicts(listOf(roadOrHome), listOf(CommandTrigger(TriggerKind.PROFILE_ON, profile = "Робота"))))
        // Вийшли з «Дорога» — може бути «Дім», тож не «ніколи»; лише «Дорога» — ніколи.
        assertFalse(CommandCheck.contradicts(listOf(roadOrHome), listOf(CommandTrigger(TriggerKind.PROFILE_OFF, profile = "Дорога"))))
        assertTrue(CommandCheck.contradicts(listOf(CommandCondition(ConditionKind.PROFILE, profile = "Дорога")), listOf(CommandTrigger(TriggerKind.PROFILE_OFF, profile = "Дорога"))))
        val command = CustomCommand("11", "Музика", steps("грай музику"), conditions = listOf(roadOrHome))
        assertEquals(listOf(command), InMemoryCustomCommandStore.decode(InMemoryCustomCommandStore.encode(listOf(command))))
    }

    @Test fun sameTriggerInAnotherCommand() {
        val on = CommandTrigger(TriggerKind.PROFILE_ON, profile = "Дорога")
        val music = CustomCommand("7", "", steps("грай музику"), triggers = listOf(on))
        val other = CustomCommand("8", "Маршрут", steps("маршрут до роботи"), triggers = listOf(on.copy(profile = "дорога")))
        val unrelated = CustomCommand("9", "", steps("пауза"), triggers = listOf(CommandTrigger(TriggerKind.PROFILE_OFF, profile = "Дорога")))
        val third = other.copy(id = "10", phrase = "Новини")
        val found = CommandCheck.check(music, { true }, profiles, true, listOf(other, unrelated, music, third))
        // Одне попередження з усіма командами від тієї ж події (без самої себе).
        assertEquals(listOf(CommandProblem(CommandProblemKind.SAME_TRIGGER, detail = "Маршрут, Новини")), found)
    }

    @Test fun profileTriggersMatchEncodeAndReverse() {
        val on = CommandTrigger(TriggerKind.PROFILE_ON, profile = "Дорога")
        assertTrue(on.matches(TriggerKind.PROFILE_ON, "дорога"))
        assertFalse(on.matches(TriggerKind.PROFILE_ON, "Робота"))
        assertFalse(on.matches(TriggerKind.PROFILE_OFF, "Дорога"))
        val command = CustomCommand("6", "", steps("грай музику"), triggers = listOf(on))
        assertEquals(listOf(command), InMemoryCustomCommandStore.decode(InMemoryCustomCommandStore.encode(listOf(command))))
        val texts = StepTexts("увімкни ліхтарик", "вимкни ліхтарик", "грай музику", "пауза", "гучніше", "тихіше", "головний екран", "попередній режим")
        assertEquals(listOf(CommandTrigger(TriggerKind.PROFILE_OFF, profile = "Дорога")), CommandReverse.reverse(command, texts)!!.triggers)
    }
}

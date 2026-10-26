package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class StepCommandsTest {
    private val res = File(System.getProperty("blacklaunch.res") ?: "src/main/res")

    private fun string(folder: String, name: String): String =
        Regex("""<string name="$name">(.*?)</string>""").find(File(res, "$folder/strings.xml").readText())!!.groupValues[1]

    @Test fun waitHomeAndPreviousInEveryLanguage() {
        for (folder in listOf("values", "values-uk", "values-pl")) {
            assertEquals(folder, StepCommand.Wait(15), StepCommands.parse(string(folder, "cstep_wait_seconds").format(15)))
            assertEquals(folder, StepCommand.Wait(300), StepCommands.parse(string(folder, "cstep_wait_minutes").format(5)))
            assertEquals(folder, StepCommand.Home, StepCommands.parse(string(folder, "cstep_home")))
            assertEquals(folder, StepCommand.PreviousProfile, StepCommands.parse(string(folder, "cstep_previous_profile")))
        }
        assertEquals(StepCommand.Wait(10), StepCommands.parse("зачекай 10"))
        assertEquals(StepCommand.Wait(90), StepCommands.parse("wait 1 minute 30 seconds"))
        assertNull(StepCommands.parse("пауза"))
        assertNull(StepCommands.parse("режим дім"))
    }

    @Test fun splitAtFirstWait() {
        val (now, wait, rest) = StepCommands.splitAtWait(listOf("режим дорога", "зачекай 10 секунд", "грай музику", "зачекай 1 хвилину", "пауза"))
        assertEquals(listOf("режим дорога"), now)
        assertEquals(10, wait)
        assertEquals(listOf("грай музику", "зачекай 1 хвилину", "пауза"), rest)
        assertEquals(Triple(listOf("пауза"), null, emptyList<String>()), StepCommands.splitAtWait(listOf("пауза")))
    }

    @Test fun reverseCommand() {
        val texts = StepTexts("увімкни ліхтарик", "вимкни ліхтарик", "грай музику", "пауза", "гучніше", "тихіше", "головний екран", "попередній режим")
        val music = CustomCommand(
            "1", "Навушники",
            listOf(CommandStep("режим дорога"), CommandStep("відкрий музику"), CommandStep("грай музику"), CommandStep("увімкни ліхтарик"), CommandStep("таймер 5 хвилин")),
            triggers = listOf(CommandTrigger(TriggerKind.BLUETOOTH_CONNECTED, "AA:BB", "Навушники")),
        )
        val reversed = CommandReverse.reverse(music, texts)!!
        assertEquals(listOf(CommandTrigger(TriggerKind.BLUETOOTH_DISCONNECTED, "AA:BB", "Навушники")), reversed.triggers)
        assertEquals(listOf("вимкни ліхтарик", "пауза", "головний екран", "попередній режим"), reversed.stepTexts)
        assertEquals("", reversed.id)
        // Без події перевертати нічого.
        assertNull(CommandReverse.reverse(music.copy(triggers = emptyList()), texts))
    }
}

package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.voice.MediaCommand
import com.blacklaunch.launcher.voice.SystemPanel
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceCommand
import com.blacklaunch.launcher.voice.VoiceCommandParser
import com.blacklaunch.launcher.voice.VolumeChange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Кроки конструктора «Моїх команд» (рядки з ресурсів) мають розбиратися тією ж командою всіма мовами. */
class StepTemplatesTest {
    private val res = File(System.getProperty("blacklaunch.res") ?: "src/main/res")

    private fun strings(folder: String): Map<String, String> {
        val xml = File(res, "$folder/strings.xml").readText()
        return Regex("""<string name="(step_[a-z_]+)">(.*?)</string>""").findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'").replace("&amp;", "&") }
    }

    private val expected: Map<String, Pair<List<Any>, VoiceCommand>> = mapOf(
        "step_open" to (listOf<Any>("Maps") to VoiceCommand.Open("maps", explicit = true)),
        "step_profile" to (listOf<Any>("Дім") to VoiceCommand.SwitchProfile("дім")),
        "step_flashlight_on" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Flashlight(true))),
        "step_flashlight_off" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Flashlight(false))),
        "step_brightness" to (listOf<Any>(40) to VoiceCommand.Action(VoiceAction.BrightnessLevel(40))),
        "step_timer" to (listOf<Any>(15) to VoiceCommand.Action(VoiceAction.Timer(900))),
        "step_alarm" to (listOf<Any>(7, 5) to VoiceCommand.Action(VoiceAction.Alarm(7, 5))),
        "step_play" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Media(MediaCommand.PLAY))),
        "step_pause" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Media(MediaCommand.PAUSE))),
        "step_next" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Media(MediaCommand.NEXT))),
        "step_volume_up" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Volume(VolumeChange.UP))),
        "step_volume_down" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Volume(VolumeChange.DOWN))),
        "step_mute" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Volume(VolumeChange.MUTE))),
        "step_wifi" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Panel(SystemPanel.WIFI))),
        "step_bluetooth" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Panel(SystemPanel.BLUETOOTH))),
        "step_mobile_data" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Panel(SystemPanel.INTERNET))),
        "step_time" to (emptyList<Any>() to VoiceCommand.Action(VoiceAction.Time)),
        "step_route" to (listOf<Any>("Робота") to VoiceCommand.Action(VoiceAction.Navigate("Робота"))),
        "step_call" to (listOf<Any>("Олег") to VoiceCommand.Action(VoiceAction.Call("олег"))),
        "step_sms" to (listOf<Any>("Олег", "Буду пізніше") to VoiceCommand.Action(VoiceAction.Sms("олег", "Буду пізніше"))),
    )

    @Test fun everyTemplateParsesInEveryLanguage() {
        for (folder in listOf("values", "values-uk", "values-pl")) {
            val templates = strings(folder)
            assertEquals("$folder: set of step strings", expected.keys, templates.keys)
            for ((name, case) in expected) {
                val (args, command) = case
                val text = templates.getValue(name).format(*args.toTypedArray())
                assertEquals("$folder/$name: «$text»", command, VoiceCommandParser.parse(text))
            }
        }
    }
}

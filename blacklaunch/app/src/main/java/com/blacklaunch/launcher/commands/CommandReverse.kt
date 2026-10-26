package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import com.blacklaunch.launcher.voice.MediaCommand
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceCommand
import com.blacklaunch.launcher.voice.VoiceCommandParser
import com.blacklaunch.launcher.voice.VolumeChange

/** Тексти кроків мовою телефону — з ресурсів (step_*, cstep_*). */
data class StepTexts(
    val flashlightOn: String,
    val flashlightOff: String,
    val play: String,
    val pause: String,
    val louder: String,
    val quieter: String,
    val home: String,
    val previousProfile: String,
)

/**
 * «Зворотна команда»: подія навпаки (підключено → відключено) і протилежні кроки у зворотному порядку —
 * музика → пауза, ліхтарик увімк. → вимк., режим → попередній режим, програма → головний екран.
 * Те, що не має протилежного (таймер, дзвінок, маршрут…), пропускається; умови не переносяться.
 */
object CommandReverse {
    fun reverse(command: CustomCommand, texts: StepTexts): CustomCommand? {
        val triggers = command.triggers.mapNotNull { t -> opposite(t.kind)?.let { t.copy(kind = it) } }
        if (triggers.isEmpty()) return null
        val steps = command.steps.asReversed().mapNotNull { opposite(it.text, texts) }.distinct().map(::CommandStep)
        if (steps.isEmpty()) return null
        return CustomCommand("", "", steps, triggers)
    }

    private fun opposite(kind: TriggerKind): TriggerKind? = when (kind) {
        TriggerKind.BLUETOOTH_CONNECTED -> TriggerKind.BLUETOOTH_DISCONNECTED
        TriggerKind.BLUETOOTH_DISCONNECTED -> TriggerKind.BLUETOOTH_CONNECTED
        TriggerKind.POWER_CONNECTED -> TriggerKind.POWER_DISCONNECTED
        TriggerKind.POWER_DISCONNECTED -> TriggerKind.POWER_CONNECTED
        TriggerKind.HEADSET_PLUGGED -> TriggerKind.HEADSET_UNPLUGGED
        TriggerKind.HEADSET_UNPLUGGED -> TriggerKind.HEADSET_PLUGGED
        TriggerKind.TIME -> null
        TriggerKind.PROFILE_ON -> TriggerKind.PROFILE_OFF
        TriggerKind.PROFILE_OFF -> TriggerKind.PROFILE_ON
    }

    private fun opposite(step: String, texts: StepTexts): String? {
        when (StepCommands.parse(step)) {
            is StepCommand.Wait, StepCommand.Home, StepCommand.PreviousProfile, is StepCommand.Say -> return null
            null -> Unit
        }
        return when (val parsed = VoiceCommandParser.parse(step)) {
            is VoiceCommand.SwitchProfile -> texts.previousProfile
            is VoiceCommand.Open, VoiceCommand.OpenLast -> texts.home
            is VoiceCommand.Action -> when (val action = parsed.action) {
                is VoiceAction.Flashlight -> when (action.on) {
                    true -> texts.flashlightOff
                    false -> texts.flashlightOn
                    null -> step
                }
                is VoiceAction.Media -> when (action.command) {
                    MediaCommand.PLAY, MediaCommand.PLAY_PAUSE -> texts.pause
                    MediaCommand.PAUSE -> texts.play
                    else -> null
                }
                is VoiceAction.Volume -> when (action.change) {
                    VolumeChange.UP -> texts.quieter
                    VolumeChange.DOWN -> texts.louder
                    VolumeChange.MUTE -> null
                }
                // Панель Wi‑Fi / Bluetooth — той самий перемикач.
                is VoiceAction.Panel -> step
                else -> null
            }
            null -> null
        }
    }
}

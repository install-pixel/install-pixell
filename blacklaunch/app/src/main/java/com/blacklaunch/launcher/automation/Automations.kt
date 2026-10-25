package com.blacklaunch.launcher.automation

import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Подія, від якої запускається «Моя команда». */
enum class TriggerKind { BLUETOOTH_CONNECTED, BLUETOOTH_DISCONNECTED, POWER_CONNECTED, POWER_DISCONNECTED, HEADSET_PLUGGED, HEADSET_UNPLUGGED }

/**
 * Автоматизація «Коли → Що»: [kind] (для Bluetooth — конкретний пристрій [deviceAddress]) запускає
 * «Мою команду» [commandId]. Зберігається лише на телефоні.
 */
data class Automation(
    val id: String,
    val kind: TriggerKind,
    val commandId: String,
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val enabled: Boolean = true,
) {
    val isBluetooth get() = kind == TriggerKind.BLUETOOTH_CONNECTED || kind == TriggerKind.BLUETOOTH_DISCONNECTED

    fun matches(event: TriggerKind, address: String?): Boolean =
        enabled && kind == event && (!isBluetooth || deviceAddress == null || deviceAddress.equals(address, ignoreCase = true))

    companion object {
        fun create(kind: TriggerKind, commandId: String, deviceAddress: String?, deviceName: String?) =
            Automation(UUID.randomUUID().toString(), kind, commandId, deviceAddress, deviceName)
    }
}

/**
 * Які кроки Android дозволяє виконати без екрана (музика, гучність, ліхтарик, яскравість, режим, таймер).
 * Решту — відкрити програму, панель, дзвінок — виконує Black Glass, коли його відкрито.
 */
object BackgroundSteps {
    fun canRunHidden(command: VoiceCommand?): Boolean = when (command) {
        is VoiceCommand.SwitchProfile -> true
        is VoiceCommand.Action -> when (command.action) {
            is VoiceAction.Media, is VoiceAction.Volume, is VoiceAction.Flashlight,
            is VoiceAction.BrightnessLevel, is VoiceAction.BrightnessStep, is VoiceAction.Timer -> true
            else -> false
        }
        else -> false
    }
}

interface AutomationStore {
    val automations: StateFlow<List<Automation>>
    fun save(automation: Automation)
    fun delete(id: String)
}

open class InMemoryAutomationStore(initial: List<Automation> = emptyList()) : AutomationStore {
    private val _automations = MutableStateFlow(initial)
    override val automations: StateFlow<List<Automation>> = _automations.asStateFlow()

    override fun save(automation: Automation) {
        val list = _automations.value
        _automations.value = if (list.any { it.id == automation.id }) {
            list.map { if (it.id == automation.id) automation else it }
        } else {
            list + automation
        }
        persist(_automations.value)
    }

    override fun delete(id: String) {
        _automations.value = _automations.value.filterNot { it.id == id }
        persist(_automations.value)
    }

    protected open fun persist(list: List<Automation>) = Unit

    companion object {
        private const val FIELD = '\t'

        fun encode(list: List<Automation>): String = list.joinToString("\n") { a ->
            listOf(a.id, a.kind.name, a.commandId, a.deviceAddress.orEmpty(), a.deviceName.orEmpty().replace(FIELD, ' '), a.enabled.toString())
                .joinToString(FIELD.toString())
        }

        fun decode(text: String?): List<Automation> = text.orEmpty().lineSequence().mapNotNull { line ->
            val p = line.split(FIELD)
            if (p.size != 6) return@mapNotNull null
            val kind = TriggerKind.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null
            Automation(p[0], kind, p[2], p[3].ifEmpty { null }, p[4].ifEmpty { null }, p[5] != "false")
        }.toList()
    }
}

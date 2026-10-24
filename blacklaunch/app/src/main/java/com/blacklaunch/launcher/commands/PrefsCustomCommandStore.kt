package com.blacklaunch.launcher.commands

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** Власні команди в SharedPreferences — лише на телефоні. */
class PrefsCustomCommandStore(context: Context) : CustomCommandStore {
    private val prefs = context.applicationContext.getSharedPreferences("custom_commands", Context.MODE_PRIVATE)
    private val memory = InMemoryCustomCommandStore(InMemoryCustomCommandStore.decode(prefs.getString(KEY, null)))
    override val commands: StateFlow<List<CustomCommand>> = memory.commands

    override fun save(command: CustomCommand) {
        memory.save(command)
        persist()
    }

    override fun delete(id: String) {
        memory.delete(id)
        persist()
    }

    private fun persist() = prefs.edit().putString(KEY, InMemoryCustomCommandStore.encode(memory.commands.value)).apply()

    private companion object {
        const val KEY = "commands"
    }
}

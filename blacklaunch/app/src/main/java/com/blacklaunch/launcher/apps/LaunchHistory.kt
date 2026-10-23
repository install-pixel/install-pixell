package com.blacklaunch.launcher.apps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LaunchRecord(val appKey: String, val timeMillis: Long)

/** Локальний журнал запусків через BlackLaunch: «останній» і «часті». */
interface LaunchHistory {
    val records: StateFlow<List<LaunchRecord>>
    fun record(app: AppInfo, timeMillis: Long)
    fun clear()
}

class InMemoryLaunchHistory(private val maxRecords: Int = MAX_RECORDS) : LaunchHistory {
    private val _records = MutableStateFlow<List<LaunchRecord>>(emptyList())
    override val records: StateFlow<List<LaunchRecord>> = _records.asStateFlow()

    override fun record(app: AppInfo, timeMillis: Long) {
        _records.value = (_records.value + LaunchRecord(app.key, timeMillis)).takeLast(maxRecords)
    }

    override fun clear() {
        _records.value = emptyList()
    }

    internal fun restore(saved: List<LaunchRecord>) {
        _records.value = saved.takeLast(maxRecords)
    }

    companion object {
        const val MAX_RECORDS = 200
    }
}

/** Зберігає журнал у SharedPreferences: рядки «час|ключ», не більше 200 записів. */
class PrefsLaunchHistory(context: Context) : LaunchHistory {
    private val prefs = context.applicationContext.getSharedPreferences("launch_history", Context.MODE_PRIVATE)
    private val memory = InMemoryLaunchHistory().apply { restore(decode(prefs.getString(KEY, null))) }
    override val records: StateFlow<List<LaunchRecord>> = memory.records

    override fun record(app: AppInfo, timeMillis: Long) {
        memory.record(app, timeMillis)
        save()
    }

    override fun clear() {
        memory.clear()
        save()
    }

    private fun save() {
        prefs.edit().putString(KEY, encode(memory.records.value)).apply()
    }

    internal companion object {
        const val KEY = "records"

        fun encode(records: List<LaunchRecord>): String =
            records.joinToString("\n") { "${it.timeMillis}|${it.appKey}" }

        fun decode(text: String?): List<LaunchRecord> =
            text.orEmpty().lineSequence().mapNotNull { line ->
                val time = line.substringBefore('|', "").toLongOrNull()
                val key = line.substringAfter('|', "")
                if (time != null && key.isNotEmpty()) LaunchRecord(key, time) else null
            }.toList()
    }
}

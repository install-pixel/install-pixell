package com.blacklaunch.qrcodes.data

import android.content.Context
import com.blacklaunch.qrcodes.qr.ScanForm
import org.json.JSONArray

/** Збережені форми для сканера — у налаштуваннях програми, на телефоні. */
class FormStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("scan_forms", Context.MODE_PRIVATE)

    fun load(): List<ScanForm> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(ScanForm::fromJson) }
    }.getOrDefault(emptyList())

    /** Додає нову або замінює ту саму (за id). */
    fun save(form: ScanForm): List<ScanForm> {
        val list = load().toMutableList()
        val i = list.indexOfFirst { it.id == form.id }
        if (i >= 0) list[i] = form else list.add(form)
        write(list)
        return list
    }

    fun remove(id: String): List<ScanForm> {
        val list = load().filter { it.id != id }
        write(list)
        return list
    }

    fun write(list: List<ScanForm>) {
        prefs.edit().putString(KEY, JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()).apply()
    }

    private companion object {
        const val KEY = "list"
    }
}

package com.offlinekeyboard.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The last texts copied on the phone, for the keyboard's clipboard panel. Only on the phone (no-backup
 * storage); texts marked sensitive by the app they were copied from (passwords) are never kept. Unpinned
 * texts are forgotten after [KEEP_MS]; pinned ones stay until unpinned.
 */
object ClipboardHistory {

    data class Clip(val text: String, val at: Long, val pinned: Boolean)

    private const val FILE = "clipboard_history.json"
    private const val MAX_RECENT = 10
    private const val MAX_PINNED = 10
    private const val MAX_LENGTH = 20_000
    const val KEEP_MS = 60L * 60 * 1000

    private var clips: MutableList<Clip>? = null

    /** Pinned first, then the newest; expired ones are dropped. */
    fun items(context: Context, now: Long = System.currentTimeMillis()): List<Clip> {
        val list = load(context)
        val before = list.size
        list.removeAll { !it.pinned && now - it.at > KEEP_MS }
        if (list.size != before) save(context)
        return list.sortedWith(compareByDescending<Clip> { it.pinned }.thenByDescending { it.at })
    }

    fun add(context: Context, text: String, now: Long = System.currentTimeMillis()) {
        val clean = text.take(MAX_LENGTH)
        if (clean.isBlank()) return
        val list = load(context)
        val old = list.firstOrNull { it.text == clean }
        list.removeAll { it.text == clean }
        list.add(Clip(clean, now, old?.pinned == true))
        val recent = list.filter { !it.pinned }.sortedByDescending { it.at }
        if (recent.size > MAX_RECENT) list.removeAll(recent.drop(MAX_RECENT).toSet())
        save(context)
    }

    /** Pins or unpins [text]; returns whether it is pinned now. */
    fun togglePin(context: Context, text: String): Boolean {
        val list = load(context)
        val i = list.indexOfFirst { it.text == text }
        if (i < 0) return false
        val pin = !list[i].pinned
        if (pin && list.count { it.pinned } >= MAX_PINNED) return false
        list[i] = list[i].copy(pinned = pin, at = System.currentTimeMillis())
        save(context)
        return pin
    }

    /** Forgets [text] (pinned or not); returns it, to be put back with [restore]. */
    fun remove(context: Context, text: String): Clip? {
        val list = load(context)
        val clip = list.firstOrNull { it.text == text } ?: return null
        list.remove(clip)
        save(context)
        return clip
    }

    /** Puts back a text forgotten with [remove]. */
    fun restore(context: Context, clip: Clip) {
        val list = load(context)
        list.removeAll { it.text == clip.text }
        list.add(clip)
        save(context)
    }

    /** Forgets the unpinned texts. */
    fun clear(context: Context) {
        load(context).removeAll { !it.pinned }
        save(context)
    }

    private fun load(context: Context): MutableList<Clip> = clips ?: runCatching {
        val file = File(context.noBackupFilesDir, FILE)
        val array = if (file.isFile) JSONArray(file.readText()) else JSONArray()
        MutableList(array.length()) { i ->
            val o = array.getJSONObject(i)
            Clip(o.getString("text"), o.getLong("at"), o.optBoolean("pinned"))
        }
    }.getOrElse { mutableListOf() }.also { clips = it }

    private fun save(context: Context) {
        val list = clips ?: return
        val array = JSONArray()
        for (c in list) array.put(JSONObject().put("text", c.text).put("at", c.at).put("pinned", c.pinned))
        runCatching {
            val file = File(context.noBackupFilesDir, FILE)
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}

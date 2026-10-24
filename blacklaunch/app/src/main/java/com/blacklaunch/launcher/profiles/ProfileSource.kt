package com.blacklaunch.launcher.profiles

import kotlinx.coroutines.flow.Flow

/** Те, що потрібно головному екрану від профілів (для тестів — підробка). */
interface ProfileSource {
    val profiles: Flow<List<LauncherProfile>>
    val activeProfile: Flow<LauncherProfile?>
    fun setActive(id: String)

    /** Закріпити/відкріпити програму в профілі (меню програми, зона «Прибрати»). */
    suspend fun setPinned(profileId: String, appKey: String, pinned: Boolean)
}

/** Вибір наступного/попереднього профілю по колу (свайп, плитка в шторці). */
fun List<LauncherProfile>.neighbour(currentId: String?, delta: Int): LauncherProfile? {
    if (isEmpty()) return null
    val index = indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    return this[Math.floorMod(index + delta, size)]
}

/** Зміни списку закріплених програм (чисті функції — для тестів і екрана). */
object PinnedEdits {
    fun add(keys: List<String>, key: String): List<String> = if (key in keys) keys else keys + key

    fun remove(keys: List<String>, key: String): List<String> = keys - key

    fun move(keys: List<String>, key: String, delta: Int): List<String> {
        val from = keys.indexOf(key)
        if (from < 0) return keys
        val to = (from + delta).coerceIn(0, keys.lastIndex)
        if (from == to) return keys
        return keys.toMutableList().apply { add(to, removeAt(from)) }
    }
}

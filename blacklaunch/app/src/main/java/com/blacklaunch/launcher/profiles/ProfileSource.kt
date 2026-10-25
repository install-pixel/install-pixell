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

/**
 * Швидке перемикання (подвійний дотик, плитка, назва профілю, свайпи) — лише профілі з [quick];
 * якщо таких немає — усі. З профілю поза списком — до найближчого позначеного в тому ж напрямку.
 */
fun List<LauncherProfile>.quickNeighbour(currentId: String?, delta: Int, quick: (String) -> Boolean): LauncherProfile? {
    val candidates = filter { quick(it.id) }.ifEmpty { this }
    if (candidates.any { it.id == currentId }) return candidates.neighbour(currentId, delta)
    val start = indexOfFirst { it.id == currentId }
    if (start < 0) return candidates.first()
    val step = if (delta < 0) -1 else 1
    for (offset in 1..size) {
        val profile = this[Math.floorMod(start + offset * step, size)]
        if (profile in candidates) return profile
    }
    return null
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

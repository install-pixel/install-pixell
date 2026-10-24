package com.blacklaunch.launcher.profiles

import kotlinx.coroutines.flow.Flow

/** Те, що потрібно головному екрану від профілів (для тестів — підробка). */
interface ProfileSource {
    val profiles: Flow<List<LauncherProfile>>
    val activeProfile: Flow<LauncherProfile?>
    fun setActive(id: String)
}

/** Вибір наступного/попереднього профілю по колу (свайп, плитка в шторці). */
fun List<LauncherProfile>.neighbour(currentId: String?, delta: Int): LauncherProfile? {
    if (isEmpty()) return null
    val index = indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    return this[Math.floorMod(index + delta, size)]
}

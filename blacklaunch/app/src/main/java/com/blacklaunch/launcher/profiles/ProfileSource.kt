package com.blacklaunch.launcher.profiles

import kotlinx.coroutines.flow.Flow

/** Те, що потрібно головному екрану від профілів (для тестів — підробка). */
interface ProfileSource {
    val profiles: Flow<List<LauncherProfile>>
    val activeProfile: Flow<LauncherProfile?>
    fun setActive(id: String)

    /** Новий порядок закріплених програм профілю (перетягування на головному екрані). */
    suspend fun reorderPinned(profileId: String, orderedKeys: List<String>)
}

/** Невстановлені зараз програми не губляться: лишаються в кінці списку. */
fun mergePinnedOrder(saved: List<String>, visibleOrdered: List<String>): List<String> =
    visibleOrdered + saved.filter { it !in visibleOrdered }

/** Вибір наступного/попереднього профілю по колу (свайп, плитка в шторці). */
fun List<LauncherProfile>.neighbour(currentId: String?, delta: Int): LauncherProfile? {
    if (isEmpty()) return null
    val index = indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    return this[Math.floorMod(index + delta, size)]
}

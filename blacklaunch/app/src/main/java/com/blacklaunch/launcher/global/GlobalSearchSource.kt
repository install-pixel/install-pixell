package com.blacklaunch.launcher.global

/** Джерела, що потребують Android: налаштування, ярлики, контакти, календар, SMS. */
interface GlobalSearchSource {
    /**
     * [appPackages] — програми, що найкраще підійшли до запиту: показуємо і їхні ярлики.
     * [expanded] — група, для якої натиснули «Показати всі».
     */
    suspend fun search(
        query: String,
        kinds: Set<GroupKind>,
        appPackages: List<String> = emptyList(),
        expanded: GroupKind? = null,
    ): List<ResultGroup>
}

object NoGlobalSearch : GlobalSearchSource {
    override suspend fun search(query: String, kinds: Set<GroupKind>, appPackages: List<String>, expanded: GroupKind?): List<ResultGroup> =
        emptyList()
}

/** Джерела, які не потребують дозволів, увімкнені одразу; решта — лише за згодою. */
val DEFAULT_GLOBAL_KINDS: Set<GroupKind> =
    setOf(GroupKind.CALCULATOR, GroupKind.PROFILES, GroupKind.SETTINGS, GroupKind.SHORTCUTS)

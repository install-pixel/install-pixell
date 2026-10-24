package com.blacklaunch.launcher.profiles

import android.util.Log
import com.blacklaunch.launcher.data.NotificationRuleEntity
import com.blacklaunch.launcher.data.PinnedAppEntity
import com.blacklaunch.launcher.data.ProfileDao
import com.blacklaunch.launcher.data.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Де зберігати id активного профілю (поза базою, щоб читати миттєво при старті). */
interface ActiveProfileStore {
    val activeId: Flow<String?>
    fun setActive(id: String)
}

class ProfileRepository(
    private val dao: ProfileDao,
    private val activeStore: ActiveProfileStore,
    /** Транзакція бази: у застосунку — `withTransaction`, у тестах — простий виклик. */
    private val transaction: suspend (suspend () -> Unit) -> Unit,
) : ProfileSource {
    override val profiles: Flow<List<LauncherProfile>> =
        combine(dao.observeProfiles(), dao.observePinned(), dao.observeRules()) { profiles, pinned, rules ->
            val pinnedBy = pinned.groupBy { it.profileId }
            val rulesBy = rules.groupBy { it.profileId }
            profiles.map { entity ->
                entity.toDomain(
                    pinned = pinnedBy[entity.id].orEmpty().sortedBy { it.position }.map { it.appKey },
                    allowed = rulesBy[entity.id].orEmpty().map { it.packageName },
                )
            }
        }.catch { error ->
            // Помилка читання бази не повинна валити головний екран.
            Log.e(TAG, "Cannot read profiles", error)
            emit(emptyList())
        }

    /** Активний профіль; якщо збереженого id вже немає — перший у списку. */
    override val activeProfile: Flow<LauncherProfile?> =
        combine(profiles, activeStore.activeId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }

    /** Створює стандартні профілі один раз. Повторний виклик нічого не дублює. */
    suspend fun ensureDefaults(defaults: List<LauncherProfile>): Result<Unit> = runCatching {
        transaction {
            if (dao.count() == 0) {
                dao.insertIgnore(defaults.mapIndexed { index, profile -> profile.toEntity(index) })
            }
        }
    }.logFailure("ensureDefaults")

    override fun setActive(id: String) = activeStore.setActive(id)

    suspend fun create(name: String): Result<String> = runCatching {
        val id = UUID.randomUUID().toString()
        transaction { dao.upsert(LauncherProfile(id, name.trim()).toEntity(dao.maxPosition() + 1)) }
        id
    }.logFailure("create")

    /** Зберігає профіль повністю: властивості, закріплені програми (з порядком) і правила. */
    suspend fun save(profile: LauncherProfile, position: Int): Result<Unit> = runCatching {
        transaction {
            dao.upsert(profile.toEntity(position))
            dao.clearPinned(profile.id)
            dao.insertPinned(
                profile.pinnedAppKeys.distinct().mapIndexed { index, key -> PinnedAppEntity(profile.id, key, index) },
            )
            dao.clearRules(profile.id)
            dao.insertRules(profile.allowedNotificationPackages.distinct().map { NotificationRuleEntity(profile.id, it) })
        }
    }.logFailure("save")

    override suspend fun reorderPinned(profileId: String, orderedKeys: List<String>) {
        val list = profiles.first()
        val index = list.indexOfFirst { it.id == profileId }
        if (index < 0) return
        val profile = list[index]
        save(profile.copy(pinnedAppKeys = mergePinnedOrder(profile.pinnedAppKeys, orderedKeys)), index)
    }

    /** Останній профіль видалити не можна. */
    suspend fun delete(id: String): Result<Unit> = runCatching {
        transaction {
            if (dao.count() <= 1) throw LastProfileException()
            dao.delete(id)
        }
    }.logFailure("delete")

    class LastProfileException : IllegalStateException("Cannot delete the last profile")

    private fun <T> Result<T>.logFailure(action: String): Result<T> =
        onFailure { if (it !is LastProfileException) Log.e(TAG, "Profile $action failed", it) }

    private companion object {
        const val TAG = "ProfileRepository"
    }
}

internal fun LauncherProfile.toEntity(position: Int) = ProfileEntity(
    id = id,
    name = name,
    soundMode = soundMode.name,
    notificationMode = notificationMode.name,
    scheduleEnabled = scheduleEnabled,
    position = position,
)

internal fun ProfileEntity.toDomain(pinned: List<String>, allowed: List<String>) = LauncherProfile(
    id = id,
    name = name,
    pinnedAppKeys = pinned,
    soundMode = SoundMode.entries.firstOrNull { it.name == soundMode } ?: SoundMode.NORMAL,
    notificationMode = NotificationMode.entries.firstOrNull { it.name == notificationMode }
        ?: NotificationMode.SYSTEM_DEFAULT,
    allowedNotificationPackages = allowed,
    scheduleEnabled = scheduleEnabled,
)

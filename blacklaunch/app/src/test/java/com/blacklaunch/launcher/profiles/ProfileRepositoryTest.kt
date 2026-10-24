package com.blacklaunch.launcher.profiles

import com.blacklaunch.launcher.data.NotificationRuleEntity
import com.blacklaunch.launcher.data.PinnedAppEntity
import com.blacklaunch.launcher.data.ProfileDao
import com.blacklaunch.launcher.data.ProfileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {
    private class FakeDao : ProfileDao {
        val profiles = MutableStateFlow<List<ProfileEntity>>(emptyList())
        val pinned = MutableStateFlow<List<PinnedAppEntity>>(emptyList())
        val rules = MutableStateFlow<List<NotificationRuleEntity>>(emptyList())

        override fun observeProfiles() = profiles.map { list -> list.sortedWith(compareBy({ it.position }, { it.name })) }
        override fun observePinned() = pinned
        override fun observeRules() = rules
        override suspend fun count() = profiles.value.size
        override suspend fun maxPosition() = profiles.value.maxOfOrNull { it.position } ?: -1
        override suspend fun insertIgnore(profiles: List<ProfileEntity>) {
            val ids = this.profiles.value.map { it.id }.toSet()
            this.profiles.value = this.profiles.value + profiles.filter { it.id !in ids }
        }
        override suspend fun upsert(profile: ProfileEntity) {
            profiles.value = profiles.value.filter { it.id != profile.id } + profile
        }
        override suspend fun delete(id: String) {
            profiles.value = profiles.value.filter { it.id != id }
            pinned.value = pinned.value.filter { it.profileId != id }
            rules.value = rules.value.filter { it.profileId != id }
        }
        override suspend fun clearPinned(profileId: String) {
            pinned.value = pinned.value.filter { it.profileId != profileId }
        }
        override suspend fun insertPinned(apps: List<PinnedAppEntity>) {
            pinned.value = pinned.value + apps
        }
        override suspend fun clearRules(profileId: String) {
            rules.value = rules.value.filter { it.profileId != profileId }
        }
        override suspend fun insertRules(rules: List<NotificationRuleEntity>) {
            this.rules.value = this.rules.value + rules
        }
    }

    private class FakeActiveStore : ActiveProfileStore {
        override val activeId = MutableStateFlow<String?>(null)
        override fun setActive(id: String) { activeId.value = id }
    }

    private val dao = FakeDao()
    private val active = FakeActiveStore()
    private val repository = ProfileRepository(dao, active) { block -> block() }
    private val defaults = DefaultProfiles.create("Робота", "Дім", "Відпочинок")

    @Test fun defaultsAreCreatedOnce() = runTest {
        repository.ensureDefaults(defaults)
        repository.ensureDefaults(defaults)
        assertEquals(listOf("Робота", "Дім", "Відпочинок"), repository.profiles.first().map { it.name })
    }

    @Test fun workProfileDefaultsToVibrate() = runTest {
        repository.ensureDefaults(defaults)
        val work = repository.profiles.first().first { it.id == DefaultProfiles.WORK }
        assertEquals(SoundMode.VIBRATE, work.soundMode)
        assertEquals(NotificationMode.SYSTEM_DEFAULT, work.notificationMode)
        assertEquals(false, work.scheduleEnabled)
    }

    @Test fun defaultsDoNotOverwriteUserChanges() = runTest {
        repository.ensureDefaults(defaults)
        repository.save(defaults[0].copy(name = "Склад"), position = 0)
        repository.ensureDefaults(defaults)
        assertEquals("Склад", repository.profiles.first().first { it.id == DefaultProfiles.WORK }.name)
    }

    @Test fun activeFallsBackToFirstAndIsRemembered() = runTest {
        repository.ensureDefaults(defaults)
        assertEquals(DefaultProfiles.WORK, repository.activeProfile.first()?.id)
        repository.setActive(DefaultProfiles.REST)
        assertEquals(DefaultProfiles.REST, repository.activeProfile.first()?.id)
    }

    @Test fun deletedActiveProfileFallsBackToFirst() = runTest {
        repository.ensureDefaults(defaults)
        repository.setActive(DefaultProfiles.HOME)
        repository.delete(DefaultProfiles.HOME)
        assertEquals(DefaultProfiles.WORK, repository.activeProfile.first()?.id)
    }

    @Test fun pinnedAppsKeepOrderAndAreSeparatePerProfile() = runTest {
        repository.ensureDefaults(defaults)
        repository.save(defaults[0].copy(pinnedAppKeys = listOf("b/Main/0", "a/Main/0", "b/Main/0")), position = 0)
        repository.save(defaults[1].copy(pinnedAppKeys = listOf("c/Main/0")), position = 1)
        val list = repository.profiles.first()
        assertEquals(listOf("b/Main/0", "a/Main/0"), list.first { it.id == DefaultProfiles.WORK }.pinnedAppKeys)
        assertEquals(listOf("c/Main/0"), list.first { it.id == DefaultProfiles.HOME }.pinnedAppKeys)
        assertEquals(emptyList<String>(), list.first { it.id == DefaultProfiles.REST }.pinnedAppKeys)
    }

    @Test fun saveReplacesPinnedAndRules() = runTest {
        repository.ensureDefaults(defaults)
        repository.save(defaults[2].copy(pinnedAppKeys = listOf("x/Main/0"), allowedNotificationPackages = listOf("p1")), 2)
        repository.save(defaults[2].copy(pinnedAppKeys = emptyList(), allowedNotificationPackages = listOf("p2")), 2)
        val rest = repository.profiles.first().first { it.id == DefaultProfiles.REST }
        assertEquals(emptyList<String>(), rest.pinnedAppKeys)
        assertEquals(listOf("p2"), rest.allowedNotificationPackages)
    }

    @Test fun createAddsProfileAtTheEnd() = runTest {
        repository.ensureDefaults(defaults)
        val id = repository.create("  Дорога ").getOrThrow()
        val last = repository.profiles.first().last()
        assertEquals(id, last.id)
        assertEquals("Дорога", last.name)
    }

    @Test fun lastProfileCannotBeDeleted() = runTest {
        repository.ensureDefaults(defaults.take(1))
        val result = repository.delete(DefaultProfiles.WORK)
        assertTrue(result.exceptionOrNull() is ProfileRepository.LastProfileException)
        assertEquals(1, repository.profiles.first().size)
    }

    @Test fun unknownEnumValuesFallBackSafely() = runTest {
        dao.profiles.value = listOf(ProfileEntity("x", "X", "LOUD", "WHATEVER", false, 0))
        val profile = repository.profiles.first().single()
        assertEquals(SoundMode.NORMAL, profile.soundMode)
        assertEquals(NotificationMode.SYSTEM_DEFAULT, profile.notificationMode)
    }

    @Test fun databaseErrorsAreReturnedNotThrown() = runTest {
        val broken = ProfileRepository(dao, active) { _ -> throw IllegalStateException("disk I/O") }
        assertTrue(broken.ensureDefaults(defaults).isFailure)
    }
}

package com.blacklaunch.launcher.viewmodel

import com.blacklaunch.launcher.apps.FakeAppSource
import com.blacklaunch.launcher.profiles.LauncherProfile
import com.blacklaunch.launcher.profiles.ProfileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileHomeTest {
    private class FakeProfiles(list: List<LauncherProfile>) : ProfileSource {
        override val profiles = MutableStateFlow(list)
        val active = MutableStateFlow<String?>(null)
        override val activeProfile = combine(profiles, active) { all, id -> all.firstOrNull { it.id == id } ?: all.firstOrNull() }
        override fun setActive(id: String) { active.value = id }
        override suspend fun reorderPinned(profileId: String, orderedKeys: List<String>) {
            profiles.value = profiles.value.map {
                if (it.id == profileId) it.copy(pinnedAppKeys = com.blacklaunch.launcher.profiles.mergePinnedOrder(it.pinnedAppKeys, orderedKeys)) else it
            }
        }
    }

    private val apps = FakeAppSource("Sklado", "Scanner", "Telegram")
    private fun key(label: String) = FakeAppSource.app(label).key

    private val profiles = FakeProfiles(
        listOf(
            LauncherProfile("work", "Робота", pinnedAppKeys = listOf(key("Scanner"), key("Sklado"), "gone/Main/0")),
            LauncherProfile("home", "Дім", pinnedAppKeys = listOf(key("Telegram"))),
            LauncherProfile("rest", "Відпочинок"),
        ),
    )
    private lateinit var viewModel: LauncherViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LauncherViewModel(apps, profileSource = profiles, computeDispatcher = Dispatchers.Main)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test fun homeShowsPinnedAppsInOrderAndHidesUninstalled() {
        assertEquals("Робота", viewModel.home.value.profileName)
        assertEquals(listOf("Scanner", "Sklado"), viewModel.home.value.pinned.map { it.label })
    }

    @Test fun swipeSwitchesToNextAndWrapsAround() {
        viewModel.switchProfile(+1)
        assertEquals("home", viewModel.home.value.profileId)
        assertEquals(listOf("Telegram"), viewModel.home.value.pinned.map { it.label })
        viewModel.switchProfile(+1)
        assertEquals("rest", viewModel.home.value.profileId)
        assertEquals(emptyList<Any>(), viewModel.home.value.pinned)
        viewModel.switchProfile(+1)
        assertEquals("work", viewModel.home.value.profileId)
        viewModel.switchProfile(-1)
        assertEquals("rest", viewModel.home.value.profileId)
    }

    @Test fun switchAnnouncesProfileName() {
        viewModel.switchProfile(+1)
        assertEquals("Дім", viewModel.profileSwitched.tryReceive().getOrNull())
    }

    @Test fun voiceSwitchesProfileByName() {
        viewModel.onVoiceResult(listOf("режим відпочинок"))
        assertEquals("rest", viewModel.home.value.profileId)
    }

    @Test fun voiceProfileMatchesPrefixAndClosesSearch() {
        viewModel.openSearch()
        viewModel.onVoiceResult(listOf("режим дім"))
        assertEquals("home", viewModel.home.value.profileId)
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
    }

    @Test fun searchStillFindsAllAppsInAnyProfile() {
        viewModel.switchProfile(+2) // «Відпочинок» без закріплених програм
        viewModel.openSearch()
        viewModel.updateQuery("tele")
        assertEquals(listOf("Telegram"), (viewModel.state.value as LauncherUiState.SearchOpen).results.map { it.label })
    }

    private fun groups() = (viewModel.state.value as LauncherUiState.SearchOpen).groups

    @Test fun profileAppearsInSearchAndActivates() {
        viewModel.openSearch()
        viewModel.updateQuery("відпоч")
        val profile = groups().single { it.kind == com.blacklaunch.launcher.global.GroupKind.PROFILES }.items.single()
        assertEquals("Відпочинок", profile.title)
        viewModel.activateProfile("rest")
        assertEquals("rest", viewModel.home.value.profileId)
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
    }

    @Test fun calculatorAppearsInSearch() {
        viewModel.openSearch()
        viewModel.updateQuery("25*4")
        val calc = groups().single { it.kind == com.blacklaunch.launcher.global.GroupKind.CALCULATOR }.items.single()
        assertEquals("= 100", calc.title)
    }

    @Test fun plainWordShowsNoCalculator() {
        viewModel.openSearch()
        viewModel.updateQuery("tele")
        assertEquals(emptyList<Any>(), groups())
    }

    @Test fun reorderKeepsUninstalledPinnedApps() {
        viewModel.reorderPinned(listOf(key("Sklado"), key("Scanner")))
        assertEquals(listOf("Sklado", "Scanner"), viewModel.home.value.pinned.map { it.label })
        assertEquals("gone/Main/0", profiles.profiles.value.first().pinnedAppKeys.last())
    }
}

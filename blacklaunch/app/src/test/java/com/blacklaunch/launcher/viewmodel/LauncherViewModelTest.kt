package com.blacklaunch.launcher.viewmodel

import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.apps.FakeAppSource
import com.blacklaunch.launcher.apps.InMemoryLaunchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {
    private lateinit var source: FakeAppSource
    private lateinit var viewModel: LauncherViewModel
    private lateinit var history: InMemoryLaunchHistory
    private var clock = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        source = FakeAppSource("Telegram", "YouTube", "Камера")
        history = InMemoryLaunchHistory()
        viewModel = LauncherViewModel(source, history = history, now = { ++clock }, computeDispatcher = Dispatchers.Main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun search(): LauncherUiState.SearchOpen = viewModel.state.value as LauncherUiState.SearchOpen

    @Test
    fun startsOnIdleHome() {
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
    }

    @Test
    fun openSearchShowsEmptyQueryWithoutResults() {
        viewModel.openSearch()
        assertEquals(LauncherUiState.SearchOpen(""), viewModel.state.value)
    }

    @Test
    fun openSearchAgainKeepsQuery() {
        viewModel.openSearch()
        viewModel.updateQuery("tel")
        viewModel.openSearch()
        assertEquals("tel", search().query)
    }

    @Test
    fun queryIsIgnoredWhileSearchClosed() {
        viewModel.updateQuery("tel")
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
    }

    @Test
    fun clearQueryKeepsSearchOpenAndHidesResults() {
        viewModel.openSearch()
        viewModel.updateQuery("ка")
        viewModel.clearQuery()
        assertEquals(LauncherUiState.SearchOpen(""), viewModel.state.value)
    }

    @Test
    fun voiceActionGoesToTheScreen() {
        viewModel.onVoiceResult(listOf("таймер 10 хвилин"))
        assertEquals(VoiceAction.Timer(600), viewModel.voiceActions.tryReceive().getOrNull())
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
    }

    @Test
    fun disabledVoiceActionBecomesASearch() {
        val vm = LauncherViewModel(source, history = history, now = { ++clock }, computeDispatcher = Dispatchers.Main, voiceActionsEnabled = { emptySet() })
        vm.onVoiceResult(listOf("ліхтарик"))
        assertNull(vm.voiceActions.tryReceive().getOrNull())
        assertEquals("ліхтарик", (vm.state.value as LauncherUiState.SearchOpen).query)
    }

    @Test
    fun closeSearchReturnsToIdleAndRestoresRecentQuery() {
        viewModel.openSearch()
        viewModel.updateQuery("tel")
        viewModel.closeSearch()
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
        viewModel.openSearch()
        assertEquals("tel", search().query)
    }

    @Test
    fun oldQueryIsNotRestored() {
        viewModel.openSearch()
        viewModel.updateQuery("tel")
        viewModel.closeSearch()
        clock += LauncherViewModel.RESTORE_QUERY_MS + 1
        viewModel.openSearch()
        assertEquals(LauncherUiState.SearchOpen(""), viewModel.state.value)
    }

    @Test
    fun forgetSearchAndClearDropQuery() {
        viewModel.openSearch()
        viewModel.updateQuery("tel")
        viewModel.forgetSearch()
        viewModel.openSearch()
        assertEquals(LauncherUiState.SearchOpen(""), viewModel.state.value)
        viewModel.updateQuery("you")
        viewModel.clearQuery()
        viewModel.closeSearch()
        viewModel.openSearch()
        assertEquals(LauncherUiState.SearchOpen(""), viewModel.state.value)
    }

    @Test
    fun queryProducesResults() {
        viewModel.openSearch()
        viewModel.updateQuery("tele")
        assertEquals(listOf("Telegram"), search().results.map { it.label })
    }

    @Test
    fun blankQueryHasNoResults() {
        viewModel.openSearch()
        viewModel.updateQuery("   ")
        assertEquals(emptyList<Any>(), search().results)
    }

    @Test
    fun resultsFollowInstalledAppsChanges() {
        viewModel.openSearch()
        viewModel.updateQuery("you")
        assertEquals(1, search().results.size)
        source.apps.value = source.apps.value.filterNot { it.label == "YouTube" }
        assertEquals(emptyList<Any>(), search().results)
    }

    @Test
    fun launchReportsFailureForRemovedApp() {
        source.launchSucceeds = false
        assertFalse(viewModel.launch(FakeAppSource.app("Telegram")))
    }

    private fun app(label: String) = source.apps.value.first { it.label == label }

    @Test
    fun voiceAppNameAloneShowsResultsInsteadOfLaunching() {
        viewModel.onVoiceResult(listOf("ютубе", "you tube", "youtube"))
        assertEquals(emptyList<Any>(), source.launched)
        assertEquals(listOf("YouTube"), search().results.map { it.label })
    }

    @Test
    fun voiceOpenCommandLaunchesAppAndKeepsTheQuery() {
        viewModel.onVoiceResult(listOf("відкрий телеграм"))
        assertEquals(listOf("Telegram"), source.launched.map { it.label })
        assertEquals("телеграм", search().query)
    }

    @Test
    fun ambiguousVoiceShowsList() {
        source.apps.value = listOf(FakeAppSource.app("Google"), FakeAppSource.app("Google Maps"))
        viewModel.onVoiceResult(listOf("відкрий goo"))
        assertEquals(emptyList<Any>(), source.launched)
        assertEquals("goo", search().query)
        assertEquals(2, search().results.size)
    }

    @Test
    fun voiceOpenLastLaunchesPreviousApp() {
        viewModel.launch(app("Камера"))
        viewModel.launch(app("Telegram"))
        source.launched.clear()
        viewModel.onVoiceResult(listOf("відкрий останній"))
        assertEquals(listOf("Telegram"), source.launched.map { it.label })
    }

    @Test
    fun openLastSkipsUninstalledApps() {
        viewModel.launch(app("YouTube"))
        viewModel.launch(app("Telegram"))
        source.apps.value = source.apps.value.filterNot { it.label == "Telegram" }
        assertEquals("YouTube", viewModel.lastLaunched()?.label)
    }

    @Test
    fun openLastWithoutHistoryReportsMessage() {
        viewModel.onVoiceResult(listOf("відкрий останній"))
        assertEquals(UiMessage.NO_HISTORY, viewModel.messages.tryReceive().getOrNull())
    }

    @Test
    fun unknownProfileCommandReportsMessage() {
        viewModel.onVoiceResult(listOf("режим робота"))
        assertEquals(UiMessage.PROFILE_NOT_FOUND, viewModel.messages.tryReceive().getOrNull())
    }

    @Test
    fun successfulLaunchIsRecorded() {
        viewModel.launch(app("Telegram"))
        assertEquals(listOf(app("Telegram").key), history.records.value.map { it.appKey })
    }

    @Test
    fun failedLaunchIsNotRecordedAndReported() {
        source.launchSucceeds = false
        viewModel.launch(app("Telegram"))
        assertEquals(emptyList<Any>(), history.records.value)
        assertEquals(UiMessage.LAUNCH_FAILED, viewModel.messages.tryReceive().getOrNull())
        assertNull(viewModel.lastLaunched())
    }

    @Test
    fun frequentAppsRankHigherInResults() {
        source.apps.value = listOf(FakeAppSource.app("Camera A"), FakeAppSource.app("Camera B"))
        repeat(2) { viewModel.launch(app("Camera B")) }
        viewModel.openSearch()
        viewModel.updateQuery("camera")
        assertEquals(listOf("Camera B", "Camera A"), search().results.map { it.label })
    }

    @Test
    fun voiceResultWithoutAppsShowsWhatWasHeard() {
        viewModel.onVoiceResult(listOf("холодильник", "хол"))
        assertEquals("холодильник", search().query)
        assertEquals(emptyList<Any>(), search().results)
    }


    @Test
    fun emptyVoiceResultChangesNothing() {
        viewModel.onVoiceResult(emptyList())
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
    }

    @Test
    fun launchFromSearchTeachesTheQuery() {
        source.apps.value = listOf(FakeAppSource.app("Camera A"), FakeAppSource.app("Camera B"))
        viewModel.openSearch()
        viewModel.updateQuery("фотик")
        assertEquals(emptyList<Any>(), search().results)
        viewModel.launch(app("Camera B"))
        viewModel.openSearch()
        viewModel.updateQuery("фотик")
        assertEquals(listOf("Camera B"), search().results.map { it.label })
    }
}

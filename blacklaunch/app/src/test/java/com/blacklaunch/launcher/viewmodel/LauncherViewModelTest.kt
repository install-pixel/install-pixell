package com.blacklaunch.launcher.viewmodel

import com.blacklaunch.launcher.apps.FakeAppSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {
    private lateinit var source: FakeAppSource
    private lateinit var viewModel: LauncherViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        source = FakeAppSource("Telegram", "YouTube", "Камера")
        viewModel = LauncherViewModel(source)
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
    fun closeSearchReturnsToIdleAndDropsQuery() {
        viewModel.openSearch()
        viewModel.updateQuery("tel")
        viewModel.closeSearch()
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
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
}

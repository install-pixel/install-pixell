package com.blacklaunch.launcher.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherViewModelTest {
    private val viewModel = LauncherViewModel()

    @Test
    fun startsOnIdleHome() {
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
    }

    @Test
    fun openSearchShowsEmptyQuery() {
        viewModel.openSearch()
        assertEquals(LauncherUiState.SearchOpen(""), viewModel.state.value)
    }

    @Test
    fun openSearchAgainKeepsQuery() {
        viewModel.openSearch()
        viewModel.updateQuery("tel")
        viewModel.openSearch()
        assertEquals(LauncherUiState.SearchOpen("tel"), viewModel.state.value)
    }

    @Test
    fun queryIsIgnoredWhileSearchClosed() {
        viewModel.updateQuery("tel")
        assertEquals(LauncherUiState.HomeIdle, viewModel.state.value)
    }

    @Test
    fun clearQueryKeepsSearchOpen() {
        viewModel.openSearch()
        viewModel.updateQuery("ютуб")
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
}

package com.blacklaunch.launcher.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface LauncherUiState {
    data object HomeIdle : LauncherUiState
    data class SearchOpen(val query: String = "") : LauncherUiState
}

class LauncherViewModel : ViewModel() {
    private val _state = MutableStateFlow<LauncherUiState>(LauncherUiState.HomeIdle)
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()

    fun openSearch() {
        _state.update { it as? LauncherUiState.SearchOpen ?: LauncherUiState.SearchOpen() }
    }

    fun updateQuery(query: String) {
        _state.update { if (it is LauncherUiState.SearchOpen) it.copy(query = query) else it }
    }

    fun clearQuery() = updateQuery("")

    fun closeSearch() {
        _state.value = LauncherUiState.HomeIdle
    }
}

package com.blacklaunch.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.AppSource
import com.blacklaunch.launcher.search.AppSearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface LauncherUiState {
    data object HomeIdle : LauncherUiState
    data class SearchOpen(
        val query: String = "",
        val results: List<AppInfo> = emptyList(),
    ) : LauncherUiState
}

class LauncherViewModel(
    private val appSource: AppSource,
    userAliases: Flow<Map<String, Set<String>>> = flowOf(emptyMap()),
    engine: AppSearchEngine = AppSearchEngine(),
) : ViewModel() {
    // null — пошук закритий.
    private val query = MutableStateFlow<String?>(null)

    val state: StateFlow<LauncherUiState> =
        // Індекс перебудовується лише при зміні програм чи псевдонімів, не на кожну літеру.
        combine(query, combine(appSource.apps, userAliases, engine::index)) { query, index ->
            if (query == null) {
                LauncherUiState.HomeIdle
            } else {
                LauncherUiState.SearchOpen(query, index.search(query))
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState.HomeIdle)

    fun openSearch() {
        query.update { it ?: "" }
    }

    fun updateQuery(text: String) {
        query.update { if (it == null) null else text }
    }

    fun clearQuery() = updateQuery("")

    fun closeSearch() {
        query.value = null
    }

    fun launch(app: AppInfo): Boolean = appSource.launch(app)
}

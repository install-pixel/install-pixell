package com.blacklaunch.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.AppSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface LauncherUiState {
    data object HomeIdle : LauncherUiState
    data class SearchOpen(
        val query: String = "",
        val results: List<AppInfo> = emptyList(),
    ) : LauncherUiState
}

class LauncherViewModel(private val appSource: AppSource) : ViewModel() {
    // null — пошук закритий.
    private val query = MutableStateFlow<String?>(null)

    val state: StateFlow<LauncherUiState> =
        combine(query, appSource.apps) { query, apps ->
            if (query == null) {
                LauncherUiState.HomeIdle
            } else {
                LauncherUiState.SearchOpen(query, search(apps, query))
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

    private fun search(apps: List<AppInfo>, query: String): List<AppInfo> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        return apps.filter { it.label.contains(text, ignoreCase = true) }
    }
}

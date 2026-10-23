package com.blacklaunch.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.AppSource
import com.blacklaunch.launcher.apps.InMemoryLaunchHistory
import com.blacklaunch.launcher.apps.LaunchHistory
import com.blacklaunch.launcher.search.AppSearchEngine
import com.blacklaunch.launcher.search.SearchIndex
import com.blacklaunch.launcher.voice.VoiceCommand
import com.blacklaunch.launcher.voice.VoiceCommandParser
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface LauncherUiState {
    data object HomeIdle : LauncherUiState
    data class SearchOpen(
        val query: String = "",
        val results: List<AppInfo> = emptyList(),
    ) : LauncherUiState
}

/** Одноразові повідомлення для користувача. */
enum class UiMessage { LAUNCH_FAILED, NO_HISTORY, PROFILES_NOT_READY }

class LauncherViewModel(
    private val appSource: AppSource,
    userAliases: Flow<Map<String, Set<String>>> = flowOf(emptyMap()),
    private val history: LaunchHistory = InMemoryLaunchHistory(),
    engine: AppSearchEngine = AppSearchEngine(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    // null — пошук закритий.
    private val query = MutableStateFlow<String?>(null)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: ReceiveChannel<UiMessage> get() = _messages

    // Індекс перебудовується лише при зміні програм чи псевдонімів, не на кожну літеру.
    private val index: StateFlow<SearchIndex> =
        combine(appSource.apps, userAliases, engine::index)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SearchIndex(emptyList()))

    private val launchCounts: StateFlow<Map<String, Int>> =
        history.records
            .map { records -> records.groupingBy { it.appKey }.eachCount() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val state: StateFlow<LauncherUiState> =
        combine(query, index, launchCounts) { query, index, counts ->
            if (query == null) {
                LauncherUiState.HomeIdle
            } else {
                LauncherUiState.SearchOpen(query, index.search(query, counts))
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

    fun launch(app: AppInfo): Boolean {
        val ok = appSource.launch(app)
        if (ok) history.record(app, now()) else _messages.trySend(UiMessage.LAUNCH_FAILED)
        return ok
    }

    /** Остання запущена через BlackLaunch програма, яка досі встановлена. */
    fun lastLaunched(): AppInfo? {
        val installed = appSource.apps.value.associateBy { it.key }
        return history.records.value.asReversed().firstNotNullOfOrNull { installed[it.appKey] }
    }

    /**
     * Розпізнавач повертає кілька варіантів. Беремо перший, що дає результат.
     * Якщо програма визначена однозначно — запускаємо одразу, інакше показуємо список.
     */
    fun onVoiceResult(hypotheses: List<String>) {
        val commands = hypotheses.mapNotNull(VoiceCommandParser::parse)
        val command = commands.firstOrNull { it.isActionable() } ?: commands.firstOrNull() ?: return
        when (command) {
            VoiceCommand.OpenLast -> {
                val last = lastLaunched()
                if (last != null) launch(last) else _messages.trySend(UiMessage.NO_HISTORY)
            }
            is VoiceCommand.SwitchProfile -> _messages.trySend(UiMessage.PROFILES_NOT_READY)
            is VoiceCommand.Open -> {
                val results = index.value.searchScored(command.query, launchCounts.value)
                val app = SearchIndex.unambiguous(results)
                if (app != null && launch(app)) return
                openSearch()
                updateQuery(command.query)
            }
        }
    }

    private fun VoiceCommand.isActionable(): Boolean = when (this) {
        VoiceCommand.OpenLast -> lastLaunched() != null
        is VoiceCommand.SwitchProfile -> false
        is VoiceCommand.Open -> index.value.search(query).isNotEmpty()
    }
}

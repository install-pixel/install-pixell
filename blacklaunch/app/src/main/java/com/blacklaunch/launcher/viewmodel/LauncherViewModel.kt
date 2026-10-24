package com.blacklaunch.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.AppSource
import com.blacklaunch.launcher.apps.InMemoryLaunchHistory
import com.blacklaunch.launcher.apps.LaunchHistory
import com.blacklaunch.launcher.global.Calculator
import com.blacklaunch.launcher.global.DEFAULT_GLOBAL_KINDS
import com.blacklaunch.launcher.global.GlobalResult
import com.blacklaunch.launcher.global.GlobalSearchSource
import com.blacklaunch.launcher.global.GroupKind
import com.blacklaunch.launcher.global.NoGlobalSearch
import com.blacklaunch.launcher.global.ResultGroup
import com.blacklaunch.launcher.profiles.LauncherProfile
import com.blacklaunch.launcher.profiles.ProfileSource
import com.blacklaunch.launcher.profiles.neighbour
import com.blacklaunch.launcher.search.AppSearchEngine
import com.blacklaunch.launcher.search.InMemoryLearnedQueries
import com.blacklaunch.launcher.search.IndexedName
import com.blacklaunch.launcher.search.NameMatcher
import com.blacklaunch.launcher.search.PreparedQuery
import com.blacklaunch.launcher.search.LearnedQueries
import com.blacklaunch.launcher.search.Phonetic
import com.blacklaunch.launcher.search.SearchNormalizer
import com.blacklaunch.launcher.search.SearchIndex
import com.blacklaunch.launcher.voice.VoiceCommand
import com.blacklaunch.launcher.voice.VoiceCommandParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LauncherUiState {
    data object HomeIdle : LauncherUiState
    data class SearchOpen(
        val query: String = "",
        val results: List<AppInfo> = emptyList(),
        /** Глобальний пошук: калькулятор, профілі, налаштування, ярлики, контакти… */
        val groups: List<ResultGroup> = emptyList(),
    ) : LauncherUiState
}

/** Одноразові повідомлення для користувача. */
enum class UiMessage { LAUNCH_FAILED, NO_HISTORY, PROFILE_NOT_FOUND }

/** Головний екран: активний профіль і його закріплені програми, які справді встановлені. */
data class HomeState(
    val profileId: String? = null,
    val profileName: String = "",
    val pinned: List<AppInfo> = emptyList(),
    val profileCount: Int = 0,
)

/** Порожнє джерело профілів (тести, що не стосуються профілів). */
private object NoProfiles : ProfileSource {
    override val profiles: Flow<List<LauncherProfile>> = flowOf(emptyList())
    override val activeProfile: Flow<LauncherProfile?> = flowOf(null)
    override fun setActive(id: String) = Unit
    override suspend fun reorderPinned(profileId: String, orderedKeys: List<String>) = Unit
}

class LauncherViewModel(
    private val appSource: AppSource,
    userAliases: Flow<Map<String, Set<String>>> = flowOf(emptyMap()),
    private val history: LaunchHistory = InMemoryLaunchHistory(),
    engine: AppSearchEngine = AppSearchEngine(),
    private val now: () -> Long = System::currentTimeMillis,
    private val profileSource: ProfileSource = NoProfiles,
    private val learnedQueries: LearnedQueries = InMemoryLearnedQueries(),
    /** Побудова індексу й пошук — поза головним потоком, щоб не гальмувала анімація. */
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val globalSource: GlobalSearchSource = NoGlobalSearch,
    globalKinds: Flow<Set<GroupKind>> = flowOf(DEFAULT_GLOBAL_KINDS),
) : ViewModel() {
    // null — пошук закритий.
    private val query = MutableStateFlow<String?>(null)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: ReceiveChannel<UiMessage> get() = _messages

    // Індекс перебудовується лише при зміні програм, псевдонімів чи навчання, не на кожну літеру.
    private val index: StateFlow<SearchIndex> =
        combine(appSource.apps, userAliases, learnedQueries.learned, engine::index)
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SearchIndex(emptyList()))

    private val launchCounts: StateFlow<Map<String, Int>> =
        history.records
            .map { records -> records.groupingBy { it.appKey }.eachCount() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val profiles: StateFlow<List<LauncherProfile>> =
        profileSource.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val home: StateFlow<HomeState> =
        combine(profileSource.activeProfile, appSource.apps, profiles) { active, apps, all ->
            val installed = apps.associateBy { it.key }
            HomeState(
                profileId = active?.id,
                profileName = active?.name.orEmpty(),
                // Видалені програми просто не показуються, налаштування профілю не змінюються.
                pinned = active?.pinnedAppKeys.orEmpty().mapNotNull { installed[it] },
                profileCount = all.size,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    /** Назва профілю, на який щойно перемкнули, — показується на мить. */
    private val _profileSwitched = Channel<String>(Channel.CONFLATED)
    val profileSwitched: ReceiveChannel<String> get() = _profileSwitched

    // Результати рахуються у фоні; сам текст запиту оновлюється одразу, щоб набір не «стрибав».
    private val results: StateFlow<List<AppInfo>> =
        combine(query.filterNotNull(), index, launchCounts) { query, index, counts -> index.search(query, counts) }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val groups: StateFlow<List<ResultGroup>> =
        combine(query.filterNotNull(), globalKinds, profiles) { query, kinds, profiles -> Triple(query, kinds, profiles) }
            .mapLatest { (query, kinds, profiles) ->
                if (query.isBlank()) return@mapLatest emptyList()
                buildList {
                    if (GroupKind.CALCULATOR in kinds) {
                        Calculator.evaluate(query)?.let { add(ResultGroup(GroupKind.CALCULATOR, listOf(GlobalResult.Calculation(query.trim(), it)))) }
                    }
                    if (GroupKind.PROFILES in kinds) {
                        val prepared = PreparedQuery(query)
                        val matching = profiles.filter {
                            NameMatcher.score(listOf(IndexedName(it.name)), prepared) >= NameMatcher.WORD_PREFIX
                        }
                        if (matching.isNotEmpty()) {
                            add(ResultGroup(GroupKind.PROFILES, matching.map { GlobalResult.Profile(it.id, it.name) }))
                        }
                    }
                    addAll(globalSource.search(query, kinds))
                }
            }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<LauncherUiState> =
        combine(query, results, groups) { query, results, groups ->
            when {
                query == null -> LauncherUiState.HomeIdle
                query.isBlank() -> LauncherUiState.SearchOpen(query)
                else -> LauncherUiState.SearchOpen(query, results, groups)
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

    /** Свайп вліво/вправо: наступний чи попередній профіль по колу. */
    fun switchProfile(delta: Int) {
        val next = profiles.value.neighbour(home.value.profileId, delta) ?: return
        if (next.id == home.value.profileId) return
        activate(next)
    }

    fun reorderPinned(orderedKeys: List<String>) {
        val profileId = home.value.profileId ?: return
        viewModelScope.launch { profileSource.reorderPinned(profileId, orderedKeys) }
    }

    /** Профіль, знайдений у пошуку: перемикаємо й повертаємося на головний екран. */
    fun activateProfile(id: String) {
        profiles.value.firstOrNull { it.id == id }?.let(::activate)
        closeSearch()
    }

    private fun activate(profile: LauncherProfile) {
        profileSource.setActive(profile.id)
        _profileSwitched.trySend(profile.name)
    }

    /** «Режим робота»: точна назва, початок назви або схоже звучання. */
    private fun findProfile(name: String): LauncherProfile? {
        val q = SearchNormalizer.normalize(name)
        if (q.isEmpty()) return null
        val all = profiles.value
        return all.firstOrNull { SearchNormalizer.normalize(it.name) == q }
            ?: all.firstOrNull { SearchNormalizer.normalize(it.name).startsWith(q) }
            ?: all.maxByOrNull { Phonetic.similarity(Phonetic.skeleton(SearchNormalizer.normalize(it.name)), Phonetic.skeleton(q)) }
                ?.takeIf {
                    Phonetic.similarity(Phonetic.skeleton(SearchNormalizer.normalize(it.name)), Phonetic.skeleton(q)) >= PROFILE_MATCH
                }
    }

    fun launch(app: AppInfo): Boolean = launch(app, query.value)

    /** [searchQuery] — з якого запиту відкрили: наступного разу ця програма буде першою. */
    private fun launch(app: AppInfo, searchQuery: String?): Boolean {
        val ok = appSource.launch(app)
        if (ok) {
            history.record(app, now())
            if (!searchQuery.isNullOrBlank()) learnedQueries.record(searchQuery, app.key)
        } else {
            _messages.trySend(UiMessage.LAUNCH_FAILED)
        }
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
                if (last != null) launch(last, null) else _messages.trySend(UiMessage.NO_HISTORY)
            }
            is VoiceCommand.SwitchProfile -> {
                val profile = findProfile(command.name)
                if (profile != null) {
                    activate(profile)
                    closeSearch()
                } else {
                    _messages.trySend(UiMessage.PROFILE_NOT_FOUND)
                }
            }
            is VoiceCommand.Open -> {
                val results = index.value.searchScored(command.query, launchCounts.value)
                val app = SearchIndex.unambiguous(results)
                if (app != null && launch(app, command.query)) return
                openSearch()
                updateQuery(command.query)
            }
        }
    }

    private fun VoiceCommand.isActionable(): Boolean = when (this) {
        VoiceCommand.OpenLast -> lastLaunched() != null
        is VoiceCommand.SwitchProfile -> findProfile(name) != null
        is VoiceCommand.Open -> index.value.search(query).isNotEmpty()
    }

    private companion object {
        const val PROFILE_MATCH = 0.75
    }
}

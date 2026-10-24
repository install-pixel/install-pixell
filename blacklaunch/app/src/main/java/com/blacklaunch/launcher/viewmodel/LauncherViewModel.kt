package com.blacklaunch.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.AppSource
import com.blacklaunch.launcher.apps.InMemoryLaunchHistory
import com.blacklaunch.launcher.apps.LaunchHistory
import com.blacklaunch.launcher.global.Calculator
import com.blacklaunch.launcher.global.UnitConverter
import com.blacklaunch.launcher.global.DEFAULT_GLOBAL_KINDS
import com.blacklaunch.launcher.global.GlobalResult
import com.blacklaunch.launcher.global.GlobalSearchSource
import com.blacklaunch.launcher.global.GroupKind
import com.blacklaunch.launcher.global.NoGlobalSearch
import com.blacklaunch.launcher.global.ResultGroup
import com.blacklaunch.launcher.profiles.HomeGrid
import com.blacklaunch.launcher.profiles.HomeLayoutStore
import com.blacklaunch.launcher.profiles.InMemoryHomeLayoutStore
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
import com.blacklaunch.launcher.commands.CustomCommand
import com.blacklaunch.launcher.search.AppSuggestions
import com.blacklaunch.launcher.search.InMemoryRecentQueries
import com.blacklaunch.launcher.search.RecentQueries
import com.blacklaunch.launcher.commands.CustomCommandMatcher
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceActionKind
import com.blacklaunch.launcher.voice.VoiceActionParser
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
        /** Текстові команди: «wifi», «таймер 10 хвилин», «ліхтарик» — те саме, що голосом. */
        val actions: List<VoiceAction> = emptyList(),
        /** Власні команди, що збігаються з набраним. */
        val custom: List<CustomCommand> = emptyList(),
        /** Порожній пошук: програми, які зазвичай відкриваєте в цей час, і нещодавні. */
        val suggestions: List<AppInfo> = emptyList(),
        /** Порожній пошук: останні запити (найновіший перший). */
        val recent: List<String> = emptyList(),
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
    /** Ключ програми → клітинка вільної сітки ([HomeGrid]). */
    val cells: Map<String, Int> = emptyMap(),
)

private data class GroupInput(
    val query: String,
    val kinds: Set<GroupKind>,
    val profiles: List<LauncherProfile>,
    val apps: List<AppInfo>,
)

/** Порожнє джерело профілів (тести, що не стосуються профілів). */
private object NoProfiles : ProfileSource {
    override val profiles: Flow<List<LauncherProfile>> = flowOf(emptyList())
    override val activeProfile: Flow<LauncherProfile?> = flowOf(null)
    override fun setActive(id: String) = Unit
    override suspend fun setPinned(profileId: String, appKey: String, pinned: Boolean) = Unit
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
    private val layoutStore: HomeLayoutStore = InMemoryHomeLayoutStore(),
    /** Які голосові дії ввімкнено (будильник, ліхтарик, дзвінки…). */
    private val voiceActionsEnabled: () -> Set<VoiceActionKind> = { VoiceActionKind.entries.toSet() },
    /** Власні команди користувача: фраза → кроки. */
    private val customCommands: () -> List<CustomCommand> = { emptyList() },
    /** Останні запити (порожній пошук). */
    private val recentQueries: RecentQueries = InMemoryRecentQueries(),
    /** Показувати підказки програм і останні запити (вимикаються в налаштуваннях). */
    private val showSuggestions: () -> Boolean = { false },
    private val showRecent: () -> Boolean = { false },
) : ViewModel() {
    // null — пошук закритий.
    private val query = MutableStateFlow<String?>(null)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: ReceiveChannel<UiMessage> get() = _messages

    // Голосові дії виконує екран: їм потрібні системні сервіси й дозволи.
    private val _voiceActions = Channel<VoiceAction>(Channel.BUFFERED)
    val voiceActions: ReceiveChannel<VoiceAction> get() = _voiceActions

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
        combine(profileSource.activeProfile, appSource.apps, profiles, layoutStore.layouts) { active, apps, all, layouts ->
            val installed = apps.associateBy { it.key }
            // Видалені програми просто не показуються, налаштування профілю не змінюються.
            val pinned = active?.pinnedAppKeys.orEmpty().mapNotNull { installed[it] }
            HomeState(
                profileId = active?.id,
                profileName = active?.name.orEmpty(),
                pinned = pinned,
                profileCount = all.size,
                cells = HomeGrid.assign(pinned.map { it.key }, layouts[active?.id].orEmpty()),
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
        combine(query.filterNotNull(), globalKinds, profiles, results) { query, kinds, profiles, apps ->
            GroupInput(query, kinds, profiles, apps)
        }
            .mapLatest { (query, kinds, profiles, apps) ->
                if (query.isBlank()) return@mapLatest emptyList()
                buildList {
                    if (GroupKind.CALCULATOR in kinds) {
                        Calculator.evaluate(query)?.let { add(ResultGroup(GroupKind.CALCULATOR, listOf(GlobalResult.Calculation(query.trim(), it)))) }
                    }
                    // Конвертер одиниць — завжди: «10 км в милі», «30 c в f».
                    UnitConverter.convert(query)?.let { add(ResultGroup(GroupKind.CALCULATOR, listOf(GlobalResult.Calculation(query.trim(), it)))) }
                    if (GroupKind.PROFILES in kinds) {
                        val prepared = PreparedQuery(query)
                        val matching = profiles.filter {
                            NameMatcher.score(listOf(IndexedName(it.name)), prepared) >= NameMatcher.WORD_PREFIX
                        }
                        if (matching.isNotEmpty()) {
                            add(ResultGroup(GroupKind.PROFILES, matching.map { GlobalResult.Profile(it.id, it.name) }))
                        }
                    }
                    // Ярлики лише найкращої програми — щоб не засипати список чужими чатами.
                    addAll(globalSource.search(query, kinds, apps.take(1).map { it.packageName }))
                }
            }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<LauncherUiState> =
        combine(query, results, groups, history.records, recentQueries.queries) { query, results, groups, records, recent ->
            when {
                query == null -> LauncherUiState.HomeIdle
                query.isBlank() -> LauncherUiState.SearchOpen(
                    query = query,
                    suggestions = if (showSuggestions()) AppSuggestions.suggest(records, appSource.apps.value, now(), limit = SUGGESTIONS) else emptyList(),
                    recent = if (showRecent()) recent else emptyList(),
                )
                else -> {
                    val enabled = voiceActionsEnabled()
                    val actions = VoiceActionParser.suggest(query).filter { it.kind in enabled }
                    val custom = CustomCommandMatcher.suggest(customCommands(), query)
                    LauncherUiState.SearchOpen(query, results, groups, actions, custom)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState.HomeIdle)

    fun openSearch() {
        // Пошук закрили недавно (свайпом чи переходом у програму) — повертаємо той самий запит.
        val restored = lastQuery?.takeIf { now() - lastQueryAt <= RESTORE_QUERY_MS }.orEmpty()
        query.update { it ?: restored }
    }

    fun updateQuery(text: String) {
        query.update { if (it == null) null else text }
    }

    fun clearQuery() {
        lastQuery = null
        updateQuery("")
    }

    fun closeSearch() {
        query.value?.takeIf { it.isNotBlank() }?.let {
            lastQuery = it
            lastQueryAt = now()
        }
        query.value = null
    }

    /** Кнопка Home чи очищення поля: наступний пошук починається з порожнього. */
    fun forgetSearch() {
        lastQuery = null
        query.value = null
    }

    private var lastQuery: String? = null
    private var lastQueryAt = 0L

    /** Свайп вліво/вправо: наступний чи попередній профіль по колу. */
    fun switchProfile(delta: Int) {
        val next = profiles.value.neighbour(home.value.profileId, delta) ?: return
        if (next.id == home.value.profileId) return
        activate(next)
    }

    /** Чи закріплена програма на головному екрані активного профілю. */
    fun isPinned(app: AppInfo): Boolean = profiles.value.firstOrNull { it.id == home.value.profileId }
        ?.pinnedAppKeys?.contains(app.key) == true

    fun setPinned(app: AppInfo, pinned: Boolean) {
        val profileId = home.value.profileId ?: return
        viewModelScope.launch { profileSource.setPinned(profileId, app.key, pinned) }
    }

    /** Значок кинули в зону «Прибрати» на головному екрані. */
    fun unpin(appKey: String) {
        val profileId = home.value.profileId ?: return
        viewModelScope.launch { profileSource.setPinned(profileId, appKey, false) }
    }

    /** Програму перетягнули в іншу клітинку головного екрана (своя розкладка для кожного профілю). */
    fun moveApp(appKey: String, cell: Int) {
        val profileId = home.value.profileId ?: return
        layoutStore.save(profileId, HomeGrid.move(home.value.cells, appKey, cell))
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

    /** Програма за назвою, як її сказали («телеграм» → Telegram), — для повідомлень через месенджер. */
    fun findApp(name: String): AppInfo? = index.value.search(name, launchCounts.value).firstOrNull()

    /**
     * Власна команда: кроки по черзі, кожен — як сказана команда. Дії виконує екран
     * (навіть вимкнені в налаштуваннях — користувач сам їх задав), профіль і програму — тут.
     */
    fun runCustom(command: CustomCommand) {
        query.value?.let(recentQueries::record)
        command.steps.forEach { step ->
            when (val parsed = VoiceCommandParser.parse(step)) {
                is VoiceCommand.Action -> _voiceActions.trySend(parsed.action)
                is VoiceCommand.SwitchProfile -> findProfile(parsed.name)?.let(::activate)
                VoiceCommand.OpenLast -> lastLaunched()?.let { launch(it, null) }
                is VoiceCommand.Open -> index.value.search(parsed.query, launchCounts.value).firstOrNull()?.let { launch(it, null) }
                null -> Unit
            }
        }
    }

    /** Команду вибрали в результатах пошуку — виконує екран, як голосову. */
    fun runAction(action: VoiceAction) {
        query.value?.let(recentQueries::record)
        _voiceActions.trySend(action)
    }

    /** Натиснули останній запит — шукаємо його знову. */
    fun useRecent(text: String) = updateQuery(text)

    fun removeRecent(text: String) = recentQueries.remove(text)

    /** [searchQuery] — з якого запиту відкрили: наступного разу ця програма буде першою. */
    private fun launch(app: AppInfo, searchQuery: String?): Boolean {
        val ok = appSource.launch(app)
        if (ok) {
            history.record(app, now())
            if (!searchQuery.isNullOrBlank()) {
                learnedQueries.record(searchQuery, app.key)
                recentQueries.record(searchQuery)
            }
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
        // Власна команда має перевагу: користувач сам задав цю фразу.
        hypotheses.firstNotNullOfOrNull { text -> CustomCommandMatcher.exact(customCommands(), text)?.let { text to it } }
            ?.let { (heard, custom) ->
                openSearch()
                updateQuery(heard)
                runCustom(custom)
                return
            }
        val parsed = hypotheses.mapNotNull { text -> VoiceCommandParser.parse(text)?.let { text to it } }
        val (heard, command) = parsed.firstOrNull { it.second.isActionable() } ?: parsed.firstOrNull() ?: return
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
            is VoiceCommand.Action -> {
                // Сказане лишається в пошуку (як і набране) — після повернення воно на місці.
                openSearch()
                updateQuery(heard)
                // Вимкнену дію просто шукаємо як звичайний запит.
                if (command.action.kind in voiceActionsEnabled()) runAction(command.action)
            }
            is VoiceCommand.Open -> {
                // Запит завжди потрапляє в пошук — після повернення з програми він на місці.
                openSearch()
                updateQuery(command.query)
                // Одразу запускаємо лише на «відкрий X» і лише однозначну програму; просто назва —
                // список, з якого користувач вибирає сам (розпізнавання буває неточним).
                if (command.explicit) {
                    val results = index.value.searchScored(command.query, launchCounts.value)
                    SearchIndex.unambiguous(results)?.let { launch(it, command.query) }
                }
            }
        }
    }

    private fun VoiceCommand.isActionable(): Boolean = when (this) {
        VoiceCommand.OpenLast -> lastLaunched() != null
        is VoiceCommand.SwitchProfile -> findProfile(name) != null
        is VoiceCommand.Open -> index.value.search(query).isNotEmpty()
        is VoiceCommand.Action -> action.kind in voiceActionsEnabled()
    }

    companion object {
        private const val PROFILE_MATCH = 0.75

        /** Скільки часу після закриття пошуку повертається останній запит. */
        const val RESTORE_QUERY_MS = 60 * 1000L

        /** Один ряд підказок — щоб порожній пошук не займав увесь екран (керування однією рукою). */
        private const val SUGGESTIONS = 4
    }
}

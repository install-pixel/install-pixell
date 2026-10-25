package com.blacklaunch.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.AppSource
import com.blacklaunch.launcher.apps.InMemoryLaunchHistory
import com.blacklaunch.launcher.apps.LaunchHistory
import com.blacklaunch.launcher.global.AppSettingsParser
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
import com.blacklaunch.launcher.profiles.quickNeighbour
import com.blacklaunch.launcher.search.AppSearchEngine
import com.blacklaunch.launcher.search.InMemoryLearnedQueries
import com.blacklaunch.launcher.search.IndexedName
import com.blacklaunch.launcher.search.NameMatcher
import com.blacklaunch.launcher.search.PreparedQuery
import com.blacklaunch.launcher.search.LearnedQueries
import com.blacklaunch.launcher.search.Phonetic
import com.blacklaunch.launcher.search.SearchNormalizer
import com.blacklaunch.launcher.search.SearchIndex
import com.blacklaunch.launcher.commands.CommandCondition
import com.blacklaunch.launcher.commands.CustomCommand
import com.blacklaunch.launcher.profiles.SearchScope
import com.blacklaunch.launcher.profiles.GlobalAccess
import com.blacklaunch.launcher.profiles.GlobalMode
import com.blacklaunch.launcher.profiles.RolePackages
import com.blacklaunch.launcher.profiles.defaultGlobalMode
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        /** Кнопки біля контакту: у профілі «За програмами» — лише якщо є «Телефон» / «Повідомлення». */
        val canCall: Boolean = true,
        val canMessage: Boolean = true,
    ) : LauncherUiState
}

/** Пошук в активному профілі: [pinned] — його програми. */
private data class ProfileScope(val profileId: String, val scope: SearchScope, val pinned: Set<String>, val globalMode: GlobalMode) {
    /** Пакети програм профілю (ключ — «пакет/activity/користувач»). */
    val pinnedPackages: Set<String> get() = pinned.mapTo(HashSet()) { it.substringBefore('/') }
}

/** Одноразові повідомлення для користувача. */
enum class UiMessage { LAUNCH_FAILED, NO_HISTORY, PROFILE_NOT_FOUND, CONDITION_NOT_MET }

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
    val scope: ProfileScope,
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
    /** Приховані з пошуку програми (ключі). */
    hiddenApps: Flow<Set<String>> = flowOf(emptySet()),
    /** Що шукати в кожному профілі (усі / свої першими / лише свої). */
    searchScopes: Flow<Map<String, SearchScope>> = flowOf(emptyMap()),
    /** Режим глобального пошуку в кожному профілі (без вибору — за замовчуванням від «Пошуку в профілі»). */
    globalModes: Flow<Map<String, GlobalMode>> = flowOf(emptyMap()),
    /** Програми за замовчуванням для дзвінків, SMS, календаря, налаштувань — для режиму «За програмами». */
    private val rolePackages: () -> RolePackages = { RolePackages() },
    /** Чи профіль у швидкому перемиканні (свайпи, подвійний дотик, назва профілю). */
    private val inQuickSwitch: (String) -> Boolean = { true },
    /** «Лише якщо» команди: чи підключено вибрані навушники, зарядку… (перевірка в момент запуску). */
    private val conditionMet: suspend (CommandCondition) -> Boolean = { true },
) : ViewModel() {
    // null — пошук закритий.
    private val query = MutableStateFlow<String?>(null)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: ReceiveChannel<UiMessage> get() = _messages

    // Голосові дії виконує екран: їм потрібні системні сервіси й дозволи.
    private val _voiceActions = Channel<VoiceAction>(Channel.BUFFERED)
    val voiceActions: ReceiveChannel<VoiceAction> get() = _voiceActions

    // Індекс перебудовується лише при зміні програм, псевдонімів чи навчання, не на кожну літеру.
    // Приховані програми не шукаються й не підказуються; на головному екрані лишаються.
    // Пошук у профілі: вибір користувача і програми активного профілю.
    private val scope: StateFlow<ProfileScope> =
        combine(profileSource.activeProfile, searchScopes, globalModes) { active, scopes, modes ->
            val scope = active?.let { scopes[it.id] } ?: SearchScope.ALL
            ProfileScope(
                profileId = active?.id.orEmpty(),
                scope = scope,
                pinned = active?.pinnedAppKeys.orEmpty().toSet(),
                globalMode = active?.let { modes[it.id] } ?: defaultGlobalMode(scope),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileScope("", SearchScope.ALL, emptySet(), GlobalMode.ON))

    /** Що дозволяє глобальний пошук в активному профілі зараз. */
    private fun access(scope: ProfileScope = this.scope.value, all: Set<GroupKind> = GroupKind.entries.toSet()) =
        GlobalAccess.of(scope.globalMode, all, scope.pinnedPackages, rolePackages())

    private val visibleApps: StateFlow<List<AppInfo>> =
        combine(appSource.apps, hiddenApps, scope) { apps, hidden, scope ->
            val shown = apps.filterNot { it.key in hidden }
            // «Лише програми профілю» — пошук, підказки, «Усі програми» й голосове «відкрий» бачать тільки їх.
            if (scope.scope == SearchScope.PROFILE_ONLY) shown.filter { it.key in scope.pinned } else shown
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Усі (не приховані) програми за абеткою — список A–Z у порожньому пошуку. */
    val allApps: StateFlow<List<AppInfo>> =
        visibleApps.map { apps -> apps.sortedWith(compareBy(collator) { it.label }) }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val index: StateFlow<SearchIndex> =
        combine(visibleApps, userAliases, learnedQueries.learned, engine::index)
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
        combine(query.filterNotNull(), index, launchCounts, scope) { query, index, counts, scope ->
            val found = index.search(query, counts)
            // «Програми профілю першими» — стабільно вгору, решта в тому ж порядку.
            if (scope.scope == SearchScope.PROFILE_FIRST) found.sortedBy { if (it.key in scope.pinned) 0 else 1 } else found
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** «Показати всі» під групою — лише для поточного запиту; новий запит згортає. */
    private val expandedGroup = MutableStateFlow<Pair<String, GroupKind>?>(null)

    /** Шукати те саме ще раз — після видалення фото, щоб вони зникли з результатів. */
    private val refreshTick = MutableStateFlow(0)

    fun refreshResults() = refreshTick.update { it + 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val groups: StateFlow<List<ResultGroup>> =
        combine(query.filterNotNull(), globalKinds, profiles, results, scope) { query, allKinds, profiles, apps, scope ->
            // Глобальний пошук у профілі: вимкнено / за програмами профілю / усе.
            val kinds = access(scope, allKinds).kinds
            GroupInput(query, kinds, profiles, apps, scope)
        }
            .combine(combine(expandedGroup, refreshTick) { expanded, _ -> expanded }) { input, expanded ->
                input to expanded?.takeIf { it.first == input.query }?.second
            }
            .mapLatest { (input, expanded) ->
                val (query, kinds, profiles, apps, scope) = input
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
                    // «сповіщення Telegram» — сторінка налаштувань цієї програми, першою серед налаштувань.
                    val appSetting = if (GroupKind.SETTINGS !in kinds) {
                        null
                    } else {
                        AppSettingsParser.parse(query)?.let { (kind, appName) ->
                            index.value.search(appName, launchCounts.value).firstOrNull()?.let {
                                GlobalResult.AppSetting(it.packageName, it.label, kind)
                            }
                        }
                    }
                    val global = globalSource.search(query, kinds, apps.take(1).map { it.packageName }, expanded).let { groups ->
                        when {
                            appSetting == null -> groups
                            groups.any { it.kind == GroupKind.SETTINGS } -> groups.map { group ->
                                if (group.kind == GroupKind.SETTINGS) group.copy(items = listOf(appSetting) + group.items) else group
                            }
                            else -> groups + ResultGroup(GroupKind.SETTINGS, listOf(appSetting))
                        }
                    }
                    // «Лише програми профілю» — і ярлики лише цих програм.
                    addAll(
                        if (scope.scope != SearchScope.PROFILE_ONLY && !access(scope).shortcutsOfProfileOnly) {
                            global
                        } else {
                            global.mapNotNull { group ->
                                if (group.kind != GroupKind.SHORTCUTS) return@mapNotNull group
                                val allowed = scope.pinnedPackages
                                group.items.filter { (it as? GlobalResult.Shortcut)?.packageName in allowed }
                                    .takeIf { it.isNotEmpty() }?.let { group.copy(items = it) }
                            }
                        },
                    )
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
                    suggestions = if (showSuggestions()) AppSuggestions.suggest(records, visibleApps.value, now(), limit = SUGGESTIONS) else emptyList(),
                    recent = if (showRecent()) recent[scope.value.profileId].orEmpty() else emptyList(),
                )
                else -> {
                    val enabled = voiceActionsEnabled()
                    val actions = VoiceActionParser.suggest(query).filter { it.kind in enabled }
                    val custom = CustomCommandMatcher.suggest(customCommands(), query)
                    val access = access()
                    LauncherUiState.SearchOpen(query, results, groups, actions, custom, canCall = access.canCall, canMessage = access.canMessage)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState.HomeIdle)

    fun openSearch() {
        // Пошук закрили недавно (свайпом чи переходом у програму) — повертаємо той самий запит.
        val restored = lastQuery?.takeIf { now() - lastQueryAt <= RESTORE_QUERY_MS }.orEmpty()
        query.update { it ?: restored }
    }

    fun expandGroup(kind: GroupKind?) {
        val current = query.value ?: return
        expandedGroup.value = kind?.let { current to it }
    }

    fun updateQuery(text: String) {
        query.update { if (it == null) null else text }
    }

    fun clearQuery() {
        lastQuery = null
        voiceText = null
        updateQuery("")
    }

    /**
     * Набір у полі пошуку. Якщо в полі сказане голосом — перша набрана літера його замінює
     * (стирати не треба): «телеграм» + «ю» → «ю».
     */
    fun type(text: String) {
        val heard = voiceText
        voiceText = null
        voiceExpiry?.cancel()
        if (heard == null || query.value != heard) {
            updateQuery(text)
            return
        }
        updateQuery(if (text.startsWith(heard)) text.removePrefix(heard) else if (text.length < heard.length) "" else text)
    }

    /** Поле пошуку отримало фокус — сказане голосом звільняє місце для набору. */
    fun onFieldFocused() {
        if (voiceText != null && query.value == voiceText) type("")
    }

    // Сказане голосом показується в полі (видно, що розпізналось), а через хвилину без дій зникає.
    private var voiceText: String? = null
    private var voiceExpiry: Job? = null

    private fun showHeard(text: String) {
        openSearch()
        updateQuery(text)
        voiceText = text
        voiceExpiry?.cancel()
        voiceExpiry = viewModelScope.launch {
            delay(RESTORE_QUERY_MS)
            if (voiceText != null && query.value == voiceText) {
                voiceText = null
                updateQuery("")
            }
        }
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
        val next = profiles.value.quickNeighbour(home.value.profileId, delta, inQuickSwitch) ?: return
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
    /** Рядок команди в пошуку чи Enter: та сама фраза може мати кілька варіантів з різними умовами. */
    fun runCustom(command: CustomCommand) =
        runMatching(CustomCommandMatcher.exactAll(customCommands(), command.phrase).ifEmpty { listOf(command) })

    /**
     * Перша команда, чия умова виконана (з умовою — першими). Без умов — одразу, як і раніше;
     * жодна умова не виконана — коротке пояснення.
     */
    private fun runMatching(commands: List<CustomCommand>) {
        query.value?.takeIf { it.isNotBlank() }?.let { recentQueries.record(scope.value.profileId, it) }
        // Виконано — пошук закривається, щоб не лишались результати за словами команди.
        if (query.value != null) forgetSearch()
        val first = commands.firstOrNull() ?: return
        if (first.condition == null) {
            runSteps(first.steps)
            return
        }
        viewModelScope.launch {
            val chosen = commands.firstOrNull { command -> command.condition?.let { conditionMet(it) } ?: true }
            if (chosen != null) runSteps(chosen.steps) else _messages.trySend(UiMessage.CONDITION_NOT_MET)
        }
    }

    /** Кроки по черзі — «Моя команда» або решта автоматизації після дотику до сповіщення. */
    fun runSteps(steps: List<String>) {
        steps.forEach { step ->
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
        query.value?.let { recentQueries.record(scope.value.profileId, it) }
        _voiceActions.trySend(action)
    }

    /** Натиснули останній запит — шукаємо його знову. */
    fun useRecent(text: String) = updateQuery(text)

    fun removeRecent(text: String) = recentQueries.remove(scope.value.profileId, text)

    /** [searchQuery] — з якого запиту відкрили: наступного разу ця програма буде першою. */
    private fun launch(app: AppInfo, searchQuery: String?): Boolean {
        val ok = appSource.launch(app)
        if (ok) {
            history.record(app, now())
            if (!searchQuery.isNullOrBlank()) {
                learnedQueries.record(searchQuery, app.key)
                recentQueries.record(scope.value.profileId, searchQuery)
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
    /**
     * Розпізнавач дає кілька варіантів почутого. Для «відкрий …» беремо той, що найкраще збігається
     * з програмою чи власною назвою, а не просто перший — так власні назви знаходяться надійніше.
     */
    private fun bestOpen(parsed: List<Pair<String, VoiceCommand>>): Pair<String, VoiceCommand>? =
        parsed.filter { it.second is VoiceCommand.Open }
            .maxByOrNull { (_, command) ->
                index.value.searchScored((command as VoiceCommand.Open).query, launchCounts.value).firstOrNull()?.score ?: 0
            }

    /** «Монітор» голосом — Black Glass відкриває свій монітор системи. */
    private val _openMonitor = Channel<Unit>(Channel.CONFLATED)
    val openMonitor: ReceiveChannel<Unit> get() = _openMonitor

    private val monitorPhrases = setOf(
        "монітор", "монітор системи", "відкрий монітор", "покажи монітор",
        "monitor", "system monitor", "open monitor", "show monitor", "monitor systemu", "otworz monitor", "pokaz monitor",
    )

    fun onVoiceResult(hypotheses: List<String>) {
        if (customCommands().isEmpty() || hypotheses.none { CustomCommandMatcher.exactAll(customCommands(), it).isNotEmpty() }) {
            if (hypotheses.any { SearchNormalizer.normalize(it) in monitorPhrases }) {
                _openMonitor.trySend(Unit)
                return
            }
        }
        // Власна команда має перевагу: користувач сам задав цю фразу.
        hypotheses.map { text -> CustomCommandMatcher.exactAll(customCommands(), text) }.firstOrNull { it.isNotEmpty() }
            ?.let { matching ->
                // Своя команда просто виконується — без результатів пошуку за цими словами.
                runMatching(matching)
                return
            }
        val parsed = hypotheses.mapNotNull { text -> VoiceCommandParser.parse(text)?.let { text to it } }
        // Дія чи профіль — як і раніше, перший варіант, що спрацює; програма — найкращий збіг серед усіх варіантів.
        val (heard, command) = parsed.firstOrNull { it.second !is VoiceCommand.Open && it.second.isActionable() }
            ?: bestOpen(parsed)?.takeIf { it.second.isActionable() }
            ?: parsed.firstOrNull() ?: return
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
                showHeard(heard)
                // Вимкнену дію просто шукаємо як звичайний запит; дзвінок і SMS у профілі «За програмами» —
                // лише якщо «Телефон» / «Повідомлення» є в профілі.
                if (command.action.kind in voiceActionsEnabled() && contactActionAllowed(command.action)) runAction(command.action)
            }
            is VoiceCommand.Open -> {
                // Запит завжди потрапляє в пошук — після повернення з програми він на місці.
                showHeard(command.query)
                // Одразу запускаємо лише на «відкрий X» і лише однозначну програму; просто назва —
                // список, з якого користувач вибирає сам (розпізнавання буває неточним).
                if (command.explicit) {
                    val results = index.value.searchScored(command.query, launchCounts.value)
                    SearchIndex.unambiguous(results)?.let { launch(it, command.query) }
                }
            }
        }
    }

    private fun contactActionAllowed(action: VoiceAction): Boolean {
        val current = scope.value
        if (current.globalMode != GlobalMode.BY_APPS) return true
        val access = access(current)
        return when (action) {
            is VoiceAction.Call -> access.canCall
            is VoiceAction.Sms -> access.canMessage
            else -> true
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

        private val collator: java.text.Collator = java.text.Collator.getInstance()

        /** Один ряд підказок — щоб порожній пошук не займав увесь екран (керування однією рукою). */
        private const val SUGGESTIONS = 4
    }
}

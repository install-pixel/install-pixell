package com.blacklaunch.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.AppSource
import com.blacklaunch.launcher.apps.InMemoryLaunchHistory
import com.blacklaunch.launcher.apps.LaunchHistory
import com.blacklaunch.launcher.global.AppSettingsParser
import com.blacklaunch.launcher.global.Calculator
import com.blacklaunch.launcher.global.SpokenMath
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
import com.blacklaunch.launcher.profiles.FocusGate
import com.blacklaunch.launcher.profiles.FocusRules
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
import com.blacklaunch.launcher.commands.StepCommand
import com.blacklaunch.launcher.commands.StepCommands
import com.blacklaunch.launcher.voice.AppActionPhrase
import com.blacklaunch.launcher.voice.MediaCommand
import com.blacklaunch.launcher.voice.PlayInApp
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
enum class UiMessage { LAUNCH_FAILED, NO_HISTORY, PROFILE_NOT_FOUND, CONDITION_NOT_MET, NOT_A_PLAYER, NOT_IN_MODE }

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
    /** Крок «зачекати»: решта кроків пізніше (коротко — у Black Glass, довго — системним будильником). */
    private val scheduleSteps: (List<String>, Int) -> Unit = { _, _ -> },
    /** «Відтворити» в щойно відкритій програмі з музикою (пакет) — після «увімкни музику в …». */
    private val playMedia: suspend (String) -> Unit = {},
    /** Ярлики програми з фрази «відкрий <що> в <програма>», що підходять під «що»; null — не така фраза / немає програми. */
    private val phraseShortcuts: (String) -> List<GlobalResult.Shortcut>? = { null },
    /** Назва програми з такої фрази (якщо вона має ярлики) — для відповіді «не вміє». */
    private val phraseAppLabel: (String) -> String? = { null },
    private val startShortcut: (GlobalResult.Shortcut) -> Boolean = { false },
    /** «Команди — лише програми режиму»: чи може крок команди відкрити програму з цим пакетом зараз. */
    private val stepPackageAllowed: (String) -> Boolean = { true },
    /** Гравець, якому піде «відтворити» без назви (щоб перевірити режим). */
    private val lastPlayerPackage: () -> String? = { null },
    /** Чи програма — гравець музики (для «увімкни музику в …»). */
    private val isMusicApp: (AppInfo) -> Boolean = { true },
    /** Фрази вголос з кроків команди; null — поточний час. */
    private val speakSteps: (List<String?>) -> Unit = {},
    /** Профіль, що був до останнього перемикання, — для кроку «попередній режим». */
    private val previousProfileId: () -> String? = { null },
    /** Кроки команди з урахуванням умов (null — умови команди не виконані). */
    private val resolveCommand: suspend (CustomCommand) -> List<String>? = { it.stepTexts },
    /** «Зосередження»: чи можна зараз перейти в режим (id) — фокус-таймер і «Важкий вихід». */
    private val focusGate: (String) -> FocusGate = { FocusGate.Allowed },
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
    private val _focusPrompts = Channel<FocusPrompt>(Channel.CONFLATED)

    /** Перемикання затримане «Зосередженням»: екран показує відлік чи «ще 23 хв». */
    val focusPrompts: ReceiveChannel<FocusPrompt> get() = _focusPrompts

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
                        val typed = Calculator.evaluate(query)
                        if (typed != null) {
                            add(ResultGroup(GroupKind.CALCULATOR, listOf(GlobalResult.Calculation(query.trim(), typed))))
                        } else {
                            // Сказане словами: «двісті помножити на чотири» → 200*4.
                            SpokenMath.toExpression(query)?.let { expression ->
                                Calculator.evaluate(expression)?.let { add(ResultGroup(GroupKind.CALCULATOR, listOf(GlobalResult.Calculation(expression, it)))) }
                            }
                        }
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

    /** Перемикання, яке робить користувач: спершу «Зосередження». true — перемкнуто одразу. */
    private fun activate(profile: LauncherProfile): Boolean = when (val gate = focusGate(profile.id)) {
        FocusGate.Allowed -> {
            switchTo(profile)
            true
        }
        is FocusGate.Locked -> {
            _focusPrompts.trySend(FocusPrompt.Locked(gate.remainingMs))
            false
        }
        is FocusGate.Delay -> {
            _focusPrompts.trySend(FocusPrompt.Exit(profile.id, profile.name, gate.seconds))
            false
        }
    }

    /** Відлік «Важкого виходу» минув і вихід підтверджено. */
    fun activateConfirmed(id: String) {
        profiles.value.firstOrNull { it.id == id }?.let(::switchTo)
    }

    private fun switchTo(profile: LauncherProfile) {
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
        if (commands.isEmpty()) return
        viewModelScope.launch {
            for (command in commands) {
                val steps = resolveCommand(command) ?: continue
                runSteps(steps)
                return@launch
            }
            _messages.trySend(UiMessage.CONDITION_NOT_MET)
        }
    }

    /** Кроки по черзі — «Моя команда» або решта автоматизації після дотику до сповіщення. */
    fun runSteps(steps: List<String>) {
        val (now, wait, rest) = StepCommands.splitAtWait(steps)
        if (wait != null && rest.isNotEmpty()) scheduleSteps(rest, wait)
        val speech = mutableListOf<String?>()
        now.forEach { step ->
            when (val own = StepCommands.parse(step)) {
                is StepCommand.Say -> {
                    speech.add(own.text)
                    return@forEach
                }
                StepCommand.Home -> {
                    forgetSearch()
                    return@forEach
                }
                StepCommand.PreviousProfile -> {
                    previousProfileId()?.let { id -> profiles.value.firstOrNull { it.id == id } }?.let(::activate)
                    return@forEach
                }
                is StepCommand.Wait, null -> Unit
            }
            // «Грай музику в Symfonium» кроком команди.
            PlayInApp.parse(step)?.let { name -> if (playIn(name, null)) return@forEach }
            // «Відкрий підписки в YouTube» кроком команди.
            if (AppActionPhrase.parse(step).isNotEmpty() && openAppAction(step, speak = false)) return@forEach
            when (val parsed = VoiceCommandParser.parse(step)) {
                // Час у команді — завжди вголос (разом з іншими фразами).
                is VoiceCommand.Action -> when {
                    parsed.action == VoiceAction.Time -> speech.add(null)
                    // «Грай музику» без назви: гравець, якого немає в режимі, не вмикаємо.
                    parsed.action == VoiceAction.Media(MediaCommand.PLAY) && lastPlayerPackage()?.let { !stepPackageAllowed(it) } == true ->
                        _messages.trySend(UiMessage.NOT_IN_MODE)
                    else -> _voiceActions.trySend(parsed.action)
                }
                is VoiceCommand.SwitchProfile -> findProfile(parsed.name)?.let(::activate)
                VoiceCommand.OpenLast -> lastLaunched()?.let(::launchStep)
                is VoiceCommand.Open -> index.value.search(parsed.query, launchCounts.value).firstOrNull()?.let(::launchStep)
                null -> Unit
            }
        }
        if (speech.isNotEmpty()) speakSteps(speech)
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
        // Лише видимі в профілі: «відкрий останню» не виводить за межі «Лише програми профілю».
        val installed = visibleApps.value.associateBy { it.key }
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
    /** Що сказати вголос у відповідь на голосовий запит (лише з «Відповідати голосом»). */
    private val _voiceReplies = Channel<VoiceReply>(Channel.BUFFERED)
    val voiceReplies: ReceiveChannel<VoiceReply> get() = _voiceReplies

    private val _openMonitor = Channel<Unit>(Channel.CONFLATED)
    val openMonitor: ReceiveChannel<Unit> get() = _openMonitor

    // «Моніторинг» (з 1.1.8) — слово, яке знає й офлайн-модель; «монітор» лишається запасним. «Пульт» прибрано — так звуть програми для телевізора.
    private val monitorPhrases = setOf(
        "моніторинг", "відкрий моніторинг", "покажи моніторинг", "монітор",
        "monitoring", "open monitoring", "show monitoring", "otworz monitoring", "pokaz monitoring",
        "dashboard", "open dashboard", "show dashboard",
        "panel", "otworz panel", "pokaz panel",
    )

    fun onVoiceResult(hypotheses: List<String>) {
        if (customCommands().isEmpty() || hypotheses.none { CustomCommandMatcher.exactAll(customCommands(), it).isNotEmpty() }) {
            hypotheses.firstOrNull { SearchNormalizer.normalize(it) in monitorPhrases }?.let { heard ->
                // Як і решта голосових запитів — в історію останніх.
                recentQueries.record(scope.value.profileId, heard)
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
        // «Скільки буде двісті на чотири» — обчислення: показуємо в пошуку, нічого не запускаючи.
        hypotheses.firstOrNull { text -> SpokenMath.toExpression(text)?.let(Calculator::evaluate) != null }?.let { math ->
            showHeard(math)
            SpokenMath.toExpression(math)?.let(Calculator::evaluate)?.let { _voiceReplies.trySend(VoiceReply.Result(it)) }
            return
        }
        // «Увімкни музику на Symfonium» — відкрити програму й увімкнути відтворення.
        hypotheses.firstNotNullOfOrNull { text -> PlayInApp.parse(text)?.let { name -> text to name } }?.let { (heard, name) ->
            if (playIn(name, heard)) return
        }
        // «Відкрий підписки в YouTube» — ярлик програми; програма не вміє — кажемо, що вміє (ярлики в пошуку).
        hypotheses.firstOrNull { AppActionPhrase.parse(it).isNotEmpty() }?.let { text ->
            if (openAppAction(text, speak = true)) return
        }
        val parsed = hypotheses.mapNotNull { text -> VoiceCommandParser.parse(text)?.let { text to it } }
            .map { (text, command) ->
                // Офлайн-модель не знає «Spotify» і чує «спорт фай»: нічого не знайдено — шукаємо за звучанням.
                if (command is VoiceCommand.Open && index.value.search(command.query).isEmpty()) {
                    index.value.soundsLike(command.query)?.let { text to command.copy(query = it.label) } ?: (text to command)
                } else {
                    text to command
                }
            }
        // Дія чи профіль — як і раніше, перший варіант, що спрацює; програма — найкращий збіг серед усіх варіантів.
        val (heard, command) = parsed.firstOrNull { it.second !is VoiceCommand.Open && it.second.isActionable() }
            ?: bestOpen(parsed)?.takeIf { it.second.isActionable() }
            ?: parsed.firstOrNull() ?: return
        when (command) {
            VoiceCommand.OpenLast -> {
                val last = lastLaunched()
                if (last != null) {
                    _voiceReplies.trySend(VoiceReply.Opening(last.label))
                    launch(last, null)
                } else {
                    _messages.trySend(UiMessage.NO_HISTORY)
                }
            }
            is VoiceCommand.SwitchProfile -> {
                val profile = findProfile(command.name)
                if (profile != null) {
                    val gate = focusGate(profile.id)
                    activate(profile)
                    closeSearch()
                    when (gate) {
                        FocusGate.Allowed -> _voiceReplies.trySend(VoiceReply.Profile(profile.name))
                        is FocusGate.Locked -> _voiceReplies.trySend(VoiceReply.FocusLocked(FocusRules.minutesLeft(gate.remainingMs)))
                        // Відлік на екрані — відповідь не потрібна.
                        is FocusGate.Delay -> Unit
                    }
                } else {
                    _messages.trySend(UiMessage.PROFILE_NOT_FOUND)
                    _voiceReplies.trySend(VoiceReply.ProfileMissing)
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
                val results = index.value.searchScored(command.query, launchCounts.value)
                val single = if (command.explicit) SearchIndex.unambiguous(results) else null
                if (single != null) {
                    _voiceReplies.trySend(VoiceReply.Opening(single.label))
                    launch(single, command.query)
                } else {
                    // Кілька програм — вибір на екрані; жодної — інші результати пошуку (контакти, налаштування…).
                    _voiceReplies.trySend(if (results.isEmpty()) VoiceReply.SearchShown else VoiceReply.Choose)
                }
            }
        }
    }

    /** Програма з кроку команди — лише якщо дозволена в режимі («Команди — лише програми режиму»). */
    private fun launchStep(app: AppInfo) {
        if (stepPackageAllowed(app.packageName)) launch(app, null) else _messages.trySend(UiMessage.NOT_IN_MODE)
    }

    /** Ярлик з фрази «відкрий <що> в <програма>»: true — оброблено (відкрито або сказано, що програма не вміє). */
    private fun openAppAction(text: String, speak: Boolean): Boolean {
        // «Лише програми профілю» — ярлики лише програм цього режиму.
        val visible = visibleApps.value.mapTo(HashSet()) { it.packageName }
        val found = phraseShortcuts(text)?.firstOrNull { it.packageName in visible }
        if (found != null) {
            // Крок команди — лише програма режиму; сказане самим користувачем — завжди.
            if (!speak && !stepPackageAllowed(found.packageName)) {
                _messages.trySend(UiMessage.NOT_IN_MODE)
                return true
            }
            if (speak) {
                showHeard(text)
                _voiceReplies.trySend(VoiceReply.OpeningIn(found.title, found.appLabel))
            }
            if (!startShortcut(found)) _messages.trySend(UiMessage.LAUNCH_FAILED)
            return true
        }
        val label = phraseAppLabel(text)?.takeIf { name -> visibleApps.value.any { it.label == name } } ?: return false
        if (speak) {
            // У пошуку — усі ярлики цієї програми: видно, що вона вміє.
            showHeard(text)
            _voiceReplies.trySend(VoiceReply.CantDo(label, AppActionPhrase.parse(text).firstOrNull()?.first.orEmpty()))
        }
        return true
    }

    /**
     * Відкрити програму з музикою й за мить натиснути «відтворити» (гравцю треба запуститися). Програму не знайдено
     * однозначно — false, тоді фраза йде звичайним шляхом.
     */
    private fun playIn(name: String, heard: String?): Boolean {
        val results = index.value.searchScored(name, launchCounts.value)
        val named = SearchIndex.unambiguous(results) ?: results.firstOrNull()?.app?.takeIf { results.size == 1 }
            ?: index.value.soundsLike(name).takeIf { results.isEmpty() } ?: return false
        // Назвали не гравець («музику в Claude») — нічого не відкриваємо: інакше заграв би інший гравець.
        val app = named.takeIf(isMusicApp) ?: results.map { it.app }.firstOrNull { it.label == named.label && isMusicApp(it) }
        if (app == null) {
            _messages.trySend(UiMessage.NOT_A_PLAYER)
            if (heard != null) {
                showHeard(heard)
                _voiceReplies.trySend(VoiceReply.NotPlayer(named.label))
            }
            return true
        }
        if (heard == null && !stepPackageAllowed(app.packageName)) {
            _messages.trySend(UiMessage.NOT_IN_MODE)
            return true
        }
        if (heard != null) _voiceReplies.trySend(VoiceReply.Playing(app.label))
        if (!launch(app, null)) return true
        viewModelScope.launch {
            delay(PLAY_AFTER_LAUNCH_MS)
            playMedia(app.packageName)
        }
        return true
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

        /** Скільки чекати після відкриття гравця, перш ніж шукати його й натискати «відтворити». */
        private const val PLAY_AFTER_LAUNCH_MS = 700L

        /** Скільки часу після закриття пошуку повертається останній запит. */
        const val RESTORE_QUERY_MS = 60 * 1000L

        private val collator: java.text.Collator = java.text.Collator.getInstance()

        /** Один ряд підказок — щоб порожній пошук не займав увесь екран (керування однією рукою). */
        private const val SUGGESTIONS = 4
    }
}

/** Відповідь голосом на голосовий запит; тексти — у ресурсах, вимовляє екран. */
sealed interface VoiceReply {
    /** Результат калькулятора чи конвертера. */
    data class Result(val value: String) : VoiceReply
    data class Opening(val label: String) : VoiceReply
    data class Playing(val label: String) : VoiceReply
    data class NotPlayer(val label: String) : VoiceReply
    data class OpeningIn(val what: String, val label: String) : VoiceReply
    /** Програма [label] не має ярлика «[what]». */
    data class CantDo(val label: String, val what: String) : VoiceReply
    data object Choose : VoiceReply
    data object SearchShown : VoiceReply
    data class Profile(val name: String) : VoiceReply
    data object ProfileMissing : VoiceReply

    /** Фокус-таймер: режим не змінено, лишилось [minutes] хв. */
    data class FocusLocked(val minutes: Int) : VoiceReply
}

/** Перемикання, затримане «Зосередженням». */
sealed interface FocusPrompt {
    data class Locked(val remainingMs: Long) : FocusPrompt
    data class Exit(val profileId: String, val profileName: String, val seconds: Int) : FocusPrompt
}

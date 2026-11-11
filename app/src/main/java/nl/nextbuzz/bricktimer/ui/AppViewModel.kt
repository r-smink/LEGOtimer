package nl.nextbuzz.bricktimer.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.nextbuzz.bricktimer.BuildConfig
import nl.nextbuzz.bricktimer.data.Account
import nl.nextbuzz.bricktimer.data.AiModel
import nl.nextbuzz.bricktimer.data.ApiException
import nl.nextbuzz.bricktimer.data.AuthExpiredException
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.BuildContext
import nl.nextbuzz.bricktimer.data.BuildStatus
import nl.nextbuzz.bricktimer.data.CommunityApi
import nl.nextbuzz.bricktimer.data.DEFAULT_OPENROUTER_MODEL
import nl.nextbuzz.bricktimer.data.Confidence
import nl.nextbuzz.bricktimer.data.LeaderboardSort
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.data.mergePortfolios
import nl.nextbuzz.bricktimer.data.OpenRouterApi
import nl.nextbuzz.bricktimer.data.Player
import nl.nextbuzz.bricktimer.data.PortfolioEntry
import nl.nextbuzz.bricktimer.data.RebrickableApi
import nl.nextbuzz.bricktimer.data.RemoteBuild
import nl.nextbuzz.bricktimer.data.Repository
import nl.nextbuzz.bricktimer.data.Theme
import nl.nextbuzz.bricktimer.data.ThemeMode
import nl.nextbuzz.bricktimer.data.AppLanguage
import nl.nextbuzz.bricktimer.logic.AchievementKind
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.logic.MILESTONES
import nl.nextbuzz.bricktimer.logic.achievement
import nl.nextbuzz.bricktimer.logic.builderStats
import nl.nextbuzz.bricktimer.logic.isEveningHour
import nl.nextbuzz.bricktimer.logic.isMorningHour
import nl.nextbuzz.bricktimer.logic.isSpeedBrick
import nl.nextbuzz.bricktimer.logic.newTitles
import nl.nextbuzz.bricktimer.logic.tr
import nl.nextbuzz.bricktimer.util.ImageUtils

sealed interface Screen {
    data object Timer : Screen
    data object Catalog : Screen
    data object Portfolio : Screen
    data object Achievements : Screen
    data object History : Screen
    data object Leaderboard : Screen
    data class Detail(val set: LegoSet) : Screen
    data class Player(val id: String, val username: String) : Screen
}

/** A theme as offered in the filter, labelled with its parent path ("Star Wars › Ultimate Collector Series"). */
data class ThemeOption(val id: Int, val label: String)

/** Piece-count buckets for the catalog filter; bounds are inclusive. */
enum class PartsRange(private val labelNl: String, private val labelEn: String, val min: Int, val max: Int?) {
    TINY("Tot 250", "Under 250", 1, 249),
    SMALL("250 – 999", "250 – 999", 250, 999),
    MEDIUM("1.000 – 2.499", "1,000 – 2,499", 1_000, 2_499),
    LARGE("2.500 – 4.999", "2,500 – 4,999", 2_500, 4_999),
    HUGE("5.000 – 9.999", "5,000 – 9,999", 5_000, 9_999),
    MEGA("10.000+", "10,000+", 10_000, null),
    ;

    val label: String get() = tr(labelNl, labelEn)
}

data class CatalogState(
    val query: String = "",
    val theme: ThemeOption? = null,
    val parts: PartsRange? = null,
    val items: List<LegoSet> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

data class Badge(val title: String, val desc: String)

data class Summary(
    val title: String,
    val setName: String,
    val bigLabel: String,
    val big: String,
    val stat: String,
    val statLabel: String,
    val date: String,
    val dateLabel: String,
    val badges: List<Badge>,
    val setNum: String,
    /** The session a photo estimate is stored on; null when the summary offers no estimate. */
    val sessionId: Long? = null,
) {
    val canEstimate: Boolean get() = sessionId != null
}

/** Progress of the optional AI photo estimate shown in the session summary. */
sealed interface EstimateState {
    data object Idle : EstimateState
    data object Loading : EstimateState
    data class Result(val pct: Int, val note: String, val confidence: Confidence) : EstimateState
    data class Error(val message: String) : EstimateState
}

data class LeaderboardState(
    val sort: LeaderboardSort = LeaderboardSort.TIME,
    val players: List<Player> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Another builder's public profile; [player] is null while loading or when the account no longer exists. */
data class PlayerState(
    val id: String,
    val player: Player? = null,
    val builds: List<RemoteBuild> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/** Progress of signing in or up; [info] is a non-error note such as "confirm your e-mail". */
data class AuthState(val busy: Boolean = false, val error: String? = null, val info: String? = null)

private val USERNAME = Regex("[A-Za-z0-9_.-]{3,20}")

private val SET_NUMBER = Regex("""\d{3,7}(-\d+)?""")

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    private val store = repo.store
    private val api = RebrickableApi()
    private val openRouter = OpenRouterApi()
    private val community = CommunityApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    private val refreshLock = Mutex()
    private val syncLock = Mutex()

    /** False when this build has no Supabase project configured. */
    val communityEnabled: Boolean get() = community.configured

    private val _leaderboard = MutableStateFlow(LeaderboardState())
    val leaderboard: StateFlow<LeaderboardState> = _leaderboard.asStateFlow()
    private var leaderboardJob: Job? = null

    private val _player = MutableStateFlow<PlayerState?>(null)
    val player: StateFlow<PlayerState?> = _player.asStateFlow()
    private var playerJob: Job? = null

    private val _auth = MutableStateFlow(AuthState())
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    /** Shared with the home screen widget, so timer changes there show up here live. */
    val data: StateFlow<AppData> = repo.data

    private val _catalog = MutableStateFlow(CatalogState())
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _summary = MutableStateFlow<Summary?>(null)
    val summary: StateFlow<Summary?> = _summary.asStateFlow()

    private val _photos = MutableStateFlow<List<Uri>>(emptyList())
    val photos: StateFlow<List<Uri>> = _photos.asStateFlow()

    private val _models = MutableStateFlow(ModelsState())
    val models: StateFlow<ModelsState> = _models.asStateFlow()
    private var modelsJob: Job? = null

    private val _estimateTarget = MutableStateFlow<Pair<PortfolioEntry, nl.nextbuzz.bricktimer.data.Session>?>(null)
    val estimateTarget: StateFlow<Pair<PortfolioEntry, nl.nextbuzz.bricktimer.data.Session>?> = _estimateTarget.asStateFlow()

    private val _manualSessionTarget = MutableStateFlow<LegoSet?>(null)
    val manualSessionTarget: StateFlow<LegoSet?> = _manualSessionTarget.asStateFlow()
    private val _showManualSessionDialog = MutableStateFlow(false)
    val showManualSessionDialog: StateFlow<Boolean> = _showManualSessionDialog.asStateFlow()

    private val _estimate = MutableStateFlow<EstimateState>(EstimateState.Idle)
    val estimate: StateFlow<EstimateState> = _estimate.asStateFlow()
    private var estimateJob: Job? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    /** Fires when a timer starts in the free version; the activity shows an interstitial on top of the running timer. */
    private val _adRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val adRequests = _adRequests.asSharedFlow()

    private var themes: Map<Int, Theme> = store.loadThemes()
    private val _themeOptions = MutableStateFlow(themeOptions(themes))
    val themeOptions: StateFlow<List<ThemeOption>> = _themeOptions.asStateFlow()

    /** Minifigure counts for sets outside the portfolio (portfolio sets store their own). */
    private val _minifigs = MutableStateFlow<Map<String, Int>>(emptyMap())
    val minifigs: StateFlow<Map<String, Int>> = _minifigs.asStateFlow()
    private val minifigJobs = HashMap<String, Job>()

    private var loadJob: Job? = null
    private var themesJob: Job? = null

    /** Back stack; the app always opens on the timer. */
    val stack = mutableStateListOf<Screen>(Screen.Timer)
    val screen: Screen get() = stack.last()

    init {
        backfillMinifigs()
        syncCommunity()
    }

    private fun update(f: (AppData) -> AppData) = repo.update(f)

    private fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun themeName(id: Int) = themes[id]?.name ?: ""

    // ---------------------------------------------------------------- navigation

    fun goTab(root: Screen) {
        stack.clear()
        stack.add(root)
    }

    fun push(s: Screen) {
        stack.add(s)
    }

    fun openSet(set: LegoSet) = push(Screen.Detail(set))

    /** @return false when the back press should leave the app. */
    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (screen != Screen.Timer) {
            goTab(Screen.Timer)
            return true
        }
        return false
    }

    // ---------------------------------------------------------------- settings

    val effectiveKey: String get() = repo.value.apiKey.ifBlank { BuildConfig.REBRICKABLE_API_KEY }

    fun setApiKey(key: String) {
        update { it.copy(apiKey = key.trim()) }
        _catalog.update { CatalogState(query = it.query, theme = it.theme, parts = it.parts) }
        reloadCatalog()
    }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setLanguage(language: AppLanguage) = update { it.copy(language = language) }

    fun setOpenRouter(key: String, model: String) =
        update { it.copy(openRouterKey = key.trim(), openRouterModel = model.trim().ifBlank { DEFAULT_OPENROUTER_MODEL }) }

    // ---------------------------------------------------------------- catalog

    fun onQuery(q: String) {
        _catalog.update { it.copy(query = q) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(400)
            loadPage(reset = true)
        }
    }

    fun setThemeFilter(theme: ThemeOption?) {
        if (theme == _catalog.value.theme) return
        _catalog.update { it.copy(theme = theme) }
        reloadCatalog()
    }

    fun setPartsFilter(parts: PartsRange?) {
        if (parts == _catalog.value.parts) return
        _catalog.update { it.copy(parts = parts) }
        reloadCatalog()
    }

    /** Makes sure the theme list is loading so the filter picker can show it. */
    fun ensureThemeOptions() {
        val key = effectiveKey
        if (key.isNotBlank()) ensureThemes(key)
    }

    fun ensureCatalog() {
        val c = _catalog.value
        if (c.items.isEmpty() && !c.loading && c.error == null) reloadCatalog()
    }

    fun reloadCatalog() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadPage(reset = true) }
    }

    fun loadMore() {
        val c = _catalog.value
        if (c.loading || !c.hasMore || c.error != null || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { loadPage(reset = false) }
    }

    private suspend fun loadPage(reset: Boolean): Unit = coroutineScope {
        val key = effectiveKey
        if (key.isBlank()) return@coroutineScope
        val page = if (reset) 1 else _catalog.value.page + 1
        _catalog.update {
            if (reset) it.copy(items = emptyList(), page = 0, hasMore = true, loading = true, error = null)
            else it.copy(loading = true, error = null)
        }
        ensureThemes(key)
        val q = _catalog.value.query.trim()
        val themeId = _catalog.value.theme?.id
        val parts = _catalog.value.parts
        if (reset && themeId == null && parts == null && SET_NUMBER.matches(q)) {
            // Search can be very slow; a set number lookup usually answers much sooner.
            launch {
                val found = runCatching { api.getSet(key, if ('-' in q) q else "$q-1") }.getOrNull() ?: return@launch
                val named = found.copy(themeName = themeName(found.themeId))
                _catalog.update { c -> if (c.items.any { it.setNum == named.setNum }) c else c.copy(items = listOf(named) + c.items) }
            }
        }
        try {
            val res = api.searchSets(key, _catalog.value.query, page, themeId, parts?.min ?: 1, parts?.max)
            val sets = res.results.map { it.copy(themeName = themeName(it.themeId)) }
            _catalog.update {
                it.copy(items = (it.items + sets).distinctBy { s -> s.setNum }, total = res.count, page = page, hasMore = res.hasMore, loading = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _catalog.update { it.copy(loading = false, error = e.message ?: tr("Er ging iets mis.", "Something went wrong.")) }
        }
    }

    /** The theme list is slow (~8s), so it loads alongside the sets and fills in names when ready. */
    private fun ensureThemes(key: String) {
        if (themes.isNotEmpty() || themesJob?.isActive == true) return
        themesJob = viewModelScope.launch {
            val loaded = runCatching { api.themes(key) }.getOrNull() ?: return@launch
            themes = loaded
            _themeOptions.value = themeOptions(loaded)
            launch(Dispatchers.IO) { store.saveThemes(loaded) }
            _catalog.update { c -> c.copy(items = c.items.map { it.copy(themeName = loaded[it.themeId]?.name ?: it.themeName) }) }
            update { d ->
                d.copy(portfolio = d.portfolio.map { e ->
                    if (e.set.themeName.isBlank()) e.copy(set = e.set.copy(themeName = loaded[e.set.themeId]?.name ?: "")) else e
                })
            }
        }
    }

    /** Rebrickable's theme filter is exact, so every sub-theme is its own option, labelled with its parents. */
    private fun themeOptions(all: Map<Int, Theme>): List<ThemeOption> {
        fun path(t: Theme, depth: Int = 0): String {
            val parent = t.parentId?.let(all::get)
            return if (parent == null || depth > 8) t.name else path(parent, depth + 1) + " › " + t.name
        }
        return all.values.map { ThemeOption(it.id, path(it)) }.sortedBy { it.label.lowercase() }
    }

    // ---------------------------------------------------------------- minifigures

    /** Fetches the minifigure count of [set] once, storing it on the portfolio entry when there is one. */
    fun ensureMinifigs(set: LegoSet) {
        val num = set.setNum
        if (repo.value.entry(num)?.set?.numMinifigs != null || num in _minifigs.value || minifigJobs[num]?.isActive == true) return
        val key = effectiveKey
        if (key.isBlank()) return
        minifigJobs[num] = viewModelScope.launch {
            val count = runCatching { api.minifigCount(key, num) }.getOrNull() ?: return@launch
            storeMinifigs(num, count)
        }
    }

    private fun storeMinifigs(setNum: String, count: Int) {
        _minifigs.update { it + (setNum to count) }
        update { d ->
            d.copy(portfolio = d.portfolio.map { e -> if (e.set.setNum == setNum) e.copy(set = e.set.copy(numMinifigs = count)) else e })
        }
    }

    /** Portfolio sets added before minifigures were tracked get their count fetched once, gently. */
    private fun backfillMinifigs() {
        val key = effectiveKey
        val missing = repo.value.portfolio.filter { it.set.numMinifigs == null }.map { it.set.setNum }
        if (key.isBlank() || missing.isEmpty()) return
        viewModelScope.launch {
            for (num in missing) {
                if (repo.value.entry(num)?.set?.numMinifigs != null) continue
                runCatching { api.minifigCount(key, num) }.getOrNull()?.let { storeMinifigs(num, it) }
                delay(1100) // stay well under Rebrickable's rate limit
            }
        }
    }

    // ---------------------------------------------------------------- portfolio

    fun addToPortfolio(set: LegoSet) {
        if (repo.value.entry(set.setNum) != null) return
        val withFigs = if (set.numMinifigs == null) set.copy(numMinifigs = _minifigs.value[set.setNum]) else set
        update { it.copy(portfolio = it.portfolio + PortfolioEntry(withFigs, BuildStatus.PLANNED, System.currentTimeMillis())) }
        toast(tr("${set.name} toegevoegd aan je portfolio", "${set.name} added to your portfolio"))
        ensureMinifigs(withFigs)
    }

    fun setCurrent(setNum: String) {
        val d = repo.value
        if (d.timer.running && d.current?.set?.setNum != setNum) {
            toast(tr("Stop eerst de lopende timer", "Stop the running timer first"))
            return
        }
        update {
            it.copy(portfolio = it.portfolio.map { e ->
                when {
                    e.set.setNum == setNum -> e.copy(status = BuildStatus.CURRENT, completedMs = null)
                    e.status == BuildStatus.CURRENT -> e.copy(status = BuildStatus.PLANNED)
                    else -> e
                }
            })
        }
        toast(tr("Ingesteld als huidige build", "Set as current build"))
    }

    fun markDone(setNum: String) {
        val d = repo.value
        if (d.timer.running && d.current?.set?.setNum == setNum) repo.finishSession()
        val before = repo.value
        val now = System.currentTimeMillis()
        update {
            it.copy(portfolio = it.portfolio.map { e ->
                if (e.set.setNum == setNum) e.copy(status = BuildStatus.DONE, completedMs = now) else e
            })
        }
        val e = repo.value.entry(setNum) ?: return
        val badges = mutableListOf<Badge>()
        if (isSpeedBrick(e)) badges += badgeFor(AchievementKind.SPEED)
        badges += titleBadges(before)
        val first = e.sessions.minOfOrNull { it.startMs } ?: now
        _summary.value = Summary(
            title = tr("Set voltooid!", "Set finished!"),
            setName = e.set.name,
            bigLabel = tr("Totale bouwtijd", "Total build time"),
            big = Fmt.dur(e.totalSec),
            stat = Fmt.spb(e.totalSec, e.set.numParts),
            statLabel = tr("sec/steen", "sec/brick"),
            date = "${e.sessions.size}",
            dateLabel = if (e.sessions.size == 1) tr("sessie", "session") else tr("sessies · sinds ${Fmt.dateShort(first)}", "sessions · since ${Fmt.dateShort(first)}"),
            badges = badges,
            setNum = setNum,
        )
    }

    fun removeFromPortfolio(setNum: String) {
        val d = repo.value
        if (d.timer.running && d.current?.set?.setNum == setNum) {
            toast(tr("Stop eerst de lopende timer", "Stop the running timer first"))
            return
        }
        update { it.copy(portfolio = it.portfolio.filterNot { e -> e.set.setNum == setNum }) }
    }

    fun deleteSession(setNum: String, sessionId: Long) = update {
        it.copy(portfolio = it.portfolio.map { e ->
            if (e.set.setNum == setNum) e.copy(sessions = e.sessions.filterNot { s -> s.id == sessionId }) else e
        })
    }

    fun openManualSession(set: LegoSet? = null) {
        _manualSessionTarget.value = set ?: repo.value.current?.set ?: repo.value.portfolio.firstOrNull()?.set
        _showManualSessionDialog.value = true
    }

    fun closeManualSession() {
        _showManualSessionDialog.value = false
        _manualSessionTarget.value = null
    }

    /** @param startMs when the session began; it may not end in the future. */
    fun addManualSession(setNum: String, minutes: Int, startMs: Long) {
        if (minutes <= 0) return
        val start = minOf(startMs, System.currentTimeMillis() - minutes * 60_000L)
        val before = repo.value
        val res = repo.addManualSession(setNum, minutes.toLong(), start) ?: return
        val (entry, session) = res
        val total = repo.value.entry(setNum)?.totalSec ?: session.durSec
        val badges = mutableListOf<Badge>()
        val hour = Fmt.hourOf(session.startMs)
        if (isEveningHour(hour)) badges += badgeFor(AchievementKind.EVENING)
        if (isMorningHour(hour)) badges += badgeFor(AchievementKind.MORNING)
        badges += titleBadges(before)
        closeManualSession()
        _summary.value = Summary(
            title = tr("Sessie handmatig opgeslagen!", "Session added manually!"),
            setName = entry.set.name,
            bigLabel = tr("Toegevoegde tijd", "Time added"),
            big = Fmt.dur(session.durSec),
            stat = Fmt.dur(total),
            statLabel = tr("totaal deze set", "total for this set"),
            date = Fmt.dateShort(session.startMs),
            dateLabel = Fmt.range(session.startMs, session.durSec),
            badges = badges,
            setNum = entry.set.setNum,
            sessionId = if (entry.set.imgUrl != null) session.id else null,
        )
    }

    // ---------------------------------------------------------------- timer

    fun toggleTimer() {
        if (repo.startTimer()) {
            if (!repo.value.premium) _adRequests.tryEmit(Unit)
            return
        }
        val before = repo.value
        val (entry, session) = repo.finishSession() ?: return
        val total = repo.value.entry(entry.set.setNum)?.totalSec ?: session.durSec
        val badges = mutableListOf<Badge>()
        val hour = Fmt.hourOf(session.startMs)
        if (isEveningHour(hour)) badges += badgeFor(AchievementKind.EVENING)
        if (isMorningHour(hour)) badges += badgeFor(AchievementKind.MORNING)
        badges += titleBadges(before)
        // Seconds per brick only means something once the whole set is built, so show the running total instead.
        _summary.value = Summary(
            title = tr("Sessie opgeslagen!", "Session saved!"),
            setName = entry.set.name,
            bigLabel = tr("Deze sessie", "This session"),
            big = Fmt.dur(session.durSec),
            stat = Fmt.dur(total),
            statLabel = tr("totaal deze set", "total for this set"),
            date = Fmt.dateShort(session.startMs),
            dateLabel = Fmt.range(session.startMs, session.durSec),
            badges = badges,
            setNum = entry.set.setNum,
            // Comparing needs the picture of the finished set.
            sessionId = if (entry.set.imgUrl != null) session.id else null,
        )
    }

    private fun badgeFor(kind: AchievementKind): Badge {
        val count = achievement(repo.value, kind).count
        val desc = if (count in MILESTONES) tr("Nieuwe ster verdiend! ${count}× behaald", "New star earned! Done ${count}×") else tr("${kind.desc} · ${count}× behaald", "${kind.desc} · done ${count}×")
        return Badge(kind.title, desc)
    }

    private fun titleBadges(before: AppData): List<Badge> =
        newTitles(before, repo.value).map { ch -> Badge(tr("Nieuwe titel: ${ch.title}", "New title: ${ch.title}"), tr("Nieuwe ster! ${ch.kind.title} · ${ch.kind.fmt(ch.value)}", "New star! ${ch.kind.title} · ${ch.kind.fmt(ch.value)}")) }

    fun closeSummary() {
        estimateJob?.cancel()
        _estimate.value = EstimateState.Idle
        _photos.value = emptyList()
        _summary.value = null
    }

    // ---------------------------------------------------------------- community

    fun signIn(email: String, password: String) = authCall { community.signIn(email.trim(), password) }

    fun signUp(email: String, password: String, username: String) {
        val name = username.trim()
        if (!USERNAME.matches(name)) {
            _auth.value = AuthState(error = tr("Gebruikersnaam: 3 tot 20 tekens, alleen letters, cijfers, _ . en -", "Username: 3 to 20 characters, only letters, digits, _ . and -"))
            return
        }
        authCall { community.signUp(email.trim(), password, name) }
    }

    /** Stores the account [call] signs in to; a null account means the e-mail address must be confirmed first. */
    private fun authCall(call: suspend () -> Account?) {
        if (_auth.value.busy) return
        _auth.value = AuthState(busy = true)
        viewModelScope.launch {
            try {
                val account = call()
                if (account == null) {
                    _auth.value = AuthState(info = tr("Check je mail en bevestig je account. Log daarna hier in.", "Check your email to confirm your account, then sign in here."))
                    return@launch
                }
                update { it.copy(account = account) }
                _auth.value = AuthState()
                toast(tr("Ingelogd als ${account.username}", "Signed in as ${account.username}"))
                pushToCommunity()
                loadLeaderboard()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _auth.value = AuthState(error = e.message ?: tr("Er ging iets mis.", "Something went wrong."))
            }
        }
    }

    fun clearAuthMessage() {
        if (!_auth.value.busy) _auth.value = AuthState()
    }

    fun signOut() {
        val account = repo.value.account ?: return
        update { it.copy(account = null) }
        viewModelScope.launch { runCatching { community.signOut(account) } }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                withAuth { community.deleteAccount(it) }
                update { it.copy(account = null) }
                toast(tr("Je account en gedeelde builds zijn verwijderd", "Your account and shared builds have been deleted"))
                loadLeaderboard()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: tr("Verwijderen mislukt.", "Couldn't delete."))
            }
        }
    }

    /** Runs [call] with a valid access token, refreshing it when it is about to expire or the server rejects it. */
    private suspend fun <R> withAuth(call: suspend (Account) -> R): R {
        val account = repo.value.account ?: throw ApiException(tr("Log eerst in.", "Sign in first."))
        val fresh = if (account.expiresAtMs - System.currentTimeMillis() < 60_000) refreshAccount(account) else account
        return try {
            call(fresh)
        } catch (e: AuthExpiredException) {
            call(refreshAccount(fresh))
        }
    }

    /** Refresh tokens are single use, so concurrent callers share one refresh. Signs out when the session is gone. */
    private suspend fun refreshAccount(stale: Account): Account = refreshLock.withLock {
        val current = repo.value.account ?: throw AuthExpiredException()
        if (current.accessToken != stale.accessToken) return@withLock current
        try {
            community.refresh(current).also { fresh -> update { it.copy(account = fresh) } }
        } catch (e: AuthExpiredException) {
            update { it.copy(account = null) }
            toast(tr("Je bent uitgelogd. Log opnieuw in om je builds te delen.", "You've been signed out. Sign in again to share your builds."))
            throw e
        }
    }

    /** Publishes stats and builds a moment after the portfolio changes, and once at start-up. */
    @OptIn(FlowPreview::class)
    private fun syncCommunity() {
        if (!community.configured) return
        viewModelScope.launch {
            repo.data.map { it.portfolio }.distinctUntilChanged().debounce(2_000).collect { pushToCommunity() }
        }
    }

    private suspend fun pushToCommunity() = syncLock.withLock {
        if (repo.value.account == null) return@withLock
        try {
            withAuth { account ->
                // Never publish before the backup is in: an empty fresh install would wipe the builds online.
                if (!account.restored) restoreBackup(account)
                val d = repo.value
                community.pushStats(account, builderStats(d))
                community.pushBuilds(account, d.portfolio)
                community.pushBackup(account, d.portfolio)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or a server hiccup: the next change or app start tries again.
        }
    }

    /** Merges the portfolio saved online into this device's, then marks the account as restored. */
    private suspend fun restoreBackup(account: Account) {
        val backup = community.fetchBackup(account)
        var added = 0
        update { d ->
            val remote = if (backup.fromBuilds) backup.portfolio.filter { r -> d.entry(r.set.setNum) == null } else backup.portfolio
            val merged = mergePortfolios(d.portfolio, remote)
            added = merged.sumOf { it.sessions.size } - d.portfolio.sumOf { it.sessions.size }
            d.copy(
                portfolio = merged,
                account = d.account?.let { if (it.userId == account.userId) it.copy(restored = true) else it },
            )
        }
        if (added > 0) toast(tr("$added bouwsessie(s) teruggezet uit je online backup", "Restored $added build session(s) from your online backup"))
    }

    fun ensureLeaderboard() {
        val l = _leaderboard.value
        if (l.players.isEmpty() && !l.loading && l.error == null) loadLeaderboard()
    }

    fun setLeaderboardSort(sort: LeaderboardSort) {
        if (sort == _leaderboard.value.sort) return
        _leaderboard.update { it.copy(sort = sort, players = emptyList()) }
        loadLeaderboard()
    }

    fun loadLeaderboard() {
        if (!community.configured) return
        leaderboardJob?.cancel()
        _leaderboard.update { it.copy(loading = true, error = null) }
        leaderboardJob = viewModelScope.launch {
            try {
                val players = community.leaderboard(_leaderboard.value.sort)
                _leaderboard.update { it.copy(players = players, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _leaderboard.update { it.copy(loading = false, error = e.message ?: tr("Er ging iets mis.", "Something went wrong.")) }
            }
        }
    }

    fun openPlayer(p: Player) = push(Screen.Player(p.id, p.username))

    /** Loads a builder's profile and builds, unless they are already shown. */
    fun ensurePlayer(id: String) {
        val current = _player.value
        if (current?.id == id && current.error == null) return
        playerJob?.cancel()
        _player.value = PlayerState(id)
        playerJob = viewModelScope.launch {
            try {
                // coroutineScope turns a failed request into an exception here instead of a crash.
                _player.value = coroutineScope {
                    val profile = async { community.player(id) }
                    val builds = async { community.builds(id) }
                    PlayerState(id, profile.await(), builds.await(), loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _player.value = PlayerState(id, loading = false, error = e.message ?: tr("Er ging iets mis.", "Something went wrong."))
            }
        }
    }

    // ---------------------------------------------------------------- AI progress estimate

    fun addPhotos(uris: List<Uri>) {
        _photos.update { (it + uris).take(MAX_PHOTOS) }
    }

    fun removePhoto(uri: Uri) {
        _photos.update { it - uri }
    }

    fun loadModels() {
        if (_models.value.models.isNotEmpty() && !_models.value.loading) return
        modelsJob?.cancel()
        _models.value = ModelsState(loading = true)
        modelsJob = viewModelScope.launch {
            try {
                val list = openRouter.visionModels()
                _models.value = ModelsState(models = list)
            } catch (e: Exception) {
                _models.value = ModelsState(error = e.message ?: tr("Mislukt om modellen te laden.", "Couldn't load models."))
            }
        }
    }

    fun openEstimate(setNum: String, sessionId: Long) {
        val entry = repo.value.entry(setNum) ?: return
        val session = entry.sessions.firstOrNull { it.id == sessionId } ?: return
        _photos.value = emptyList()
        _estimate.value = EstimateState.Idle
        _estimateTarget.value = entry to session
    }

    fun closeEstimateDialog() {
        estimateJob?.cancel()
        _estimate.value = EstimateState.Idle
        _photos.value = emptyList()
        _estimateTarget.value = null
    }

    /** Sends the photos to the OpenRouter model and stores the estimate on the summary or target session. */
    fun estimateProgress() {
        val target = _estimateTarget.value
        val sum = _summary.value
        val (setNum, sessionId) = when {
            target != null -> target.first.set.setNum to target.second.id
            sum != null && sum.sessionId != null -> sum.setNum to sum.sessionId
            else -> return
        }
        val photosList = _photos.value
        if (photosList.isEmpty()) return
        val d = repo.value
        val entry = d.entry(setNum) ?: return
        if (d.openRouterKey.isBlank()) {
            _estimate.value = EstimateState.Error(tr("Stel eerst een OpenRouter API-sleutel in bij Instellingen.", "Add an OpenRouter API key in Settings first."))
            return
        }
        estimateJob?.cancel()
        _estimate.value = EstimateState.Loading
        estimateJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            try {
                val encoded = photosList.map { ImageUtils.encodeForUpload(ctx, it) }
                val build = BuildContext(entry.set, entry.totalSec, entry.sessions.size)
                val res = openRouter.estimate(d.openRouterKey, d.openRouterModel, build, encoded)
                repo.setSessionProgress(setNum, sessionId, res.percent, res.note.ifBlank { null })
                if (_estimateTarget.value?.second?.id == sessionId) {
                    val updatedEntry = repo.value.entry(setNum)
                    val updatedSession = updatedEntry?.sessions?.firstOrNull { it.id == sessionId }
                    if (updatedEntry != null && updatedSession != null) {
                        _estimateTarget.value = updatedEntry to updatedSession
                    }
                }
                _estimate.value = EstimateState.Result(res.percent, res.note, res.confidence)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estimate.value = EstimateState.Error(e.message ?: tr("Er ging iets mis.", "Something went wrong."))
            } finally {
                ImageUtils.clearCameraCache(ctx)
            }
        }
    }

    fun resetEstimate() {
        _estimate.value = EstimateState.Idle
    }
}

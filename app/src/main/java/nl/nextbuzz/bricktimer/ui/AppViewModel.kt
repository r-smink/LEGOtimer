package nl.nextbuzz.bricktimer.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.nextbuzz.bricktimer.BuildConfig
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.BuildStatus
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.data.PortfolioEntry
import nl.nextbuzz.bricktimer.data.RebrickableApi
import nl.nextbuzz.bricktimer.data.Repository
import nl.nextbuzz.bricktimer.data.Theme
import nl.nextbuzz.bricktimer.data.ThemeMode
import nl.nextbuzz.bricktimer.logic.AchievementKind
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.logic.MILESTONES
import nl.nextbuzz.bricktimer.logic.achievement
import nl.nextbuzz.bricktimer.logic.isEveningHour
import nl.nextbuzz.bricktimer.logic.isMorningHour
import nl.nextbuzz.bricktimer.logic.isSpeedBrick
import nl.nextbuzz.bricktimer.logic.newTitles

sealed interface Screen {
    data object Timer : Screen
    data object Catalog : Screen
    data object Portfolio : Screen
    data object Achievements : Screen
    data object History : Screen
    data class Detail(val set: LegoSet) : Screen
}

/** A theme as offered in the filter, labelled with its parent path ("Star Wars › Ultimate Collector Series"). */
data class ThemeOption(val id: Int, val label: String)

/** Piece-count buckets for the catalog filter; bounds are inclusive. */
enum class PartsRange(val label: String, val min: Int, val max: Int?) {
    TINY("Tot 250", 1, 249),
    SMALL("250 – 999", 250, 999),
    MEDIUM("1.000 – 2.499", 1_000, 2_499),
    LARGE("2.500 – 4.999", 2_500, 4_999),
    HUGE("5.000 – 9.999", 5_000, 9_999),
    MEGA("10.000+", 10_000, null),
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
)

private val SET_NUMBER = Regex("""\d{3,7}(-\d+)?""")

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    private val store = repo.store
    private val api = RebrickableApi()

    /** Shared with the home screen widget, so timer changes there show up here live. */
    val data: StateFlow<AppData> = repo.data

    private val _catalog = MutableStateFlow(CatalogState())
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _summary = MutableStateFlow<Summary?>(null)
    val summary: StateFlow<Summary?> = _summary.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

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
            _catalog.update { it.copy(loading = false, error = e.message ?: "Er ging iets mis.") }
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
        toast("${set.name} toegevoegd aan je portfolio")
        ensureMinifigs(withFigs)
    }

    fun setCurrent(setNum: String) {
        val d = repo.value
        if (d.timer.running && d.current?.set?.setNum != setNum) {
            toast("Stop eerst de lopende timer")
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
        toast("Ingesteld als huidige build")
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
            title = "Set voltooid!",
            setName = e.set.name,
            bigLabel = "Totale bouwtijd",
            big = Fmt.dur(e.totalSec),
            stat = Fmt.spb(e.totalSec, e.set.numParts),
            statLabel = "sec/steen",
            date = "${e.sessions.size}",
            dateLabel = if (e.sessions.size == 1) "sessie" else "sessies · sinds ${Fmt.dateShort(first)}",
            badges = badges,
        )
    }

    fun removeFromPortfolio(setNum: String) {
        val d = repo.value
        if (d.timer.running && d.current?.set?.setNum == setNum) {
            toast("Stop eerst de lopende timer")
            return
        }
        update { it.copy(portfolio = it.portfolio.filterNot { e -> e.set.setNum == setNum }) }
    }

    fun deleteSession(setNum: String, sessionId: Long) = update {
        it.copy(portfolio = it.portfolio.map { e ->
            if (e.set.setNum == setNum) e.copy(sessions = e.sessions.filterNot { s -> s.id == sessionId }) else e
        })
    }

    // ---------------------------------------------------------------- timer

    fun toggleTimer() {
        if (repo.startTimer()) return
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
            title = "Sessie opgeslagen!",
            setName = entry.set.name,
            bigLabel = "Deze sessie",
            big = Fmt.dur(session.durSec),
            stat = Fmt.dur(total),
            statLabel = "totaal deze set",
            date = Fmt.dateShort(session.startMs),
            dateLabel = Fmt.range(session.startMs, session.durSec),
            badges = badges,
        )
    }

    private fun badgeFor(kind: AchievementKind): Badge {
        val count = achievement(repo.value, kind).count
        val desc = if (count in MILESTONES) "Nieuwe ster verdiend! ${count}× behaald" else "${kind.desc} · ${count}× behaald"
        return Badge(kind.title, desc)
    }

    private fun titleBadges(before: AppData): List<Badge> =
        newTitles(before, repo.value).map { ch -> Badge("Nieuwe titel: ${ch.title}", "Nieuwe ster! ${ch.kind.title} · ${ch.kind.fmt(ch.value)}") }

    fun closeSummary() {
        _summary.value = null
    }
}

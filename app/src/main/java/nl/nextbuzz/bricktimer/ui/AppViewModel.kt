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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.nextbuzz.bricktimer.BuildConfig
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.BuildStatus
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.data.PortfolioEntry
import nl.nextbuzz.bricktimer.data.RebrickableApi
import nl.nextbuzz.bricktimer.data.Session
import nl.nextbuzz.bricktimer.data.Store
import nl.nextbuzz.bricktimer.data.ThemeMode
import nl.nextbuzz.bricktimer.data.TimerState
import nl.nextbuzz.bricktimer.logic.AchievementKind
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.logic.MILESTONES
import nl.nextbuzz.bricktimer.logic.achievement
import nl.nextbuzz.bricktimer.logic.isEveningHour
import nl.nextbuzz.bricktimer.logic.isMorningHour
import nl.nextbuzz.bricktimer.logic.isSpeedBrick

sealed interface Screen {
    data object Timer : Screen
    data object Catalog : Screen
    data object Portfolio : Screen
    data object Achievements : Screen
    data object History : Screen
    data class Detail(val set: LegoSet) : Screen
}

data class CatalogState(
    val query: String = "",
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
    val spb: String,
    val date: String,
    val dateLabel: String,
    val badges: List<Badge>,
)

private val SET_NUMBER = Regex("""\d{3,7}(-\d+)?""")

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val api = RebrickableApi()
    private val saveLock = Mutex()

    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _catalog = MutableStateFlow(CatalogState())
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _summary = MutableStateFlow<Summary?>(null)
    val summary: StateFlow<Summary?> = _summary.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var themes: Map<Int, String> = store.loadThemes()
    private var loadJob: Job? = null
    private var themesJob: Job? = null

    /** Back stack; the app always opens on the timer. */
    val stack = mutableStateListOf<Screen>(Screen.Timer)
    val screen: Screen get() = stack.last()

    private fun update(f: (AppData) -> AppData) {
        _data.update(f)
        viewModelScope.launch(Dispatchers.IO) { saveLock.withLock { store.save(_data.value) } }
    }

    private fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

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

    val effectiveKey: String get() = _data.value.apiKey.ifBlank { BuildConfig.REBRICKABLE_API_KEY }

    fun setApiKey(key: String) {
        update { it.copy(apiKey = key.trim()) }
        _catalog.update { CatalogState(query = it.query) }
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
        if (reset && SET_NUMBER.matches(q)) {
            // Search can be very slow; a set number lookup usually answers much sooner.
            launch {
                val found = runCatching { api.getSet(key, if ('-' in q) q else "$q-1") }.getOrNull() ?: return@launch
                val named = found.copy(themeName = themes[found.themeId] ?: "")
                _catalog.update { c -> if (c.items.any { it.setNum == named.setNum }) c else c.copy(items = listOf(named) + c.items) }
            }
        }
        try {
            val res = api.searchSets(key, _catalog.value.query, page)
            val sets = res.results.map { it.copy(themeName = themes[it.themeId] ?: "") }
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
            launch(Dispatchers.IO) { store.saveThemes(loaded) }
            _catalog.update { c -> c.copy(items = c.items.map { it.copy(themeName = loaded[it.themeId] ?: it.themeName) }) }
            update { d ->
                d.copy(portfolio = d.portfolio.map { e ->
                    if (e.set.themeName.isBlank()) e.copy(set = e.set.copy(themeName = loaded[e.set.themeId] ?: "")) else e
                })
            }
        }
    }

    // ---------------------------------------------------------------- portfolio

    fun addToPortfolio(set: LegoSet) {
        if (_data.value.entry(set.setNum) != null) return
        update { it.copy(portfolio = it.portfolio + PortfolioEntry(set, BuildStatus.PLANNED, System.currentTimeMillis())) }
        toast("${set.name} toegevoegd aan je portfolio")
    }

    fun setCurrent(setNum: String) {
        val d = _data.value
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
        val d = _data.value
        if (d.timer.running && d.current?.set?.setNum == setNum) finishSession()
        val now = System.currentTimeMillis()
        update {
            it.copy(portfolio = it.portfolio.map { e ->
                if (e.set.setNum == setNum) e.copy(status = BuildStatus.DONE, completedMs = now) else e
            })
        }
        val e = _data.value.entry(setNum) ?: return
        val badges = mutableListOf<Badge>()
        if (isSpeedBrick(e)) badges += badgeFor(AchievementKind.SPEED)
        val first = e.sessions.minOfOrNull { it.startMs } ?: now
        _summary.value = Summary(
            title = "Set voltooid!",
            setName = e.set.name,
            bigLabel = "Totale bouwtijd",
            big = Fmt.dur(e.totalSec),
            spb = Fmt.spb(e.totalSec, e.set.numParts),
            date = "${e.sessions.size}",
            dateLabel = if (e.sessions.size == 1) "sessie" else "sessies · sinds ${Fmt.dateShort(first)}",
            badges = badges,
        )
    }

    fun removeFromPortfolio(setNum: String) {
        val d = _data.value
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
        val d = _data.value
        if (d.current == null) return
        if (!d.timer.running) {
            update { it.copy(timer = TimerState(running = true, startMs = System.currentTimeMillis())) }
            return
        }
        val (entry, session) = finishSession() ?: return
        val badges = mutableListOf<Badge>()
        val hour = Fmt.hourOf(session.startMs)
        if (isEveningHour(hour)) badges += badgeFor(AchievementKind.EVENING)
        if (isMorningHour(hour)) badges += badgeFor(AchievementKind.MORNING)
        _summary.value = Summary(
            title = "Sessie opgeslagen!",
            setName = entry.set.name,
            bigLabel = "Deze sessie",
            big = Fmt.dur(session.durSec),
            spb = Fmt.spb(session.durSec, entry.set.numParts),
            date = Fmt.dateShort(session.startMs),
            dateLabel = Fmt.range(session.startMs, session.durSec),
            badges = badges,
        )
    }

    /** Stops the running timer and stores the session on the current build. */
    private fun finishSession(): Pair<PortfolioEntry, Session>? {
        val d = _data.value
        val cur = d.current ?: return null
        if (!d.timer.running) return null
        val now = System.currentTimeMillis()
        val session = Session(id = now, startMs = d.timer.startMs, durSec = maxOf(1L, (now - d.timer.startMs + 500) / 1000))
        update {
            it.copy(
                timer = TimerState(),
                portfolio = it.portfolio.map { e -> if (e.set.setNum == cur.set.setNum) e.copy(sessions = e.sessions + session) else e },
            )
        }
        return cur to session
    }

    private fun badgeFor(kind: AchievementKind): Badge {
        val count = achievement(_data.value, kind).count
        val desc = if (count in MILESTONES) "Nieuwe ster verdiend! ${count}× behaald" else "${kind.desc} · ${count}× behaald"
        return Badge(kind.title, desc)
    }

    fun closeSummary() {
        _summary.value = null
    }
}

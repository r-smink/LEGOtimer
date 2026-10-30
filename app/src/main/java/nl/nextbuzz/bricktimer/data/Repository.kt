package nl.nextbuzz.bricktimer.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.nextbuzz.bricktimer.widget.TimerWidget

/**
 * Process-wide app state, shared by the UI and the home screen widget so a timer
 * started in one is immediately visible in the other.
 */
class Repository private constructor(private val app: Context) {
    val store = Store(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveLock = Mutex()

    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()
    val value: AppData get() = _data.value

    fun update(f: (AppData) -> AppData) {
        val before = _data.value
        _data.update(f)
        val after = _data.value
        if (after == before) return
        scope.launch { flush() }
        if (after.timer != before.timer || after.current != before.current) TimerWidget.refresh(app)
    }

    /** Writes the latest state to disk; await this before a broadcast receiver finishes. */
    suspend fun flush() = saveLock.withLock { store.save(_data.value) }

    /** Starts the timer on the current build. @return false when there is no current build or it already runs. */
    fun startTimer(): Boolean {
        val d = _data.value
        if (d.current == null || d.timer.running) return false
        update { it.copy(timer = TimerState(running = true, startMs = System.currentTimeMillis())) }
        return true
    }

    /** Stops the running timer and stores the session on the current build. */
    fun finishSession(): Pair<PortfolioEntry, Session>? {
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

    companion object {
        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context.applicationContext).also { instance = it } }
    }
}

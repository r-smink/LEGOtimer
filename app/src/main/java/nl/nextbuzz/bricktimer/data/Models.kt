package nl.nextbuzz.bricktimer.data

/** A LEGO set as returned by Rebrickable (`/lego/sets/`). */
data class LegoSet(
    val setNum: String,
    val name: String,
    val year: Int,
    val themeId: Int,
    val themeName: String,
    val numParts: Int,
    val imgUrl: String?,
    val setUrl: String?,
    /** Total minifigures (sum of quantities); null until fetched from Rebrickable. */
    val numMinifigs: Int? = null,
) {
    /** "75192-1" -> "75192"; other variants keep their suffix. */
    val displayNumber: String get() = setNum.removeSuffix("-1")
}

/** A Rebrickable theme; sub-themes point at their parent. */
data class Theme(val id: Int, val name: String, val parentId: Int?)

enum class BuildStatus { PLANNED, CURRENT, DONE }

data class Session(
    val id: Long,
    val startMs: Long,
    val durSec: Long,
    /** AI estimate of how far the build was after this session (0..100), from a photo. */
    val progressPct: Int? = null,
    val progressNote: String? = null,
)

data class PortfolioEntry(
    val set: LegoSet,
    val status: BuildStatus,
    val addedMs: Long,
    val completedMs: Long? = null,
    val sessions: List<Session> = emptyList(),
) {
    val totalSec: Long get() = sessions.sumOf { it.durSec }

    /** Most recent session with an AI progress estimate. */
    val latestProgress: Session? get() = sessions.lastOrNull { it.progressPct != null }
}

data class TimerState(val running: Boolean = false, val startMs: Long = 0L)

const val DEFAULT_OPENROUTER_MODEL = "google/gemini-2.5-flash"

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App language; SYSTEM follows the device. */
enum class AppLanguage(val tag: String?) { SYSTEM(null), NL("nl"), EN("en") }

/** Signed-in community account (Supabase); the access token is refreshed shortly before [expiresAtMs]. */
data class Account(
    val userId: String,
    val email: String,
    val username: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    /**
     * The cloud backup has been merged into this device's portfolio. Until then nothing is published,
     * so a fresh install signing in can never overwrite the builds stored online with an empty list.
     */
    val restored: Boolean = false,
)

/**
 * Combines this device's portfolio with the cloud backup without losing anything: sets missing here are
 * added, and sessions are joined by id. For a set on both sides the local status wins. Only one set can
 * be the current build, so extra ones from the backup become planned.
 */
fun mergePortfolios(local: List<PortfolioEntry>, remote: List<PortfolioEntry>): List<PortfolioEntry> {
    val remoteBySet = remote.associateBy { it.set.setNum }
    val merged = local.map { e ->
        val r = remoteBySet[e.set.setNum] ?: return@map e
        val ids = e.sessions.mapTo(HashSet()) { it.id }
        e.copy(
            addedMs = minOf(e.addedMs, r.addedMs),
            completedMs = e.completedMs ?: r.completedMs.takeIf { e.status == BuildStatus.DONE },
            sessions = (e.sessions + r.sessions.filter { it.id !in ids }).sortedBy { it.startMs },
        )
    } + remote.filter { r -> local.none { it.set.setNum == r.set.setNum } }
    var hasCurrent = false
    return merged.map { e ->
        if (e.status != BuildStatus.CURRENT) e
        else if (hasCurrent) e.copy(status = BuildStatus.PLANNED)
        else e.also { hasCurrent = true }
    }
}

data class AppData(
    val portfolio: List<PortfolioEntry> = emptyList(),
    val timer: TimerState = TimerState(),
    val apiKey: String = "",
    val openRouterKey: String = "",
    val openRouterModel: String = DEFAULT_OPENROUTER_MODEL,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val account: Account? = null,
    /** Bought the one-time ad-free upgrade; re-checked with Google Play on every launch. */
    val premium: Boolean = false,
) {
    val current: PortfolioEntry? get() = portfolio.firstOrNull { it.status == BuildStatus.CURRENT }
    fun entry(setNum: String): PortfolioEntry? = portfolio.firstOrNull { it.set.setNum == setNum }
}

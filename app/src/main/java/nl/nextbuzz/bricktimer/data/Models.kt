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
)

data class AppData(
    val portfolio: List<PortfolioEntry> = emptyList(),
    val timer: TimerState = TimerState(),
    val apiKey: String = "",
    val openRouterKey: String = "",
    val openRouterModel: String = DEFAULT_OPENROUTER_MODEL,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val account: Account? = null,
) {
    val current: PortfolioEntry? get() = portfolio.firstOrNull { it.status == BuildStatus.CURRENT }
    fun entry(setNum: String): PortfolioEntry? = portfolio.firstOrNull { it.set.setNum == setNum }
}

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
) {
    /** "75192-1" -> "75192"; other variants keep their suffix. */
    val displayNumber: String get() = setNum.removeSuffix("-1")
}

enum class BuildStatus { PLANNED, CURRENT, DONE }

data class Session(val id: Long, val startMs: Long, val durSec: Long)

data class PortfolioEntry(
    val set: LegoSet,
    val status: BuildStatus,
    val addedMs: Long,
    val completedMs: Long? = null,
    val sessions: List<Session> = emptyList(),
) {
    val totalSec: Long get() = sessions.sumOf { it.durSec }
}

data class TimerState(val running: Boolean = false, val startMs: Long = 0L)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppData(
    val portfolio: List<PortfolioEntry> = emptyList(),
    val timer: TimerState = TimerState(),
    val apiKey: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    val current: PortfolioEntry? get() = portfolio.firstOrNull { it.status == BuildStatus.CURRENT }
    fun entry(setNum: String): PortfolioEntry? = portfolio.firstOrNull { it.set.setNum == setNum }
}

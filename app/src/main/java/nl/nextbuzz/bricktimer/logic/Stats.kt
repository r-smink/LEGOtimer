package nl.nextbuzz.bricktimer.logic

import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.BuildStatus

/** Everything about a builder that the leaderboard ranks on; published with the community profile. */
data class BuilderStats(
    val totalSec: Long,
    val setsDone: Int,
    val bricksDone: Int,
    /** Fastest seconds per brick over finished sets; null until a timed set is finished. */
    val bestSpb: Double?,
    val stars: Int,
    val platinum: Int,
    val titles: Int,
    val rankTitle: String?,
    val evening: Int,
    val morning: Int,
    val speed: Int,
)

fun builderStats(data: AppData): BuilderStats {
    val habits = achievements(data)
    val challengeList = challenges(data)
    val titles = challengeList.sumOf { it.level }
    val done = data.portfolio.filter { it.status == BuildStatus.DONE }
    fun count(kind: AchievementKind) = habits.first { it.kind == kind }.count
    return BuilderStats(
        totalSec = data.portfolio.sumOf { it.totalSec },
        setsDone = done.size,
        bricksDone = done.sumOf { it.set.numParts },
        bestSpb = done.filter { it.sessions.isNotEmpty() && it.set.numParts > 0 }.minOfOrNull { it.totalSec.toDouble() / it.set.numParts },
        // Every challenge tier reached earns a star on top of the habit stars.
        stars = habits.sumOf { if (it.platinum) 0 else it.stars } + titles,
        platinum = habits.sumOf { if (it.platinum) 3 else 0 },
        titles = titles,
        rankTitle = challengeList.first { it.kind == ChallengeKind.RANK }.title,
        evening = count(AchievementKind.EVENING),
        morning = count(AchievementKind.MORNING),
        speed = count(AchievementKind.SPEED),
    )
}

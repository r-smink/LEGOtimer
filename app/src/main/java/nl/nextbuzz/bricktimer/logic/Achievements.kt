package nl.nextbuzz.bricktimer.logic

import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.BuildStatus
import nl.nextbuzz.bricktimer.data.PortfolioEntry

enum class AchievementKind(private val titleNl: String, private val titleEn: String, private val descNl: String, private val descEn: String) {
    EVENING("Avond Bouwer", "Night Owl", "Bouwsessies tussen 20:00 en 00:00", "Build sessions between 8 pm and midnight"),
    MORNING("Ochtend Bouwer", "Early Bird", "Bouwsessies tussen 06:00 en 10:00", "Build sessions between 6 am and 10 am"),
    SPEED("SpeedBrick", "SpeedBrick", "Sets afronden onder 10s per steen", "Finish sets under 10s per brick"),
    ;

    val title: String get() = tr(titleNl, titleEn)
    val desc: String get() = tr(descNl, descEn)
}

/** Star thresholds: 25 / 50 / 100 gold stars, 500 = three platinum stars. */
val MILESTONES = listOf(25, 50, 100, 500)
const val PLATINUM = 500
const val SPEED_LIMIT_SEC_PER_BRICK = 10.0

data class Achievement(val kind: AchievementKind, val count: Int) {
    val platinum: Boolean get() = count >= PLATINUM
    val stars: Int get() = when {
        count >= 100 -> 3
        count >= 50 -> 2
        count >= 25 -> 1
        else -> 0
    }
    val next: Int? get() = MILESTONES.firstOrNull { it > count }
    val progress: Float get() {
        val n = next ?: return 1f
        val prev = MILESTONES.getOrNull(MILESTONES.indexOf(n) - 1) ?: 0
        return (count - prev).toFloat() / (n - prev)
    }
    val nextLabel: String get() {
        val n = next ?: return tr("Platinum behaald", "Platinum reached")
        val goal = if (n == PLATINUM) "platinum" else "$n×"
        return tr("Nog ${n - count} tot $goal", "${n - count} more to $goal")
    }
}

fun isEveningHour(h: Int) = h in 20..23
fun isMorningHour(h: Int) = h in 6..9

/** A finished set qualifies for SpeedBrick when its total build time is under 10 seconds per brick. */
fun isSpeedBrick(e: PortfolioEntry) =
    e.status == BuildStatus.DONE && e.sessions.isNotEmpty() && e.set.numParts > 0 &&
        e.totalSec.toDouble() / e.set.numParts < SPEED_LIMIT_SEC_PER_BRICK

fun achievements(data: AppData): List<Achievement> {
    val sessions = data.portfolio.flatMap { it.sessions }
    return listOf(
        Achievement(AchievementKind.EVENING, sessions.count { isEveningHour(Fmt.hourOf(it.startMs)) }),
        Achievement(AchievementKind.MORNING, sessions.count { isMorningHour(Fmt.hourOf(it.startMs)) }),
        Achievement(AchievementKind.SPEED, data.portfolio.count(::isSpeedBrick)),
    )
}

fun achievement(data: AppData, kind: AchievementKind) = achievements(data).first { it.kind == kind }

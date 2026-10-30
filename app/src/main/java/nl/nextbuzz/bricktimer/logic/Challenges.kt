package nl.nextbuzz.bricktimer.logic

import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.BuildStatus
import java.time.Instant
import java.time.ZoneId

/** One level of a challenge: reaching [threshold] earns [title]. */
data class Tier(val threshold: Long, val title: String)

/** Tiered building challenges; each tier awards a title. [fmt] renders a value in the challenge's unit. */
enum class ChallengeKind(val title: String, val desc: String, val tiers: List<Tier>, val fmt: (Long) -> String) {
    RANK(
        "Bouwrang", "Volledig gebouwde sets",
        listOf(Tier(5, "Opperman"), Tier(20, "Vakman"), Tier(50, "Voorman"), Tier(100, "Uitvoerder"), Tier(250, "Brickmaster")),
        { if (it == 1L) "1 set" else "$it sets" },
    ),
    BRICKS(
        "Stenenteller", "Stenen verwerkt in voltooide sets",
        listOf(Tier(1_000, "Steenzetter"), Tier(10_000, "Metselaar"), Tier(50_000, "Torenbouwer"), Tier(100_000, "Wolkenkrabber"), Tier(250_000, "Stenenmagnaat")),
        { "${Fmt.pieces(it.toInt())} stenen" },
    ),
    HOURS(
        "Bouwuren", "Totale bouwtijd over alle sets",
        listOf(Tier(10, "Hobbyist"), Tier(50, "Fanaat"), Tier(100, "Doorzetter"), Tier(250, "Veteraan"), Tier(500, "Legende")),
        { "${it}u" },
    ),
    BIG_SET(
        "Groots bouwen", "Grootste voltooide set in stenen",
        listOf(Tier(500, "Projectbouwer"), Tier(1_000, "Kasteelbouwer"), Tier(2_500, "Megabouwer"), Tier(5_000, "Monumentbouwer"), Tier(10_000, "Wereldwonder")),
        { "${Fmt.pieces(it.toInt())} stenen" },
    ),
    THEMES(
        "Themaverzamelaar", "Verschillende thema's voltooid",
        listOf(Tier(3, "Ontdekker"), Tier(5, "Avonturier"), Tier(10, "Wereldreiziger"), Tier(20, "Multiversum")),
        { if (it == 1L) "1 thema" else "$it thema's" },
    ),
    MARATHON(
        "Marathon", "Langste bouwsessie in één ruk",
        listOf(Tier(60, "Volhouder"), Tier(120, "Marathonbouwer"), Tier(240, "IJzeren bouwer"), Tier(480, "Bouwmachine")),
        { if (it >= 60) "${it / 60}u ${(it % 60).toString().padStart(2, '0')}m" else "${it}m" },
    ),
    STREAK(
        "Bouwstreak", "Dagen op rij met een bouwsessie",
        listOf(Tier(3, "Op dreef"), Tier(7, "Weekstrijder"), Tier(14, "Onstuitbaar"), Tier(30, "Maandmeester")),
        { if (it == 1L) "1 dag" else "$it dagen" },
    ),
    MINIFIGS(
        "Minifig-verzamelaar", "Minifiguren in voltooide sets",
        listOf(Tier(10, "Figurant"), Tier(25, "Teamleider"), Tier(50, "Kapitein"), Tier(100, "Generaal"), Tier(250, "Minifig-koning")),
        { "$it minifiguren" },
    ),
}

data class Challenge(val kind: ChallengeKind, val value: Long) {
    /** Number of tiers reached. */
    val level: Int get() = kind.tiers.count { value >= it.threshold }
    val title: String? get() = kind.tiers.getOrNull(level - 1)?.title
    val next: Tier? get() = kind.tiers.getOrNull(level)
    val maxed: Boolean get() = next == null
    val progress: Float get() {
        val n = next ?: return 1f
        val prev = kind.tiers.getOrNull(level - 1)?.threshold ?: 0L
        return (value - prev).toFloat() / (n.threshold - prev)
    }
    val nextLabel: String get() {
        val n = next ?: return "Hoogste titel behaald"
        return "Nog ${kind.fmt(n.threshold - value)} tot ${n.title}"
    }
}

fun challenges(data: AppData): List<Challenge> {
    val done = data.portfolio.filter { it.status == BuildStatus.DONE }
    val sessions = data.portfolio.flatMap { it.sessions }
    return ChallengeKind.entries.map { kind ->
        val value: Long = when (kind) {
            ChallengeKind.RANK -> done.size.toLong()
            ChallengeKind.BRICKS -> done.sumOf { it.set.numParts.toLong() }
            ChallengeKind.HOURS -> sessions.sumOf { it.durSec } / 3600
            ChallengeKind.BIG_SET -> done.maxOfOrNull { it.set.numParts.toLong() } ?: 0L
            ChallengeKind.THEMES -> done.map { it.set.themeId }.distinct().size.toLong()
            ChallengeKind.MARATHON -> (sessions.maxOfOrNull { it.durSec } ?: 0L) / 60
            ChallengeKind.STREAK -> longestStreak(sessions.map { it.startMs })
            ChallengeKind.MINIFIGS -> done.sumOf { (it.set.numMinifigs ?: 0).toLong() }
        }
        Challenge(kind, value)
    }
}

/** Longest run of consecutive calendar days that each have at least one session. */
private fun longestStreak(starts: List<Long>): Long {
    val zone = ZoneId.systemDefault()
    val days = starts.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }.distinct().sorted()
    var best = 0L
    var run = 0L
    var prev = Long.MIN_VALUE
    for (d in days) {
        run = if (d == prev + 1) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }
    return best
}

/** Challenges whose level went up between [before] and [after]. */
fun newTitles(before: AppData, after: AppData): List<Challenge> {
    val old = challenges(before).associate { it.kind to it.level }
    return challenges(after).filter { it.level > (old[it.kind] ?: 0) }
}

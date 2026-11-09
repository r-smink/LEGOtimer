package nl.nextbuzz.bricktimer.logic

import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.BuildStatus
import java.time.Instant
import java.time.ZoneId

/** One level of a challenge: reaching [threshold] earns [title]. */
data class Tier(val threshold: Long, val titleNl: String, val titleEn: String) {
    val title: String get() = tr(titleNl, titleEn)
}

/** Tiered building challenges; each tier awards a title. [fmt] renders a value in the challenge's unit. */
enum class ChallengeKind(
    private val titleNl: String, private val titleEn: String,
    private val descNl: String, private val descEn: String,
    val tiers: List<Tier>, val fmt: (Long) -> String,
) {
    RANK(
        "Bouwrang", "Builder rank", "Volledig gebouwde sets", "Fully built sets",
        listOf(Tier(5, "Opperman", "Apprentice"), Tier(20, "Vakman", "Craftsman"), Tier(50, "Voorman", "Foreman"), Tier(100, "Uitvoerder", "Site Manager"), Tier(250, "Brickmaster", "Brickmaster")),
        { if (it == 1L) "1 set" else "$it sets" },
    ),
    BRICKS(
        "Stenenteller", "Brick counter", "Stenen verwerkt in voltooide sets", "Bricks used in finished sets",
        listOf(Tier(1_000, "Steenzetter", "Bricklayer"), Tier(10_000, "Metselaar", "Mason"), Tier(50_000, "Torenbouwer", "Tower Builder"), Tier(100_000, "Wolkenkrabber", "Skyscraper"), Tier(250_000, "Stenenmagnaat", "Brick Tycoon")),
        { "${Fmt.pieces(it.toInt())} ${tr("stenen", "bricks")}" },
    ),
    HOURS(
        "Bouwuren", "Build hours", "Totale bouwtijd over alle sets", "Total build time across all sets",
        listOf(Tier(10, "Hobbyist", "Hobbyist"), Tier(50, "Fanaat", "Enthusiast"), Tier(100, "Doorzetter", "Persistent"), Tier(250, "Veteraan", "Veteran"), Tier(500, "Legende", "Legend")),
        { "${it}${tr("u", "h")}" },
    ),
    BIG_SET(
        "Groots bouwen", "Building big", "Grootste voltooide set in stenen", "Largest finished set in bricks",
        listOf(Tier(500, "Projectbouwer", "Project Builder"), Tier(1_000, "Kasteelbouwer", "Castle Builder"), Tier(2_500, "Megabouwer", "Mega Builder"), Tier(5_000, "Monumentbouwer", "Monument Builder"), Tier(10_000, "Wereldwonder", "World Wonder")),
        { "${Fmt.pieces(it.toInt())} ${tr("stenen", "bricks")}" },
    ),
    THEMES(
        "Themaverzamelaar", "Theme collector", "Verschillende thema's voltooid", "Different themes finished",
        listOf(Tier(3, "Ontdekker", "Explorer"), Tier(5, "Avonturier", "Adventurer"), Tier(10, "Wereldreiziger", "Globetrotter"), Tier(20, "Multiversum", "Multiverse")),
        { if (it == 1L) tr("1 thema", "1 theme") else tr("$it thema's", "$it themes") },
    ),
    MARATHON(
        "Marathon", "Marathon", "Langste bouwsessie in één ruk", "Longest build session in one go",
        listOf(Tier(60, "Volhouder", "Stayer"), Tier(120, "Marathonbouwer", "Marathon Builder"), Tier(240, "IJzeren bouwer", "Iron Builder"), Tier(480, "Bouwmachine", "Building Machine")),
        { if (it >= 60) "${it / 60}${tr("u", "h")} ${(it % 60).toString().padStart(2, '0')}m" else "${it}m" },
    ),
    STREAK(
        "Bouwstreak", "Build streak", "Dagen op rij met een bouwsessie", "Days in a row with a build session",
        listOf(Tier(3, "Op dreef", "On a Roll"), Tier(7, "Weekstrijder", "Week Warrior"), Tier(14, "Onstuitbaar", "Unstoppable"), Tier(30, "Maandmeester", "Month Master")),
        { if (it == 1L) tr("1 dag", "1 day") else tr("$it dagen", "$it days") },
    ),
    MINIFIGS(
        "Minifig-verzamelaar", "Minifig collector", "Minifiguren in voltooide sets", "Minifigures in finished sets",
        listOf(Tier(10, "Figurant", "Extra"), Tier(25, "Teamleider", "Team Leader"), Tier(50, "Kapitein", "Captain"), Tier(100, "Generaal", "General"), Tier(250, "Minifig-koning", "Minifig King")),
        { "$it ${tr("minifiguren", "minifigures")}" },
    ),
    ;

    val title: String get() = tr(titleNl, titleEn)
    val desc: String get() = tr(descNl, descEn)
}

/** Shows a rank title from another player (stored in their language) in the current language. */
fun localizedTitle(title: String): String =
    ChallengeKind.entries.flatMap { it.tiers }.firstOrNull { it.titleNl == title || it.titleEn == title }?.title ?: title

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
        val n = next ?: return tr("Hoogste titel behaald", "Highest title reached")
        return tr("Nog ${kind.fmt(n.threshold - value)} tot ${n.title}", "${kind.fmt(n.threshold - value)} more to ${n.title}")
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

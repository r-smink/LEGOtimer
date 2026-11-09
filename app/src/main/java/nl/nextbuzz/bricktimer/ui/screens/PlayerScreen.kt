package nl.nextbuzz.bricktimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import nl.nextbuzz.bricktimer.data.BuildStatus
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.data.Player
import nl.nextbuzz.bricktimer.data.RemoteBuild
import nl.nextbuzz.bricktimer.logic.AchievementKind
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.logic.localizedTitle
import nl.nextbuzz.bricktimer.logic.tr
import nl.nextbuzz.bricktimer.ui.EmptyCard
import nl.nextbuzz.bricktimer.ui.Grid2
import nl.nextbuzz.bricktimer.ui.OutlineButton
import nl.nextbuzz.bricktimer.ui.Overline
import nl.nextbuzz.bricktimer.ui.PlayerState
import nl.nextbuzz.bricktimer.ui.SetThumb
import nl.nextbuzz.bricktimer.ui.StatTile
import nl.nextbuzz.bricktimer.ui.StatusPill
import nl.nextbuzz.bricktimer.ui.T
import nl.nextbuzz.bricktimer.ui.card
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors

/** Another builder's public profile: rank, awards, stats and builds. */
@Composable
fun PlayerScreen(
    username: String,
    state: PlayerState?,
    isMe: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenSet: (LegoSet) -> Unit,
) {
    val c = LocalAppColors.current
    val player = state?.player
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.card)
                        .border(2.dp, c.border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, tr("Terug", "Back"), tint = c.text) }
                Column(Modifier.weight(1f)) {
                    T(if (isMe) "$username ${tr("(jij)", "(you)")}" else username, 22.sp, weight = FontWeight.Bold, letterSpacing = (-0.02).em, maxLines = 1)
                    T("${state?.builds?.size ?: 0} builds in portfolio", 13.sp, color = c.textMuted)
                }
            }
        }
        when {
            state == null || state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            }
            state.error != null -> item {
                EmptyCard {
                    T(state.error, 13.sp, color = c.textMuted, align = TextAlign.Center)
                    OutlineButton(tr("Opnieuw", "Retry"), onRetry, Modifier.padding(top = 12.dp))
                }
            }
            player == null -> item {
                EmptyCard { T(tr("Dit account bestaat niet meer.", "This account no longer exists."), 13.sp, color = c.textMuted, align = TextAlign.Center) }
            }
            else -> {
                item { RankCard(player) }
                item {
                    val s = player.stats
                    Grid2(
                        listOf<@Composable (Modifier) -> Unit>(
                            { m -> StatTile(tr("Bouwtijd", "Build time"), Fmt.dur(s.totalSec), m) },
                            { m -> StatTile(tr("Sets af", "Sets done"), s.setsDone.toString(), m) },
                            { m -> StatTile(tr("Stenen", "Bricks"), Fmt.pieces(s.bricksDone), m) },
                            { m -> StatTile(tr("Snelste", "Fastest"), s.bestSpb?.let { Fmt.spb(it) + tr("/st", "/brick") } ?: "—", m) },
                        ),
                    )
                }
                item { Overline(tr("Bouwgewoontes", "Build habits"), Modifier.padding(start = 2.dp, top = 12.dp)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Habit(Icons.Rounded.NightsStay, AchievementKind.EVENING.title, player.stats.evening, Modifier.weight(1f))
                        Habit(Icons.Rounded.WbSunny, AchievementKind.MORNING.title, player.stats.morning, Modifier.weight(1f))
                        Habit(Icons.Rounded.Speed, AchievementKind.SPEED.title, player.stats.speed, Modifier.weight(1f))
                    }
                }
                item { Overline("Builds", Modifier.padding(start = 2.dp, top = 12.dp)) }
                if (state.builds.isEmpty()) item {
                    T(tr("Nog geen sets in dit portfolio.", "No sets in this portfolio yet."), 13.sp, Modifier.fillMaxWidth().card(12.dp).padding(24.dp), color = c.textMuted, align = TextAlign.Center)
                }
                items(state.builds, key = { it.set.setNum }) { b -> BuildRow(b) { onOpenSet(b.set) } }
            }
        }
    }
}

@Composable
private fun RankCard(p: Player) {
    val shape = RoundedCornerShape(16.dp)
    val divider = Color.White.copy(alpha = .25f)
    Column(
        Modifier.fillMaxWidth().shadow(10.dp, shape, ambientColor = Brand.Glow, spotColor = Brand.Glow)
            .clip(shape).background(Brand.gradient).padding(18.dp),
    ) {
        Overline(tr("Bouwrang", "Builder rank"), color = Color.White.copy(alpha = .85f), size = 11.sp)
        Row(Modifier.padding(top = 2.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.MilitaryTech, null, tint = Color.White, modifier = Modifier.size(28.dp))
            T(p.stats.rankTitle?.let(::localizedTitle) ?: tr("Nog geen titel", "No title yet"), 24.sp, weight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
        Row(Modifier.height(IntrinsicSize.Min)) {
            RankStat(p.stats.stars, tr("Sterren", "Stars"), Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(divider))
            RankStat(p.stats.platinum, "Platinum", Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(divider))
            RankStat(p.stats.titles, tr("Titels", "Titles"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun RankStat(value: Int, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(value.toString(), 26.sp, weight = FontWeight.Bold, color = Color.White)
        Overline(label, color = Color.White.copy(alpha = .85f), size = 11.sp)
    }
}

@Composable
private fun Habit(icon: ImageVector, title: String, count: Int, modifier: Modifier) {
    val c = LocalAppColors.current
    Column(modifier.card(12.dp).padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        T("${count}×", 17.sp, Modifier.padding(top = 4.dp), weight = FontWeight.Bold)
        T(title, 11.sp, color = c.textMuted, maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
private fun BuildRow(b: RemoteBuild, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().card(13.dp, onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        SetThumb(b.set, 46.dp, 11.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(b.set.name, 14.5.sp, weight = FontWeight.SemiBold, maxLines = 1)
            StatusPill(b.status)
        }
        if (b.totalSec > 0) {
            Column(horizontalAlignment = Alignment.End) {
                T(Fmt.dur(b.totalSec), 15.sp, weight = FontWeight.Bold)
                if (b.status == BuildStatus.DONE) T("${Fmt.spb(b.totalSec, b.set.numParts)}${tr("/steen", "/brick")}", 11.sp, color = c.textSubtle)
                else T("${b.sessions} ${if (b.sessions == 1) tr("sessie", "session") else tr("sessies", "sessions")}", 11.sp, color = c.textSubtle)
            }
        }
    }
}

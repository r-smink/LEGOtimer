package nl.nextbuzz.bricktimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.logic.Achievement
import nl.nextbuzz.bricktimer.logic.AchievementKind
import nl.nextbuzz.bricktimer.logic.Challenge
import nl.nextbuzz.bricktimer.logic.ChallengeKind
import nl.nextbuzz.bricktimer.logic.achievements
import nl.nextbuzz.bricktimer.logic.challenges
import nl.nextbuzz.bricktimer.ui.Overline
import nl.nextbuzz.bricktimer.ui.ScreenTitle
import nl.nextbuzz.bricktimer.ui.T
import nl.nextbuzz.bricktimer.ui.card
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors

@Composable
fun AchievementsScreen(data: AppData) {
    val list = achievements(data)
    val challengeList = challenges(data)
    val titles = challengeList.sumOf { it.level }
    // Every challenge tier reached earns a star on top of the habit stars.
    val totalStars = list.sumOf { if (it.platinum) 0 else it.stars } + titles
    val platinumStars = list.sumOf { if (it.platinum) 3 else 0 }
    val rank = challengeList.first { it.kind == ChallengeKind.RANK }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp)) {
        ScreenTitle("Awards", "Verdien sterren en titels met bouwen", Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 16.dp))

        val shape = RoundedCornerShape(16.dp)
        val divider = Color.White.copy(alpha = .25f)
        Column(
            Modifier.fillMaxWidth().padding(bottom = 20.dp).shadow(10.dp, shape, ambientColor = Brand.Glow, spotColor = Brand.Glow)
                .clip(shape).background(Brand.gradient).padding(18.dp),
        ) {
            Overline("Jouw bouwrang", color = Color.White.copy(alpha = .85f), size = 11.sp)
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.MilitaryTech, null, tint = Color.White, modifier = Modifier.size(28.dp))
                T(rank.title ?: "Nog geen titel", 26.sp, weight = FontWeight.Bold, color = Color.White)
            }
            T(
                "${rank.kind.fmt(rank.value)} gebouwd · ${rank.nextLabel.replaceFirstChar { it.lowercase() }}", 13.sp,
                Modifier.padding(top = 4.dp, bottom = 10.dp), color = Color.White.copy(alpha = .85f),
            )
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(divider)) {
                Box(Modifier.fillMaxWidth(rank.progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Color.White))
            }
            Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(divider))
            Row(Modifier.height(IntrinsicSize.Min)) {
                HeroStat(totalStars, "Sterren", Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(divider))
                HeroStat(platinumStars, "Platinum", Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(divider))
                HeroStat(titles, "Titels", Modifier.weight(1f))
            }
        }

        Overline("Challenges", Modifier.padding(start = 2.dp, bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { challengeList.forEach { ChallengeCard(it) } }

        Overline("Bouwgewoontes", Modifier.padding(start = 2.dp, top = 24.dp, bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { list.forEach { AchievementCard(it) } }
    }
}

private fun challengeStyle(kind: ChallengeKind) = when (kind) {
    ChallengeKind.RANK -> Icons.Rounded.MilitaryTech to listOf(0xFFF97316, 0xFFDB2777)
    ChallengeKind.BRICKS -> Icons.Rounded.Widgets to listOf(0xFFEF4444, 0xFFB91C1C)
    ChallengeKind.HOURS -> Icons.Rounded.HourglassBottom to listOf(0xFF0EA5E9, 0xFF1D4ED8)
    ChallengeKind.BIG_SET -> Icons.Rounded.Apartment to listOf(0xFF64748B, 0xFF1E293B)
    ChallengeKind.THEMES -> Icons.Rounded.Explore to listOf(0xFF14B8A6, 0xFF0F766E)
    ChallengeKind.MARATHON -> Icons.AutoMirrored.Rounded.DirectionsRun to listOf(0xFF8B5CF6, 0xFF6D28D9)
    ChallengeKind.STREAK -> Icons.Rounded.LocalFireDepartment to listOf(0xFFF59E0B, 0xFFEA580C)
    ChallengeKind.MINIFIGS -> Icons.Rounded.Groups to listOf(0xFFEAB308, 0xFFCA8A04)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeCard(ch: Challenge) {
    val c = LocalAppColors.current
    val (icon, colors) = challengeStyle(ch.kind)
    Column(Modifier.fillMaxWidth().card(16.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(colors.map { Color(it) })), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                T(ch.kind.title, 16.sp, weight = FontWeight.Bold)
                T(ch.kind.desc, 12.5.sp, color = c.textMuted, lineHeight = 17.sp)
            }
            // One star per tier; all stars turn platinum once the highest title is reached.
            val emptyStar = if (c.isDark) Color.White.copy(alpha = .15f) else Color(0xFFE5E7EB)
            Row {
                for (i in 1..ch.kind.tiers.size) {
                    val col = when {
                        ch.maxed -> platinumColor
                        i <= ch.level -> gold
                        else -> emptyStar
                    }
                    Icon(Icons.Rounded.Star, null, tint = col, modifier = Modifier.size(17.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            T(ch.title ?: ch.kind.fmt(ch.value), 13.sp, Modifier.weight(1f), weight = FontWeight.SemiBold, maxLines = 1)
            T(ch.nextLabel, 12.sp, color = c.textSubtle, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.track)) {
            Box(
                Modifier.fillMaxWidth(ch.progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                    .background(if (ch.maxed) Brand.gradient else Brush.linearGradient(listOf(gold, gold))),
            )
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ch.kind.tiers.forEach { t -> Milestone("${ch.kind.fmt(t.threshold)} · ${t.title}", ch.value >= t.threshold) }
        }
    }
}

@Composable
private fun HeroStat(value: Int, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(value.toString(), 30.sp, weight = FontWeight.Bold, color = Color.White)
        Overline(label, color = Color.White.copy(alpha = .85f), size = 11.sp)
    }
}

private val gold = Brand.Gold
private val platinumColor = Brand.Lava2

@Composable
private fun AchievementCard(a: Achievement) {
    val c = LocalAppColors.current
    val (icon, chip) = when (a.kind) {
        AchievementKind.EVENING -> Icons.Rounded.NightsStay to Brush.linearGradient(listOf(Color(0xFFDB2777), Color(0xFF831843)))
        AchievementKind.MORNING -> Icons.Rounded.WbSunny to Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFF97316)))
        AchievementKind.SPEED -> Icons.Rounded.Speed to Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFF9F1239)))
    }
    val emptyStar = if (c.isDark) Color.White.copy(alpha = .15f) else Color(0xFFE5E7EB)
    Column(Modifier.fillMaxWidth().card(16.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(chip), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                T(a.kind.title, 16.sp, weight = FontWeight.Bold)
                T(a.kind.desc, 12.5.sp, color = c.textMuted, lineHeight = 17.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (i in 1..3) {
                    val col = when {
                        a.platinum -> platinumColor
                        i <= a.stars -> gold
                        else -> emptyStar
                    }
                    Icon(Icons.Rounded.Star, null, tint = col, modifier = Modifier.size(21.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            T("${a.count}× behaald", 13.sp, Modifier.weight(1f), weight = FontWeight.SemiBold)
            T(a.nextLabel, 12.sp, color = c.textSubtle)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.track)) {
            Box(
                Modifier.fillMaxWidth(a.progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                    .background(if (a.platinum) Brand.gradient else Brush.linearGradient(listOf(gold, gold))),
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Milestone("25", a.count >= 25)
            Milestone("50", a.count >= 50)
            Milestone("100", a.count >= 100)
            Milestone("500 ★", a.platinum)
        }
    }
}

@Composable
private fun Milestone(label: String, reached: Boolean) {
    val c = LocalAppColors.current
    T(
        label, 10.5.sp,
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (reached) c.tint else c.sunken).padding(horizontal = 8.dp, vertical = 3.dp),
        weight = FontWeight.Bold, color = if (reached) c.accent else c.textSubtle,
    )
}

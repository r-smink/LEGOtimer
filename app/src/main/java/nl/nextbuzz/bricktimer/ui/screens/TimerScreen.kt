package nl.nextbuzz.bricktimer.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.ui.GradientButton
import nl.nextbuzz.bricktimer.ui.IconCircle
import nl.nextbuzz.bricktimer.ui.LogoTile
import nl.nextbuzz.bricktimer.ui.Wordmark
import nl.nextbuzz.bricktimer.ui.Overline
import nl.nextbuzz.bricktimer.ui.SetThumb
import nl.nextbuzz.bricktimer.ui.StatTile
import nl.nextbuzz.bricktimer.ui.T
import nl.nextbuzz.bricktimer.ui.card
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors
import java.util.Calendar

@Composable
fun TimerScreen(
    data: AppData,
    onToggle: () -> Unit,
    onOpenCurrent: () -> Unit,
    onBrowse: () -> Unit,
) {
    val c = LocalAppColors.current
    val running = data.timer.running
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val elapsed = if (running) maxOf(0L, (now - data.timer.startMs) / 1000) else 0L
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Goedemorgen"
        hour < 18 -> "Goedemiddag"
        else -> "Goedenavond"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 18.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Overline(greeting)
                Wordmark(26.sp, c.text, if (c.isDark) Brand.textGradient else Brand.gradient, Modifier.padding(top = 2.dp))
            }
            LogoTile(44.dp)
        }

        val cur = data.current
        if (cur == null) {
            Column(
                Modifier.fillMaxWidth().padding(top = 40.dp, start = 10.dp, end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconCircle(Icons.Rounded.Schedule, 96.dp, 44.dp)
                T("Nog geen huidige build", 19.sp, weight = FontWeight.Bold)
                T(
                    "Voeg een set toe aan je portfolio en markeer hem als huidige build om de timer te starten.",
                    14.sp, color = c.textMuted, lineHeight = 21.sp, align = TextAlign.Center,
                )
                GradientButton("Sets bekijken", onBrowse)
            }
            return@Column
        }

        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(256.dp), contentAlignment = Alignment.Center) {
                TimerRing(fraction = (elapsed % 60) / 60f, track = c.track)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(Fmt.clock(elapsed), 40.sp, weight = FontWeight.Bold, letterSpacing = (-0.02).em)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatusDot(running)
                        T(if (running) "Aan het bouwen" else "Klaar om te starten", 13.sp, weight = FontWeight.SemiBold, color = c.textMuted)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            GradientButton(
                text = if (running) "Stop bouwen" else "Start bouwen",
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                icon = if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                brush = if (running) Brand.stopGradient else Brand.gradient,
                glow = if (running) Brand.Danger else Brand.Glow,
                radius = 14.dp,
                fontSize = 17.sp,
                vertical = 17.dp,
            )

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().card(14.dp, onOpenCurrent).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SetThumb(cur.set, 52.dp, 11.dp)
                Column(Modifier.weight(1f)) {
                    Overline("Huidige build", color = c.accent, size = 11.sp)
                    T(cur.set.name, 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    T("${Fmt.pieces(cur.set.numParts)} stukjes · ${cur.set.themeName.ifBlank { cur.set.displayNumber }}", 12.5.sp, color = c.textMuted, maxLines = 1)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textSubtle)
            }

            val todayStart = Fmt.startOfToday()
            val today = data.portfolio.flatMap { it.sessions }.filter { it.startMs >= todayStart }
            val todaySec = today.sumOf { it.durSec }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Vandaag", if (todaySec > 0) Fmt.dur(todaySec) else "0m", Modifier.weight(1f), valueSize = 22.sp)
                StatTile("Sessies", today.size.toString(), Modifier.weight(1f), valueSize = 22.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Totaal deze set", if (cur.totalSec > 0) Fmt.dur(cur.totalSec) else "0m", Modifier.weight(1f), valueSize = 22.sp)
                StatTile("Sec / steen", if (cur.totalSec > 0) Fmt.spb(cur.totalSec, cur.set.numParts) else "—", Modifier.weight(1f), valueSize = 22.sp)
            }
        }
    }
}

@Composable
private fun TimerRing(fraction: Float, track: Color) {
    Canvas(Modifier.size(256.dp)) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2 + 7.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (fraction > 0.002f) {
            drawArc(
                brush = Brush.linearGradient(listOf(Brand.Lava1, Brand.Lava2), Offset.Zero, Offset(size.width, size.height)),
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun StatusDot(running: Boolean) {
    if (!running) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Brand.Gray300))
        return
    }
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Box(
        Modifier.size(9.dp)
            .scale(1f + .35f * p)
            .graphicsLayer { alpha = .55f + .45f * p }
            .clip(CircleShape)
            .background(Brand.Success),
    )
}

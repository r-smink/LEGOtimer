package nl.nextbuzz.bricktimer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import nl.nextbuzz.bricktimer.ui.theme.Brand

const val SPLASH_MS = 1800L

private val darkSplashBg = Color(0xFF140D24)
private val popEasing = CubicBezierEasing(.2f, .8f, .3f, 1f)

/** Splash from "BrickTimer Splash.dc.html": dark variant in dark mode, gradient variant in light mode. */
@Composable
fun SplashScreen(dark: Boolean) {
    val pop = remember { Animatable(.7f) }
    val popAlpha = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val bricks = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            listOf(
                async { pop.animateTo(1f, keyframes { durationMillis = 700; 1.05f at 420 using popEasing }) },
                async { popAlpha.animateTo(1f, tween(420)) },
                async { delay(350); rise.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) },
                async { delay(700); bricks.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) },
            ).awaitAll()
        }
    }

    val bg = if (dark) Modifier.background(darkSplashBg)
    else Modifier.background(Brush.linearGradient(listOf(Brand.Lava1, Color(0xFFE0518A), Brand.Lava2), Offset.Zero, Offset(900f, 2000f)))

    Box(Modifier.fillMaxSize().then(bg), contentAlignment = Alignment.Center) {
        // decorative glows
        Canvas(Modifier.fillMaxSize()) {
            if (dark) {
                drawCircle(Brush.radialGradient(listOf(Brand.Lava1.copy(alpha = .45f), Color.Transparent), Offset(size.width, 0f), 150.dp.toPx() * 1.4f), 150.dp.toPx() * 1.4f, Offset(size.width, 0f))
                drawCircle(Brush.radialGradient(listOf(Brand.Lava2.copy(alpha = .32f), Color.Transparent), Offset(0f, size.height), 130.dp.toPx() * 1.4f), 130.dp.toPx() * 1.4f, Offset(0f, size.height))
            } else {
                drawCircle(Color.White.copy(alpha = .1f), 140.dp.toPx(), Offset(size.width - 60.dp.toPx(), 60.dp.toPx()))
                drawCircle(Color.White.copy(alpha = .07f), 110.dp.toPx(), Offset(40.dp.toPx(), size.height - 50.dp.toPx()))
            }
        }

        Column(
            Modifier.offset(y = (-30).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            val popMod = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = popAlpha.value }
            if (dark) LogoTile(130.dp, popMod, spinning = true, glow = 22.dp)
            else BrickLogo(120.dp, popMod, spinning = true)

            Column(
                Modifier.graphicsLayer { alpha = rise.value; translationY = (1 - rise.value) * 14.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Wordmark(42.sp, Color.White, Brand.textGradient)
                Spacer(Modifier.height(12.dp))
                T("BOUW · TIJD · HERHAAL", 13.sp, weight = FontWeight.SemiBold, color = Color.White.copy(alpha = if (dark) .6f else .78f), letterSpacing = 0.18.em)
            }
        }

        StackingBricks(
            colors = if (dark) listOf(Color(0xFFFB923C), Color(0xFFF472B6), Color(0xFFEC4899)) else List(3) { Color.White },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 52.dp)
                .graphicsLayer { alpha = bricks.value; translationY = (1 - bricks.value) * 14.dp.toPx() },
        )
    }
}

/** Three bricks dropping onto each other in a loop (bt-drop). */
@Composable
private fun StackingBricks(colors: List<Color>, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "bricks")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // top brick drops last, like column-reverse in the design
        for (i in 2 downTo 0) {
            val p by t.animateFloat(
                0f, 1f,
                InfiniteRepeatableSpec(tween(2100, easing = LinearEasing), RepeatMode.Restart, StartOffset(i * 300)),
                label = "b$i",
            )
            // 0-18%: fall in, 18-82%: rest, 82-100%: fade
            val drop = when {
                p < .18f -> 1f - FastOutSlowInEasing.transform(p / .18f)
                else -> 0f
            }
            val alpha = when {
                p < .18f -> p / .18f
                p < .82f -> 1f
                else -> 1f - (p - .82f) / .18f
            }
            val lift = if (p >= .82f) (p - .82f) / .18f * 4f else 0f
            Brick(
                colors[i],
                Modifier.graphicsLayer { this.alpha = alpha; translationY = (-26f * drop - lift).dp.toPx() },
            )
        }
    }
}

@Composable
private fun Brick(color: Color, modifier: Modifier) {
    Box(modifier.size(48.dp, 19.dp)) {
        Box(Modifier.align(Alignment.TopStart).offset(x = 10.dp).size(9.dp, 6.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(color))
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-10).dp).size(9.dp, 6.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(color))
        Box(Modifier.align(Alignment.BottomCenter).size(48.dp, 15.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/** Keeps the splash on screen for at least [SPLASH_MS]. */
suspend fun awaitSplash() = delay(SPLASH_MS)

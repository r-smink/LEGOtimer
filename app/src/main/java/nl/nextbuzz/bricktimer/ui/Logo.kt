package nl.nextbuzz.bricktimer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import nl.nextbuzz.bricktimer.ui.theme.Brand

/**
 * The BrickTimer mark: a timer ring around a 2×2 stud grid (design viewBox 0..100).
 * With [spinning] the ring becomes a rotating 150/76 dash over a faint track, as on the splash.
 */
@Composable
fun BrickLogo(size: Dp, modifier: Modifier = Modifier, color: Color = Color.White, spinning: Boolean = false) {
    val rotation = if (spinning) {
        val t = rememberInfiniteTransition(label = "ring")
        val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "r")
        r
    } else 0f
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 100f
        val r = 36 * u
        val stroke = Stroke(9 * u, cap = StrokeCap.Round)
        val topLeft = Offset(50 * u - r, 50 * u - r)
        val arc = Size(r * 2, r * 2)
        if (spinning) {
            drawArc(color.copy(alpha = color.alpha * .3f), 0f, 360f, false, topLeft, arc, style = Stroke(9 * u))
            // dasharray 150 of a 226 circumference
            drawArc(color, -90f + rotation, 239f, false, topLeft, arc, style = stroke)
        } else {
            // dasharray 178 48, rotated -104°
            drawArc(color, -104f, 283f, false, topLeft, arc, style = stroke)
        }
        for ((x, y) in listOf(38f to 38f, 62f to 38f, 38f to 62f, 62f to 62f)) {
            drawCircle(color, 7.6f * u, Offset(x * u, y * u))
        }
    }
}

/** Rounded gradient tile with the mark, as used for the app icon. */
@Composable
fun LogoTile(size: Dp, modifier: Modifier = Modifier, spinning: Boolean = false, glow: Dp = 8.dp) {
    val shape = RoundedCornerShape(size * 0.25f)
    Box(
        modifier.size(size).shadow(glow, shape, ambientColor = Brand.Glow, spotColor = Brand.Glow).clip(shape).background(Brand.gradient),
        contentAlignment = Alignment.Center,
    ) { BrickLogo(size * 0.62f, spinning = spinning) }
}

/** "Brick" + gradient "Timer". */
@OptIn(ExperimentalTextApi::class)
@Composable
fun Wordmark(fontSize: TextUnit, color: Color, timerBrush: Brush, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("Brick")
            withStyle(SpanStyle(brush = timerBrush)) { append("Timer") }
        },
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.02).em,
        maxLines = 1,
    )
}

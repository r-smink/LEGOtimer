package nl.nextbuzz.bricktimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import nl.nextbuzz.bricktimer.data.BuildStatus
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors

@Composable
fun T(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalAppColors.current.text,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
    align: TextAlign? = null,
) = Text(
    text = text,
    modifier = modifier,
    fontSize = size,
    fontWeight = weight,
    color = color,
    letterSpacing = letterSpacing,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    lineHeight = lineHeight,
    textAlign = align,
)

/** Small uppercase label, e.g. section headers. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = LocalAppColors.current.textSubtle, size: TextUnit = 12.sp) =
    T(text.uppercase(), size, modifier, FontWeight.SemiBold, color, letterSpacing = 0.05.em, maxLines = 1)

@Composable
fun ScreenTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(modifier) {
        T(title, 26.sp, weight = FontWeight.Bold, letterSpacing = (-0.025).em)
        if (subtitle != null) T(subtitle, 14.sp, color = c.textMuted)
    }
}

/** White card with the design's subtle shadow. */
@Composable
fun Modifier.card(radius: Dp = 14.dp, onClick: (() -> Unit)? = null): Modifier {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(if (c.isDark) 0.dp else 1.5.dp, shape)
        .clip(shape)
        .background(c.card)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    brush: Brush = Brand.gradient,
    glow: Color = Brand.Glow,
    radius: Dp = 8.dp,
    fontSize: TextUnit = 15.sp,
    vertical: Dp = 13.dp,
    horizontal: Dp = 22.dp,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .shadow(6.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontal, vertical = vertical),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            T(text, fontSize, weight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color? = null, borderColor: Color? = null) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.card)
            .border(2.dp, borderColor ?: c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color ?: c.text, modifier = Modifier.size(16.dp))
        T(text, 13.sp, weight = FontWeight.SemiBold, color = color ?: c.text, maxLines = 1)
    }
}

@Composable
fun StatusPill(status: BuildStatus?, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val (bg, fg, label) = when (status) {
        BuildStatus.CURRENT -> Triple(c.tint, c.accent, "Huidige build")
        BuildStatus.DONE -> Triple(Brand.SuccessLight, Brand.SuccessDark, "Voltooid")
        BuildStatus.PLANNED -> Triple(c.sunken, c.textMuted, "Gepland")
        null -> Triple(c.sunken, c.textMuted, "Niet toegevoegd")
    }
    T(
        label.uppercase(), 10.5.sp,
        modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
        weight = FontWeight.Bold, color = fg, letterSpacing = 0.03.em, maxLines = 1,
    )
}

/** Set image from Rebrickable on white; falls back to the theme gradient with the set number. */
@Composable
fun SetThumb(set: LegoSet, size: Dp, radius: Dp = 12.dp, modifier: Modifier = Modifier) {
    var loaded by remember(set.imgUrl) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(if (loaded) Brush.linearGradient(listOf(Color.White, Color.White)) else Brand.setGradient(set.themeId)),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = .85f))) }
                }
                T(set.displayNumber, 11.sp, weight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
        }
        if (set.imgUrl != null) {
            AsyncImage(
                model = set.imgUrl,
                contentDescription = set.name,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize().padding(size / 14),
            )
        }
    }
}

/** Stat tile with a coloured accent bar on the left. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Brush? = null, valueSize: TextUnit = 24.sp) {
    val c = LocalAppColors.current
    Box(modifier.card(12.dp)) {
        if (accent != null) Box(Modifier.matchParentSize().padding(end = 0.dp)) { Box(Modifier.width(4.dp).fillMaxHeight().background(accent)) }
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (accent != null) 16.dp else 14.dp)) {
            T(label.uppercase(), 11.sp, weight = FontWeight.SemiBold, color = c.textSubtle, letterSpacing = 0.04.em, maxLines = 1)
            Spacer(Modifier.size(3.dp))
            T(value, valueSize, weight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun Grid2(items: List<@Composable (Modifier) -> Unit>, gap: Dp = 10.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun IconCircle(icon: ImageVector, size: Dp, iconSize: Dp, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Box(modifier.size(size).clip(CircleShape).background(c.tint), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun EmptyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().card(14.dp).padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Rounded filter chip; selected chips use the accent tint. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) c.tint else c.card)
            .border(2.dp, if (selected) c.accent.copy(alpha = .35f) else c.border, shape)
            .clickable(onClick = onClick)
            .padding(start = 13.dp, end = if (icon != null) 9.dp else 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        T(label, 13.sp, weight = FontWeight.SemiBold, color = if (selected) c.accent else c.textMuted, maxLines = 1)
        if (icon != null) Icon(icon, null, tint = if (selected) c.accent else c.textSubtle, modifier = Modifier.size(16.dp))
    }
}

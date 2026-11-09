package nl.nextbuzz.bricktimer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import nl.nextbuzz.bricktimer.data.BuildStatus
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.data.PortfolioEntry
import nl.nextbuzz.bricktimer.logic.tr
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors
import nl.nextbuzz.bricktimer.ui.theme.Outfit

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
        BuildStatus.CURRENT -> Triple(c.tint, c.accent, tr("Huidige build", "Current build"))
        BuildStatus.DONE -> Triple(Brand.SuccessLight, Brand.SuccessDark, tr("Voltooid", "Finished"))
        BuildStatus.PLANNED -> Triple(c.sunken, c.textMuted, tr("Gepland", "Planned"))
        null -> Triple(c.sunken, c.textMuted, tr("Niet toegevoegd", "Not added"))
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

/** Horizontal progress bar on the theme track; [progress] runs from 0 to 1. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, brush: Brush = Brand.gradient, track: Color = LocalAppColors.current.track) {
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(shape).background(brush))
    }
}

/** AI estimate of how far a build is: big percentage, animated bar and the model's explanation. */
@Composable
fun BuildProgressCard(pct: Int, note: String, confidence: String?, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(pct) { target = pct / 100f }
    val animated by animateFloatAsState(target, tween(900), label = "progress")
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.sunken).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Overline(tr("Geschatte voortgang", "Estimated progress"), size = 11.sp)
                T("${(animated * 100).toInt()}%", 30.sp, weight = FontWeight.Bold, letterSpacing = (-0.02).em)
            }
            if (confidence != null) {
                T(
                    confidence, 11.sp,
                    Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.tint).padding(horizontal = 9.dp, vertical = 3.dp),
                    weight = FontWeight.SemiBold, color = c.accent, maxLines = 1,
                )
            }
        }
        ProgressBar(animated, Modifier.padding(top = 6.dp), height = 12.dp)
        if (note.isNotBlank()) T(note, 12.5.sp, Modifier.padding(top = 10.dp), color = c.textMuted, lineHeight = 18.sp)
    }
}

/** Dialog for manually adding a session duration for a build when timer was forgotten. */
@Composable
fun AddManualSessionDialog(
    portfolio: List<PortfolioEntry>,
    initialSet: LegoSet?,
    onSave: (setNum: String, minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    var selectedSetNum by remember { mutableStateOf(initialSet?.setNum ?: portfolio.firstOrNull()?.set?.setNum.orEmpty()) }
    var hoursText by remember { mutableStateOf("0") }
    var minutesText by remember { mutableStateOf("30") }

    val totalMin = (hoursText.toIntOrNull() ?: 0) * 60 + (minutesText.toIntOrNull() ?: 0)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T(tr("Sessie handmatig toevoegen", "Add session manually"), 20.sp, weight = FontWeight.Bold)
            T(tr("Heb je gebouwd zonder de timer aan te zetten? Voeg je bouwtijd hier toe.", "Built without starting the timer? Add your build time here."), 12.5.sp, color = c.textMuted, lineHeight = 17.sp)

            if (portfolio.size > 1) {
                Overline(tr("Kies een set", "Choose a set"))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    portfolio.forEach { entry ->
                        val sel = entry.set.setNum == selectedSetNum
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (sel) c.tint else c.sunken)
                                .clickable { selectedSetNum = entry.set.setNum }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SetThumb(entry.set, 36.dp, 8.dp)
                            Column(Modifier.weight(1f)) {
                                T(entry.set.name, 13.5.sp, weight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) c.accent else c.text, maxLines = 1)
                                T(entry.set.displayNumber, 11.sp, color = c.textSubtle)
                            }
                            if (sel) Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else if (portfolio.isNotEmpty()) {
                val set = portfolio.firstOrNull { it.set.setNum == selectedSetNum }?.set ?: portfolio.first().set
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.sunken).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SetThumb(set, 40.dp, 8.dp)
                    Column {
                        T(set.name, 14.sp, weight = FontWeight.SemiBold)
                        T(set.displayNumber, 11.5.sp, color = c.textMuted)
                    }
                }
            }

            Overline(tr("Tijdsduur", "Duration"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    T(tr("Uren", "Hours"), 12.sp, color = c.textMuted, modifier = Modifier.padding(bottom = 4.dp))
                    DurationInputField(hoursText, { hoursText = it.filter { ch -> ch.isDigit() } })
                }
                Column(Modifier.weight(1f)) {
                    T(tr("Minuten", "Minutes"), 12.sp, color = c.textMuted, modifier = Modifier.padding(bottom = 4.dp))
                    DurationInputField(minutesText, { minutesText = it.filter { ch -> ch.isDigit() } })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15 to "15m", 30 to "30m", 45 to "45m", 60 to tr("1u", "1h"), 90 to tr("1,5u", "1.5h"), 120 to tr("2u", "2h")).forEach { (min, label) ->
                    val isSel = totalMin == min
                    T(
                        label, 12.5.sp,
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) c.accent else c.sunken)
                            .clickable {
                                hoursText = (min / 60).toString()
                                minutesText = (min % 60).toString()
                            }
                            .padding(vertical = 8.dp),
                        weight = FontWeight.SemiBold,
                        color = if (isSel) Color.White else c.textMuted,
                        align = TextAlign.Center,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                T(tr("Annuleren", "Cancel"), 14.sp, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(12.dp), weight = FontWeight.SemiBold, color = c.textMuted)
                GradientButton(
                    tr("Toevoegen", "Add"),
                    { if (totalMin > 0 && selectedSetNum.isNotEmpty()) onSave(selectedSetNum, totalMin) },
                    brush = if (totalMin > 0 && selectedSetNum.isNotEmpty()) Brand.gradient else Brush.linearGradient(listOf(Brand.Gray400, Brand.Gray400)),
                    fontSize = 14.sp, vertical = 11.dp, horizontal = 18.dp,
                )
            }
        }
    }
}

@Composable
private fun DurationInputField(value: String, onChange: (String) -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.sunken)
            .border(2.dp, c.border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontFamily = Outfit, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.text),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Single-line text field on a sunken background; [secret] hides the text, as for passwords and API keys. */
@Composable
fun InputField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean = false,
    keyboardType: KeyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
) {
    val c = LocalAppColors.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.sunken)
            .border(2.dp, c.border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) T(placeholder, 14.sp, color = c.textSubtle)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Outfit, fontSize = 14.sp, color = c.text),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

package nl.nextbuzz.bricktimer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.BuildStatus
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.data.Session
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.logic.SPEED_LIMIT_SEC_PER_BRICK
import nl.nextbuzz.bricktimer.logic.numberLocale
import nl.nextbuzz.bricktimer.logic.tr
import nl.nextbuzz.bricktimer.ui.GradientButton
import nl.nextbuzz.bricktimer.ui.Grid2
import nl.nextbuzz.bricktimer.ui.IconCircle
import nl.nextbuzz.bricktimer.ui.OutlineButton
import nl.nextbuzz.bricktimer.ui.Overline
import nl.nextbuzz.bricktimer.ui.SetThumb
import nl.nextbuzz.bricktimer.ui.StatTile
import nl.nextbuzz.bricktimer.ui.StatusPill
import nl.nextbuzz.bricktimer.ui.T
import nl.nextbuzz.bricktimer.ui.card
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors

@Composable
fun DetailScreen(
    data: AppData,
    set: LegoSet,
    minifigs: Int?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSetCurrent: () -> Unit,
    onMarkDone: () -> Unit,
    onRemove: () -> Unit,
    onDeleteSession: (Long) -> Unit,
    onAddManualSession: () -> Unit,
) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val entry = data.entry(set.setNum)
    val s = entry?.set ?: set
    var confirmRemove by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
        // header
        Column(Modifier.fillMaxWidth().background(Brand.setGradient(s.themeId)).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .2f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, tr("Terug", "Back"), tint = Color.White, modifier = Modifier.size(26.dp)) }
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SetThumb(s, 96.dp, 16.dp, Modifier.border(1.dp, Color.White.copy(alpha = .25f), RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    if (s.themeName.isNotBlank()) Overline(s.themeName, color = Color.White.copy(alpha = .85f), size = 11.sp)
                    T(s.name, 21.sp, weight = FontWeight.Bold, color = Color.White, lineHeight = 25.sp)
                }
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(entry?.status)
                Spacer(Modifier.weight(1f))
                if (entry != null && entry.status != BuildStatus.CURRENT) {
                    GradientButton(tr("Markeer huidige", "Set as current"), onSetCurrent, fontSize = 13.sp, vertical = 9.dp, horizontal = 13.dp)
                }
                if (entry?.status == BuildStatus.CURRENT) {
                    OutlineButton(tr("Markeer klaar", "Mark done"), onMarkDone, color = Brand.Success, borderColor = Brand.SuccessBorder)
                }
            }

            Overline(tr("Set gegevens", "Set details"), Modifier.padding(bottom = 10.dp))
            Grid2(
                buildList<@Composable (Modifier) -> Unit> {
                    add { m -> StatTile(tr("Stukjes", "Pieces"), Fmt.pieces(s.numParts), m, valueSize = 20.sp) }
                    add { m -> StatTile(tr("Setnummer", "Set number"), s.setNum, m, valueSize = 20.sp) }
                    add { m -> StatTile(tr("Thema", "Theme"), s.themeName.ifBlank { "—" }, m, valueSize = 16.sp) }
                    add { m -> StatTile(tr("Jaar", "Year"), s.year.toString(), m, valueSize = 20.sp) }
                    if (minifigs != null && minifigs > 0) add { m -> StatTile(tr("Minifiguren", "Minifigures"), minifigs.toString(), m, valueSize = 20.sp) }
                },
            )
            if (s.setUrl != null) {
                Row(
                    Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.setUrl))) }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    T(tr("Bekijk op Rebrickable", "View on Rebrickable"), 13.sp, weight = FontWeight.SemiBold, color = c.accent)
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = c.accent, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(18.dp))

            if (entry == null) {
                Column(
                    Modifier.fillMaxWidth().card(14.dp).padding(horizontal = 16.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconCircle(Icons.Rounded.AddCircle, 72.dp, 34.dp)
                    T(tr("Nog niet in je portfolio", "Not in your portfolio yet"), 16.sp, Modifier.padding(top = 14.dp), weight = FontWeight.Bold)
                    T(
                        tr(
                            "Voeg deze set toe om bouwtijd bij te houden en statistieken te verzamelen.",
                            "Add this set to track your build time and collect stats.",
                        ),
                        13.5.sp, Modifier.padding(top = 6.dp, bottom = 16.dp), color = c.textMuted, lineHeight = 20.sp, align = TextAlign.Center,
                    )
                    GradientButton(tr("Toevoegen aan portfolio", "Add to portfolio"), onAdd)
                }
                return@Column
            }

            val has = entry.sessions.isNotEmpty()
            val tot = entry.totalSec
            // Per-brick speed is only meaningful once the whole set is built.
            val done = entry.status == BuildStatus.DONE && has
            Overline(tr("Jouw statistieken", "Your stats"), Modifier.padding(bottom = 10.dp))
            Grid2(
                buildList<@Composable (Modifier) -> Unit> {
                    add { m -> StatTile(tr("Totale tijd", "Total time"), if (has) Fmt.dur(tot) else "—", m, accent = Brand.gradient) }
                    if (done) add { m -> StatTile(tr("Sec / steen", "Sec / brick"), Fmt.spb(tot, s.numParts), m, accent = solid(Brand.Lava2)) }
                    add { m -> StatTile(tr("Sessies", "Sessions"), entry.sessions.size.toString(), m, accent = solid(Brand.Lava1)) }
                    add { m -> StatTile(tr("Snelste sessie", "Fastest session"), entry.sessions.minOfOrNull { it.durSec }?.let(Fmt::dur) ?: "—", m, accent = solid(Brand.Success)) }
                    add { m -> StatTile(tr("Gem. sessie", "Avg. session"), if (has) Fmt.dur(tot / entry.sessions.size) else "—", m, accent = solid(Brand.Gold)) }
                    if (done) add { m ->
                        val perMin = s.numParts * 60.0 / tot
                        StatTile(tr("Stenen / min", "Bricks / min"), String.format(numberLocale, "%.1f", perMin), m, accent = solid(Brand.Danger))
                    }
                },
            )
            if (has && !done) {
                T(tr("Sec / steen en stenen / min verschijnen zodra de set klaar is.", "Sec / brick and bricks / min appear once the set is done."), 12.sp, Modifier.padding(top = 8.dp, start = 2.dp), color = c.textSubtle)
            }
            if (has) {
                val first = entry.sessions.minOf { it.startMs }
                val period = buildString {
                    append(tr("Gestart ", "Started ")).append(Fmt.date(first))
                    entry.completedMs?.let { append(tr(" · voltooid ", " · finished ")).append(Fmt.date(it)) }
                }
                T(period, 12.5.sp, Modifier.padding(top = 10.dp, start = 2.dp), color = c.textMuted)
            }
            Spacer(Modifier.height(12.dp))

            if (done && tot.toDouble() / s.numParts < SPEED_LIMIT_SEC_PER_BRICK) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp)).background(c.tint).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Speed, null, tint = c.accent, modifier = Modifier.size(22.dp))
                    T(tr("SpeedBrick tempo — onder 10s per steen", "SpeedBrick pace — under 10s per brick"), 13.sp, weight = FontWeight.SemiBold, color = c.accent)
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Overline(tr("Bouwsessies", "Build sessions"), Modifier.weight(1f))
                T(
                    tr("+ Handmatig toevoegen", "+ Add manually"), 12.sp,
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAddManualSession).padding(horizontal = 8.dp, vertical = 4.dp),
                    weight = FontWeight.SemiBold, color = c.accent,
                )
            }
            if (!has) {
                T(
                    tr("Nog geen sessies. Markeer als huidige build en start de timer.", "No sessions yet. Set it as your current build and start the timer."), 13.sp,
                    Modifier.fillMaxWidth().card(12.dp).padding(horizontal = 10.dp, vertical = 24.dp),
                    color = c.textMuted, align = TextAlign.Center,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.sessions.sortedByDescending { it.startMs }.forEach { se -> SessionRow(se, s.numParts.takeIf { done }) { sessionToDelete = se } }
                }
                T(tr("Houd een sessie ingedrukt om hem te verwijderen.", "Long-press a session to delete it."), 11.5.sp, Modifier.padding(top = 8.dp, start = 2.dp), color = c.textSubtle)
            }

            T(
                tr("Verwijderen uit portfolio", "Remove from portfolio"), 13.sp,
                Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { confirmRemove = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                weight = FontWeight.SemiBold, color = Brand.Danger,
            )
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = tr("Set verwijderen?", "Delete set?"),
            text = tr("${s.name} en alle bijbehorende sessies worden uit je portfolio verwijderd.", "${s.name} and all its sessions will be removed from your portfolio."),
            confirm = tr("Verwijderen", "Delete"),
            onConfirm = { confirmRemove = false; onRemove() },
            onDismiss = { confirmRemove = false },
        )
    }
    sessionToDelete?.let { se ->
        ConfirmDialog(
            title = tr("Sessie verwijderen?", "Delete session?"),
            text = "${Fmt.date(se.startMs)} · ${Fmt.range(se.startMs, se.durSec)} (${Fmt.dur(se.durSec)})",
            confirm = tr("Verwijderen", "Delete"),
            onConfirm = { sessionToDelete = null; onDeleteSession(se.id) },
            onDismiss = { sessionToDelete = null },
        )
    }
}

private fun solid(color: Color): Brush = Brush.linearGradient(listOf(color, color))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(se: Session, pieces: Int?, onLongPress: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().card(12.dp).combinedClickable(onClick = {}, onLongClick = onLongPress).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.tint), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            T(Fmt.date(se.startMs), 14.sp, weight = FontWeight.SemiBold)
            T(Fmt.range(se.startMs, se.durSec) + (pieces?.let { " · ${Fmt.spb(se.durSec, it)}${tr("/steen", "/brick")}" } ?: ""), 12.sp, color = c.textMuted)
        }
        T(Fmt.dur(se.durSec), 15.sp, weight = FontWeight.Bold)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, color = c.text, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = c.textMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Brand.Danger, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuleren", "Cancel"), color = c.textMuted) } },
    )
}

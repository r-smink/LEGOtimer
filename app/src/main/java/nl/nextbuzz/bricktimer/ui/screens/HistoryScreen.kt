package nl.nextbuzz.bricktimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.ui.SetThumb
import nl.nextbuzz.bricktimer.ui.T
import nl.nextbuzz.bricktimer.ui.card
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors

@Composable
fun HistoryScreen(data: AppData, onBack: () -> Unit, onOpen: (LegoSet) -> Unit) {
    val c = LocalAppColors.current
    val rows = data.portfolio.flatMap { e -> e.sessions.map { e.set to it } }.sortedByDescending { it.second.startMs }
    val totalSec = rows.sumOf { it.second.durSec }

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
                ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Terug", tint = c.text) }
                Column {
                    T("Historie", 22.sp, weight = FontWeight.Bold, letterSpacing = (-0.02).em)
                    T("${rows.size} bouwsessies · ${Fmt.dur(totalSec)} totaal", 13.sp, color = c.textMuted)
                }
            }
        }
        if (rows.isEmpty()) {
            item {
                T(
                    "Nog geen bouwsessies geregistreerd.", 13.sp,
                    Modifier.fillMaxWidth().card(12.dp).padding(24.dp), color = c.textMuted, align = TextAlign.Center,
                )
            }
        }
        items(rows, key = { it.second.id }) { (set, se) ->
            Row(
                Modifier.fillMaxWidth().card(13.dp) { onOpen(set) }.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                SetThumb(set, 46.dp, 11.dp)
                Column(Modifier.weight(1f)) {
                    T(set.name, 14.5.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    T("${Fmt.date(se.startMs)} · ${Fmt.range(se.startMs, se.durSec)}", 12.sp, color = c.textMuted, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    T(Fmt.dur(se.durSec), 15.sp, weight = FontWeight.Bold)
                    T("${Fmt.spb(se.durSec, set.numParts)}/steen", 11.sp, color = c.textSubtle)
                }
            }
        }
    }
}

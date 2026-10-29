package nl.nextbuzz.bricktimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.BuildStatus
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.ui.EmptyCard
import nl.nextbuzz.bricktimer.ui.GradientButton
import nl.nextbuzz.bricktimer.ui.IconCircle
import nl.nextbuzz.bricktimer.ui.OutlineButton
import nl.nextbuzz.bricktimer.ui.Overline
import nl.nextbuzz.bricktimer.ui.ScreenTitle
import nl.nextbuzz.bricktimer.ui.SetThumb
import nl.nextbuzz.bricktimer.ui.StatusPill
import nl.nextbuzz.bricktimer.ui.T
import nl.nextbuzz.bricktimer.ui.card
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors

private val statusOrder = mapOf(BuildStatus.CURRENT to 0, BuildStatus.PLANNED to 1, BuildStatus.DONE to 2)

@Composable
fun PortfolioScreen(data: AppData, onOpen: (LegoSet) -> Unit, onHistory: () -> Unit, onBrowse: () -> Unit) {
    val c = LocalAppColors.current
    val entries = data.portfolio.sortedWith(compareBy<nl.nextbuzz.bricktimer.data.PortfolioEntry> { statusOrder[it.status] }.thenByDescending { it.addedMs })
    val built = data.portfolio.count { it.status == BuildStatus.DONE }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Mijn builds", "${data.portfolio.size} ${if (data.portfolio.size == 1) "set" else "sets"} in portfolio · $built gebouwd", Modifier.weight(1f))
                OutlineButton("Historie", onHistory, icon = Icons.Rounded.History)
            }
        }

        data.current?.let { cur ->
            item {
                val shape = RoundedCornerShape(16.dp)
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .shadow(10.dp, shape, ambientColor = Brand.Glow, spotColor = Brand.Glow)
                        .clip(shape).background(Brand.gradient).clickable { onOpen(cur.set) }.padding(18.dp),
                ) {
                    Box(Modifier.matchParentSize()) { Box(Modifier.align(Alignment.TopEnd).offset(42.dp, (-42).dp).size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f))) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                                Overline("Huidige build", color = Color.White.copy(alpha = .85f), size = 11.sp)
                            }
                            T(cur.set.name, 21.sp, Modifier.padding(top = 6.dp), weight = FontWeight.Bold, color = Color.White, lineHeight = 25.sp)
                            T(
                                "${Fmt.pieces(cur.set.numParts)} stukjes · ${if (cur.totalSec > 0) Fmt.dur(cur.totalSec) else "0m"} gebouwd",
                                13.sp, Modifier.padding(top = 4.dp), color = Color.White.copy(alpha = .85f),
                            )
                        }
                        SetThumb(cur.set, 64.dp)
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyCard {
                    IconCircle(Icons.Rounded.Inventory2, 72.dp, 34.dp)
                    T("Je portfolio is nog leeg", 16.sp, Modifier.padding(top = 14.dp), weight = FontWeight.Bold)
                    T("Zoek een LEGO set en voeg hem toe om je bouwtijd bij te houden.", 13.5.sp, Modifier.padding(top = 6.dp, bottom = 16.dp), color = c.textMuted, align = TextAlign.Center)
                    GradientButton("Sets bekijken", onBrowse)
                }
            }
            return@LazyColumn
        }

        item { Overline("Alle sets", Modifier.padding(start = 2.dp)) }
        items(entries, key = { it.set.setNum }) { e ->
            Row(
                Modifier.fillMaxWidth().card(14.dp) { onOpen(e.set) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SetThumb(e.set, 58.dp, 11.dp)
                Column(Modifier.weight(1f)) {
                    T(e.set.name, 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    val sub = if (e.sessions.isNotEmpty()) {
                        "${Fmt.dur(e.totalSec)} · ${e.sessions.size} ${if (e.sessions.size == 1) "sessie" else "sessies"} · ${Fmt.spb(e.totalSec, e.set.numParts)}/steen"
                    } else {
                        "${e.set.displayNumber} · ${Fmt.pieces(e.set.numParts)} stukjes"
                    }
                    T(sub, 12.5.sp, Modifier.padding(top = 2.dp), color = c.textMuted, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    StatusPill(e.status)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textSubtle)
            }
        }
    }
}

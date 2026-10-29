package nl.nextbuzz.bricktimer.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nextbuzz.bricktimer.data.ThemeMode
import nl.nextbuzz.bricktimer.ui.screens.AchievementsScreen
import nl.nextbuzz.bricktimer.ui.screens.CatalogScreen
import nl.nextbuzz.bricktimer.ui.screens.DetailScreen
import nl.nextbuzz.bricktimer.ui.screens.HistoryScreen
import nl.nextbuzz.bricktimer.ui.screens.PortfolioScreen
import nl.nextbuzz.bricktimer.ui.screens.TimerScreen
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors
import nl.nextbuzz.bricktimer.ui.theme.Outfit

private data class NavItem(val root: Screen, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Screen.Timer, "Timer", Icons.Rounded.Schedule),
    NavItem(Screen.Catalog, "Sets", Icons.Rounded.GridView),
    NavItem(Screen.Portfolio, "Mijn builds", Icons.Rounded.Inventory2),
    NavItem(Screen.Achievements, "Awards", Icons.Rounded.Star),
)

@Composable
fun BrickTimerApp(vm: AppViewModel, onExit: () -> Unit) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val data by vm.data.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
    BackHandler { if (!vm.back()) onExit() }

    val screen = vm.screen
    val activeRoot = vm.stack.first()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 40 }) togetherWith fadeOut(tween(120)) },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Timer -> TimerScreen(
                            data = data,
                            onToggle = vm::toggleTimer,
                            onOpenCurrent = { data.current?.let { vm.openSet(it.set) } },
                            onBrowse = { vm.goTab(Screen.Catalog) },
                        )
                        Screen.Catalog -> {
                            LaunchedEffect(Unit) { vm.ensureCatalog() }
                            CatalogScreen(
                                data = data,
                                state = catalog,
                                hasKey = vm.effectiveKey.isNotBlank(),
                                onQuery = vm::onQuery,
                                onLoadMore = vm::loadMore,
                                onRetry = vm::reloadCatalog,
                                onOpen = vm::openSet,
                                onAdd = vm::addToPortfolio,
                                onSettings = { showSettings = true },
                            )
                        }
                        Screen.Portfolio -> PortfolioScreen(
                            data = data,
                            onOpen = vm::openSet,
                            onHistory = { vm.push(Screen.History) },
                            onBrowse = { vm.goTab(Screen.Catalog) },
                        )
                        Screen.Achievements -> AchievementsScreen(data)
                        Screen.History -> HistoryScreen(data, onBack = { vm.back() }, onOpen = vm::openSet)
                        is Screen.Detail -> DetailScreen(
                            data = data,
                            set = s.set,
                            onBack = { vm.back() },
                            onAdd = { vm.addToPortfolio(s.set) },
                            onSetCurrent = { vm.setCurrent(s.set.setNum) },
                            onMarkDone = { vm.markDone(s.set.setNum) },
                            onRemove = { vm.removeFromPortfolio(s.set.setNum); vm.back() },
                            onDeleteSession = { id -> vm.deleteSession(s.set.setNum, id) },
                        )
                    }
                }
            }
            BottomNav(activeRoot) { vm.goTab(it) }
        }

        summary?.let { SummaryOverlay(it, vm::closeSummary) }
    }

    if (showSettings) {
        SettingsDialog(
            apiKey = data.apiKey,
            themeMode = data.themeMode,
            onSave = { key, mode ->
                showSettings = false
                vm.setThemeMode(mode)
                if (key != data.apiKey) vm.setApiKey(key)
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun BottomNav(active: Screen, onSelect: (Screen) -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(c.card)) {
        HorizontalDivider(color = c.border, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)) {
            navItems.forEach { item ->
                val color = if (item.root == active) c.accent else Brand.Gray400
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onSelect(item.root) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(item.icon, item.label, tint = color, modifier = Modifier.size(24.dp))
                    T(item.label, 10.5.sp, weight = FontWeight.SemiBold, color = color, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SummaryOverlay(sum: Summary, onClose: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.fillMaxSize().background(Color(0x8C15101E))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth().shadow(30.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(c.card)) {
            Box(Modifier.fillMaxWidth().background(Brand.gradient).padding(horizontal = 22.dp, vertical = 24.dp)) {
                Box(Modifier.matchParentSize()) { Box(Modifier.align(Alignment.TopEnd).offset(42.dp, (-44).dp).size(100.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f))) }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(62.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    T(sum.title, 19.sp, weight = FontWeight.Bold, color = Color.White)
                    T(sum.setName, 13.sp, Modifier.padding(top = 2.dp), color = Color.White.copy(alpha = .85f), align = TextAlign.Center)
                }
            }
            Column(Modifier.padding(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Overline(sum.bigLabel, size = 11.sp)
                    T(sum.big, 40.sp, Modifier.padding(top = 2.dp), weight = FontWeight.Bold, letterSpacing = (-0.02).em)
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryStat(sum.spb, "sec/steen", Modifier.weight(1f))
                    SummaryStat(sum.date, sum.dateLabel, Modifier.weight(1f))
                }
                sum.badges.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(c.tint).padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = c.accent, modifier = Modifier.size(24.dp))
                        Column {
                            T(b.title, 14.sp, weight = FontWeight.Bold, color = c.accent)
                            T(b.desc, 12.sp, color = c.accent)
                        }
                    }
                }
                GradientButton("Klaar", onClose, Modifier.fillMaxWidth().padding(top = 6.dp), radius = 10.dp, vertical = 14.dp)
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier) {
    val c = LocalAppColors.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.sunken).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(value, 18.sp, weight = FontWeight.Bold, maxLines = 1)
        T(label, 11.sp, weight = FontWeight.SemiBold, color = c.textMuted, maxLines = 2, align = TextAlign.Center)
    }
}

@Composable
private fun SettingsDialog(apiKey: String, themeMode: ThemeMode, onSave: (String, ThemeMode) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    var key by remember { mutableStateOf(apiKey) }
    var mode by remember { mutableStateOf(themeMode) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(22.dp)) {
            T("Instellingen", 20.sp, weight = FontWeight.Bold)
            Overline("Rebrickable API-sleutel", Modifier.padding(top = 18.dp, bottom = 8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.sunken)
                    .border(2.dp, c.border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (key.isEmpty()) T("Plak hier je API key", 14.sp, color = c.textSubtle)
                BasicTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Outfit, fontSize = 14.sp, color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            T(
                "Gratis aan te maken op rebrickable.com onder Account → Settings → API. Laat leeg om de sleutel uit de build te gebruiken.",
                12.sp, Modifier.padding(top = 6.dp), color = c.textMuted, lineHeight = 17.sp,
            )

            Overline("Weergave", Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.DARK to "Donker", ThemeMode.LIGHT to "Licht", ThemeMode.SYSTEM to "Systeem").forEach { (m, label) ->
                    val sel = m == mode
                    T(
                        label, 13.sp,
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (sel) c.tint else c.sunken)
                            .clickable { mode = m }.padding(vertical = 10.dp),
                        weight = FontWeight.SemiBold, color = if (sel) c.accent else c.textMuted, align = TextAlign.Center,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                T("Annuleren", 14.sp, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(12.dp), weight = FontWeight.SemiBold, color = c.textMuted)
                GradientButton("Opslaan", { onSave(key.trim(), mode) }, fontSize = 14.sp, vertical = 11.dp, horizontal = 18.dp)
            }
        }
    }
}

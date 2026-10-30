package nl.nextbuzz.bricktimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.ui.CatalogState
import nl.nextbuzz.bricktimer.ui.Chip
import nl.nextbuzz.bricktimer.ui.PartsRange
import nl.nextbuzz.bricktimer.ui.ThemeOption
import nl.nextbuzz.bricktimer.ui.EmptyCard
import nl.nextbuzz.bricktimer.ui.GradientButton
import nl.nextbuzz.bricktimer.ui.IconCircle
import nl.nextbuzz.bricktimer.ui.Overline
import nl.nextbuzz.bricktimer.ui.ScreenTitle
import nl.nextbuzz.bricktimer.ui.SetThumb
import nl.nextbuzz.bricktimer.ui.T
import nl.nextbuzz.bricktimer.ui.card
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors
import nl.nextbuzz.bricktimer.ui.theme.Outfit

@Composable
fun CatalogScreen(
    data: AppData,
    state: CatalogState,
    hasKey: Boolean,
    themeOptions: List<ThemeOption>,
    onQuery: (String) -> Unit,
    onTheme: (ThemeOption?) -> Unit,
    onParts: (PartsRange?) -> Unit,
    onOpenThemes: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (LegoSet) -> Unit,
    onAdd: (LegoSet) -> Unit,
    onSettings: () -> Unit,
) {
    val c = LocalAppColors.current
    val list = rememberLazyListState()
    var pickTheme by remember { mutableStateOf(false) }
    var pickParts by remember { mutableStateOf(false) }
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.items.isNotEmpty()) onLoadMore() }

    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Sets", "Blader en voeg toe aan je portfolio", Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.card)
                        .border(2.dp, c.border, RoundedCornerShape(12.dp)).clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Settings, "Instellingen", tint = c.text, modifier = Modifier.size(20.dp)) }
            }
        }
        item { SearchField(state.query, onQuery) }
        if (hasKey) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Chip(state.theme?.label ?: "Alle thema's", state.theme != null, { onOpenThemes(); pickTheme = true }, icon = Icons.Rounded.ExpandMore)
                    Chip(state.parts?.let { "${it.label} stukjes" } ?: "Alle stukjes", state.parts != null, { pickParts = true }, icon = Icons.Rounded.ExpandMore)
                    if (state.theme != null || state.parts != null) {
                        Chip("Wissen", false, { onTheme(null); onParts(null) }, icon = Icons.Rounded.Close)
                    }
                }
            }
        }

        if (!hasKey) {
            item {
                EmptyCard {
                    IconCircle(Icons.Rounded.Key, 72.dp, 34.dp)
                    Spacer(Modifier.height(14.dp))
                    T("Rebrickable API-sleutel nodig", 16.sp, weight = FontWeight.Bold)
                    T(
                        "Maak gratis een sleutel aan via rebrickable.com → Settings → API en vul hem hier in om alle LEGO sets te laden.",
                        13.5.sp, Modifier.padding(top = 6.dp, bottom = 16.dp), color = c.textMuted, lineHeight = 20.sp, align = TextAlign.Center,
                    )
                    GradientButton("Sleutel invullen", onSettings)
                }
            }
            return@LazyColumn
        }

        if (state.items.isNotEmpty()) {
            item { Overline("${Fmt.pieces(state.total)} ${if (state.total == 1) "set" else "sets"} gevonden", Modifier.padding(start = 2.dp, top = 6.dp)) }
        }
        items(state.items, key = { it.setNum }) { set ->
            CatalogRow(set, inPortfolio = data.entry(set.setNum) != null, onOpen = { onOpen(set) }, onAdd = { onAdd(set) })
        }

        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
            }
            state.error != null -> item {
                EmptyCard {
                    IconCircle(Icons.Rounded.WifiOff, 64.dp, 30.dp)
                    T(state.error, 14.sp, Modifier.padding(top = 12.dp, bottom = 14.dp), color = c.textMuted, align = TextAlign.Center)
                    GradientButton("Opnieuw proberen", onRetry)
                }
            }
            state.items.isEmpty() && state.page > 0 -> item {
                val msg = buildString {
                    append("Geen sets gevonden")
                    if (state.query.isNotBlank()) append(" voor \"${state.query}\"")
                    state.theme?.let { append(" in ${it.label}") }
                    state.parts?.let { append(" met ${it.label} stukjes") }
                }
                EmptyCard { T(msg, 14.sp, color = c.textMuted, align = TextAlign.Center) }
            }
        }
    }

    if (pickParts) {
        Dialog(onDismissRequest = { pickParts = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(top = 20.dp, bottom = 12.dp)) {
                T("Filter op stukjes", 20.sp, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), weight = FontWeight.Bold)
                ThemeRow("Alle stukjes", state.parts == null) { pickParts = false; onParts(null) }
                PartsRange.entries.forEach { r -> ThemeRow("${r.label} stukjes", state.parts == r) { pickParts = false; onParts(r) } }
            }
        }
    }

    if (pickTheme) {
        ThemePicker(
            options = themeOptions,
            selected = state.theme,
            onPick = { pickTheme = false; onTheme(it) },
            onDismiss = { pickTheme = false },
        )
    }
}

@Composable
private fun ThemePicker(options: List<ThemeOption>, selected: ThemeOption?, onPick: (ThemeOption?) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    var filter by remember { mutableStateOf("") }
    val shown = remember(options, filter) {
        val f = filter.trim()
        if (f.isEmpty()) options else options.filter { it.label.contains(f, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(top = 20.dp, bottom = 12.dp)) {
            T("Filter op thema", 20.sp, Modifier.padding(horizontal = 20.dp), weight = FontWeight.Bold)
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)) { SearchField(filter, { filter = it }, "Zoek een thema") }
            if (options.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
                T("Thema's worden geladen…", 13.sp, Modifier.fillMaxWidth().padding(bottom = 12.dp), color = c.textMuted, align = TextAlign.Center)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    if (filter.isBlank()) item { ThemeRow("Alle thema's", selected == null) { onPick(null) } }
                    items(shown, key = { it.id }) { t -> ThemeRow(t.label, t.id == selected?.id) { onPick(t) } }
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(label, 14.5.sp, Modifier.weight(1f), weight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) c.accent else c.text)
        if (selected) Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String = "Zoek op naam of setnummer") {
    val c = LocalAppColors.current
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp)).background(c.card).border(2.dp, c.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.textSubtle, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) T(placeholder, 14.sp, color = c.textSubtle, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Outfit, fontSize = 14.sp, color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Wissen", tint = c.textSubtle, modifier = Modifier.size(18.dp).clickable { onQuery("") })
        }
    }
}

@Composable
private fun CatalogRow(set: LegoSet, inPortfolio: Boolean, onOpen: () -> Unit, onAdd: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().card(14.dp, onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SetThumb(set, 64.dp)
        Column(Modifier.weight(1f)) {
            if (set.themeName.isNotBlank()) {
                T(set.themeName.uppercase(), 11.sp, weight = FontWeight.SemiBold, color = c.accent, letterSpacing = 0.04.em, maxLines = 1)
            }
            T(set.name, 15.sp, weight = FontWeight.SemiBold, maxLines = 2, lineHeight = 19.sp)
            T("${set.displayNumber} · ${Fmt.pieces(set.numParts)} stukjes · ${set.year}", 12.5.sp, Modifier.padding(top = 2.dp), color = c.textMuted, maxLines = 1)
        }
        val shape = RoundedCornerShape(8.dp)
        if (inPortfolio) {
            T("Toegevoegd", 12.5.sp, Modifier.clip(shape).background(c.sunken).padding(horizontal = 12.dp, vertical = 9.dp), weight = FontWeight.SemiBold, color = c.textSubtle)
        } else {
            T(
                "+ Toevoegen", 12.5.sp,
                Modifier.shadow(4.dp, shape, ambientColor = Brand.Glow, spotColor = Brand.Glow).clip(shape)
                    .background(Brand.gradient).clickable(onClick = onAdd).padding(horizontal = 13.dp, vertical = 9.dp),
                weight = FontWeight.SemiBold, color = Color.White,
            )
        }
    }
}

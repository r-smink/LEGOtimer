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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.remember
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
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.LegoSet
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.ui.CatalogState
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
    onQuery: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (LegoSet) -> Unit,
    onAdd: (LegoSet) -> Unit,
    onSettings: () -> Unit,
) {
    val c = LocalAppColors.current
    val list = rememberLazyListState()
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
            item { Overline("${Fmt.pieces(state.total)} sets gevonden", Modifier.padding(start = 2.dp, top = 6.dp)) }
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
                EmptyCard { T("Geen sets gevonden voor \"${state.query}\"", 14.sp, color = c.textMuted, align = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
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
            if (query.isEmpty()) T("Zoek op naam of setnummer", 14.sp, color = c.textSubtle, maxLines = 1)
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
                Modifier.shadow(4.dp, shape, ambientColor = Brand.Purple, spotColor = Brand.Purple).clip(shape)
                    .background(Brand.gradient).clickable(onClick = onAdd).padding(horizontal = 13.dp, vertical = 9.dp),
                weight = FontWeight.SemiBold, color = Color.White,
            )
        }
    }
}

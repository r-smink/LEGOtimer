package nl.nextbuzz.bricktimer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import nl.nextbuzz.bricktimer.data.AiModel
import nl.nextbuzz.bricktimer.data.PortfolioEntry
import nl.nextbuzz.bricktimer.data.Session
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.ui.screens.SearchField
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors
import nl.nextbuzz.bricktimer.util.ImageUtils

/** Everything the estimate UI can ask the view model to do. */
class EstimateActions(
    val onAddPhotos: (List<Uri>) -> Unit,
    val onRemovePhoto: (Uri) -> Unit,
    val onAnalyze: () -> Unit,
    val onRetry: () -> Unit,
    val onSettings: () -> Unit,
)

/** Up to [MAX_PHOTOS] photos of the build in progress, which an AI model compares to the finished set. */
@Composable
fun EstimateSection(estimate: EstimateState, photos: List<Uri>, hasKey: Boolean, actions: EstimateActions) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) actions.onAddPhotos(listOf(uri))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) actions.onAddPhotos(uris)
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        when (estimate) {
            EstimateState.Idle -> if (!hasKey) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.sunken).clickable(onClick = actions.onSettings).padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = c.textSubtle, modifier = Modifier.size(22.dp))
                    T("Voeg een OpenRouter API-sleutel toe in Instellingen om met een foto je voortgang te laten schatten.", 12.sp, color = c.textMuted, lineHeight = 17.sp)
                }
            } else {
                Overline("Hoe ver ben je?", size = 11.sp)
                T(
                    "Maximaal $MAX_PHOTOS foto's, bijvoorbeeld van losse secties die nog niet aan elkaar zitten.",
                    12.sp, Modifier.padding(top = 2.dp, bottom = 8.dp), color = c.textMuted, lineHeight = 17.sp,
                )
                if (photos.isNotEmpty()) {
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { uri -> PhotoThumb(uri) { actions.onRemovePhoto(uri) } }
                    }
                }
                if (photos.size < MAX_PHOTOS) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlineButton(
                            "Foto maken",
                            {
                                val uri = ImageUtils.newCameraUri(ctx)
                                cameraUri = uri
                                camera.launch(uri)
                            },
                            Modifier.weight(1f),
                            icon = Icons.Rounded.PhotoCamera,
                        )
                        OutlineButton(
                            "Uit galerij",
                            { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            Modifier.weight(1f),
                            icon = Icons.Rounded.PhotoLibrary,
                        )
                    }
                }
                if (photos.isNotEmpty()) {
                    GradientButton(
                        if (photos.size == 1) "Voortgang schatten" else "Voortgang schatten (${photos.size} foto's)",
                        actions.onAnalyze,
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        icon = Icons.Rounded.AutoAwesome,
                        radius = 10.dp,
                        fontSize = 14.sp,
                        vertical = 11.dp,
                    )
                }
            }
            EstimateState.Loading -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.sunken).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                T(if (photos.size > 1) "AI analyseert je ${photos.size} foto's…" else "AI analyseert je build…", 13.sp, weight = FontWeight.SemiBold, color = c.textMuted)
            }
            is EstimateState.Result -> BuildProgressCard(estimate.pct, estimate.note, estimate.confidence.label)
            is EstimateState.Error -> Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.sunken).padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                T(estimate.message, 12.5.sp, color = Brand.Danger, lineHeight = 17.sp)
                OutlineButton("Opnieuw", actions.onRetry)
            }
        }
    }
}

@Composable
private fun PhotoThumb(uri: Uri, onRemove: () -> Unit) {
    val c = LocalAppColors.current
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(c.sunken)) {
        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Verwijderen", tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}

/** Adds an AI estimate to an earlier session, e.g. when the summary was closed before taking a photo. */
@Composable
fun EstimateDialog(
    entry: PortfolioEntry,
    session: Session,
    estimate: EstimateState,
    photos: List<Uri>,
    hasKey: Boolean,
    actions: EstimateActions,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).verticalScroll(rememberScrollState()).padding(22.dp)) {
            T("Voortgang schatten", 20.sp, weight = FontWeight.Bold)
            T(entry.set.name, 13.sp, Modifier.padding(top = 2.dp), color = c.textMuted, maxLines = 2)
            T("Sessie van ${Fmt.date(session.startMs)} · ${Fmt.range(session.startMs, session.durSec)}", 12.sp, Modifier.padding(bottom = 14.dp), color = c.textSubtle)
            if (session.progressPct != null && estimate == EstimateState.Idle) {
                BuildProgressCard(session.progressPct, session.progressNote.orEmpty(), null, Modifier.padding(bottom = 14.dp))
            }
            EstimateSection(estimate, photos, hasKey, actions)
            GradientButton(
                if (estimate is EstimateState.Result) "Klaar" else "Sluiten", onDismiss,
                Modifier.fillMaxWidth().padding(top = 6.dp), radius = 10.dp, vertical = 13.dp,
            )
        }
    }
}

/** Searchable list of OpenRouter models that understand images. */
@Composable
fun ModelPicker(state: ModelsState, selected: String, onPick: (String) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    var filter by remember { mutableStateOf("") }
    val shown = remember(state.models, filter) {
        val f = filter.trim()
        if (f.isEmpty()) state.models else state.models.filter { it.name.contains(f, ignoreCase = true) || it.id.contains(f, ignoreCase = true) }
    }
    LaunchedEffect(Unit) { onRetry() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(top = 20.dp, bottom = 12.dp)) {
            T("Kies een AI-model", 20.sp, Modifier.padding(horizontal = 20.dp), weight = FontWeight.Bold)
            T("Alleen modellen die foto's begrijpen, goedkoopste eerst.", 12.sp, Modifier.padding(horizontal = 20.dp), color = c.textMuted)
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)) { SearchField(filter, { filter = it }, "Zoek een model") }
            when {
                state.loading -> {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    }
                    T("Modellen worden geladen…", 13.sp, Modifier.fillMaxWidth().padding(bottom = 12.dp), color = c.textMuted, align = TextAlign.Center)
                }
                state.error != null -> Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(state.error, 13.sp, color = Brand.Danger, align = TextAlign.Center)
                    OutlineButton("Opnieuw", onRetry)
                }
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    // Lets a model id be typed in directly, e.g. one that is newer than the list.
                    val typed = filter.trim()
                    if ('/' in typed && shown.none { it.id == typed }) {
                        item { ModelRow(AiModel(typed, "Gebruik \"$typed\"", null), false) { onPick(typed) } }
                    }
                    items(shown, key = { it.id }) { m -> ModelRow(m, m.id == selected) { onPick(m.id) } }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(model: AiModel, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            T(model.name, 14.5.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) c.accent else c.text, maxLines = 1)
            T(model.id, 11.5.sp, color = c.textSubtle, maxLines = 1)
        }
        model.promptPrice?.let { T(priceLabel(it), 11.5.sp, color = c.textMuted, maxLines = 1) }
        if (selected) Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
    }
}

private fun priceLabel(perMillion: Double): String = when {
    perMillion == 0.0 -> "gratis"
    perMillion < 1 -> "$" + "%.2f".format(perMillion) + "/M"
    else -> "$" + "%.1f".format(perMillion) + "/M"
}

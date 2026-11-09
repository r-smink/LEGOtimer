package nl.nextbuzz.bricktimer.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.nextbuzz.bricktimer.data.DEFAULT_OPENROUTER_MODEL
import nl.nextbuzz.bricktimer.data.ThemeMode
import nl.nextbuzz.bricktimer.data.AppLanguage
import nl.nextbuzz.bricktimer.logic.tr
import nl.nextbuzz.bricktimer.monetize.Ads
import nl.nextbuzz.bricktimer.monetize.Premium
import nl.nextbuzz.bricktimer.util.ImageUtils
import nl.nextbuzz.bricktimer.ui.screens.AchievementsScreen
import nl.nextbuzz.bricktimer.ui.screens.CatalogScreen
import nl.nextbuzz.bricktimer.ui.screens.DetailScreen
import nl.nextbuzz.bricktimer.ui.screens.HistoryScreen
import nl.nextbuzz.bricktimer.ui.screens.LeaderboardScreen
import nl.nextbuzz.bricktimer.ui.screens.PlayerScreen
import nl.nextbuzz.bricktimer.ui.screens.PortfolioScreen
import nl.nextbuzz.bricktimer.ui.screens.TimerScreen
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors
import nl.nextbuzz.bricktimer.ui.theme.Outfit

private data class NavItem(val root: Screen, private val labelNl: String, private val labelEn: String, val icon: ImageVector) {
    val label: String get() = tr(labelNl, labelEn)
}

private val navItems = listOf(
    NavItem(Screen.Timer, "Timer", "Timer", Icons.Rounded.Schedule),
    NavItem(Screen.Catalog, "Sets", "Sets", Icons.Rounded.GridView),
    NavItem(Screen.Portfolio, "Mijn builds", "My builds", Icons.Rounded.Inventory2),
    NavItem(Screen.Achievements, "Awards", "Awards", Icons.Rounded.Star),
    NavItem(Screen.Leaderboard, "Ranking", "Ranking", Icons.Rounded.Leaderboard),
)

@Composable
fun BrickTimerApp(vm: AppViewModel, onExit: () -> Unit) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val data by vm.data.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val themeOptions by vm.themeOptions.collectAsStateWithLifecycle()
    val minifigs by vm.minifigs.collectAsStateWithLifecycle()
    val estimate by vm.estimate.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val estimateTarget by vm.estimateTarget.collectAsStateWithLifecycle()
    val showManualSessionDialog by vm.showManualSessionDialog.collectAsStateWithLifecycle()
    val manualSessionTarget by vm.manualSessionTarget.collectAsStateWithLifecycle()
    val leaderboard by vm.leaderboard.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val auth by vm.auth.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val estimateActions = remember(vm) {
        EstimateActions(vm::addPhotos, vm::removePhoto, vm::estimateProgress, vm::resetEstimate) { showSettings = true }
    }

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
                            onAddManualSession = { vm.openManualSession() },
                        )
                        Screen.Catalog -> {
                            LaunchedEffect(Unit) { vm.ensureCatalog() }
                            CatalogScreen(
                                data = data,
                                state = catalog,
                                hasKey = vm.effectiveKey.isNotBlank(),
                                themeOptions = themeOptions,
                                onQuery = vm::onQuery,
                                onTheme = vm::setThemeFilter,
                                onParts = vm::setPartsFilter,
                                onOpenThemes = vm::ensureThemeOptions,
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
                        Screen.Leaderboard -> {
                            LaunchedEffect(Unit) { vm.ensureLeaderboard() }
                            LeaderboardScreen(
                                enabled = vm.communityEnabled,
                                account = data.account,
                                state = leaderboard,
                                auth = auth,
                                onSort = vm::setLeaderboardSort,
                                onRetry = vm::loadLeaderboard,
                                onOpenPlayer = vm::openPlayer,
                                onSignIn = vm::signIn,
                                onSignUp = vm::signUp,
                                onClearAuth = vm::clearAuthMessage,
                                onSignOut = vm::signOut,
                                onDeleteAccount = vm::deleteAccount,
                            )
                        }
                        is Screen.Player -> {
                            LaunchedEffect(s.id) { vm.ensurePlayer(s.id) }
                            PlayerScreen(
                                username = s.username,
                                state = player?.takeIf { it.id == s.id },
                                isMe = data.account?.userId == s.id,
                                onBack = { vm.back() },
                                onRetry = { vm.ensurePlayer(s.id) },
                                onOpenSet = vm::openSet,
                            )
                        }
                        Screen.History -> HistoryScreen(
                            data = data,
                            onBack = { vm.back() },
                            onOpen = vm::openSet,
                            onDeleteSession = vm::deleteSession,
                            onEstimate = vm::openEstimate,
                            onAddManualSession = { vm.openManualSession() },
                        )
                        is Screen.Detail -> {
                            LaunchedEffect(s.set.setNum) { vm.ensureMinifigs(s.set) }
                            DetailScreen(
                                data = data,
                                set = s.set,
                                minifigs = data.entry(s.set.setNum)?.set?.numMinifigs ?: minifigs[s.set.setNum],
                                onBack = { vm.back() },
                                onAdd = { vm.addToPortfolio(s.set) },
                                onSetCurrent = { vm.setCurrent(s.set.setNum) },
                                onMarkDone = { vm.markDone(s.set.setNum) },
                                onRemove = { vm.removeFromPortfolio(s.set.setNum); vm.back() },
                                onDeleteSession = { id -> vm.deleteSession(s.set.setNum, id) },
                                onAddManualSession = { vm.openManualSession(s.set) },
                            )
                        }
                    }
                }
            }
            BottomNav(activeRoot) { vm.goTab(it) }
        }

        summary?.let {
            SummaryOverlay(
                sum = it,
                estimate = estimate,
                photos = photos,
                hasOpenRouterKey = data.openRouterKey.isNotBlank(),
                actions = estimateActions,
                onClose = vm::closeSummary,
            )
        }

        estimateTarget?.let { (entry, session) ->
            EstimateDialog(
                entry = entry,
                session = session,
                estimate = estimate,
                photos = photos,
                hasKey = data.openRouterKey.isNotBlank(),
                actions = estimateActions,
                onDismiss = vm::closeEstimateDialog,
            )
        }
    }

    if (showManualSessionDialog) {
        AddManualSessionDialog(
            portfolio = data.portfolio,
            initialSet = manualSessionTarget,
            onSave = { setNum, minutes -> vm.addManualSession(setNum, minutes) },
            onDismiss = vm::closeManualSession,
        )
    }

    if (showSettings) {
        val activity = ctx.findActivity()
        val premium = remember { Premium.get(ctx) }
        val price by premium.price.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { premium.refresh() }
        SettingsDialog(
            isPremium = data.premium,
            price = price,
            onBuyPremium = {
                if (activity == null || !premium.buy(activity)) {
                    Toast.makeText(ctx, tr("Google Play is nu niet bereikbaar. Probeer het later opnieuw.", "Google Play isn't available right now. Please try again later."), Toast.LENGTH_SHORT).show()
                }
            },
            onAdPrivacy = if (activity != null && !data.premium && Ads.privacyOptionsRequired(ctx)) ({ Ads.showPrivacyOptions(activity) }) else null,
            apiKey = data.apiKey,
            openRouterKey = data.openRouterKey,
            openRouterModel = data.openRouterModel,
            themeMode = data.themeMode,
            language = data.language,
            models = models,
            onLoadModels = vm::loadModels,
            onSave = { key, orKey, orModel, mode, language ->
                showSettings = false
                vm.setThemeMode(mode)
                vm.setLanguage(language)
                vm.setOpenRouter(orKey, orModel)
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
private fun SummaryOverlay(
    sum: Summary,
    estimate: EstimateState,
    photos: List<Uri>,
    hasOpenRouterKey: Boolean,
    actions: EstimateActions,
    onClose: () -> Unit,
) {
    val c = LocalAppColors.current
    Box(
        Modifier.fillMaxSize().background(Color(0x8C15101E))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .padding(horizontal = 26.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Badges and photos can make the card taller than the screen, so it scrolls.
        Column(Modifier.fillMaxWidth().shadow(30.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(c.card).verticalScroll(rememberScrollState())) {
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
                    SummaryStat(sum.stat, sum.statLabel, Modifier.weight(1f))
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
                if (sum.canEstimate) EstimateSection(estimate, photos, hasOpenRouterKey, actions)
                GradientButton(tr("Klaar", "Done"), onClose, Modifier.fillMaxWidth().padding(top = 6.dp), radius = 10.dp, vertical = 14.dp)
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
private fun SettingsDialog(
    isPremium: Boolean,
    price: String?,
    onBuyPremium: () -> Unit,
    onAdPrivacy: (() -> Unit)?,
    apiKey: String,
    openRouterKey: String,
    openRouterModel: String,
    themeMode: ThemeMode,
    language: AppLanguage,
    models: ModelsState,
    onLoadModels: () -> Unit,
    onSave: (key: String, openRouterKey: String, openRouterModel: String, mode: ThemeMode, language: AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    var key by remember { mutableStateOf(apiKey) }
    var orKey by remember { mutableStateOf(openRouterKey) }
    var orModel by remember { mutableStateOf(openRouterModel) }
    var mode by remember { mutableStateOf(themeMode) }
    var lang by remember { mutableStateOf(language) }
    var pickModel by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).verticalScroll(rememberScrollState()).padding(22.dp)) {
            T(tr("Instellingen", "Settings"), 20.sp, weight = FontWeight.Bold)

            Overline("Premium", Modifier.padding(top = 18.dp, bottom = 8.dp))
            if (isPremium) {
                T(tr("Reclamevrij ✓ Bedankt voor je steun!", "Ad-free ✓ Thanks for your support!"), 14.sp, weight = FontWeight.SemiBold, color = c.accent)
            } else {
                GradientButton(
                    if (price != null) tr("Reclamevrij voor $price", "Go ad-free for $price") else tr("Reclamevrij maken", "Go ad-free"),
                    onBuyPremium, Modifier.fillMaxWidth(), fontSize = 14.sp, vertical = 12.dp,
                )
                T(
                    tr("Eenmalige aankoop: geen advertentie meer bij het starten van de timer.", "One-time purchase: no more ads when you start the timer."),
                    12.sp, Modifier.padding(top = 6.dp), color = c.textMuted, lineHeight = 17.sp,
                )
                if (onAdPrivacy != null) {
                    T(
                        tr("Privacykeuzes voor advertenties", "Ad privacy choices"), 13.sp,
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onAdPrivacy).padding(vertical = 6.dp),
                        weight = FontWeight.SemiBold, color = c.accent,
                    )
                }
            }
            Overline(tr("Rebrickable API-sleutel", "Rebrickable API key"), Modifier.padding(top = 18.dp, bottom = 8.dp))
            InputField(key, { key = it }, tr("Plak hier je API key", "Paste your API key here"))
            T(
                tr("Gratis aan te maken op rebrickable.com onder Account → Settings → API. Laat leeg om de sleutel uit de build te gebruiken.", "Get one free at rebrickable.com under Account → Settings → API. Leave empty to use the built-in key."),
                12.sp, Modifier.padding(top = 6.dp), color = c.textMuted, lineHeight = 17.sp,
            )

            Overline(tr("OpenRouter API-sleutel (optioneel)", "OpenRouter API key (optional)"), Modifier.padding(top = 18.dp, bottom = 8.dp))
            InputField(orKey, { orKey = it }, "sk-or-…", secret = true)
            Overline(tr("AI-model", "AI model"), Modifier.padding(top = 12.dp, bottom = 8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.sunken)
                    .border(2.dp, c.border, RoundedCornerShape(10.dp)).clickable { pickModel = true; onLoadModels() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                T(orModel.ifBlank { DEFAULT_OPENROUTER_MODEL }, 14.sp, Modifier.weight(1f), color = if (orModel.isBlank()) c.textSubtle else c.text, maxLines = 1)
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textSubtle, modifier = Modifier.size(20.dp))
            }
            T(
                tr("Hiermee schat een AI-model na een sessie aan de hand van een foto hoe ver je bent. Maak een sleutel aan op openrouter.ai/keys; het model moet afbeeldingen ondersteunen.", "Lets an AI model estimate from a photo how far along you are after a session. Create a key at openrouter.ai/keys; the model must support images."),
                12.sp, Modifier.padding(top = 6.dp), color = c.textMuted, lineHeight = 17.sp,
            )

            Overline(tr("Weergave", "Appearance"), Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.DARK to tr("Donker", "Dark"), ThemeMode.LIGHT to tr("Licht", "Light"), ThemeMode.SYSTEM to tr("Systeem", "System")).forEach { (m, label) ->
                    val sel = m == mode
                    T(
                        label, 13.sp,
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (sel) c.tint else c.sunken)
                            .clickable { mode = m }.padding(vertical = 10.dp),
                        weight = FontWeight.SemiBold, color = if (sel) c.accent else c.textMuted, align = TextAlign.Center,
                    )
                }
            }

            Overline(tr("Taal", "Language"), Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Language names stay in their own language so they are recognisable either way.
                listOf(AppLanguage.SYSTEM to tr("Systeem", "System"), AppLanguage.NL to "Nederlands", AppLanguage.EN to "English").forEach { (l, label) ->
                    val sel = l == lang
                    T(
                        label, 13.sp,
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (sel) c.tint else c.sunken)
                            .clickable { lang = l }.padding(vertical = 10.dp),
                        weight = FontWeight.SemiBold, color = if (sel) c.accent else c.textMuted, align = TextAlign.Center, maxLines = 1,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                T(tr("Annuleren", "Cancel"), 14.sp, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(12.dp), weight = FontWeight.SemiBold, color = c.textMuted)
                GradientButton(tr("Opslaan", "Save"), { onSave(key.trim(), orKey.trim(), orModel.trim(), mode, lang) }, fontSize = 14.sp, vertical = 11.dp, horizontal = 18.dp)
            }
        }
    }

    if (pickModel) {
        ModelPicker(
            state = models,
            selected = orModel,
            onPick = { orModel = it; pickModel = false },
            onRetry = onLoadModels,
            onDismiss = { pickModel = false },
        )
    }
}

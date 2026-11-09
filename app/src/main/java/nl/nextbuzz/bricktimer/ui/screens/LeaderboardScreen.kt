package nl.nextbuzz.bricktimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.nextbuzz.bricktimer.data.Account
import nl.nextbuzz.bricktimer.data.LeaderboardSort
import nl.nextbuzz.bricktimer.data.Player
import nl.nextbuzz.bricktimer.logic.BuilderStats
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.logic.localizedTitle
import nl.nextbuzz.bricktimer.logic.tr
import nl.nextbuzz.bricktimer.ui.AuthState
import nl.nextbuzz.bricktimer.ui.Chip
import nl.nextbuzz.bricktimer.ui.EmptyCard
import nl.nextbuzz.bricktimer.ui.GradientButton
import nl.nextbuzz.bricktimer.ui.IconCircle
import nl.nextbuzz.bricktimer.ui.InputField
import nl.nextbuzz.bricktimer.ui.LeaderboardState
import nl.nextbuzz.bricktimer.ui.OutlineButton
import nl.nextbuzz.bricktimer.ui.Overline
import nl.nextbuzz.bricktimer.ui.ScreenTitle
import nl.nextbuzz.bricktimer.ui.T
import nl.nextbuzz.bricktimer.ui.card
import nl.nextbuzz.bricktimer.ui.theme.Brand
import nl.nextbuzz.bricktimer.ui.theme.LocalAppColors

@Composable
fun LeaderboardScreen(
    enabled: Boolean,
    account: Account?,
    state: LeaderboardState,
    auth: AuthState,
    onSort: (LeaderboardSort) -> Unit,
    onRetry: () -> Unit,
    onOpenPlayer: (Player) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, username: String) -> Unit,
    onClearAuth: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val c = LocalAppColors.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Ranking", tr("Vergelijk je builds met andere bouwers", "Compare your builds with other builders"), Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 6.dp)) }
        if (!enabled) {
            item {
                EmptyCard {
                    T(tr("Community nog niet ingesteld", "Community not set up yet"), 15.sp, weight = FontWeight.Bold)
                    T(
                        tr(
                            "Deze build heeft geen Supabase-project. Zet SUPABASE_URL en SUPABASE_ANON_KEY in local.properties en bouw opnieuw.",
                            "This build has no Supabase project. Set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties and rebuild.",
                        ),
                        13.sp, Modifier.padding(top = 6.dp), color = c.textMuted, align = TextAlign.Center, lineHeight = 18.sp,
                    )
                }
            }
            return@LazyColumn
        }
        item {
            if (account == null) AuthCard(auth, onSignIn, onSignUp, onClearAuth)
            else AccountCard(account, onSignOut, onDeleteAccount)
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeaderboardSort.entries.forEach { s -> Chip(s.label, s == state.sort, { onSort(s) }) }
            }
        }
        when {
            state.loading && state.players.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            }
            state.error != null -> item {
                EmptyCard {
                    T(state.error, 13.sp, color = c.textMuted, align = TextAlign.Center)
                    OutlineButton(tr("Opnieuw", "Retry"), onRetry, Modifier.padding(top = 12.dp))
                }
            }
            state.players.isEmpty() -> item {
                EmptyCard {
                    T(
                        if (state.sort == LeaderboardSort.SPEED) tr("Nog niemand heeft een getimede set afgerond.", "Nobody has finished a timed set yet.")
                        else tr("Nog niemand op het leaderboard. Wees de eerste!", "Nobody on the leaderboard yet. Be the first!"),
                        13.sp, color = c.textMuted, align = TextAlign.Center,
                    )
                }
            }
            else -> itemsIndexed(state.players, key = { _, p -> p.id }) { i, p ->
                PlayerRow(i + 1, p, state.sort, isMe = p.id == account?.userId) { onOpenPlayer(p) }
            }
        }
    }
}

private val LeaderboardSort.unit: String
    get() = when (this) {
        LeaderboardSort.TIME -> tr("bouwtijd", "build time")
        LeaderboardSort.SETS -> tr("voltooid", "finished")
        LeaderboardSort.BRICKS -> tr("stenen", "bricks")
        LeaderboardSort.SPEED -> tr("per steen", "per brick")
        LeaderboardSort.STARS -> tr("sterren", "stars")
    }

private fun LeaderboardSort.value(s: BuilderStats): String = when (this) {
    LeaderboardSort.TIME -> Fmt.dur(s.totalSec)
    LeaderboardSort.SETS -> s.setsDone.toString()
    LeaderboardSort.BRICKS -> Fmt.pieces(s.bricksDone)
    LeaderboardSort.SPEED -> s.bestSpb?.let(Fmt::spb) ?: "—"
    LeaderboardSort.STARS -> s.stars.toString()
}

/** Gold, silver and bronze for the podium. */
private fun podium(rank: Int): Color? = when (rank) {
    1 -> Brand.Gold
    2 -> Color(0xFFA8B0BD)
    3 -> Color(0xFFCD7F32)
    else -> null
}

@Composable
private fun PlayerRow(rank: Int, p: Player, sort: LeaderboardSort, isMe: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val medal = podium(rank)
    Row(
        Modifier.fillMaxWidth().card(13.dp, onClick).then(if (isMe) Modifier.background(c.tint) else Modifier).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(medal ?: c.sunken),
            contentAlignment = Alignment.Center,
        ) { T(rank.toString(), 14.sp, weight = FontWeight.Bold, color = if (medal != null) Color.White else c.textMuted) }
        Column(Modifier.weight(1f)) {
            T(if (isMe) "${p.username} ${tr("(jij)", "(you)")}" else p.username, 15.sp, weight = FontWeight.SemiBold, maxLines = 1, color = if (isMe) c.accent else c.text)
            T(
                listOfNotNull(p.stats.rankTitle?.let(::localizedTitle), if (p.stats.setsDone == 1) "1 set" else "${p.stats.setsDone} sets", "${p.stats.stars} ★").joinToString(" · "),
                12.sp, color = c.textMuted, maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            T(sort.value(p.stats), 16.sp, weight = FontWeight.Bold, maxLines = 1)
            T(sort.unit, 11.sp, color = c.textSubtle, maxLines = 1)
        }
    }
}

@Composable
private fun AuthCard(
    auth: AuthState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onClearAuth: () -> Unit,
) {
    val c = LocalAppColors.current
    var signUp by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().card(16.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T(tr("Doe mee met de community", "Join the community"), 17.sp, weight = FontWeight.Bold)
        T(
            tr(
                "Met een account komen je bouwtijden, builds en awards op het leaderboard en kunnen anderen ze bekijken.",
                "With an account, your build times, builds and awards show up on the leaderboard for others to see.",
            ),
            13.sp, color = c.textMuted, lineHeight = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to tr("Inloggen", "Sign in"), true to tr("Account maken", "Create account")).forEach { (mode, label) ->
                val sel = mode == signUp
                T(
                    label, 13.sp,
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (sel) c.tint else c.sunken)
                        .clickable { signUp = mode; onClearAuth() }.padding(vertical = 10.dp),
                    weight = FontWeight.SemiBold, color = if (sel) c.accent else c.textMuted, align = TextAlign.Center,
                )
            }
        }
        if (signUp) InputField(username, { username = it }, tr("Gebruikersnaam", "Username"))
        InputField(email, { email = it.trim() }, tr("E-mailadres", "Email"), keyboardType = KeyboardType.Email)
        InputField(password, { password = it }, if (signUp) tr("Wachtwoord (minimaal 6 tekens)", "Password (at least 6 characters)") else tr("Wachtwoord", "Password"), secret = true)
        auth.error?.let { T(it, 12.5.sp, color = Brand.Danger, lineHeight = 17.sp) }
        auth.info?.let { T(it, 12.5.sp, color = c.accent, lineHeight = 17.sp) }
        val ready = email.isNotBlank() && password.isNotEmpty() && (!signUp || username.isNotBlank())
        GradientButton(
            when {
                auth.busy -> tr("Bezig…", "Working…")
                signUp -> tr("Account maken", "Create account")
                else -> tr("Inloggen", "Sign in")
            },
            {
                if (ready && !auth.busy) {
                    if (signUp) onSignUp(email, password, username) else onSignIn(email, password)
                }
            },
            Modifier.fillMaxWidth().padding(top = 4.dp),
            radius = 10.dp,
        )
    }
}

@Composable
private fun AccountCard(account: Account, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    val c = LocalAppColors.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().card(16.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconCircle(Icons.Rounded.Person, 42.dp, 22.dp)
            Column(Modifier.weight(1f)) {
                Overline(tr("Ingelogd als", "Signed in as"), size = 10.5.sp)
                T(account.username.ifBlank { account.email }, 16.sp, weight = FontWeight.Bold, maxLines = 1)
            }
            OutlineButton(tr("Uitloggen", "Sign out"), onSignOut)
        }
        T(
            tr("Je builds, tijden en awards worden automatisch gedeeld.", "Your builds, times and awards are shared automatically."), 12.sp,
            Modifier.padding(top = 10.dp), color = c.textSubtle,
        )
        T(
            tr("Account verwijderen", "Delete account"), 12.sp,
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { confirmDelete = true }.padding(vertical = 4.dp),
            weight = FontWeight.SemiBold, color = Brand.Danger,
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = tr("Account verwijderen?", "Delete account?"),
            text = tr(
                "Je account, profiel en gedeelde builds worden van de server verwijderd. Je builds op deze telefoon blijven bewaard.",
                "Your account, profile and shared builds will be deleted from the server. Your builds on this phone are kept.",
            ),
            confirm = tr("Verwijderen", "Delete"),
            onConfirm = { confirmDelete = false; onDeleteAccount() },
            onDismiss = { confirmDelete = false },
        )
    }
}

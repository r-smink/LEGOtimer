package nl.nextbuzz.bricktimer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.nextbuzz.bricktimer.logic.BuilderStats
import nl.nextbuzz.bricktimer.logic.tr
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

/** A builder as shown on the leaderboard; the stats are whatever their app last published. */
data class Player(
    val id: String,
    val username: String,
    val stats: BuilderStats,
)

/** One set in another builder's portfolio. */
data class RemoteBuild(
    val set: LegoSet,
    val status: BuildStatus,
    val totalSec: Long,
    val sessions: Int,
)

/** What the leaderboard can be ordered by; [column] is the profiles column, ascending only for speed. */
enum class LeaderboardSort(private val labelNl: String, private val labelEn: String, val column: String, val ascending: Boolean = false) {
    TIME("Bouwtijd", "Build time", "total_sec"),
    SETS("Sets", "Sets", "sets_done"),
    BRICKS("Stenen", "Bricks", "bricks_done"),
    SPEED("Snelste", "Fastest", "best_spb", ascending = true),
    STARS("Sterren", "Stars", "stars");

    val label: String get() = tr(labelNl, labelEn)
}

/** The access token expired or was revoked; refresh it and try again. */
class AuthExpiredException : ApiException(tr("Je sessie is verlopen. Log opnieuw in.", "Your session has expired. Please sign in again."))

/**
 * Accounts, profiles and shared builds on Supabase (https://supabase.com), over its plain REST API.
 * The tables and access rules are in supabase/schema.sql.
 */
class CommunityApi(baseUrl: String, private val anonKey: String) {
    private val base = baseUrl.trimEnd('/')

    /** False when the build has no SUPABASE_URL / SUPABASE_ANON_KEY in local.properties. */
    val configured: Boolean get() = base.isNotBlank() && anonKey.isNotBlank()

    // ---------------------------------------------------------------- auth

    /** @return the signed-in account, or null when the project requires the e-mail address to be confirmed first. */
    suspend fun signUp(email: String, password: String, username: String): Account? {
        val body = JSONObject().put("email", email).put("password", password).put("data", JSONObject().put("username", username))
        val res = JSONObject(request("POST", "/auth/v1/signup", token = null, body = body.toString()))
        return if (res.has("access_token")) account(res) else null
    }

    suspend fun signIn(email: String, password: String): Account {
        val body = JSONObject().put("email", email).put("password", password)
        return account(JSONObject(request("POST", "/auth/v1/token?grant_type=password", token = null, body = body.toString())))
    }

    suspend fun refresh(a: Account): Account {
        val body = JSONObject().put("refresh_token", a.refreshToken)
        return account(JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", token = null, body = body.toString())))
            .let { it.copy(username = it.username.ifBlank { a.username }) }
    }

    suspend fun signOut(a: Account) {
        request("POST", "/auth/v1/logout", a.accessToken, body = "{}")
    }

    suspend fun deleteAccount(a: Account) {
        request("POST", "/rest/v1/rpc/delete_account", a.accessToken, body = "{}")
    }

    private fun account(o: JSONObject): Account {
        val user = o.getJSONObject("user")
        return Account(
            userId = user.getString("id"),
            email = user.optString("email"),
            username = user.optJSONObject("user_metadata")?.optString("username").orEmpty(),
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            expiresAtMs = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
        )
    }

    // ---------------------------------------------------------------- publishing

    suspend fun pushStats(a: Account, s: BuilderStats) {
        val body = JSONObject()
            .put("total_sec", s.totalSec).put("sets_done", s.setsDone).put("bricks_done", s.bricksDone)
            .put("best_spb", s.bestSpb ?: JSONObject.NULL)
            .put("stars", s.stars).put("platinum", s.platinum).put("titles", s.titles)
            .put("rank_title", s.rankTitle ?: JSONObject.NULL)
            .put("evening", s.evening).put("morning", s.morning).put("speed", s.speed)
            .put("updated_at", java.time.Instant.now().toString())
        request("PATCH", "/rest/v1/profiles?id=eq.${a.userId}", a.accessToken, body.toString(), prefer = "return=minimal")
    }

    /** Replaces the published portfolio with [portfolio]: upserts every entry and drops sets that were removed. */
    suspend fun pushBuilds(a: Account, portfolio: List<PortfolioEntry>) {
        if (portfolio.isNotEmpty()) {
            val rows = JSONArray()
            portfolio.forEach { e ->
                rows.put(
                    JSONObject()
                        .put("user_id", a.userId).put("set_num", e.set.setNum).put("name", e.set.name)
                        .put("img_url", e.set.imgUrl ?: JSONObject.NULL).put("num_parts", e.set.numParts)
                        .put("year", e.set.year).put("theme_id", e.set.themeId)
                        .put("theme_name", e.set.themeName).put("status", e.status.name)
                        .put("total_sec", e.totalSec).put("sessions", e.sessions.size)
                        .put("completed_at", e.completedMs?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
                )
            }
            request("POST", "/rest/v1/builds", a.accessToken, rows.toString(), prefer = "resolution=merge-duplicates,return=minimal")
        }
        val keep = portfolio.joinToString(",", "(", ")") { "\"${it.set.setNum}\"" }
        val filter = if (portfolio.isEmpty()) "" else "&set_num=not.in.${enc(keep)}"
        request("DELETE", "/rest/v1/builds?user_id=eq.${a.userId}$filter", a.accessToken, body = null, prefer = "return=minimal")
    }

    // ---------------------------------------------------------------- reading (public, no account needed)

    suspend fun leaderboard(sort: LeaderboardSort): List<Player> {
        val order = "${sort.column}.${if (sort.ascending) "asc" else "desc"},total_sec.desc"
        val filter = if (sort.ascending) "&${sort.column}=not.is.null" else ""
        val arr = JSONArray(request("GET", "/rest/v1/profiles?select=*&order=$order$filter&limit=100", token = null, body = null))
        return (0 until arr.length()).map { player(arr.getJSONObject(it)) }
    }

    suspend fun player(userId: String): Player? {
        val arr = JSONArray(request("GET", "/rest/v1/profiles?select=*&id=eq.$userId", token = null, body = null))
        return if (arr.length() == 0) null else player(arr.getJSONObject(0))
    }

    suspend fun builds(userId: String): List<RemoteBuild> {
        val arr = JSONArray(request("GET", "/rest/v1/builds?select=*&user_id=eq.$userId&order=status.asc,completed_at.desc.nullslast", token = null, body = null))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RemoteBuild(
                set = LegoSet(
                    setNum = o.getString("set_num"),
                    name = o.optString("name"),
                    year = o.optInt("year"),
                    themeId = o.optInt("theme_id"),
                    themeName = o.optString("theme_name"),
                    numParts = o.optInt("num_parts"),
                    imgUrl = if (o.isNull("img_url")) null else o.optString("img_url"),
                    setUrl = null,
                ),
                status = runCatching { BuildStatus.valueOf(o.getString("status")) }.getOrDefault(BuildStatus.PLANNED),
                totalSec = o.optLong("total_sec"),
                sessions = o.optInt("sessions"),
            )
        }
    }

    private fun player(o: JSONObject) = Player(
        id = o.getString("id"),
        username = o.optString("username"),
        stats = BuilderStats(
            totalSec = o.optLong("total_sec"),
            setsDone = o.optInt("sets_done"),
            bricksDone = o.optInt("bricks_done"),
            bestSpb = if (o.isNull("best_spb")) null else o.optDouble("best_spb"),
            stars = o.optInt("stars"),
            platinum = o.optInt("platinum"),
            titles = o.optInt("titles"),
            rankTitle = if (o.isNull("rank_title")) null else o.optString("rank_title"),
            evening = o.optInt("evening"),
            morning = o.optInt("morning"),
            speed = o.optInt("speed"),
        ),
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun request(method: String, path: String, token: String?, body: String?, prefer: String? = null): String = withContext(Dispatchers.IO) {
        if (!configured) throw ApiException(tr("De community is niet geconfigureerd in deze build.", "The community isn't set up in this build."))
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${token ?: anonKey}")
            setRequestProperty("Accept", "application/json")
            if (prefer != null) setRequestProperty("Prefer", prefer)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) return@withContext conn.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val err = runCatching { JSONObject(conn.errorStream.bufferedReader().use { it.readText() }) }.getOrNull()
            throw error(code, err, authCall = path.startsWith("/auth/"))
        } catch (e: SocketTimeoutException) {
            throw ApiException(tr("De server reageerde niet op tijd. Probeer het opnieuw.", "The server didn't respond in time. Please try again."))
        } catch (e: IOException) {
            throw ApiException(tr("Geen verbinding met de server. Controleer je internet.", "Can't reach the server. Check your internet connection."))
        } finally {
            conn.disconnect()
        }
    }

    /** Supabase Auth answers with error_code/msg (or the older error/error_description), PostgREST with code/message. */
    private fun error(code: Int, err: JSONObject?, authCall: Boolean): ApiException {
        val errorCode = err?.optString("error_code").orEmpty()
        val msg = listOf("msg", "error_description", "message").firstNotNullOfOrNull { k -> err?.optString(k)?.takeIf { it.isNotBlank() } }.orEmpty()
        return when {
            errorCode == "invalid_credentials" || msg.contains("Invalid login credentials") -> ApiException(tr("Onjuist e-mailadres of wachtwoord.", "Incorrect email or password."))
            errorCode == "email_not_confirmed" -> ApiException(tr("Bevestig eerst je e-mailadres via de link in je mail.", "Confirm your email first using the link we sent you."))
            errorCode == "user_already_exists" || msg.contains("already registered") -> ApiException(tr("Er bestaat al een account met dit e-mailadres.", "An account with this email already exists."))
            errorCode == "weak_password" -> ApiException(tr("Kies een sterker wachtwoord (minimaal 6 tekens).", "Choose a stronger password (at least 6 characters)."))
            errorCode == "validation_failed" || errorCode == "email_address_invalid" -> ApiException(tr("Ongeldig e-mailadres.", "Invalid email."))
            msg.contains("Database error saving new user") -> ApiException(tr("Die gebruikersnaam is al bezet.", "That username is already taken."))
            errorCode.contains("rate_limit") || code == 429 -> ApiException(tr("Te veel pogingen. Wacht even en probeer het opnieuw.", "Too many attempts. Wait a moment and try again."))
            // An expired or revoked access token; the refresh token itself failing means signing in again.
            !authCall && code == 401 -> AuthExpiredException()
            authCall && errorCode.startsWith("refresh_token") -> AuthExpiredException()
            else -> ApiException(tr("Serverfout ($code)", "Server error ($code)") + if (msg.isBlank()) "." else ": $msg")
        }
    }
}

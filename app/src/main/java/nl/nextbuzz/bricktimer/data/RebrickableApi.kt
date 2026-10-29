package nl.nextbuzz.bricktimer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(message: String) : Exception(message)

data class SetPage(val count: Int, val hasMore: Boolean, val results: List<LegoSet>)

/** Minimal client for https://rebrickable.com/api/v3/docs/ */
class RebrickableApi {
    private val base = "https://rebrickable.com/api/v3/"

    /** Sets matching [query] (name or set number), newest first. Sets without parts are skipped. */
    suspend fun searchSets(key: String, query: String, page: Int, pageSize: Int = 20): SetPage {
        val params = buildString {
            append("page=$page&page_size=$pageSize&min_parts=1&ordering=-year,-num_parts")
            if (query.isNotBlank()) append("&search=").append(URLEncoder.encode(query.trim(), "UTF-8"))
        }
        val o = get(key, "lego/sets/?$params")
        val arr = o.getJSONArray("results")
        return SetPage(
            count = o.optInt("count"),
            hasMore = !o.isNull("next"),
            results = (0 until arr.length()).map { parseSet(arr.getJSONObject(it)) },
        )
    }

    /** All themes as id -> name. */
    suspend fun themes(key: String): Map<Int, String> {
        val out = HashMap<Int, String>()
        var page = 1
        while (true) {
            val o = get(key, "lego/themes/?page=$page&page_size=1000")
            val arr = o.getJSONArray("results")
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                out[t.getInt("id")] = t.getString("name")
            }
            if (o.isNull("next")) return out
            page++
        }
    }

    private fun parseSet(o: JSONObject) = LegoSet(
        setNum = o.getString("set_num"),
        name = o.optString("name"),
        year = o.optInt("year"),
        themeId = o.optInt("theme_id"),
        themeName = "",
        numParts = o.optInt("num_parts"),
        imgUrl = if (o.isNull("set_img_url")) null else o.getString("set_img_url"),
        setUrl = if (o.isNull("set_url")) null else o.getString("set_url"),
    )

    private suspend fun get(key: String, path: String): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "key $key")
            setRequestProperty("Accept", "application/json")
        }
        try {
            when (val code = conn.responseCode) {
                200 -> JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                401, 403 -> throw ApiException("Ongeldige of ontbrekende Rebrickable API-sleutel.")
                404 -> throw ApiException("Niet gevonden op Rebrickable.")
                429 -> throw ApiException("Te veel verzoeken. Probeer het over een paar seconden opnieuw.")
                else -> throw ApiException("Rebrickable gaf een fout ($code).")
            }
        } catch (e: IOException) {
            throw ApiException("Geen verbinding met Rebrickable. Controleer je internet.")
        } finally {
            conn.disconnect()
        }
    }
}

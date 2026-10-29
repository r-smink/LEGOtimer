package nl.nextbuzz.bricktimer.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the portfolio, sessions, timer and settings as JSON in app storage. */
class Store(context: Context) {
    private val dataFile = AtomicFile(File(context.filesDir, "bricktimer.json"))
    private val themesFile = AtomicFile(File(context.filesDir, "themes.json"))

    fun load(): AppData = try {
        if (dataFile.baseFile.exists()) appDataFromJson(JSONObject(read(dataFile))) else AppData()
    } catch (e: Exception) {
        AppData()
    }

    @Synchronized
    fun save(data: AppData) = write(dataFile, appDataToJson(data).toString())

    fun loadThemes(): Map<Int, String> = try {
        if (!themesFile.baseFile.exists()) emptyMap()
        else JSONObject(read(themesFile)).let { o -> o.keys().asSequence().associate { it.toInt() to o.getString(it) } }
    } catch (e: Exception) {
        emptyMap()
    }

    @Synchronized
    fun saveThemes(themes: Map<Int, String>) {
        val o = JSONObject()
        themes.forEach { (id, name) -> o.put(id.toString(), name) }
        write(themesFile, o.toString())
    }

    private fun read(f: AtomicFile) = String(f.readFully(), Charsets.UTF_8)

    private fun write(f: AtomicFile, text: String) {
        val out = f.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

fun legoSetToJson(s: LegoSet) = JSONObject()
    .put("set_num", s.setNum).put("name", s.name).put("year", s.year)
    .put("theme_id", s.themeId).put("theme_name", s.themeName).put("num_parts", s.numParts)
    .put("set_img_url", s.imgUrl ?: JSONObject.NULL).put("set_url", s.setUrl ?: JSONObject.NULL)

fun legoSetFromJson(o: JSONObject) = LegoSet(
    setNum = o.getString("set_num"),
    name = o.optString("name"),
    year = o.optInt("year"),
    themeId = o.optInt("theme_id"),
    themeName = o.optString("theme_name", ""),
    numParts = o.optInt("num_parts"),
    imgUrl = o.optStringOrNull("set_img_url"),
    setUrl = o.optStringOrNull("set_url"),
)

private fun appDataToJson(d: AppData): JSONObject {
    val portfolio = JSONArray()
    d.portfolio.forEach { e ->
        val sessions = JSONArray()
        e.sessions.forEach { s -> sessions.put(JSONObject().put("id", s.id).put("start", s.startMs).put("dur", s.durSec)) }
        portfolio.put(
            JSONObject()
                .put("set", legoSetToJson(e.set))
                .put("status", e.status.name)
                .put("added", e.addedMs)
                .put("completed", e.completedMs ?: JSONObject.NULL)
                .put("sessions", sessions)
        )
    }
    return JSONObject()
        .put("portfolio", portfolio)
        .put("timer", JSONObject().put("running", d.timer.running).put("start", d.timer.startMs))
        .put("apiKey", d.apiKey)
        .put("themeMode", d.themeMode.name)
}

private fun appDataFromJson(o: JSONObject): AppData {
    val arr = o.optJSONArray("portfolio") ?: JSONArray()
    val portfolio = (0 until arr.length()).map { i ->
        val e = arr.getJSONObject(i)
        val ss = e.optJSONArray("sessions") ?: JSONArray()
        PortfolioEntry(
            set = legoSetFromJson(e.getJSONObject("set")),
            status = runCatching { BuildStatus.valueOf(e.getString("status")) }.getOrDefault(BuildStatus.PLANNED),
            addedMs = e.optLong("added"),
            completedMs = if (e.isNull("completed")) null else e.optLong("completed"),
            sessions = (0 until ss.length()).map { j ->
                val s = ss.getJSONObject(j)
                Session(s.getLong("id"), s.getLong("start"), s.getLong("dur"))
            },
        )
    }
    val t = o.optJSONObject("timer")
    return AppData(
        portfolio = portfolio,
        timer = TimerState(t?.optBoolean("running") ?: false, t?.optLong("start") ?: 0L),
        apiKey = o.optString("apiKey", ""),
        themeMode = runCatching { ThemeMode.valueOf(o.getString("themeMode")) }.getOrDefault(ThemeMode.DARK),
    )
}

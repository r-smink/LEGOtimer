package nl.nextbuzz.bricktimer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.nextbuzz.bricktimer.logic.isDutch
import nl.nextbuzz.bricktimer.logic.numberLocale
import nl.nextbuzz.bricktimer.logic.tr
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

enum class Confidence(private val labelNl: String, private val labelEn: String) {
    LOW("Lage zekerheid", "Low confidence"), MEDIUM("Redelijke zekerheid", "Fair confidence"), HIGH("Hoge zekerheid", "High confidence");

    val label: String get() = tr(labelNl, labelEn)
}

data class ProgressEstimate(val percent: Int, val confidence: Confidence, val note: String)

/** What the model needs to know about the build besides the images. */
data class BuildContext(val set: LegoSet, val totalSec: Long, val sessions: Int)

/** An OpenRouter model that accepts images; [promptPrice] is USD per million input tokens, null when unknown. */
data class AiModel(val id: String, val name: String, val promptPrice: Double?)

/** Asks a vision model on https://openrouter.ai how far a build is, from photos compared to the finished set. */
class OpenRouterApi {
    private val base = "https://openrouter.ai/api/v1/"

    /** Every model that takes images and answers in text, cheapest first. The list is public, no key needed. */
    suspend fun visionModels(): List<AiModel> {
        val arr = request("GET", "models", key = null, body = null).optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { m ->
                val arch = m.optJSONObject("architecture")
                arch?.optJSONArray("input_modalities").contains("image") && arch?.optJSONArray("output_modalities").let { it == null || it.contains("text") }
            }
            .map { m ->
                val price = m.optJSONObject("pricing")?.optString("prompt")?.toDoubleOrNull()?.takeIf { it >= 0 }
                AiModel(m.getString("id"), m.optString("name").ifBlank { m.getString("id") }, price?.times(1_000_000))
            }
            .sortedWith(compareBy<AiModel> { it.promptPrice ?: Double.MAX_VALUE }.thenBy { it.name.lowercase() })
    }

    suspend fun estimate(key: String, model: String, build: BuildContext, photosBase64: List<String>): ProgressEstimate {
        val set = build.set
        val minutes = build.totalSec / 60
        val facts = buildString {
            append(tr("Set: ${set.name} (${set.displayNumber}), ${set.numParts} onderdelen", "Set: ${set.name} (${set.displayNumber}), ${set.numParts} pieces"))
            if (set.numMinifigs != null) append(tr(", ${set.numMinifigs} minifiguren", ", ${set.numMinifigs} minifigures"))
            append(tr(".\nBouwtijd tot nu toe: $minutes minuten over ${build.sessions} sessie(s).", ".\nBuild time so far: $minutes minutes over ${build.sessions} session(s)."))
            if (set.numParts > 0 && minutes > 0) {
                val spp = "%.1f".format(numberLocale, build.totalSec.toDouble() / set.numParts)
                append(tr(" Verdeeld over alle onderdelen is dat $spp sec per onderdeel.", " Spread over all pieces, that is $spp sec per piece."))
            }
            append(tr("\n\nAfbeelding 1 is de voltooide set. ", "\n\nImage 1 is the finished set. "))
            if (photosBase64.size == 1) append(tr("Afbeelding 2 is mijn huidige build.", "Image 2 is my current build."))
            else append(
                tr(
                    "Afbeelding 2 t/m ${photosBase64.size + 1} zijn foto's van mijn huidige build. Dit kunnen losse secties zijn die later " +
                        "samengevoegd worden, of dezelfde build vanuit een andere hoek: tel losse secties bij elkaar op en tel niets dubbel.",
                    "Images 2 to ${photosBase64.size + 1} are photos of my current build. They may be separate sections that will be " +
                        "joined later, or the same build from another angle: add separate sections together and don't count anything twice.",
                )
            )
        }
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", facts))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", set.imgUrl)))
        photosBase64.forEach { content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it"))) }
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt()))
                    .put(JSONObject().put("role", "user").put("content", content)),
            )
        val res = request("POST", "chat/completions", key, body)
        val text = res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        return parse(text)
    }

    private fun parse(text: String): ProgressEstimate {
        // Some models wrap the JSON in a code fence or add a sentence around it.
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw ApiException(tr("Het AI-model gaf geen bruikbaar antwoord.", "The AI model didn't give a usable answer."))
        val o = runCatching { JSONObject(text.substring(start, end + 1)) }.getOrElse { throw ApiException(tr("Het AI-model gaf geen bruikbaar antwoord.", "The AI model didn't give a usable answer.")) }
        if (!o.has("percent")) throw ApiException(tr("Het AI-model gaf geen percentage terug.", "The AI model didn't return a percentage."))
        val pct = o.optDouble("percent", 0.0).let { if (it.isNaN()) 0.0 else it }.toInt().coerceIn(0, 100)
        val confidence = when (o.optString("confidence").lowercase()) {
            "high" -> Confidence.HIGH
            "low" -> Confidence.LOW
            else -> Confidence.MEDIUM
        }
        return ProgressEstimate(pct, confidence, o.optString("note").trim())
    }

    private suspend fun request(method: String, path: String, key: String?, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 90_000 // vision models can take a while on several images
            if (key != null) setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("HTTP-Referer", "https://nextbuzz.nl/bricktimer")
            setRequestProperty("X-Title", "BrickTimer")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            when (val code = conn.responseCode) {
                200 -> JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                401, 403 -> throw ApiException(tr("Ongeldige OpenRouter API-sleutel.", "Invalid OpenRouter API key."))
                402 -> throw ApiException(tr("Onvoldoende OpenRouter-tegoed.", "Not enough OpenRouter credit."))
                404 -> throw ApiException(tr("Model niet gevonden op OpenRouter. Kies een ander model in Instellingen.", "Model not found on OpenRouter. Pick another model in Settings."))
                429 -> throw ApiException(tr("Te veel verzoeken. Probeer het over een paar seconden opnieuw.", "Too many requests. Try again in a few seconds."))
                else -> {
                    val detail = runCatching {
                        JSONObject(conn.errorStream.bufferedReader().use { it.readText() }).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    throw ApiException(tr("OpenRouter gaf een fout ($code)", "OpenRouter returned an error ($code)") + if (detail.isNullOrBlank()) "." else ": $detail")
                }
            }
        } catch (e: SocketTimeoutException) {
            throw ApiException(tr("OpenRouter reageerde niet op tijd. Probeer het opnieuw.", "OpenRouter didn't respond in time. Please try again."))
        } catch (e: IOException) {
            throw ApiException(tr("Geen verbinding met OpenRouter. Controleer je internet.", "Can't reach OpenRouter. Check your internet connection."))
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONArray?.contains(value: String): Boolean = this != null && (0 until length()).any { optString(it) == value }

    private companion object {
        /** Picked per call so the prompt, and the model's note shown to the user, follow the current app language. */
        fun systemPrompt(): String = if (isDutch) SYSTEM_PROMPT_NL else SYSTEM_PROMPT_EN

        val SYSTEM_PROMPT_EN = """
            You are a LEGO building expert. You get a photo of the finished set and one or more photos of someone's build in progress.
            Estimate what percentage of the set has been built. Compare which parts of the model are already there, how big and how complete they are.
            Separate sections on different photos belong to the same build. Loose pieces, bags and the instructions in the photo are not built yet.
            Use the build time and the number of pieces as a plausibility check: an experienced builder takes about 5 to 12 seconds per piece.
            If your visual estimate is clearly impossible given the time spent, adjust it and say so in the note.
            Reply only with JSON in this form:
            {"percent": <whole number 0-100>, "confidence": "low" | "medium" | "high", "note": "<one or two short sentences in English about what is done and what is still missing>"}
        """.trimIndent()

        val SYSTEM_PROMPT_NL = """
            Je bent een LEGO-bouwexpert. Je krijgt een foto van de voltooide set en één of meer foto's van iemands build in uitvoering.
            Schat hoeveel procent van de set al gebouwd is. Vergelijk welke delen van het model al staan, hoe groot en hoe compleet ze zijn.
            Losse secties op verschillende foto's horen samen bij dezelfde build. Losse onderdelen, zakjes en de instructies op de foto zijn nog niet gebouwd.
            Gebruik de bouwtijd en het aantal onderdelen als plausibiliteitscheck: een ervaren bouwer doet ongeveer 5 tot 12 seconden per onderdeel.
            Is je visuele schatting duidelijk onmogelijk gezien de bestede tijd, stel hem dan bij en zeg dat in de toelichting.
            Antwoord uitsluitend met JSON in deze vorm:
            {"percent": <geheel getal 0-100>, "confidence": "low" | "medium" | "high", "note": "<één of twee korte zinnen in het Nederlands over wat al af is en wat nog ontbreekt>"}
        """.trimIndent()
    }
}

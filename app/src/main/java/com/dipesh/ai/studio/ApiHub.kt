package com.dipesh.ai.studio

import com.dipesh.ai.net.ApiClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class HubError(message: String) : Exception(message)

data class HubService(
    val id: String, val title: String, val icon: String, val hint: String, val example: String, val attribution: String
)

/**
 * Ten free, key-less public APIs. Every call has a timeout, maps failures to a readable message, and sends
 * a User-Agent. Free endpoints can change, be rate limited or disappear; nothing here is guaranteed.
 */
object ApiHub {
    val services = listOf(
        HubService("weather", "Weather", "🌤", "City name", "Kathmandu", "Open-Meteo.com (CC BY 4.0, free for non-commercial use)"),
        HubService("currency", "Currency", "💱", "Amount and currencies", "100 USD to EUR", "Frankfurter (European Central Bank reference rates; no NPR - try EUR, INR, GBP)"),
        HubService("country", "Countries", "🌍", "Country name", "Nepal", "REST Countries (restcountries.com)"),
        HubService("holidays", "Holidays", "🎉", "Country code and year", "US 2026", "Nager.Date (not every country is supported)"),
        HubService("wiki", "Wikipedia", "📚", "Topic", "Mount Everest", "Wikipedia/Wikimedia, text under CC BY-SA 4.0"),
        HubService("books", "Books", "📖", "Title or author", "Siddhartha", "Open Library (openlibrary.org)"),
        HubService("dictionary", "Dictionary", "📝", "English word", "serendipity", "Free Dictionary API (dictionaryapi.dev)"),
        HubService("translate", "Translate", "🔤", "from>to: text", "en>ne: Good morning", "MyMemory (free tier about 5,000 characters per day)"),
        HubService("quakes", "Earthquakes", "🌋", "Leave empty", "", "USGS Earthquake Hazards Program (public domain)"),
        HubService("air", "Air quality", "🌫", "City name", "Kathmandu", "Open-Meteo Air Quality API (CC BY 4.0)")
    )

    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8").replace("+", "%20")

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "DipeshAI/3.0 (https://github.com/dipeshpandeya791-hub/DIPESH-AI)")
            .header("Accept", "application/json").build()
        try {
            ApiClient.okHttp.newCall(req).execute().use {
                val body = it.body?.string().orEmpty()
                when {
                    it.isSuccessful -> return body
                    it.code == 404 -> throw HubError("Nothing found for that. Check the spelling or code.")
                    it.code == 429 -> throw HubError("This free service is rate limiting us. Wait a minute and try again.")
                    it.code >= 500 -> throw HubError("The service is temporarily unavailable (${it.code}). Try again later.")
                    else -> throw HubError("The service refused the request (${it.code}).")
                }
            }
        } catch (e: HubError) {
            throw e
        } catch (e: IOException) {
            throw HubError("Couldn't reach the service. Check your internet connection.")
        }
    }

    /** Blocking; call from Dispatchers.IO. Returns display text or throws HubError. */
    fun query(service: String, input: String): String {
        val q = input.trim()
        return when (service) {
            "weather" -> { val g = geocode(q); weather(g, fetch("https://api.open-meteo.com/v1/forecast?latitude=${g.lat}&longitude=${g.lon}&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=auto&forecast_days=3")) }
            "air" -> { val g = geocode(q); air(g, fetch("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${g.lat}&longitude=${g.lon}&current=us_aqi,pm10,pm2_5,ozone,nitrogen_dioxide&timezone=auto")) }
            "currency" -> {
                val c = parseCurrencyQuery(q) ?: throw HubError("Write it like: 100 USD to EUR")
                currency(c, fetch("https://api.frankfurter.dev/v1/latest?amount=${c.amount}&base=${c.from}&symbols=${c.to}"))
            }
            "country" -> country(fetch("https://restcountries.com/v3.1/name/${enc(requireText(q))}?fields=name,capital,region,subregion,population,area,languages,currencies"))
            "holidays" -> {
                val (cc, year) = parseHolidayQuery(q) ?: throw HubError("Write it like: US 2026")
                holidays(cc, year, fetch("https://date.nager.at/api/v3/PublicHolidays/$year/$cc"))
            }
            "wiki" -> {
                val search = JSONArray(fetch("https://en.wikipedia.org/w/api.php?action=opensearch&limit=1&format=json&search=${enc(requireText(q))}"))
                val title = search.optJSONArray(1)?.optString(0).orEmpty()
                if (title.isEmpty()) throw HubError("No Wikipedia article found.")
                wiki(fetch("https://en.wikipedia.org/api/rest_v1/page/summary/${enc(title.replace(' ', '_'))}"))
            }
            "books" -> books(fetch("https://openlibrary.org/search.json?limit=5&fields=title,author_name,first_publish_year&q=${enc(requireText(q))}"))
            "dictionary" -> dictionary(fetch("https://api.dictionaryapi.dev/api/v2/entries/en/${enc(requireText(q))}"))
            "translate" -> {
                val (pair, text) = parseTranslateQuery(q) ?: throw HubError("Write it like: en>ne: Good morning")
                if (text.length > 400) throw HubError("Keep the text under 400 characters (free daily limit).")
                translate(fetch("https://api.mymemory.translated.net/get?q=${enc(text)}&langpair=${enc(pair)}"))
            }
            "quakes" -> quakes(fetch("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_day.geojson"))
            else -> throw HubError("Unknown service.")
        }
    }

    private fun requireText(q: String) = q.ifBlank { throw HubError("Type something to look up first.") }

    // ---------- pure parsers (unit tested) ----------
    data class Place(val name: String, val lat: Double, val lon: Double)
    data class CurrencyQuery(val amount: String, val from: String, val to: String)

    private fun geocode(city: String): Place {
        val j = JSONObject(fetch("https://geocoding-api.open-meteo.com/v1/search?count=1&name=${enc(requireText(city))}"))
        val r = j.optJSONArray("results")?.optJSONObject(0) ?: throw HubError("I couldn't find a place called \"$city\".")
        return Place(r.optString("name") + (r.optString("country").takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""), r.getDouble("latitude"), r.getDouble("longitude"))
    }

    fun parseCurrencyQuery(q: String): CurrencyQuery? {
        val m = Regex("(?i)^\\s*([0-9]+(?:\\.[0-9]+)?)?\\s*([a-z]{3})\\s*(?:to|in|->|>)\\s*([a-z]{3})\\s*$").find(q) ?: return null
        return CurrencyQuery(m.groupValues[1].ifEmpty { "1" }, m.groupValues[2].uppercase(), m.groupValues[3].uppercase())
    }

    fun parseHolidayQuery(q: String): Pair<String, Int>? {
        val m = Regex("(?i)^\\s*([a-z]{2})\\s*(\\d{4})?\\s*$").find(q) ?: return null
        val year = m.groupValues[2].toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return m.groupValues[1].uppercase() to year
    }

    fun parseTranslateQuery(q: String): Pair<String, String>? {
        val m = Regex("(?s)^\\s*([a-zA-Z-]{2,5})\\s*(?:>|\\|)\\s*([a-zA-Z-]{2,5})\\s*:\\s*(.+)$").find(q) ?: return null
        return (m.groupValues[1].lowercase() + "|" + m.groupValues[2].lowercase()) to m.groupValues[3].trim()
    }

    fun weatherCodeText(code: Int) = when (code) {
        0 -> "Clear sky"; 1, 2 -> "Mostly clear"; 3 -> "Overcast"; 45, 48 -> "Fog"
        in 51..57 -> "Drizzle"; in 61..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Rain showers"
        85, 86 -> "Snow showers"; 95 -> "Thunderstorm"; 96, 99 -> "Thunderstorm with hail"; else -> "Unknown"
    }

    fun weather(p: Place, json: String): String {
        val j = JSONObject(json); val cur = j.getJSONObject("current"); val d = j.getJSONObject("daily")
        val sb = StringBuilder("${p.name}\nNow: ${cur.getDouble("temperature_2m")}°C, ${weatherCodeText(cur.getInt("weather_code"))}, humidity ${cur.getInt("relative_humidity_2m")}%, wind ${cur.getDouble("wind_speed_10m")} km/h\n")
        val days = d.getJSONArray("time")
        for (i in 0 until days.length())
            sb.append("${days.getString(i)}: ${d.getJSONArray("temperature_2m_min").getDouble(i)}° to ${d.getJSONArray("temperature_2m_max").getDouble(i)}°C, rain ${d.getJSONArray("precipitation_sum").getDouble(i)} mm\n")
        return sb.toString().trim()
    }

    fun air(p: Place, json: String): String {
        val c = JSONObject(json).getJSONObject("current"); val aqi = c.optInt("us_aqi", -1)
        val level = when { aqi < 0 -> "unknown"; aqi <= 50 -> "Good"; aqi <= 100 -> "Moderate"; aqi <= 150 -> "Unhealthy for sensitive groups"; aqi <= 200 -> "Unhealthy"; aqi <= 300 -> "Very unhealthy"; else -> "Hazardous" }
        return "${p.name}\nUS AQI: $aqi ($level)\nPM2.5: ${c.optDouble("pm2_5")} µg/m³, PM10: ${c.optDouble("pm10")} µg/m³\nOzone: ${c.optDouble("ozone")} µg/m³, NO₂: ${c.optDouble("nitrogen_dioxide")} µg/m³"
    }

    fun currency(q: CurrencyQuery, json: String): String {
        val j = JSONObject(json); val rates = j.getJSONObject("rates")
        if (!rates.has(q.to)) throw HubError("That currency pair isn't available.")
        return "${q.amount} ${q.from} = ${rates.getDouble(q.to)} ${q.to}\nRates date: ${j.optString("date")}"
    }

    fun country(json: String): String {
        val a = JSONArray(json); if (a.length() == 0) throw HubError("No country found.")
        val o = a.getJSONObject(0)
        val cur = o.optJSONObject("currencies")?.let { c -> c.keys().asSequence().joinToString { k -> "${c.getJSONObject(k).optString("name")} ($k)" } }.orEmpty()
        val lang = o.optJSONObject("languages")?.let { l -> l.keys().asSequence().joinToString { k -> l.getString(k) } }.orEmpty()
        return "${o.getJSONObject("name").optString("common")} (${o.getJSONObject("name").optString("official")})\nCapital: ${o.optJSONArray("capital")?.optString(0).orEmpty()}\nRegion: ${o.optString("region")}, ${o.optString("subregion")}\nPopulation: ${o.optLong("population")}\nArea: ${o.optDouble("area")} km²\nLanguages: $lang\nCurrencies: $cur"
    }

    fun holidays(cc: String, year: Int, json: String): String {
        val a = JSONArray(json); if (a.length() == 0) return "No holidays listed for $cc $year."
        return "Public holidays $cc $year\n" + (0 until minOf(a.length(), 25)).joinToString("\n") { a.getJSONObject(it).let { h -> "${h.getString("date")}  ${h.getString("name")}" } }
    }

    fun wiki(json: String): String {
        val j = JSONObject(json)
        return "${j.optString("title")}\n${j.optString("extract")}\n\nSource: Wikipedia (CC BY-SA 4.0) ${j.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page").orEmpty()}"
    }

    fun books(json: String): String {
        val docs = JSONObject(json).getJSONArray("docs"); if (docs.length() == 0) throw HubError("No books found.")
        return (0 until docs.length()).joinToString("\n") { i ->
            val d = docs.getJSONObject(i)
            "• ${d.optString("title")} - ${d.optJSONArray("author_name")?.optString(0).orEmpty()} (${d.optInt("first_publish_year").takeIf { it > 0 } ?: "year unknown"})"
        }
    }

    fun dictionary(json: String): String {
        val e = JSONArray(json).getJSONObject(0); val sb = StringBuilder(e.optString("word"))
        e.optString("phonetic").takeIf { it.isNotBlank() }?.let { sb.append("  $it") }
        val meanings = e.optJSONArray("meanings") ?: JSONArray()
        for (i in 0 until minOf(meanings.length(), 3)) {
            val m = meanings.getJSONObject(i)
            val def = m.optJSONArray("definitions")?.optJSONObject(0)?.optString("definition").orEmpty()
            sb.append("\n(${m.optString("partOfSpeech")}) $def")
        }
        return sb.toString()
    }

    fun translate(json: String): String {
        val j = JSONObject(json)
        val status = j.optInt("responseStatus", 200)
        if (status != 200) throw HubError(j.optString("responseDetails", "Translation failed.").take(160))
        return j.getJSONObject("responseData").optString("translatedText")
    }

    fun quakes(json: String): String {
        val f = JSONObject(json).getJSONArray("features"); if (f.length() == 0) return "No earthquakes of magnitude 4.5+ in the last 24 hours."
        return "Magnitude 4.5+ in the last day (${f.length()} total)\n" + (0 until minOf(f.length(), 8)).joinToString("\n") { i ->
            val p = f.getJSONObject(i).getJSONObject("properties")
            "M${p.optDouble("mag")}  ${p.optString("place")}"
        }
    }
}

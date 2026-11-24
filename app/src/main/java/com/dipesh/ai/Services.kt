package com.dipesh.ai

import org.json.JSONObject
import java.net.URLEncoder

/** Parses Google News RSS into headlines. Pure Kotlin so it is unit-testable. */
object NewsParser {
    private val itemRe = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private val titleRe = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)

    fun titles(rss: String, max: Int = 5): List<String> =
        itemRe.findAll(rss)
            .mapNotNull { titleRe.find(it.groupValues[1])?.groupValues?.get(1) }
            .map { clean(it) }
            .filter { it.isNotBlank() }
            .take(max)
            .toList()

    fun clean(s: String): String =
        s.replace("<![CDATA[", "").replace("]]>", "")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&amp;", "&")
            .trim()
}

object NewsService {
    fun headlines(topic: String): String {
        val url = if (topic.isBlank())
            "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"
        else
            "https://news.google.com/rss/search?q=" + URLEncoder.encode(topic, "UTF-8") +
                "&hl=en-US&gl=US&ceid=US:en"
        val titles = NewsParser.titles(Http.get(url), 5)
        if (titles.isEmpty()) return "No headlines found."
        return titles.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n")
    }
}

object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "clear sky"
        1 -> "mostly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "foggy"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61 -> "light rain"
        63 -> "rain"
        65 -> "heavy rain"
        66, 67 -> "freezing rain"
        71, 73, 75, 77 -> "snow"
        80, 81, 82 -> "rain showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "unknown conditions"
    }
}

/** Open-Meteo: free weather and geocoding, no API key needed. */
object WeatherService {
    fun report(city: String): String {
        val geo = JSONObject(
            Http.get(
                "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" +
                    URLEncoder.encode(city, "UTF-8")
            )
        )
        val results = geo.optJSONArray("results")
        if (results == null || results.length() == 0) return "I couldn't find a place called $city."
        val place = results.getJSONObject(0)
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val name = place.optString("name", city)

        val f = JSONObject(
            Http.get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&forecast_days=3&timezone=auto"
            )
        )
        val cur = f.getJSONObject("current")
        val daily = f.getJSONObject("daily")
        val sb = StringBuilder()
        sb.append("Weather in $name now: ${cur.getDouble("temperature_2m")}°C ")
        sb.append("(feels like ${cur.getDouble("apparent_temperature")}°C), ")
        sb.append(WeatherCodes.describe(cur.getInt("weather_code")))
        sb.append(", humidity ${cur.getInt("relative_humidity_2m")}%, ")
        sb.append("wind ${cur.getDouble("wind_speed_10m")} km/h.\n")
        val days = daily.getJSONArray("time")
        val max = daily.getJSONArray("temperature_2m_max")
        val min = daily.getJSONArray("temperature_2m_min")
        val rain = daily.optJSONArray("precipitation_probability_max")
        for (i in 0 until days.length()) {
            sb.append("${days.getString(i)}: ${min.getDouble(i)}–${max.getDouble(i)}°C")
            if (rain != null) sb.append(", rain chance ${rain.optInt(i)}%")
            sb.append("\n")
        }
        return sb.toString().trim()
    }
}

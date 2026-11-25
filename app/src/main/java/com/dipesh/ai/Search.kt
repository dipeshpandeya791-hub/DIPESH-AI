package com.dipesh.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Real web answers: Google-grounded Gemini when available, otherwise DuckDuckGo / Wikipedia. */
object SearchService {

    fun search(store: Store, query: String): String {
        if (store.provider == Store.GEMINI && store.geminiKey.isNotBlank()) {
            try {
                val body = JSONObject()
                    .put(
                        "contents", JSONArray().put(
                            JSONObject().put("role", "user").put(
                                "parts", JSONArray().put(
                                    JSONObject().put("text", "Search the web and answer concisely with current, factual information: $query")
                                )
                            )
                        )
                    )
                    .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                val (code, text) = Http.postJson(
                    "https://generativelanguage.googleapis.com/v1beta/models/${store.geminiModel}:generateContent",
                    mapOf("x-goog-api-key" to store.geminiKey),
                    body.toString()
                )
                if (code in 200..299) {
                    val parts = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                    val sb = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                    }
                    if (sb.isNotBlank()) return sb.toString().trim()
                }
            } catch (_: Exception) {
                // fall through to the free fallback
            }
        }
        return fallback(query)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fallback(q: String): String {
        try {
            val j = JSONObject(Http.get("https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=${enc(q)}"))
            val a = j.optString("AbstractText")
            if (a.isNotBlank()) return a
        } catch (_: Exception) {
        }
        try {
            val s = JSONObject(Http.get("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=1&srsearch=${enc(q)}"))
            val title = s.getJSONObject("query").getJSONArray("search").optJSONObject(0)?.optString("title")
            if (!title.isNullOrBlank()) {
                val sum = JSONObject(Http.get("https://en.wikipedia.org/api/rest_v1/page/summary/" + enc(title).replace("+", "%20")))
                val ex = sum.optString("extract")
                if (ex.isNotBlank()) return ex
            }
        } catch (_: Exception) {
        }
        return "I couldn't find a good answer online. I can open a browser search instead."
    }
}

package com.dipesh.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The AI brain. Supports Google Gemini (default) and Anthropic Claude, both with tool use. */
class Brain(private val store: Store, private val tools: Tools) {

    suspend fun reply(history: List<ChatMsg>, image: String? = null): String = withContext(Dispatchers.IO) {
        val gemini = store.provider == Store.GEMINI
        val key = if (gemini) store.geminiKey else store.apiKey
        if (key.isBlank()) {
            val which = if (gemini) "Gemini" else "Anthropic"
            return@withContext "My AI brain is offline. Open settings and paste your $which API key. " +
                "Until then I can still tell the time, open apps, and control the flashlight."
        }
        try {
            if (gemini) replyGemini(history, image) else replyClaude(history, image)
        } catch (e: Exception) {
            "I couldn't reach my brain (${e.javaClass.simpleName}). Check your internet connection."
        }
    }

    // ---------------- Google Gemini ----------------
    private fun replyGemini(history: List<ChatMsg>, image: String?): String {
        val contents = JSONArray()
        history.takeLast(20).dropWhile { it.role != "user" }.forEach {
            contents.put(
                JSONObject()
                    .put("role", if (it.role == "user") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
            )
        }
        if (image != null && contents.length() > 0) {
            contents.getJSONObject(contents.length() - 1).getJSONArray("parts").put(
                JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", image))
            )
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${store.geminiModel}:generateContent"
        val headers = mapOf("x-goog-api-key" to store.geminiKey)

        var rounds = 0
        while (rounds++ < 6) {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt()))))
                .put("contents", contents)
                .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools.geminiDeclarations())))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 2048))
            val (code, text) = Http.postJson(url, headers, body.toString())
            if (code !in 200..299) return apiError(code, text, "Gemini")

            val resp = JSONObject(text)
            val candidate = resp.optJSONArray("candidates")?.optJSONObject(0)
                ?: return "Gemini didn't return an answer (it may have been blocked). Try rephrasing."
            val content = candidate.optJSONObject("content")
                ?: return "Gemini returned an empty answer. Try again."
            val parts = content.optJSONArray("parts") ?: JSONArray()

            val calls = ArrayList<JSONObject>()
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                val fc = p.optJSONObject("functionCall")
                if (fc != null) calls.add(fc)
                else if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
            }
            if (calls.isEmpty()) return sb.toString().trim().ifEmpty { "Done." }

            // Replay the model turn exactly as received (keeps thought signatures intact).
            contents.put(content)
            val results = JSONArray()
            for (fc in calls) {
                val name = fc.getString("name")
                val out = tools.run(name, fc.optJSONObject("args") ?: JSONObject())
                val fr = JSONObject()
                    .put("name", name)
                    .put("response", JSONObject().put("result", out))
                val id = fc.optString("id")
                if (id.isNotEmpty()) fr.put("id", id)
                results.put(JSONObject().put("functionResponse", fr))
            }
            contents.put(JSONObject().put("role", "user").put("parts", results))
        }
        return "I got stuck in a loop. Please try again."
    }

    // ---------------- Anthropic Claude ----------------
    private fun replyClaude(history: List<ChatMsg>, image: String?): String {
        val msgs = JSONArray()
        history.takeLast(20).dropWhile { it.role != "user" }.forEach {
            msgs.put(JSONObject().put("role", it.role).put("content", it.text))
        }
        if (image != null && msgs.length() > 0) {
            val last = msgs.getJSONObject(msgs.length() - 1)
            val text = last.optString("content")
            last.put(
                "content", JSONArray()
                    .put(
                        JSONObject().put("type", "image").put(
                            "source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", image)
                        )
                    )
                    .put(JSONObject().put("type", "text").put("text", text))
            )
        }
        var rounds = 0
        while (rounds++ < 6) {
            val body = JSONObject()
                .put("model", store.model)
                .put("max_tokens", 700)
                .put("system", systemPrompt())
                .put("tools", tools.schema())
                .put("messages", msgs)
            val (code, text) = Http.postJson(
                CLAUDE_URL,
                mapOf("x-api-key" to store.apiKey, "anthropic-version" to "2023-06-01"),
                body.toString()
            )
            if (code !in 200..299) return apiError(code, text, "Claude")

            val resp = JSONObject(text)
            val content = resp.getJSONArray("content")
            if (resp.optString("stop_reason") != "tool_use") {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    if (b.optString("type") == "text") sb.append(b.optString("text"))
                }
                return sb.toString().trim().ifEmpty { "Done." }
            }
            msgs.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                if (b.optString("type") == "tool_use") {
                    val out = tools.run(b.getString("name"), b.optJSONObject("input") ?: JSONObject())
                    results.put(
                        JSONObject()
                            .put("type", "tool_result")
                            .put("tool_use_id", b.getString("id"))
                            .put("content", out)
                    )
                }
            }
            msgs.put(JSONObject().put("role", "user").put("content", results))
        }
        return "I got stuck in a loop. Please try again."
    }

    private fun apiError(code: Int, body: String, who: String): String {
        val msg = try {
            JSONObject(body).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            body.take(120)
        }
        val keyProblem = code == 401 || code == 403 || msg.contains("API key not valid", true)
        return when {
            keyProblem -> "The $who API key was rejected. Please check it in settings."
            code == 429 -> "$who says I'm asking too fast or the free quota is used up. Try again in a minute."
            code == 404 -> "$who doesn't know that model name. Check the model in settings."
            else -> "$who returned error $code: ${msg.take(160)}"
        }
    }

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.US).format(Date())
        val facts = store.facts()
        return buildString {
            append("You are DIPESH AI, a personal voice assistant living on the user's Android phone, ")
            append("in the style of JARVIS: capable, warm and a little witty. ")
            append("Your replies are spoken aloud, so keep them to 1-3 short sentences with no markdown, lists or emojis. ")
            append("Use your tools to act on the phone and fetch live information instead of guessing. ")
            append("Reply in the language the user speaks (English, Nepali or Hindi). Preferred language: ${store.language}. ")
            append("Current date and time: $now. ")
            if (store.city.isNotBlank()) append("The user's city: ${store.city}. ")
            if (facts.isNotEmpty()) {
                append("Things you remember about the user: ")
                append(facts.joinToString("; "))
                append(". ")
            }
            append("To operate other apps, call read_screen to see what is displayed, then tap_text, type_text, scroll_screen or press_button, and check again with read_screen. ")
            append("You can see photos the user takes with the camera. You can search the web with search_web, manage files, send WhatsApp messages, and run saved automation routines with run_routine. ")
            append("Calls and text messages need the user's approval, which the app asks for automatically. ")
            append("When the user tells you something worth remembering long-term, call remember_fact.")
        }
    }

    companion object {
        private const val CLAUDE_URL = "https://api.anthropic.com/v1/messages"
    }
}

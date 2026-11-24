package com.dipesh.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Claude-powered brain with a tool-use loop so it can act on the phone. */
class Brain(private val store: Store, private val tools: Tools) {

    suspend fun reply(history: List<ChatMsg>): String = withContext(Dispatchers.IO) {
        if (store.apiKey.isBlank()) {
            return@withContext "My AI brain is offline. Open settings and paste your Anthropic API key. " +
                "Until then I can still tell the time, open apps, and control the flashlight."
        }
        try {
            val msgs = JSONArray()
            history.takeLast(20).dropWhile { it.role != "user" }.forEach {
                msgs.put(JSONObject().put("role", it.role).put("content", it.text))
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
                    API_URL,
                    mapOf("x-api-key" to store.apiKey, "anthropic-version" to "2023-06-01"),
                    body.toString()
                )
                if (code !in 200..299) return@withContext apiError(code, text)

                val resp = JSONObject(text)
                val content = resp.getJSONArray("content")
                if (resp.optString("stop_reason") != "tool_use") {
                    val sb = StringBuilder()
                    for (i in 0 until content.length()) {
                        val b = content.getJSONObject(i)
                        if (b.optString("type") == "text") sb.append(b.optString("text"))
                    }
                    return@withContext sb.toString().trim().ifEmpty { "Done." }
                }

                // Claude asked to use tools: run them and send the results back.
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
            "I got stuck in a loop. Please try again."
        } catch (e: Exception) {
            "I couldn't reach my brain (${e.javaClass.simpleName}). Check your internet connection."
        }
    }

    private fun apiError(code: Int, body: String): String {
        val msg = try {
            JSONObject(body).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            body.take(120)
        }
        return when (code) {
            401 -> "The API key was rejected. Please check it in settings."
            else -> "The AI service returned error $code: $msg"
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
            append("When the user tells you something worth remembering long-term, call remember_fact.")
        }
    }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
    }
}

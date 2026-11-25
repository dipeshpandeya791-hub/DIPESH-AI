package com.dipesh.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatMsg(val role: String, val text: String)

/** Small private on-device storage: settings, memory (facts), notes and chat history. */
class Store(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("dipesh_ai", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("api_key", "") ?: ""
        set(v) = p.edit().putString("api_key", v.trim()).apply()

    var provider: String
        get() = p.getString("provider", GEMINI) ?: GEMINI
        set(v) = p.edit().putString("provider", v).apply()

    var geminiKey: String
        get() = p.getString("gemini_key", "") ?: ""
        set(v) = p.edit().putString("gemini_key", v.trim()).apply()

    var geminiModel: String
        get() = p.getString("gemini_model", DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL
        set(v) = p.edit().putString("gemini_model", v.trim().ifEmpty { DEFAULT_GEMINI_MODEL }).apply()

    var model: String
        get() = p.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = p.edit().putString("model", v.trim().ifEmpty { DEFAULT_MODEL }).apply()

    /** If true, screen-control actions run without asking each time (calls and SMS always ask). */
    var autoApprove: Boolean
        get() = p.getBoolean("auto_approve", false)
        set(v) = p.edit().putBoolean("auto_approve", v).apply()

    var city: String
        get() = p.getString("city", "") ?: ""
        set(v) = p.edit().putString("city", v.trim()).apply()

    var language: String
        get() = p.getString("language", "en-US") ?: "en-US"
        set(v) = p.edit().putString("language", v).apply()

    private fun readList(key: String): MutableList<String> {
        val a = JSONArray(p.getString(key, "[]") ?: "[]")
        return MutableList(a.length()) { a.getString(it) }
    }

    private fun writeList(key: String, list: List<String>) {
        p.edit().putString(key, JSONArray(list).toString()).apply()
    }

    fun facts(): List<String> = readList("facts")

    fun addFact(fact: String) {
        val l = readList("facts")
        if (!l.contains(fact)) l.add(fact)
        writeList("facts", l.takeLast(50))
    }

    fun clearFacts() = writeList("facts", emptyList())

    fun notes(): List<String> = readList("notes")

    fun addNote(note: String) {
        val l = readList("notes")
        l.add(note)
        writeList("notes", l.takeLast(200))
    }

    fun clearNotes() = writeList("notes", emptyList())

    fun history(): List<ChatMsg> {
        val a = JSONArray(p.getString("history", "[]") ?: "[]")
        return List(a.length()) {
            val o = a.getJSONObject(it)
            ChatMsg(o.getString("role"), o.getString("text"))
        }
    }

    fun saveHistory(messages: List<ChatMsg>) {
        val a = JSONArray()
        messages.takeLast(40).forEach {
            a.put(JSONObject().put("role", it.role).put("text", it.text))
        }
        p.edit().putString("history", a.toString()).apply()
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
        const val GEMINI = "gemini"
        const val CLAUDE = "claude"
    }
}

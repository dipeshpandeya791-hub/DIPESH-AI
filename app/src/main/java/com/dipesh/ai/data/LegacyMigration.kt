package com.dipesh.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One-time import of data saved by version 1 (SharedPreferences) so nothing the user entered is lost. */
object LegacyMigration {
    suspend fun run(context: Context, settings: SettingsRepository, db: AppDatabase) {
        if (settings.isMigrated()) return
        val p = context.getSharedPreferences("dipesh_ai", Context.MODE_PRIVATE)
        if (p.all.isNotEmpty()) {
            settings.update { s ->
                s.copy(
                    provider = p.getString("provider", s.provider) ?: s.provider,
                    geminiKey = p.getString("gemini_key", "")?.ifEmpty { null } ?: s.geminiKey,
                    claudeKey = p.getString("api_key", "")?.ifEmpty { null } ?: s.claudeKey,
                    geminiModel = p.getString("gemini_model", s.geminiModel) ?: s.geminiModel,
                    claudeModel = p.getString("model", s.claudeModel) ?: s.claudeModel,
                    city = p.getString("city", s.city) ?: s.city,
                    language = p.getString("language", s.language) ?: s.language,
                    wakeWords = p.getString("wake_words", s.wakeWords) ?: s.wakeWords,
                    autoApprove = p.getBoolean("auto_approve", s.autoApprove)
                )
            }
            val now = System.currentTimeMillis()
            val mem = db.memory()
            list(p.getString("facts", "[]")).forEach { mem.insertFact(FactEntity(text = it, createdAt = now)) }
            list(p.getString("notes", "[]")).forEach { mem.insertNote(NoteEntity(text = it, createdAt = now)) }
            try {
                val r = JSONObject(p.getString("routines", "{}") ?: "{}")
                r.keys().forEach { mem.saveRoutine(RoutineEntity(it, r.getString(it))) }
            } catch (_: Exception) {
            }
            try {
                val h = JSONArray(p.getString("history", "[]") ?: "[]")
                if (h.length() > 0) {
                    val id = db.conversations().insert(ConversationEntity(title = "Earlier chat", createdAt = now, updatedAt = now))
                    for (i in 0 until h.length()) {
                        val o = h.getJSONObject(i)
                        db.messages().insert(
                            MessageEntity(conversationId = id, role = o.getString("role"), text = o.getString("text"), createdAt = now + i)
                        )
                    }
                }
            } catch (_: Exception) {
            }
            // Remove the old plain-text copy of the API keys.
            p.edit().clear().apply()
        }
        settings.markMigrated()
    }

    private fun list(json: String?): List<String> = try {
        val a = JSONArray(json ?: "[]")
        List(a.length()) { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

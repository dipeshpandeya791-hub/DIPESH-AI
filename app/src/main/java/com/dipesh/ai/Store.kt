package com.dipesh.ai

import com.dipesh.ai.data.AppDatabase
import com.dipesh.ai.data.FactEntity
import com.dipesh.ai.data.NoteEntity
import com.dipesh.ai.data.RoutineEntity
import com.dipesh.ai.data.Settings
import com.dipesh.ai.data.SettingsRepository

data class ChatMsg(val role: String, val text: String)

/**
 * Synchronous facade used by the AI tools. Settings come from DataStore (via an in-memory flow) and
 * memory/notes/routines from Room. The memory/notes/routines methods touch the database, so call
 * them from a background thread (the tool loop already runs on Dispatchers.IO).
 */
class Store(private val repo: SettingsRepository, private val db: AppDatabase) {
    val settings get() = repo.state
    private val s: Settings get() = repo.state.value

    fun update(change: (Settings) -> Settings) = repo.update(change)

    var provider: String
        get() = s.provider
        set(v) = update { it.copy(provider = v) }

    var geminiKey: String
        get() = s.geminiKey
        set(v) = update { it.copy(geminiKey = v.trim()) }

    var apiKey: String // Anthropic key
        get() = s.claudeKey
        set(v) = update { it.copy(claudeKey = v.trim()) }

    var geminiModel: String
        get() = s.geminiModel
        set(v) = update { it.copy(geminiModel = v.trim().ifEmpty { Settings.DEFAULT_GEMINI_MODEL }) }

    var model: String // Claude model
        get() = s.claudeModel
        set(v) = update { it.copy(claudeModel = v.trim().ifEmpty { Settings.DEFAULT_CLAUDE_MODEL }) }

    var city: String
        get() = s.city
        set(v) = update { it.copy(city = v.trim()) }

    var language: String
        get() = s.language
        set(v) = update { it.copy(language = v) }

    var wakeWords: String
        get() = s.wakeWords
        set(v) = update { it.copy(wakeWords = v.ifBlank { Settings.DEFAULT_WAKE }) }

    var autoApprove: Boolean
        get() = s.autoApprove
        set(v) = update { it.copy(autoApprove = v) }

    // ---- memory (facts) ----
    fun facts(): List<String> = if (s.memoryEnabled) db.memory().factTexts() else emptyList()

    fun addFact(fact: String) {
        if (!s.memoryEnabled) return
        val m = db.memory()
        if (m.countFact(fact) == 0) m.insertFact(FactEntity(text = fact, createdAt = System.currentTimeMillis()))
    }

    fun clearFacts() = db.memory().clearFacts()

    // ---- notes ----
    fun notes(): List<String> = db.memory().noteTexts()

    fun addNote(note: String) = db.memory().insertNote(NoteEntity(text = note, createdAt = System.currentTimeMillis()))

    fun clearNotes() = db.memory().clearNotes()

    // ---- routines ----
    fun routines(): Map<String, String> = db.memory().routines().associate { it.name to it.steps }

    fun saveRoutine(name: String, steps: String) =
        db.memory().saveRoutine(RoutineEntity(name.trim().lowercase(), steps))

    fun deleteRoutine(name: String) = db.memory().deleteRoutine(name.trim().lowercase())

    companion object {
        const val DEFAULT_MODEL = Settings.DEFAULT_CLAUDE_MODEL
        const val DEFAULT_GEMINI_MODEL = Settings.DEFAULT_GEMINI_MODEL
        const val DEFAULT_WAKE = Settings.DEFAULT_WAKE
        const val GEMINI = "gemini"
        const val CLAUDE = "claude"
    }
}

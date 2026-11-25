package com.dipesh.ai.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.settingsStore by preferencesDataStore(name = "dipesh_settings")

/**
 * Settings are read once at start-up into a StateFlow (so reads are instant and thread-safe) and
 * every change is written straight back to DataStore.
 */
class SettingsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(runBlocking { context.settingsStore.data.first().toSettings() })
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun update(change: (Settings) -> Settings) {
        _state.updateAndGet(change)
        scope.launch {
            // Always persist the latest value so out-of-order writes cannot leave stale data.
            context.settingsStore.edit { it.write(_state.value) }
        }
    }

    suspend fun isMigrated(): Boolean = context.settingsStore.data.first()[MIGRATED] == true

    suspend fun markMigrated() {
        context.settingsStore.edit { it[MIGRATED] = true }
    }

    private companion object {
        val MIGRATED = booleanPreferencesKey("legacy_migrated")
        val USER_NAME = stringPreferencesKey("user_name")
        val THEME = stringPreferencesKey("theme")
        val PROVIDER = stringPreferencesKey("provider")
        val GEMINI_KEY = stringPreferencesKey("gemini_key_enc")
        val CLAUDE_KEY = stringPreferencesKey("claude_key_enc")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val CLAUDE_MODEL = stringPreferencesKey("claude_model")
        val CITY = stringPreferencesKey("city")
        val LANGUAGE = stringPreferencesKey("language")
        val WAKE = stringPreferencesKey("wake_words")
        val AUTO = booleanPreferencesKey("auto_approve")
        val PERSONALITY = stringPreferencesKey("personality")
        val CUSTOM = stringPreferencesKey("custom_instructions")
        val RATE = floatPreferencesKey("speech_rate")
        val PITCH = floatPreferencesKey("pitch")
        val VOICE = stringPreferencesKey("voice_name")
        val SPEAK = booleanPreferencesKey("speak_replies")
        val MEMORY = booleanPreferencesKey("memory_enabled")
        val QUICK = stringPreferencesKey("quick_commands")
        val GITHUB = stringPreferencesKey("github_token_enc")
        val SECRETS = stringPreferencesKey("secrets_enc")
        val SKILLS = stringPreferencesKey("skills_json")
        val DEVICES = stringPreferencesKey("devices_json")
        val SIT_VOICES = stringPreferencesKey("situation_voices")
        val OPENAI_URL = stringPreferencesKey("openai_url")
        val OPENAI_KEY = stringPreferencesKey("openai_key_enc")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val CYCLE = booleanPreferencesKey("cycle_voices")

        fun Preferences.toSettings(): Settings {
            val d = Settings()
            return Settings(
                userName = this[USER_NAME] ?: d.userName,
                theme = this[THEME] ?: d.theme,
                provider = this[PROVIDER] ?: d.provider,
                geminiKey = SecretBox.decrypt(this[GEMINI_KEY] ?: ""),
                claudeKey = SecretBox.decrypt(this[CLAUDE_KEY] ?: ""),
                geminiModel = this[GEMINI_MODEL] ?: d.geminiModel,
                claudeModel = this[CLAUDE_MODEL] ?: d.claudeModel,
                city = this[CITY] ?: d.city,
                language = this[LANGUAGE] ?: d.language,
                wakeWords = this[WAKE] ?: d.wakeWords,
                autoApprove = this[AUTO] ?: d.autoApprove,
                personality = this[PERSONALITY] ?: d.personality,
                customInstructions = this[CUSTOM] ?: d.customInstructions,
                speechRate = this[RATE] ?: d.speechRate,
                pitch = this[PITCH] ?: d.pitch,
                voiceName = this[VOICE] ?: d.voiceName,
                speakReplies = this[SPEAK] ?: d.speakReplies,
                memoryEnabled = this[MEMORY] ?: d.memoryEnabled,
                quickCommands = this[QUICK] ?: d.quickCommands,
                githubToken = SecretBox.decrypt(this[GITHUB] ?: ""),
                secrets = SecretBox.decrypt(this[SECRETS] ?: ""),
                skills = this[SKILLS] ?: d.skills,
                devices = this[DEVICES] ?: d.devices,
                situationVoices = this[SIT_VOICES] ?: d.situationVoices,
                openaiUrl = this[OPENAI_URL] ?: d.openaiUrl,
                openaiKey = SecretBox.decrypt(this[OPENAI_KEY] ?: ""),
                openaiModel = this[OPENAI_MODEL] ?: d.openaiModel,
                cycleVoices = this[CYCLE] ?: d.cycleVoices
            )
        }

        fun MutablePreferences.write(s: Settings) {
            this[USER_NAME] = s.userName
            this[THEME] = s.theme
            this[PROVIDER] = s.provider
            this[GEMINI_KEY] = SecretBox.encrypt(s.geminiKey)
            this[CLAUDE_KEY] = SecretBox.encrypt(s.claudeKey)
            this[GEMINI_MODEL] = s.geminiModel
            this[CLAUDE_MODEL] = s.claudeModel
            this[CITY] = s.city
            this[LANGUAGE] = s.language
            this[WAKE] = s.wakeWords
            this[AUTO] = s.autoApprove
            this[PERSONALITY] = s.personality
            this[CUSTOM] = s.customInstructions
            this[RATE] = s.speechRate
            this[PITCH] = s.pitch
            this[VOICE] = s.voiceName
            this[SPEAK] = s.speakReplies
            this[MEMORY] = s.memoryEnabled
            this[QUICK] = s.quickCommands
            this[GITHUB] = SecretBox.encrypt(s.githubToken)
            this[SECRETS] = SecretBox.encrypt(s.secrets)
            this[SKILLS] = s.skills
            this[DEVICES] = s.devices
            this[SIT_VOICES] = s.situationVoices
            this[OPENAI_URL] = s.openaiUrl
            this[OPENAI_KEY] = SecretBox.encrypt(s.openaiKey)
            this[OPENAI_MODEL] = s.openaiModel
            this[CYCLE] = s.cycleVoices
        }
    }
}

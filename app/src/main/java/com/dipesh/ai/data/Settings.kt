package com.dipesh.ai.data

/** Everything the user can configure. Stored in DataStore; API keys are encrypted at rest. */
data class Settings(
    val userName: String = "",
    val theme: String = "system", // system | dark | light
    val provider: String = "gemini", // gemini | claude
    val geminiKey: String = "",
    val claudeKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val claudeModel: String = DEFAULT_CLAUDE_MODEL,
    val city: String = "",
    val language: String = "en-US",
    val wakeWords: String = DEFAULT_WAKE,
    val autoApprove: Boolean = false,
    val personality: String = "jarvis", // jarvis | friendly | professional | concise | custom
    val customInstructions: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String = "",
    val speakReplies: Boolean = true,
    val memoryEnabled: Boolean = true,
    val quickCommands: String = "", // one per line: "Label|Prompt"
    val githubToken: String = "", // free personal access token, encrypted at rest
    val secrets: String = "", // NAME=value per line, encrypted at rest; used as {{secret:NAME}}
    val skills: String = "[]", // installed remote skills (JSON)
    val devices: String = "[]", // smart devices / computers on the LAN (JSON)
    val situationVoices: String = "", // situation=voiceName per line
    val openaiUrl: String = "http://localhost:11434/v1", // free OpenAI-compatible endpoint (Ollama, Groq, OpenRouter...)
    val openaiKey: String = "",
    val openaiModel: String = "llama3.2",
    val cycleVoices: Boolean = false
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
        const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5"
        const val DEFAULT_WAKE = "dipesh, arena, arina, deepesh, दिपेश, एरीना"
    }
}

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
    val quickCommands: String = "" // one per line: "Label|Prompt"
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
        const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5"
        const val DEFAULT_WAKE = "dipesh, arena, arina, deepesh, दिपेश, एरीना"
    }
}

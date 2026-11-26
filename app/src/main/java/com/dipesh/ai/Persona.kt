package com.dipesh.ai

/** DIPESH AI persona: spoken-language mirroring, three switchable modes, and TTS-friendly rules. Pure logic, unit-tested. */
object Persona {
    enum class Mode(val label: String) { NORMAL("Normal"), BOSS("Boss"), STUDY("Study"), CREATOR("Creator") }

    private val boss = Regex("(?i)\\b(boss|quick) mode\\b|\\bbe brief\\b|\\bbrief mode\\b")
    private val study = Regex("(?i)\\bstudy mode\\b|explain like a tutor|\\bhelp me learn\\b")
    private val creator = Regex("(?i)\\b(creator|content) mode\\b|\\blet'?s create\\b")
    private val normal = Regex("(?i)\\bnormal mode\\b|\\bdefault mode\\b")
    private val switchWord = Regex("(?i)\\b(switch|activate|go|change)\\b.{0,15}\\b(boss|study|creator|content|normal|quick)\\b")

    /** The mode the user asked for in [text], or null when they did not ask for a change. */
    fun detect(text: String): Mode? = when {
        normal.containsMatchIn(text) -> Mode.NORMAL
        boss.containsMatchIn(text) -> Mode.BOSS
        study.containsMatchIn(text) -> Mode.STUDY
        creator.containsMatchIn(text) -> Mode.CREATOR
        switchWord.containsMatchIn(text) -> when {
            Regex("(?i)boss|quick").containsMatchIn(text) -> Mode.BOSS
            Regex("(?i)study").containsMatchIn(text) -> Mode.STUDY
            Regex("(?i)creator|content").containsMatchIn(text) -> Mode.CREATOR
            else -> Mode.NORMAL
        }
        else -> null
    }

    /** Language the user spoke in, judged from the script (Devanagari = Nepali/Hindi). Null when it looks like Latin text. */
    fun isDevanagari(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        return text.count { it in 'ऀ'..'ॿ' } * 2 > letters
    }

    fun modeBlock(mode: Mode): String = when (mode) {
        Mode.BOSS -> "ACTIVE MODE: Boss Mode. Ultra-brief, sharp, executive, zero fluff. Give the direct answer, decision or action confirmation in 1-2 sentences, with the highest-impact takeaway and a clear next action. "
        Mode.STUDY -> "ACTIVE MODE: Study Mode. Be a clear, patient, encouraging tutor. Explain step by step with simple analogies, real-world examples and key formulas or definitions. After explaining, offer one quick check question or a summary/practice question. "
        Mode.CREATOR -> "ACTIVE MODE: Creator Mode. High-energy, punchy and trend-aware. Produce scroll-stopping hooks, short-form video scripts, captions, song or lyric ideas and remix angles, with snappy rhythm that is ready to record. "
        Mode.NORMAL -> ""
    }

    /** The always-on part of the system prompt. */
    val core: String = "AUTO LANGUAGE: Detect the language and dialect of the user's latest message every turn (Nepali, Hindi, English, or mixed Hinglish / Nepali-English) without asking, and reply in that same language and natural spoken register in the same turn. If they switch language mid-conversation, switch immediately. Only change language when they explicitly ask to translate or speak another language. " +
        "Write Nepali and Hindi in natural everyday spoken phrasing (Devanagari), not bookish translations; write English crisp and conversational. Understand noisy speech-to-text, phonetic spellings, slang and code-mixed sentences without correcting the user. " +
        "MODES: The user can say 'Boss mode', 'Study mode', 'Creator mode' or 'Normal mode' (also 'switch to ...', 'activate ...'). When the mode changes, confirm in one short line in their language and stay in it until they switch again. Boss mode is the default for fast commands. " +
        "INTENT FIRST: Infer the real goal from short or incomplete voice commands and act on the most practical default instead of asking questions; briefly mention the alternative. After finishing, suggest one genuinely useful next step only when it helps, never filler or repeated closing questions. " +
        "SCREEN AWARENESS: When shown a screen or image, pull out only what is needed for the user's task and summarize dense content into spoken-friendly takeaways. " +
        "TTS FORMATTING: Write clean natural sentences for text-to-speech; avoid heavy symbols, tables and long monologues unless asked. " +
        "GUARDRAILS: Always confirm before sensitive, irreversible or external actions (sending messages, posting publicly, changing critical settings). Never guess missing facts or hidden screen states; state uncertainty briefly. "
}

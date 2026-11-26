package com.dipesh.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/** Speech-to-text and text-to-speech using Android's built-in engines. Use from the main thread. */
class Voice(private val context: Context) {
    private val ui = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    var language: String = "en-US"
        set(value) {
            field = value
            applyTts()
        }
    var rate: Float = 1.0f
        set(value) {
            field = value
            applyTts()
        }
    var pitch: Float = 1.0f
        set(value) {
            field = value
            applyTts()
        }
    var voiceName: String = ""
        set(value) {
            field = value
            applyTts()
        }

    /** "situation=voice name" per line; situations: reply, briefing, ack, error. */
    var situationVoices: String = ""
    /** When on, situations without a chosen voice cycle through the other installed voices and nudge pitch/speed. */
    var cycleVoices: Boolean = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            applyTts()
        }
    }

    private val situations = listOf("reply", "briefing", "ack", "error")

    private fun voiceFor(situation: String): String {
        situationVoices.lineSequence().map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0].trim() == situation && it[1].isNotBlank() }?.let { return it[1].trim() }
        if (cycleVoices && situation != "reply") {
            val opts = voiceOptions()
            if (opts.isNotEmpty()) return opts[(situations.indexOf(situation) + opts.indexOf(voiceName).coerceAtLeast(0)) % opts.size]
        }
        return voiceName
    }

    private fun applyTts(situation: String = "reply") {
        val engine = tts ?: return
        if (!ttsReady) return
        engine.setLanguage(Locale.forLanguageTag(language))
        val (rm, pm) = if (!cycleVoices) 1f to 1f else when (situation) {
            "error" -> 0.92f to 0.9f
            "ack" -> 1.1f to 1.05f
            "briefing" -> 0.97f to 1f
            else -> 1f to 1f
        }
        engine.setSpeechRate((rate * rm).coerceIn(0.3f, 2f))
        engine.setPitch((pitch * pm).coerceIn(0.3f, 2f))
        val name = voiceFor(situation)
        if (name.isNotEmpty()) {
            try {
                engine.voices?.firstOrNull { it.name == name }?.let { engine.voice = it }
            } catch (_: Exception) {
            }
        }
    }

    /** Every language the installed (free, on-device) speech engine can speak: tag to display name. */
    fun availableLanguages(): List<Pair<String, String>> = try {
        (tts?.availableLanguages ?: emptySet()).map { it.toLanguageTag() to it.getDisplayName(Locale.ENGLISH) }
            .filter { it.first.isNotBlank() && it.first != "und" }.distinctBy { it.first }.sortedBy { it.second }
    } catch (e: Exception) { emptyList() }

    /** Names of installed voices for the current language. */
    fun voiceOptions(): List<String> = try {
        val lang = Locale.forLanguageTag(language).language
        tts?.voices?.filter { it.locale.language == lang }?.map { it.name }?.sorted() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Listen once. [onHeard] gets the transcript; [onFail] gets a SpeechRecognizer.ERROR_* code (or -1);
     * [onLevel] gets the live microphone level from 0 to 1 for the sound visualizer.
     */
    fun listen(onHeard: (String) -> Unit, onFail: (Int) -> Unit, onLevel: (Float) -> Unit) {
        stopListening()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFail(-1)
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { rearm(60_000L) }
            override fun onRmsChanged(rmsdB: Float) {
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) { rearm(60_000L) }
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                release(r)
                onFail(error)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                release(r)
                if (text.isBlank()) onFail(SpeechRecognizer.ERROR_NO_MATCH) else onHeard(text)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Auto language: also accept Nepali, Hindi and English so switching mid-chat needs no setting change.
            putStringArrayListExtra("android.speech.extra.ADDITIONAL_LANGUAGES", ArrayList(listOf("en-US", "hi-IN", "ne-NP").filter { it != language }))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        // Watchdog: some phones leave the recognizer hanging with no result and no error, which looks like "mic off".
        val dog = Runnable {
            if (recognizer === r) {
                try { r.cancel(); r.destroy() } catch (_: Exception) {}
                recognizer = null
                onFail(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            }
        }
        watchdog = dog
        rearm(25_000L)
        r.startListening(intent)
    }

    private var watchdog: Runnable? = null
    private fun rearm(ms: Long) { watchdog?.let { ui.removeCallbacks(it); ui.postDelayed(it, ms) } }

    private fun release(r: SpeechRecognizer) {
        watchdog?.let { ui.removeCallbacks(it) }
        ui.post {
            try {
                r.destroy()
            } catch (_: Exception) {
            }
            if (recognizer === r) recognizer = null
        }
    }

    fun stopListening() {
        watchdog?.let { ui.removeCallbacks(it) }
        recognizer?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (_: Exception) {
            }
        }
        recognizer = null
    }

    fun speak(text: String, situation: String = "reply", finished: () -> Unit) {
        val engine = tts
        if (!ttsReady || engine == null || text.isBlank()) {
            finished()
            return
        }
        applyTts(if (situation == "reply" && text.length > 350) "briefing" else situation)
        // Mirror the reply's language: Devanagari text is spoken with a Nepali/Hindi voice when the engine has one.
        if (Persona.isDevanagari(text)) {
            try {
                val have = engine.availableLanguages ?: emptySet()
                val pick = listOf("ne-NP", "hi-IN").map { Locale.forLanguageTag(it) }.firstOrNull { l -> have.any { it.language == l.language } }
                if (pick != null) engine.setLanguage(pick)
            } catch (_: Exception) {}
        }
        val id = UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                ui.post { finished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                ui.post { finished() }
            }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun shutdown() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

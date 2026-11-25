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

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            applyTts()
        }
    }

    private fun applyTts() {
        val engine = tts ?: return
        if (!ttsReady) return
        engine.setLanguage(Locale.forLanguageTag(language))
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
        if (voiceName.isNotEmpty()) {
            try {
                engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.voice = it }
            } catch (_: Exception) {
            }
        }
    }

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
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
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
        }
        r.startListening(intent)
    }

    private fun release(r: SpeechRecognizer) {
        ui.post {
            try {
                r.destroy()
            } catch (_: Exception) {
            }
            if (recognizer === r) recognizer = null
        }
    }

    fun stopListening() {
        recognizer?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (_: Exception) {
            }
        }
        recognizer = null
    }

    fun speak(text: String, finished: () -> Unit) {
        val engine = tts
        if (!ttsReady || engine == null || text.isBlank()) {
            finished()
            return
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

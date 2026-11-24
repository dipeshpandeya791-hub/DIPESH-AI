package com.dipesh.ai

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class Status { IDLE, LISTENING, THINKING, SPEAKING }

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val tools = Tools(app, store)
    private val brain = Brain(store, tools)
    private val voice = Voice(app)
    private val ui = Handler(Looper.getMainLooper())

    val messages = mutableStateListOf<ChatMsg>()
    var status by mutableStateOf(Status.IDLE)
        private set
    var handsFree by mutableStateOf(false)
        private set
    var hint by mutableStateOf("Tap Talk, type, or turn on Hey Dipesh mode.")
        private set

    private var awaitingCommand = false

    init {
        messages.addAll(store.history())
        voice.language = store.language
    }

    // ----- user actions -----
    fun onMicTap() {
        when (status) {
            Status.IDLE -> listenOnce()
            Status.LISTENING -> {
                voice.stopListening()
                status = Status.IDLE
            }
            Status.SPEAKING -> {
                voice.stopSpeaking()
                status = Status.IDLE
            }
            Status.THINKING -> Unit
        }
    }

    fun enableHandsFree(on: Boolean) {
        handsFree = on
        awaitingCommand = false
        if (on) {
            hint = "Say \"Hey Dipesh\" followed by your request."
            if (status == Status.IDLE) listenOnce()
        } else {
            voice.stopListening()
            voice.stopSpeaking()
            status = Status.IDLE
            hint = "Tap Talk, type, or turn on Hey Dipesh mode."
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || status == Status.THINKING) return
        voice.stopListening()
        voice.stopSpeaking()
        process(t)
    }

    fun saveSettings(provider: String, geminiKey: String, apiKey: String, city: String, language: String) {
        store.provider = provider
        store.geminiKey = geminiKey
        store.apiKey = apiKey
        store.city = city
        store.language = language
        voice.language = language
        hint = "Settings saved."
    }

    fun clearMemory() {
        store.clearFacts()
        store.clearNotes()
        hint = "Memory and notes cleared."
    }

    fun clearChat() {
        messages.clear()
        store.saveHistory(messages)
    }

    // ----- listening -----
    private fun listenOnce() {
        status = Status.LISTENING
        hint = if (handsFree && !awaitingCommand) "Waiting for \"Hey Dipesh\"…" else "Listening…"
        voice.listen(onHeard = { onHeard(it) }, onFail = { onListenError(it) })
    }

    private fun onHeard(text: String) {
        if (handsFree && !awaitingCommand) {
            val wake = WAKE.find(text)
            if (wake == null) {
                listenOnce() // not for me, keep waiting
                return
            }
            val rest = text.substring(wake.range.last + 1).trim().trimStart(',', '.', '!', '?', ' ')
            if (rest.isEmpty()) {
                awaitingCommand = true
                status = Status.SPEAKING
                voice.speak("Yes?") { listenOnce() }
            } else {
                process(rest)
            }
        } else {
            process(text)
        }
    }

    private fun onListenError(code: Int) {
        val fatal = code == -1 || code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
        if (fatal) {
            handsFree = false
            awaitingCommand = false
            status = Status.IDLE
            hint = if (code == -1) "Speech recognition isn't available on this phone."
            else "Microphone permission is needed."
            return
        }
        if (handsFree) {
            awaitingCommand = false
            status = Status.LISTENING
            ui.postDelayed({
                if (handsFree && status == Status.LISTENING) listenOnce()
            }, 700)
        } else {
            status = Status.IDLE
            hint = "I didn't catch that. Tap Talk and try again."
        }
    }

    // ----- thinking & speaking -----
    private fun process(text: String) {
        awaitingCommand = false
        messages.add(ChatMsg("user", text))
        status = Status.THINKING
        hint = "Thinking…"
        viewModelScope.launch {
            val snapshot = messages.toList()
            val reply = runCatching { tools.local(text) }.getOrNull() ?: brain.reply(snapshot)
            messages.add(ChatMsg("assistant", reply))
            store.saveHistory(messages)
            status = Status.SPEAKING
            hint = "Speaking…"
            voice.speak(spokenForm(reply)) {
                status = Status.IDLE
                if (handsFree) {
                    listenOnce()
                } else {
                    hint = "Tap Talk or type."
                }
            }
        }
    }

    private fun spokenForm(s: String) = s.replace(Regex("[*#`_>]"), "").replace("\n", ". ")

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }

    companion object {
        private val WAKE = Regex("(?i)(dipesh|dipes|dipash|deepesh|दिपेश)")
    }
}

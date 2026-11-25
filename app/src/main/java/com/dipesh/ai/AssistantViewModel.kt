package com.dipesh.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

enum class Status { IDLE, LISTENING, THINKING, SPEAKING }

class Confirmation(val text: String, val result: java.util.concurrent.CompletableFuture<Boolean>)

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

    var pendingConfirm by mutableStateOf<Confirmation?>(null)
        private set

    /** Incremented to ask the UI to open the camera. */
    var cameraRequest by mutableStateOf(0)
        private set

    private var awaitingCommand = false

    init {
        messages.addAll(store.history())
        voice.language = store.language
        tools.confirm = { text ->
            val c = Confirmation(text, java.util.concurrent.CompletableFuture())
            pendingConfirm = c
            try {
                c.result.get(60, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: Exception) {
                false
            } finally {
                if (pendingConfirm === c) pendingConfirm = null
            }
        }
    }

    fun answerConfirm(allow: Boolean) {
        pendingConfirm?.result?.complete(allow)
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
            hint = "Say \"Hey Dipesh\" or \"Hey Arena\" followed by your request."
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

    fun saveSettings(provider: String, geminiKey: String, apiKey: String, city: String, language: String, autoApprove: Boolean, wakeWords: String) {
        store.wakeWords = wakeWords
        store.autoApprove = autoApprove
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
        hint = if (handsFree && !awaitingCommand) "Waiting for \"Hey Dipesh\" / \"Hey Arena\"…" else "Listening…"
        voice.listen(onHeard = { onHeard(it) }, onFail = { onListenError(it) })
    }

    private fun onHeard(text: String) {
        if (handsFree && !awaitingCommand) {
            val wake = wakeRegex().find(text)
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
    private fun process(text: String, image: String? = null) {
        awaitingCommand = false
        if (image == null && LOOK.containsMatchIn(text.lowercase())) {
            messages.add(ChatMsg("user", text))
            val reply = "Opening the camera. Take a photo and I'll tell you what I see."
            messages.add(ChatMsg("assistant", reply))
            store.saveHistory(messages)
            cameraRequest++
            status = Status.SPEAKING
            voice.speak(reply) {
                status = Status.IDLE
                if (handsFree) listenOnce()
            }
            return
        }
        messages.add(ChatMsg("user", if (image != null) "📷 $text" else text))
        status = Status.THINKING
        hint = "Thinking…"
        viewModelScope.launch {
            val snapshot = messages.toList()
            val local = if (image == null) runCatching { tools.local(text) }.getOrNull() else null
            val reply = local ?: brain.reply(snapshot, image)
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

    /** Opens the camera from the UI (button press). */
    fun requestCamera() {
        cameraRequest++
    }

    /** Sends a photo taken by the camera to the AI together with a question. */
    fun lookAtPhoto(file: File, question: String?) {
        if (status == Status.THINKING) return
        status = Status.THINKING
        hint = "Looking…"
        viewModelScope.launch {
            val b64 = withContext(Dispatchers.IO) { encodeJpeg(file) }
            if (b64 == null) {
                status = Status.IDLE
                hint = "I couldn't read that photo."
                return@launch
            }
            process(question?.takeIf { it.isNotBlank() } ?: "What is this? Identify and describe what you see, briefly.", b64)
        }
    }

    private fun encodeJpeg(file: File): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        if (bmp == null) null else {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        null
    }

    private fun wakeRegex(): Regex {
        val words = store.wakeWords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return Regex("(?i)(" + words.joinToString("|") { Regex.escape(it) } + ")")
    }

    private fun spokenForm(s: String) = s.replace(Regex("[*#`_>]"), "").replace("\n", ". ")

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }

    companion object {
        private val LOOK = Regex("what is this|what's this|what is that|what's that|look at this|identify this|what do you see|इसे देख|यह क्या है|ye kya hai|yah kya hai")
    }
}

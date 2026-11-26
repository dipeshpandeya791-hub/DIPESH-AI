package com.dipesh.ai

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dipesh.ai.data.ConversationEntity
import com.dipesh.ai.data.FactEntity
import com.dipesh.ai.data.MessageEntity
import com.dipesh.ai.data.NoteEntity
import com.dipesh.ai.data.Settings
import com.dipesh.ai.net.networkFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

enum class Status { IDLE, LISTENING, THINKING, SPEAKING }

class Confirmation(val text: String, val result: CompletableFuture<Boolean>)

/** The single source of truth for the assistant: conversations, streaming replies and voice state. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val dipesh = app as DipeshApp
    val store = dipesh.store
    private val db = dipesh.db
    private val tools = Tools(app, store)
    private val brain = Brain(store, tools)
    private val voice = Voice(app)
    private val ui = Handler(Looper.getMainLooper())

    // ---------- observable data ----------
    val settings: StateFlow<Settings> = store.settings

    private val _conversationId = MutableStateFlow<Long?>(null)
    val conversationId: StateFlow<Long?> = _conversationId.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = _conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.messages().observe(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = db.conversations().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val facts: StateFlow<List<FactEntity>> = db.memory().observeFacts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notes: StateFlow<List<NoteEntity>> = db.memory().observeNotes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val conversationCount: StateFlow<Int> = db.conversations().observeCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val messageCount: StateFlow<Int> = db.messages().observeCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val online: StateFlow<Boolean> = networkFlow(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // ---------- UI state ----------
    var status by mutableStateOf(Status.IDLE)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var streamingText by mutableStateOf<String?>(null)
        private set
    var handsFree by mutableStateOf(false)
        private set
    var voiceConversation by mutableStateOf(false)
        private set
    var hint by mutableStateOf(IDLE_HINT)
        private set
    var banner by mutableStateOf<String?>(null)
    /** Set to a route name to make the UI navigate there (e.g. "camera"). */
    var navRequest by mutableStateOf<String?>(null)

    var pendingConfirm by mutableStateOf<Confirmation?>(null)
        private set

    private var job: Job? = null
    private var awaitingCommand = false
    private var silentRetries = 0

    init {
        // a screenshot taken by the user-approved capture service arrives here
        viewModelScope.launch {
            ScreenCapture.results.collect { file ->
                if (file == null) {
                    banner = "I couldn't capture the screen."
                } else {
                    val b64 = withContext(Dispatchers.IO) { ImageUtil.encodeFile(file) }
                    if (b64 == null) banner = "I couldn't read the screenshot." else send(ScreenCapture.prompt, b64)
                }
            }
        }
        viewModelScope.launch {
            settings.collect { s ->
                voice.language = s.language
                voice.rate = s.speechRate
                voice.pitch = s.pitch
                voice.voiceName = s.voiceName
                voice.situationVoices = s.situationVoices
                voice.cycleVoices = s.cycleVoices
            }
        }
        tools.confirm = { text ->
            val c = Confirmation(text, CompletableFuture())
            pendingConfirm = c
            try {
                c.result.get(60, TimeUnit.SECONDS)
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

    // ---------- conversations ----------
    fun newChat() {
        stopAll(exitModes = false)
        _conversationId.value = null
    }

    fun openConversation(id: Long) {
        stopAll(exitModes = false)
        _conversationId.value = id
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            db.conversations().delete(id)
            if (_conversationId.value == id) _conversationId.value = null
        }
    }

    fun deleteAllConversations() {
        stopAll(exitModes = false)
        viewModelScope.launch {
            db.conversations().deleteAll()
            _conversationId.value = null
        }
    }

    private suspend fun ensureConversation(firstMessage: String): Long {
        _conversationId.value?.let { return it }
        val now = System.currentTimeMillis()
        val id = db.conversations().insert(ConversationEntity(title = firstMessage.take(40), createdAt = now, updatedAt = now))
        _conversationId.value = id
        return id
    }

    // ---------- sending & generating ----------
    fun send(text: String, image: String? = null) {
        val t = text.trim()
        if (t.isEmpty() || status == Status.THINKING) return
        voice.stopListening()
        voice.stopSpeaking()
        awaitingCommand = false
        if (image == null && LOOK.containsMatchIn(t.lowercase())) {
            job = viewModelScope.launch {
                val id = ensureConversation(t)
                val now = System.currentTimeMillis()
                db.messages().insert(MessageEntity(conversationId = id, role = "user", text = t, createdAt = now))
                val reply = "Opening the camera. Frame what you want me to look at, then tap Capture."
                db.messages().insert(MessageEntity(conversationId = id, role = "assistant", text = reply, createdAt = now + 1))
                db.conversations().touch(id, now + 1)
                navRequest = "camera"
                if (store.settings.value.speakReplies) voice.speak(reply) { onSpeechDone() } else onSpeechDone()
            }
            return
        }
        job = viewModelScope.launch {
            val id = ensureConversation(t)
            db.messages().insert(
                MessageEntity(conversationId = id, role = "user", text = t, createdAt = System.currentTimeMillis(), hasImage = image != null)
            )
            generate(id, image)
        }
    }

    private suspend fun generate(id: Long, image: String?) {
        status = Status.THINKING
        streamingText = ""
        hint = "Thinking…"
        val history = db.messages().list(id).map { ChatMsg(it.role, it.text) }
        val last = history.lastOrNull()?.text.orEmpty()
        val local = if (image == null) runCatching { tools.local(last) }.getOrNull() else null
        val result = if (local != null) Brain.Result(local) else brain.reply(history, image) { streamingText = it }
        db.messages().insert(
            MessageEntity(
                conversationId = id, role = "assistant", text = result.text,
                createdAt = System.currentTimeMillis(), isError = result.isError
            )
        )
        db.conversations().touch(id, System.currentTimeMillis())
        streamingText = null
        if (!result.isError && store.settings.value.speakReplies) {
            status = Status.SPEAKING
            hint = "Speaking…"
            voice.speak(spokenForm(result.text)) { onSpeechDone() }
        } else if (result.isError && store.settings.value.speakReplies && result.text.length < 160) {
            status = Status.SPEAKING
            voice.speak(result.text, "error") { onSpeechDone() }
        } else {
            onSpeechDone()
        }
    }

    fun regenerate() {
        if (status == Status.THINKING) return
        voice.stopSpeaking()
        job = viewModelScope.launch {
            val id = _conversationId.value ?: return@launch
            val last = db.messages().list(id).lastOrNull() ?: return@launch
            if (last.role == "assistant") db.messages().delete(last.id)
            if (db.messages().list(id).lastOrNull()?.role == "user") generate(id, null)
        }
    }

    private fun onSpeechDone() {
        status = Status.IDLE
        hint = IDLE_HINT
        if (handsFree || voiceConversation) {
            if (voiceConversation) awaitingCommand = true
            listenOnce()
        }
    }

    /** Stops everything in flight. With [exitModes] it also leaves hands-free and voice-conversation modes. */
    fun stopAll(exitModes: Boolean = true) {
        job?.cancel()
        job = null
        voice.stopListening()
        voice.stopSpeaking()
        streamingText = null
        level = 0f
        status = Status.IDLE
        awaitingCommand = false
        hint = IDLE_HINT
        if (exitModes) {
            handsFree = false
            voiceConversation = false
        }
        syncKeepAlive()
        // Interrupting a reply in a mic mode must not switch the mic off: carry on listening.
        if (!exitModes && (handsFree || voiceConversation)) {
            if (voiceConversation) awaitingCommand = true
            ui.postDelayed({ if ((handsFree || voiceConversation) && status == Status.IDLE) listenOnce() }, 400)
        }
    }

    fun stopSpeaking() {
        voice.stopSpeaking()
        if (status == Status.SPEAKING) onSpeechDone()
    }

    fun speak(text: String) {
        voice.stopListening()
        voice.stopSpeaking()
        status = Status.SPEAKING
        voice.speak(spokenForm(text)) { status = Status.IDLE }
    }

    fun previewVoice() = speak("Hello, I'm DIPESH AI. This is how I sound.")

    fun voiceOptions(): List<String> = voice.voiceOptions()

    fun availableLanguages(): List<Pair<String, String>> = voice.availableLanguages()

    fun previewSituation(situation: String) = voice.run {
        stopSpeaking()
        speak(when (situation) { "error" -> "Sorry, something went wrong."; "ack" -> "Yes?"; "briefing" -> "Here is your briefing."; else -> "Hello, I'm DIPESH AI." }, situation) {}
    }

    // ---------- listening ----------
    fun onMicTap() {
        // While hands-free / voice conversation is on, the mic stays on until the user turns it off: tapping the mic is that "off".
        if (handsFree || voiceConversation) { stopAll(exitModes = true); return }
        when (status) {
            Status.IDLE -> {
                awaitingCommand = true
                listenOnce()
            }
            Status.LISTENING -> stopAll(exitModes = false)
            Status.SPEAKING -> stopSpeaking()
            Status.THINKING -> stopAll(exitModes = false)
        }
    }

    fun enableHandsFree(on: Boolean) {
        if (on) {
            voiceConversation = false
            handsFree = true
            awaitingCommand = false
            hint = "Say \"Hey Dipesh\" or \"Hey Arena\" followed by your request."
            syncKeepAlive()
            if (status == Status.IDLE) listenOnce()
        } else {
            stopAll(exitModes = true)
        }
    }

    fun switchVoiceConversation(on: Boolean) {
        if (on) {
            handsFree = false
            voiceConversation = true
            silentRetries = 0
            awaitingCommand = true
            syncKeepAlive()
            if (status == Status.IDLE) listenOnce()
        } else {
            stopAll(exitModes = true)
        }
    }

    /** Runs a foreground microphone service while a mic mode is on, so listening continues in the background. */
    private fun syncKeepAlive() {
        val app = getApplication<Application>()
        if (handsFree || voiceConversation) {
            VoiceKeepAliveService.onStopRequested = { stopAll(exitModes = true) }
            VoiceKeepAliveService.start(app)
        } else {
            VoiceKeepAliveService.onStopRequested = null
            VoiceKeepAliveService.stop(app)
        }
    }

    private fun listenOnce() {
        status = Status.LISTENING
        hint = if (handsFree && !awaitingCommand && !voiceConversation) "Waiting for \"Hey Dipesh\"…" else "Listening…"
        voice.listen(
            onHeard = {
                level = 0f
                silentRetries = 0
                onHeard(it)
            },
            onFail = {
                level = 0f
                onListenError(it)
            },
            onLevel = { level = it }
        )
    }

    private fun onHeard(text: String) {
        if (handsFree && !awaitingCommand && !voiceConversation) {
            val wake = wakeRegex().find(text)
            if (wake == null) {
                listenOnce() // not for me, keep waiting
                return
            }
            val rest = text.substring(wake.range.last + 1).trim().trimStart(',', '.', '!', '?', ' ')
            if (rest.isEmpty()) {
                awaitingCommand = true
                status = Status.SPEAKING
                voice.speak("Yes?", "ack") { listenOnce() }
            } else {
                send(rest)
            }
        } else {
            send(text)
        }
    }

    private fun onListenError(code: Int) {
        val fatal = code == -1 || code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
        if (fatal) {
            handsFree = false
            voiceConversation = false
            awaitingCommand = false
            status = Status.IDLE
            hint = if (code == -1) "No speech recognition service found. Install the free Google app (or Speech Recognition & Synthesis from Google) and pick it under Android Settings > Voice input." else "Microphone permission is needed."
            banner = hint
            syncKeepAlive()
            return
        }
        if (handsFree || voiceConversation) {
            // Silence, no match, busy or network hiccups never turn the mic off; keep listening until the user stops it.
            if (handsFree) awaitingCommand = false
            status = Status.LISTENING
            val quiet = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ui.postDelayed({
                if ((handsFree || voiceConversation) && status == Status.LISTENING) listenOnce()
            }, if (quiet) 300L else 1500L)
        } else {
            status = Status.IDLE
            hint = "I didn't catch that. Tap the mic and try again."
        }
    }

    private fun wakeRegex(): Regex {
        val words = store.wakeWords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return Regex("(?i)(" + words.joinToString("|") { Regex.escape(it) } + ")")
    }

    private fun spokenForm(s: String) =
        s.replace(Regex("```[\\s\\S]*?```"), " code block. ")
            .replace(Regex("[*#`_>•]"), "")
            .replace("\n", ". ")

    // ---------- settings helpers ----------
    fun update(change: (Settings) -> Settings) = store.update(change)

    fun testConnection() {
        banner = "Testing connection…"
        viewModelScope.launch {
            val r = brain.reply(listOf(ChatMsg("user", "Reply with just the word OK.")), null) {}
            banner = if (r.isError) r.text else "Connected ✓"
        }
    }

    // ---------- developer agent ----------
    fun installSkill(source: String) {
        if (source.isBlank()) return
        banner = "Installing skill…"
        viewModelScope.launch(Dispatchers.IO) { banner = Skills.install(store, GitHubClient(store), source) }
    }

    fun updateSkills() {
        banner = "Updating skills…"
        viewModelScope.launch(Dispatchers.IO) { banner = Skills.update(store, GitHubClient(store)).take(200) }
    }

    fun setSkillEnabled(name: String, on: Boolean) =
        Skills.save(store, Skills.load(store).map { if (it.name == name) it.copy(enabled = on) else it })

    fun removeSkill(name: String) = Skills.save(store, Skills.load(store).filterNot { it.name == name })

    // ---------- memory ----------
    fun addFact(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { store.addFact(t) }
    }

    fun deleteFact(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.memory().deleteFact(id) }
    }

    fun clearFacts() {
        viewModelScope.launch(Dispatchers.IO) { store.clearFacts() }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.memory().deleteNote(id) }
    }

    fun clearNotes() {
        viewModelScope.launch(Dispatchers.IO) { store.clearNotes() }
    }

    /** Wipes chats, memory, notes, routines and all settings including API keys. */
    fun eraseEverything() {
        stopAll(exitModes = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.clearAllTables() }
            store.update { Settings() }
            _conversationId.value = null
            banner = "All data erased."
        }
    }

    override fun onCleared() {
        VoiceKeepAliveService.onStopRequested = null
        VoiceKeepAliveService.stop(getApplication())
        voice.shutdown()
        super.onCleared()
    }

    companion object {
        private const val IDLE_HINT = "Tap the mic to talk, or type below."
        private val LOOK = Regex(
            "what is this|what's this|what is that|what's that|look at this|identify this|what do you see|" +
                "इसे देख|यह क्या है|ye kya hai|yah kya hai"
        )
    }
}

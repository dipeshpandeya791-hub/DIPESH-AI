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
import com.dipesh.ai.studio.ExpenseStore
import com.dipesh.ai.studio.Expenses
import com.dipesh.ai.studio.Ev
import com.dipesh.ai.studio.LocalSkills
import com.dipesh.ai.studio.Progress
import com.dipesh.ai.studio.ProgressState
import com.dipesh.ai.studio.ProgressStore
import com.dipesh.ai.studio.Shopping
import com.dipesh.ai.studio.ShoppingStore
import com.dipesh.ai.studio.ProjectLibrary
import com.dipesh.ai.studio.TaskItem
import com.dipesh.ai.studio.TaskRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    /** Holographic capabilities overlay (opened by voice command, the HUD tile or the floating widget). */
    var showFeatures by mutableStateOf(false)
    /** Set when the floating widget asks the app to start listening. */
    var pendingListen by mutableStateOf(false)

    /** User messages per day for the last 7 days (oldest first), for the predictive graph. */
    val weeklyActivity: StateFlow<List<Int>> = db.messages().observeCount()
        .flatMapLatest { db.messages().observeUserTimes(dayStart(-6)) }
        .map { times ->
            val start = dayStart(-6)
            val out = IntArray(7)
            times.forEach { out[((it - start) / 86_400_000L).toInt().coerceIn(0, 6)]++ }
            out.toList()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, List(7) { 0 })

    private fun dayStart(offsetDays: Int): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        add(java.util.Calendar.DAY_OF_YEAR, offsetDays)
    }.timeInMillis

    var banner by mutableStateOf<String?>(null)
    /** Set to a route name to make the UI navigate there (e.g. "camera"). */
    var navRequest by mutableStateOf<String?>(null)

    var pendingConfirm by mutableStateOf<Confirmation?>(null)
        private set

    private var job: Job? = null
    private var awaitingCommand = false
    private var silentRetries = 0

    init {
        WidgetBus.provider = { status.name }
        ui.post { progress = progressStore.load() } // after construction, so the lazy stores exist
        WidgetBus.levelProvider = { level }
        // tapping the Yeti talks to the assistant right here: starts a voice conversation (mic stays on until tapped again)
        WidgetBus.onTap = {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) false
            else {
                ui.post { if (handsFree || voiceConversation || status != Status.IDLE) stopAll(exitModes = true) else switchVoiceConversation(true) }
                true
            }
        }
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
                voice.naturalEnabled = s.naturalVoice
                voice.geminiKey = s.geminiKey
                voice.geminiVoice = s.geminiVoice
                voice.geminiTone = s.geminiTone
                voice.geminiModel = s.geminiTtsModel
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
        if (t.isEmpty() || status == Status.THINKING) {
            // Ignored input must never leave the mic dead in a voice mode.
            if (status != Status.THINKING && (handsFree || voiceConversation)) ui.postDelayed({ if ((handsFree || voiceConversation) && status == Status.IDLE) listenOnce() }, 300)
            return
        }
        voice.stopListening()
        voice.stopSpeaking()
        awaitingCommand = false
        // Holographic display commands work with no AI key and are never sent to the model.
        award(Ev.COMMAND)
        LocalSkills.parse(t)?.let { skill -> runSkill(t, skill); return }
        Persona.detect(t)?.let { m ->
            brain.mode = m
            val ack = when (m) {
                Persona.Mode.BOSS -> "Boss mode on. Short and sharp."
                Persona.Mode.STUDY -> "Study mode on. Let's learn."
                Persona.Mode.CREATOR -> "Creator mode on. Let's make something."
                Persona.Mode.NORMAL -> "Back to normal mode."
            }
            localCommand(t, ack) {}; return
        }
        when (RuleBased.parse(t)) {
            RuleBased.Intent.ShowFeatures -> { localCommand(t, "Here is everything I can do.") { showFeatures = true }; return }
            RuleBased.Intent.HideFeatures -> if (showFeatures) { localCommand(t, "Closing the display.") { showFeatures = false }; return }
            else -> {}
        }
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
        try {
            generateInner(id, image)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Anything unexpected: say so, and go back to listening instead of leaving the app stuck on "thinking".
            streamingText = null
            banner = "Something went wrong: ${e.message?.take(120) ?: e.javaClass.simpleName}"
            onSpeechDone()
        }
    }

    private suspend fun generateInner(id: Long, image: String?) {
        status = Status.THINKING
        streamingText = ""
        hint = "Thinking…"
        val history = db.messages().list(id).map { ChatMsg(it.role, it.text) }
        val last = history.lastOrNull()?.text.orEmpty()
        val mood = Persona.mood(last)
        brain.mood = mood
        when (mood) {
            Persona.Mood.SAD, Persona.Mood.STRESSED, Persona.Mood.TIRED -> WidgetBus.post("comfort")
            Persona.Mood.HAPPY -> WidgetBus.post("celebrate")
            else -> {}
        }
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
        WidgetBus.post(if (result.isError) "error" else "success")
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

    /** Runs a local action, records the exchange in the chat and speaks the reply (keeps hands-free mode going). */

    // ---------------------------------------------------------------- offline smart skills
    var timerEndsAt by androidx.compose.runtime.mutableLongStateOf(0L); private set
    var timerFocus by androidx.compose.runtime.mutableStateOf(false); private set
    private var timerJob: kotlinx.coroutines.Job? = null
    private val expenseStore by lazy { ExpenseStore(getApplication()) }
    private val shoppingStore by lazy { ShoppingStore(getApplication()) }
    private val progressStore by lazy { ProgressStore(getApplication()) }

    /** The Yeti's growth (XP, level, streak, badges); shown on the Home screen. */
    var progress by androidx.compose.runtime.mutableStateOf(ProgressState()); private set

    private fun today(): Long { val now = System.currentTimeMillis(); return (now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L }

    fun award(ev: Ev) {
        val (n, up) = Progress.record(progress, ev, today())
        progress = n
        progressStore.save(n)
        if (up) { banner = "Level ${Progress.level(n.xp)}: ${Progress.title(Progress.level(n.xp))}! 🎉"; WidgetBus.post("celebrate") }
    }

    private fun batteryInfo(): Pair<Int, Boolean> {
        val i = getApplication<Application>().registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = i?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING || st == android.os.BatteryManager.BATTERY_STATUS_FULL
        return (if (level < 0 || scale <= 0) -1 else level * 100 / scale) to charging
    }

    fun cancelTimer(): Boolean {
        val was = timerEndsAt > 0L
        timerJob?.cancel(); timerEndsAt = 0L; WidgetBus.focus = false
        return was
    }

    private fun timerDone(focus: Boolean) {
        timerEndsAt = 0L; WidgetBus.focus = false
        WidgetBus.post("celebrate")
        award(if (focus) Ev.FOCUS else Ev.TIMER)
        val msg = if (focus) "Focus session complete. Nice work!" else "Time's up!"
        banner = msg
        if (status == Status.IDLE || handsFree || voiceConversation) {
            voice.stopListening(); status = Status.SPEAKING
            voice.speak(msg) { onSpeechDone() }
        }
    }

    private fun runSkill(t: String, skill: LocalSkills.Skill) {
        val now = System.currentTimeMillis()
        when (skill) {
            is LocalSkills.Skill.Answer -> localCommand(t, skill.reply) { skill.event?.let { WidgetBus.post(it) } }
            is LocalSkills.Skill.ShopAdd -> {
                val before = shoppingStore.load(); val after = Shopping.add(before, skill.items); shoppingStore.save(after)
                val added = after.size - before.size
                localCommand(t, if (added == 0) "Those are already on your shopping list." else "Added ${skill.items.joinToString(", ")} to your shopping list. It has ${after.size} item${if (after.size == 1) "" else "s"} now.") {}
            }
            is LocalSkills.Skill.ShopRemove -> {
                val (after, hit) = Shopping.remove(shoppingStore.load(), skill.item)
                if (hit) shoppingStore.save(after)
                localCommand(t, if (hit) "Removed ${skill.item} from your shopping list." else "I couldn't find ${skill.item} on your shopping list.") {}
            }
            LocalSkills.Skill.ShopShow -> localCommand(t, Shopping.describe(shoppingStore.load())) {}
            LocalSkills.Skill.ShopClear -> localCommand(t, "Shopping list cleared.") { shoppingStore.save(emptyList()) }
            is LocalSkills.Skill.Timer -> {
                cancelTimer()
                timerEndsAt = now + skill.seconds * 1000L; timerFocus = skill.focus; WidgetBus.focus = skill.focus
                val focus = skill.focus
                if (skill.seconds >= 60) ReminderReceiver.schedule(getApplication(), (skill.seconds + 59) / 60, if (focus) "Focus session finished" else "Timer finished")
                timerJob = viewModelScope.launch { kotlinx.coroutines.delay(skill.seconds * 1000L); timerDone(focus) }
                localCommand(t, if (focus) "Focus mode on for ${LocalSkills.durationText(skill.seconds)}. I'll stay quiet with you." else "Timer set for ${LocalSkills.durationText(skill.seconds)}.") {}
            }
            LocalSkills.Skill.CancelTimer -> localCommand(t, if (cancelTimer()) "Timer cancelled." else "No timer is running.") {}
            LocalSkills.Skill.TimerLeft -> {
                val left = ((timerEndsAt - now) / 1000L).toInt()
                localCommand(t, if (timerEndsAt > 0L && left > 0) "${LocalSkills.durationText(left)} left." else "No timer is running.") {}
            }
            is LocalSkills.Skill.Spend -> localCommand(t, "Noted: Rs ${com.dipesh.ai.studio.Calc.format(skill.amount)} for ${skill.note}.") { expenseStore.add(skill.amount, skill.note); award(Ev.EXPENSE) }
            is LocalSkills.Skill.SpendSummary -> localCommand(t, Expenses.summarize(expenseStore.load(), skill.period, now)) {}
            LocalSkills.Skill.Battery -> {
                val (pct, charging) = batteryInfo()
                localCommand(t, if (pct < 0) "I couldn't read the battery." else LocalSkills.batteryText(pct, charging)) {}
            }
            LocalSkills.Skill.Briefing -> {
                status = Status.THINKING; hint = "Preparing your briefing…"
                viewModelScope.launch {
                    val city = store.settings.value.city
                    val weather = if (city.isBlank()) null else try {
                        kotlinx.coroutines.withTimeoutOrNull(5000) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.dipesh.ai.studio.ApiHub.query("weather", city) } }
                            ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(160)
                    } catch (_: Exception) { null }
                    val pct = batteryInfo().first
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    val date = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date())
                    val spent = Expenses.inPeriod(expenseStore.load(), LocalSkills.Period.TODAY, now).sumOf { it.amount }
                    val reply = LocalSkills.briefing(hour, date, tasks.filter { !it.done }.map { it.title }, weather, pct.takeIf { it >= 0 }, spent)
                    localCommand(t, reply) {}
                }
            }
        }
    }

    private fun localCommand(userText: String, reply: String, action: () -> Unit) {
        action()
        job = viewModelScope.launch {
            val id = ensureConversation(userText)
            val now = System.currentTimeMillis()
            db.messages().insert(MessageEntity(conversationId = id, role = "user", text = userText, createdAt = now))
            db.messages().insert(MessageEntity(conversationId = id, role = "assistant", text = reply, createdAt = now + 1))
            db.conversations().touch(id, now + 1)
            if (store.settings.value.speakReplies) { status = Status.SPEAKING; voice.speak(reply) { onSpeechDone() } } else onSpeechDone()
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
        voice.speak(spokenForm(text)) { onSpeechDone() }
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
                // One tap = a continuous voice chat: the mic re-opens after every reply and stays on (also in the
                // background) until the user taps the mic again or presses Stop in the notification.
                switchVoiceConversation(true)
            }
            Status.LISTENING -> stopAll(exitModes = true)
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
        if (errorStreak < 3) hint = if (handsFree && !awaitingCommand && !voiceConversation) "Waiting for \"Hey Dipesh\"…" else "Listening…"
        voice.listen(
            onHeard = {
                level = 0f
                silentRetries = 0
                errorStreak = 0
                onHeard(it)
            },
            onFail = {
                level = 0f
                onListenError(it)
            },
            onLevel = { level = it },
            onPartial = { if (status == Status.LISTENING) hint = "Hearing: ${it.take(60)}" }
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

    private var errorStreak = 0

    private fun errorName(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NETWORK -> "no internet for speech recognition"
        SpeechRecognizer.ERROR_AUDIO -> "microphone audio error (another app may be using the mic)"
        SpeechRecognizer.ERROR_SERVER -> "speech server error"
        SpeechRecognizer.ERROR_CLIENT -> "speech service restarted"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "speech service busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission missing"
        10 -> "too many requests"
        11 -> "speech server disconnected"
        12 -> "this language is not supported for voice input"
        13 -> "this language is not downloaded for voice input"
        else -> "code $code"
    }

    private fun onListenError(code: Int) {
        // Language not supported by the phone's recogniser: fall back to Hindi (understands Devanagari speech), then English, and say so.
        if (code == 12 || code == 13) {
            val cur = voice.recognitionLanguage
            val next = when { cur.startsWith("ne") -> "hi-IN"; cur.startsWith("hi") -> "en-US"; else -> null }
            if (next != null) {
                voice.recognitionOverride = next
                banner = "Voice input for $cur isn't available on this phone. Using $next instead. Install the language in Google app > Settings > Voice > Languages."
            }
        }
        val quietCode = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        if (quietCode) errorStreak = 0 else {
            errorStreak++
            if (errorStreak >= 3 && (handsFree || voiceConversation)) { banner = "Voice problem: ${errorName(code)}. Still trying…"; hint = "Voice problem: ${errorName(code)}" }
        }
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

    // ---------- studio: projects and tasks ----------
    val library = ProjectLibrary(java.io.File(app.filesDir, "projects"))
    private val taskRepo = TaskRepo(java.io.File(app.filesDir, "tasks.json"))
    val tasks = androidx.compose.runtime.mutableStateListOf<TaskItem>().also { it.addAll(taskRepo.load()) }

    private fun saveTasks() = taskRepo.save(tasks.toList())

    fun addTask(title: String, due: String) {
        if (title.isBlank()) return
        tasks.add(0, TaskItem(System.currentTimeMillis(), title.trim().take(300), false, due.trim().take(40)))
        saveTasks()
    }

    fun toggleTask(id: Long) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0) { tasks[i] = tasks[i].copy(done = !tasks[i].done); saveTasks(); if (tasks[i].done) { WidgetBus.post("celebrate"); award(Ev.TASK) } }
    }

    fun deleteTask(id: Long) { tasks.removeAll { it.id == id }; saveTasks() }

    fun clearDoneTasks() { tasks.removeAll { it.done }; saveTasks() }

    fun replaceTasks(list: List<TaskItem>) { tasks.clear(); tasks.addAll(list); saveTasks() }

    /** Asks the configured AI to write project files. Nothing it returns is ever executed. */
    suspend fun askForFiles(prompt: String): String = withContext(Dispatchers.IO) {
        val r = brain.reply(listOf(ChatMsg("user", prompt)), null) {}
        if (r.isError) throw IllegalStateException(r.text)
        r.text
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
        WidgetBus.provider = null
        WidgetBus.levelProvider = null
        WidgetBus.onTap = null
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

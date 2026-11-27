package com.dipesh.ai.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import com.dipesh.ai.studio.Forecast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipesh.ai.AssistantViewModel
import com.dipesh.ai.Status
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class QuickAction(val icon: String, val label: String, val prompt: String)

private val builtInQuick = listOf(
    QuickAction("☀️", "Weather", "What's the weather today?"),
    QuickAction("📰", "News", "Give me today's top news headlines."),
    QuickAction("🔋", "Battery", "How much battery do I have left?"),
    QuickAction("🔦", "Flashlight", "Turn on the flashlight"),
    QuickAction("⏰", "Reminder", "Remind me in 10 minutes to take a break."),
    QuickAction("📅", "Calendar", "What's on my calendar for the next few days?"),
    QuickAction("📝", "Notes", "Read my notes."),
    QuickAction("🌐", "Trending", "What are today's trending topics on the internet?")
)

/** Parses the user's quick commands, one per line: "Label|Prompt". */
fun parseQuickCommands(raw: String): List<QuickAction> =
    raw.lines().mapNotNull { line ->
        val i = line.indexOf('|')
        if (i <= 0 || i == line.length - 1) null
        else QuickAction("⚡", line.substring(0, i).trim(), line.substring(i + 1).trim())
    }.filter { it.label.isNotEmpty() && it.prompt.isNotEmpty() }

@Composable
fun HomeScreen(
    vm: AssistantViewModel,
    onOpenChat: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    requestMic: (() -> Unit) -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val maxH = maxHeight
        val wide = maxW >= 720.dp
        if (wide) {
            Row(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlowTitle(Modifier.padding(top = 10.dp))
                    Header(settings.userName, settings.language, vm.status, online)
                    OrbPanel(vm, minOf(maxH * 0.55f, 380.dp), requestMic, haptic)
                }
                Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                    ContentPanel(vm, onOpenChat, onOpenConversation, haptic, settings.quickCommands, onNavigate)
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlowTitle(Modifier.padding(top = 10.dp))
                Header(settings.userName, settings.language, vm.status, online)
                OrbPanel(vm, minOf(maxW * 0.72f, 300.dp), requestMic, haptic)
                Column(Modifier.fillMaxWidth()) { ContentPanel(vm, onOpenChat, onOpenConversation, haptic, settings.quickCommands, onNavigate) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(name: String, language: String, status: Status, online: Boolean) {
    val c = LocalDipeshColors.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val part = when {
        hour < 12 -> 0
        hour < 17 -> 1
        else -> 2
    }
    val hello = when (language) {
        "ne-NP" -> listOf("शुभ प्रभात", "शुभ दिन", "शुभ साँझ")[part]
        "hi-IN" -> listOf("सुप्रभात", "नमस्कार", "शुभ संध्या")[part]
        else -> listOf("Good morning", "Good afternoon", "Good evening")[part]
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (name.isBlank()) hello else "$hello, $name",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = c.text
            )
            StatusChip(status)
        }
        Text(
            SimpleDateFormat("EEEE, d MMMM  ·  h:mm a", Locale.getDefault()).format(now),
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted
        )
        if (!online) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Offline: your chats are saved, but the AI needs internet to answer.",
                style = MaterialTheme.typography.bodySmall, color = c.danger
            )
        }
    }
}

@Composable
private fun OrbPanel(vm: AssistantViewModel, orbSize: Dp, requestMic: (() -> Unit) -> Unit, haptic: HapticFeedback) {
    val c = LocalDipeshColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        HoloStage(vm.status, vm.level, Modifier.fillMaxWidth().aspectRatio(1.04f)) { AiOrb(vm.status, vm.level, Modifier.fillMaxSize()) }
        Text(
            vm.hint, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        SoundWave(vm.status, vm.level, Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                if (vm.status != Status.IDLE) {
                    OutlineButton("Stop", onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.stopAll(exitModes = false)
                    })
                }
            }
            MicButton(vm.status, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (vm.status == Status.IDLE) requestMic { vm.onMicTap() } else vm.onMicTap()
            })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                GlassChip(
                    if (vm.handsFree) "Wake word ON" else "Wake word",
                    selected = vm.handsFree,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (vm.handsFree) vm.enableHandsFree(false) else requestMic { vm.enableHandsFree(true) }
                    }
                )
            }
        }
    }
}

@Composable
private fun ContentPanel(
    vm: AssistantViewModel,
    onOpenChat: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    haptic: HapticFeedback,
    quickCommands: String,
    onNavigate: (String) -> Unit
) {
    val c = LocalDipeshColors.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val streaming = vm.streamingText
    val lastReply = messages.lastOrNull { it.role == "assistant" }

    HudPanels(vm, onNavigate)

    if (streaming != null || lastReply != null) {
        SectionTitle("Latest reply")
        GlassCard(Modifier.fillMaxWidth()) {
            if (streaming != null) {
                if (streaming.isEmpty()) TypingDots(Modifier.padding(vertical = 6.dp)) else MarkdownText(streaming)
            } else if (lastReply != null) {
                MarkdownText(lastReply.text, color = if (lastReply.isError) c.danger else c.text)
            }
            Text(
                "Open chat  →",
                modifier = Modifier.padding(top = 10.dp).clickable(onClick = onOpenChat),
                style = MaterialTheme.typography.labelLarge, color = c.violet
            )
        }
    }

    SectionTitle("Quick actions")
    val actions = remember(quickCommands) { builtInQuick + parseQuickCommands(quickCommands) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.forEach { a ->
            GlassCard(
                Modifier.width(118.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.send(a.prompt)
                }
            ) {
                Text(a.icon, fontSize = 26.sp)
                Spacer(Modifier.height(8.dp))
                Text(a.label, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1)
            }
        }
    }

    SectionTitle("Recent conversations")
    if (conversations.isEmpty()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("No conversations yet. Ask me anything.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            conversations.take(4).forEach { convo ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { onOpenConversation(convo.id) }) {
                    Text(convo.title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1)
                    Text(
                        DateUtils.getRelativeTimeSpanString(convo.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- JARVIS-style holographic HUD
@Composable
private fun HudLine(label: String, value: String, ok: Boolean? = null) {
    val c = LocalDipeshColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = when (ok) { true -> c.success; false -> c.danger; null -> c.text })
    }
}

private fun dayLetters(): List<String> {
    val cal = java.util.Calendar.getInstance()
    return (6 downTo 0).map { back ->
        val d = cal.clone() as java.util.Calendar; d.add(java.util.Calendar.DAY_OF_YEAR, -back)
        arrayOf("S", "M", "T", "W", "T", "F", "S")[d.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    }
}

@Composable
private fun HudPanels(vm: AssistantViewModel, onNavigate: (String) -> Unit) {
    val c = LocalDipeshColors.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val weekly by vm.weeklyActivity.collectAsStateWithLifecycle()
    val keyReady = when (settings.provider) { "gemini" -> settings.geminiKey.isNotBlank(); "claude" -> settings.claudeKey.isNotBlank(); else -> true }

    Spacer(Modifier.height(8.dp))
    if (vm.timerEndsAt > 0L) {
        var left by remember(vm.timerEndsAt) { mutableStateOf(0) }
        LaunchedEffect(vm.timerEndsAt) {
            while (true) {
                left = ((vm.timerEndsAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                if (left <= 0) break
                delay(500)
            }
        }
        HoloPanel(Modifier.fillMaxWidth(), onClick = { vm.cancelTimer() }) {
            HoloLabel(if (vm.timerFocus) "FOCUS MODE" else "TIMER")
            Text(com.dipesh.ai.studio.LocalSkills.clock(left), style = MaterialTheme.typography.displaySmall, color = c.cyan)
            Text(if (vm.timerFocus) "The Yeti is sitting quietly with you. Tap to end." else "Tap to cancel.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Spacer(Modifier.height(10.dp))
    }
    run {
        val lvl = com.dipesh.ai.studio.Progress.level(vm.progress.xp)
        val earned = com.dipesh.ai.studio.Progress.earned(vm.progress)
        HoloPanel(Modifier.fillMaxWidth()) {
            HoloLabel("YETI LEVEL")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lv $lvl", style = MaterialTheme.typography.headlineSmall, color = c.cyan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(com.dipesh.ai.studio.Progress.title(lvl), style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Text("${vm.progress.xp} XP  ·  ${vm.progress.streak}-day streak", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            val frac = com.dipesh.ai.studio.Progress.fraction(vm.progress.xp)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.glassBorderLo)) {
                Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.02f)).height(6.dp).background(Brush.horizontalGradient(listOf(c.cyan, c.violet))))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (earned.isEmpty()) "Finish tasks, focus sessions and daily use to earn badges." else earned.joinToString("  ") { it.icon + " " + it.name },
                style = MaterialTheme.typography.bodySmall, color = c.textMuted
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    HoloPanel(Modifier.fillMaxWidth()) {
        HoloLabel("SYSTEM STATUS")
        HudLine("AI core", if (keyReady) "Ready · ${settings.provider}" else "Add API key in Settings", keyReady)
        HudLine("Network", if (online) "Online" else "Offline", online)
        HudLine("Microphone", when { vm.handsFree -> "Hands-free ON"; vm.voiceConversation -> "Voice chat ON"; else -> "Off" }, if (vm.handsFree || vm.voiceConversation) true else null)
        HudLine("Assistant", vm.status.name.lowercase().replaceFirstChar { it.uppercase() })
        HudLine("Language", settings.language)
    }
    Spacer(Modifier.height(10.dp))

    // predictive analytics: real usage data, simple linear-trend forecast (dashed)
    val taskDays = remember(vm.tasks.toList()) {
        val start = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0); add(java.util.Calendar.DAY_OF_YEAR, -6) }.timeInMillis
        val out = IntArray(7); vm.tasks.forEach { if (it.created >= start) out[((it.created - start) / 86_400_000L).toInt().coerceIn(0, 6)]++ }; out.toList()
    }
    HoloPanel(Modifier.fillMaxWidth()) {
        HoloLabel("PREDICTIVE ANALYTICS")
        val labels = dayLetters() + listOf("+1", "+2", "+3")
        val act = weekly.map { it.toDouble() }
        HoloGraph("Your requests per day · trend ${Forecast.trend(act)}", act.map { it.toFloat() }, Forecast.linear(act, 3).map { it.toFloat() }, labels)
        Spacer(Modifier.height(10.dp))
        val tk = taskDays.map { it.toDouble() }
        HoloGraph("Tasks added per day · trend ${Forecast.trend(tk)}", tk.map { it.toFloat() }, Forecast.linear(tk, 3).map { it.toFloat() }, labels, accent = c.violet)
        Text("Solid = your real activity. Dashed = a simple linear projection, not a guarantee.", style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
    }
    Spacer(Modifier.height(10.dp))
    HoloPanel(Modifier.fillMaxWidth()) {
        HoloLabel("VOICE LOG")
        val recent = messages.takeLast(3)
        if (recent.isEmpty()) Text("No activity yet. Say \"Hey Dipesh\" or tap the orb.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        recent.forEach { m ->
            Text((if (m.role == "user") "You: " else "Assistant: ") + m.text.replace("\n", " ").take(90),
                style = MaterialTheme.typography.bodySmall, color = if (m.role == "user") c.text else c.violet, maxLines = 2,
                modifier = Modifier.padding(vertical = 2.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
    HoloPanel(Modifier.fillMaxWidth(), onClick = { onNavigate("studio/tasks") }) {
        HoloLabel("CURRENT TASKS")
        val open = vm.tasks.filter { !it.done }
        if (open.isEmpty()) Text("Nothing pending. Tap to add tasks.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        open.take(3).forEach { Text("[ ${it.title} ]", style = MaterialTheme.typography.bodySmall, color = c.text, maxLines = 1, modifier = Modifier.padding(vertical = 2.dp)) }
        if (open.size > 3) Text("+${open.size - 3} more", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
    Spacer(Modifier.height(10.dp))
    HoloPanel(Modifier.fillMaxWidth()) {
        HoloLabel("ASSISTANT OPTIONS")
        val options = listOf(
            "🎤" to "chat", "🔎" to "studio/api", "✅" to "studio/tasks",
            "🤖" to "studio/agent", "🌐" to "studio/website", "🎬" to "studio/video",
            "🎞" to "studio/videomaker", "🧮" to "studio/offline", "🗂" to "studio/library",
            "🎛" to "control", "⚙" to "settings", "✨" to "features"
        )
        val names = mapOf("chat" to "Voice/Chat", "studio/api" to "API Hub", "studio/tasks" to "Tasks", "studio/agent" to "Agents",
            "studio/website" to "Websites", "studio/video" to "Video kits", "studio/videomaker" to "Video Maker", "studio/offline" to "Utilities",
            "studio/library" to "Library", "control" to "Control", "settings" to "Settings", "features" to "All features")
        options.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, route) ->
                    Column(Modifier.weight(1f).clickable { onNavigate(route) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(icon, fontSize = 24.sp)
                        Text(names[route].orEmpty(), style = MaterialTheme.typography.labelSmall, color = c.textMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}

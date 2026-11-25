package com.dipesh.ai.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    requestMic: (() -> Unit) -> Unit
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
                    Header(settings.userName, settings.language, vm.status, online)
                    OrbPanel(vm, minOf(maxH * 0.55f, 380.dp), requestMic, haptic)
                }
                Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                    ContentPanel(vm, onOpenChat, onOpenConversation, haptic, settings.quickCommands)
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Header(settings.userName, settings.language, vm.status, online)
                OrbPanel(vm, minOf(maxW * 0.72f, 300.dp), requestMic, haptic)
                Column(Modifier.fillMaxWidth()) { ContentPanel(vm, onOpenChat, onOpenConversation, haptic, settings.quickCommands) }
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
        AiOrb(vm.status, vm.level, Modifier.size(orbSize))
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
    quickCommands: String
) {
    val c = LocalDipeshColors.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val streaming = vm.streamingText
    val lastReply = messages.lastOrNull { it.role == "assistant" }

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

package com.dipesh.ai.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipesh.ai.AssistantViewModel
import com.dipesh.ai.Status
import com.dipesh.ai.data.MessageEntity

private val suggestions = listOf(
    "What can you do?",
    "Explain quantum computing in simple words",
    "Write a short WhatsApp message to say I'll be late",
    "नमस्ते, आज के बारे में बताओ"
)

@Composable
fun ChatScreen(vm: AssistantViewModel, onOpenHistory: () -> Unit, requestMic: (() -> Unit) -> Unit) {
    val c = LocalDipeshColors.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val convoId by vm.conversationId.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val streaming = vm.streamingText
    val busy = vm.status == Status.THINKING
    val title = conversations.firstOrNull { it.id == convoId }?.title ?: "New chat"

    LaunchedEffect(messages.size, streaming?.length) {
        val n = messages.size + if (streaming != null) 1 else 0
        if (n > 0) listState.scrollToItem(n - 1)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ---- top bar ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (vm.status) {
                        Status.IDLE -> if (vm.voiceConversation) "Voice conversation on" else "Ready"
                        Status.LISTENING -> "Listening…"
                        Status.THINKING -> "Thinking…"
                        Status.SPEAKING -> "Speaking…"
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted
                )
            }
            GlassChip(
                "🎙 Voice", selected = vm.voiceConversation,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (vm.voiceConversation) vm.setVoiceConversation(false) else requestMic { vm.setVoiceConversation(true) }
                }
            )
            RoundIcon("＋") { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.newChat() }
            RoundIcon("🕘") { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpenHistory() }
        }

        // ---- messages ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && streaming == null) {
                EmptyState(onPick = { vm.send(it) })
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val lastAssistantId = messages.lastOrNull { it.role == "assistant" }?.id
                    items(messages, key = { it.id }) { m ->
                        MessageBubble(
                            m = m,
                            isLastAssistant = m.id == lastAssistantId && streaming == null && !busy,
                            onCopy = {
                                clipboard.setText(AnnotatedString(m.text))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.banner = "Copied to clipboard"
                            },
                            onSpeak = { vm.speak(m.text) },
                            onRegenerate = { vm.regenerate() }
                        )
                    }
                    if (streaming != null) {
                        item(key = "streaming") {
                            AssistantShell {
                                if (streaming.isEmpty()) TypingDots(Modifier.padding(vertical = 6.dp)) else MarkdownText(streaming)
                            }
                        }
                    }
                }
            }
        }

        // ---- composer ----
        Row(
            Modifier.fillMaxWidth().padding(12.dp).glass(RoundedCornerShape(28.dp), c)
                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message DIPESH AI…") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) { vm.send(input); input = "" }
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = c.text, unfocusedTextColor = c.text,
                    focusedPlaceholderColor = c.textMuted, unfocusedPlaceholderColor = c.textMuted,
                    cursorColor = c.violet
                )
            )
            RoundIcon("🎙") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (vm.status == Status.IDLE) requestMic { vm.onMicTap() } else vm.onMicTap()
            }
            Spacer(Modifier.size(6.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(c.violet, c.blue)))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (busy) vm.stopAll(exitModes = false)
                        else if (input.isNotBlank()) { vm.send(input); input = "" }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (busy) "■" else "➤", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun RoundIcon(glyph: String, onClick: () -> Unit) {
    val c = LocalDipeshColors.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(c.glass).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(glyph, fontSize = 17.sp, color = c.text) }
}

@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    val c = LocalDipeshColors.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        AiOrb(Status.IDLE, 0f, Modifier.size(150.dp))
        Spacer(Modifier.height(8.dp))
        Text("How can I help?", style = MaterialTheme.typography.headlineMedium, color = c.text)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            suggestions.forEach { GlassChip(it, selected = false, onClick = { onPick(it) }) }
        }
    }
}

@Composable
private fun AssistantShell(content: @Composable () -> Unit) {
    val c = LocalDipeshColors.current
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.weight(0.92f, fill = false)
                .glass(RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp), c, elevation = 6)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) { content() }
        Spacer(Modifier.weight(0.08f))
    }
}

@Composable
private fun MessageBubble(
    m: MessageEntity,
    isLastAssistant: Boolean,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRegenerate: () -> Unit
) {
    val c = LocalDipeshColors.current
    if (m.role == "user") {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(0.15f))
            Box(
                Modifier.weight(0.85f, fill = false)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 22.dp))
                    .background(Brush.linearGradient(listOf(c.violet, c.blue)))
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Text(
                    (if (m.hasImage) "📷  " else "") + m.text,
                    color = Color.White, style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }
    AssistantShell {
        Column {
            if (m.isError) {
                Text("⚠  " + m.text, color = c.danger, style = MaterialTheme.typography.bodyLarge)
                if (isLastAssistant) {
                    Spacer(Modifier.height(8.dp))
                    GradientButton("Try again", onRegenerate, danger = true)
                }
            } else {
                MarkdownText(m.text)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Action("📋 Copy", onCopy)
                    Action("🔊 Speak", onSpeak)
                    if (isLastAssistant) Action("↻ Regenerate", onRegenerate)
                }
            }
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Text(
        label, modifier = Modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.labelMedium, color = LocalDipeshColors.current.textMuted
    )
}

// ------------------------------------------------------------------ history

@Composable
fun HistoryScreen(vm: AssistantViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val c = LocalDipeshColors.current
    val haptic = LocalHapticFeedback.current
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoundIcon("←") { onBack() }
            Text("Chat history", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = c.text)
            if (conversations.isNotEmpty()) {
                Text(
                    "Clear all", modifier = Modifier.clickable { confirmClear = true },
                    style = MaterialTheme.typography.labelLarge, color = c.danger
                )
            }
        }
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet.", color = c.textMuted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(conversations, key = { it.id }) { convo ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(convo.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(convo.title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    DateUtils.getRelativeTimeSpanString(convo.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                    style = MaterialTheme.typography.bodySmall, color = c.textMuted
                                )
                            }
                            Text(
                                "🗑", fontSize = 20.sp,
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.deleteConversation(convo.id)
                                }.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all chats?") },
            text = { Text("This permanently removes every conversation from this phone.") },
            confirmButton = { TextButton(onClick = { vm.deleteAllConversations(); confirmClear = false }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

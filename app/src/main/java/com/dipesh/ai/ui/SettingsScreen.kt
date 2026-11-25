package com.dipesh.ai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipesh.ai.AssistantViewModel
import com.dipesh.ai.BuildConfig
import com.dipesh.ai.PhoneControlService
import com.dipesh.ai.data.Settings
import com.dipesh.ai.data.Settings as AppSettings

/** Re-evaluates permission status whenever the user returns to the app (e.g. from system settings). */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

private data class PermItem(val label: String, val why: String, val perms: List<String>)

private val permItems = listOf(
    PermItem("Microphone", "Voice commands and wake word", listOf(Manifest.permission.RECORD_AUDIO)),
    PermItem("Camera", "Camera vision and live preview", listOf(Manifest.permission.CAMERA)),
    PermItem("Contacts", "Find people to call or message", listOf(Manifest.permission.READ_CONTACTS)),
    PermItem("Phone calls", "Start calls (always asks first)", listOf(Manifest.permission.CALL_PHONE)),
    PermItem("SMS", "Send texts (always asks first)", listOf(Manifest.permission.SEND_SMS)),
    PermItem("Calendar", "Read upcoming events", listOf(Manifest.permission.READ_CALENDAR)),
    PermItem(
        "Location", "Tell you where you are",
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    ),
    PermItem("Notifications", "Show reminders", if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList())
)

private fun Context.granted(perms: List<String>) =
    perms.isEmpty() || perms.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

@Composable
fun SettingsScreen(vm: AssistantViewModel, onRequestPermissions: (Array<String>) -> Unit) {
    val c = LocalDipeshColors.current
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val facts by vm.facts.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val convoCount by vm.conversationCount.collectAsStateWithLifecycle()
    val msgCount by vm.messageCount.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    var confirmErase by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.padding(top = 16.dp))

        // ---------------- Profile ----------------
        SectionTitle("Profile")
        GlassCard(Modifier.fillMaxWidth()) {
            var name by remember { mutableStateOf(s.userName) }
            var city by remember { mutableStateOf(s.city) }
            GlassTextField(name, { name = it; vm.update { st -> st.copy(userName = it.trim()) } }, "Your name")
            Spacer(Modifier.height(10.dp))
            GlassTextField(city, { city = it; vm.update { st -> st.copy(city = it.trim()) } }, "Your city (for weather)")
        }

        // ---------------- Appearance ----------------
        SectionTitle("Appearance")
        GlassCard(Modifier.fillMaxWidth()) {
            ChipRow {
                listOf("system" to "System", "dark" to "Dark", "light" to "Light").forEach { (v, l) ->
                    GlassChip(l, s.theme == v, { vm.update { it.copy(theme = v) } })
                }
            }
        }

        // ---------------- AI & API ----------------
        SectionTitle("AI & API configuration")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Provider", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Spacer(Modifier.height(6.dp))
            ChipRow {
                GlassChip("Google Gemini", s.provider == "gemini", { vm.update { it.copy(provider = "gemini") } })
                GlassChip("Anthropic Claude", s.provider == "claude", { vm.update { it.copy(provider = "claude") } })
            }
            Spacer(Modifier.height(12.dp))
            var gKey by remember { mutableStateOf(s.geminiKey) }
            var gModel by remember { mutableStateOf(s.geminiModel) }
            var cKey by remember { mutableStateOf(s.claudeKey) }
            var cModel by remember { mutableStateOf(s.claudeModel) }
            GlassTextField(gKey, { gKey = it; vm.update { st -> st.copy(geminiKey = it.trim()) } }, "Gemini API key", password = true)
            Spacer(Modifier.height(10.dp))
            GlassTextField(gModel, { gModel = it; vm.update { st -> st.copy(geminiModel = it.trim().ifEmpty { Settings.DEFAULT_GEMINI_MODEL }) } }, "Gemini model")
            Spacer(Modifier.height(10.dp))
            GlassTextField(cKey, { cKey = it; vm.update { st -> st.copy(claudeKey = it.trim()) } }, "Anthropic API key (optional)", password = true)
            Spacer(Modifier.height(10.dp))
            GlassTextField(cModel, { cModel = it; vm.update { st -> st.copy(claudeModel = it.trim().ifEmpty { Settings.DEFAULT_CLAUDE_MODEL }) } }, "Claude model")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Test connection", { vm.testConnection() })
                OutlineButton("Get a free key", {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Keys are encrypted with the Android Keystore and never leave your phone except to call the AI provider you chose.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted
            )
        }

        // ---------------- Personality ----------------
        SectionTitle("AI personality")
        GlassCard(Modifier.fillMaxWidth()) {
            ChipRow {
                listOf("jarvis" to "JARVIS", "friendly" to "Friendly", "professional" to "Professional", "concise" to "Concise", "custom" to "Custom").forEach { (v, l) ->
                    GlassChip(l, s.personality == v, { vm.update { it.copy(personality = v) } })
                }
            }
            Spacer(Modifier.height(12.dp))
            var custom by remember { mutableStateOf(s.customInstructions) }
            GlassTextField(
                custom, { custom = it; vm.update { st -> st.copy(customInstructions = it) } },
                "Custom instructions (e.g. \"Always answer like a pirate\")", singleLine = false, minLines = 3
            )
        }

        // ---------------- Voice ----------------
        SectionTitle("Voice")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Language", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Spacer(Modifier.height(6.dp))
            ChipRow {
                listOf("en-US" to "English", "ne-NP" to "नेपाली", "hi-IN" to "हिन्दी").forEach { (v, l) ->
                    GlassChip(l, s.language == v, { vm.update { it.copy(language = v, voiceName = "") } })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Speed ${"%.1f".format(s.speechRate)}×", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Slider(s.speechRate, { vm.update { st -> st.copy(speechRate = it) } }, valueRange = 0.5f..1.5f, colors = sliderColors(c))
            Text("Pitch ${"%.1f".format(s.pitch)}", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Slider(s.pitch, { vm.update { st -> st.copy(pitch = it) } }, valueRange = 0.5f..1.5f, colors = sliderColors(c))

            var voices by remember(s.language) { mutableStateOf(vm.voiceOptions()) }
            Text("Voice", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Spacer(Modifier.height(6.dp))
            ChipRow {
                GlassChip("Default", s.voiceName.isEmpty(), { vm.update { it.copy(voiceName = "") } })
                voices.take(14).forEach { v -> GlassChip(v, s.voiceName == v, { vm.update { it.copy(voiceName = v) } }) }
                GlassChip("↻ Reload", false, { voices = vm.voiceOptions() })
            }
            Spacer(Modifier.height(8.dp))
            SettingRow("Speak replies aloud") {
                GlassSwitch(s.speakReplies) { v -> vm.update { it.copy(speakReplies = v) } }
            }
            var wake by remember { mutableStateOf(s.wakeWords) }
            GlassTextField(wake, { wake = it; vm.update { st -> st.copy(wakeWords = it) } }, "Wake words (comma separated)")
            Spacer(Modifier.height(10.dp))
            OutlineButton("Preview voice", { vm.previewVoice() })
        }

        // ---------------- Memory ----------------
        SectionTitle("Memory")
        GlassCard(Modifier.fillMaxWidth()) {
            SettingRow("Remember things about me", "Facts you tell me are stored on this phone and shared with the AI as context.") {
                GlassSwitch(s.memoryEnabled) { v -> vm.update { it.copy(memoryEnabled = v) } }
            }
            var newFact by remember { mutableStateOf("") }
            GlassTextField(newFact, { newFact = it }, "Add a fact (e.g. My sister is Sita)")
            Spacer(Modifier.height(8.dp))
            GradientButton("Add to memory", { vm.addFact(newFact); newFact = "" })
            Spacer(Modifier.height(10.dp))
            if (facts.isEmpty()) {
                Text("No remembered facts.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            facts.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Text("✕", Modifier.clickable { vm.deleteFact(f.id) }.padding(8.dp), color = c.danger)
                }
            }
            if (facts.isNotEmpty()) TextButton({ vm.clearFacts() }) { Text("Forget all facts", color = c.danger) }

            Text("Notes (${notes.size})", style = MaterialTheme.typography.labelLarge, color = c.textMuted, modifier = Modifier.padding(top = 8.dp))
            notes.forEach { n ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Text("✕", Modifier.clickable { vm.deleteNote(n.id) }.padding(8.dp), color = c.danger)
                }
            }
            if (notes.isNotEmpty()) TextButton({ vm.clearNotes() }) { Text("Delete all notes", color = c.danger) }
        }

        // ---------------- Quick commands ----------------
        SectionTitle("Quick commands")
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "Add your own shortcuts for the home screen. One per line, written as Label|Prompt.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted
            )
            Spacer(Modifier.height(8.dp))
            var q by remember { mutableStateOf(s.quickCommands) }
            GlassTextField(
                q, { q = it; vm.update { st -> st.copy(quickCommands = it) } },
                "e.g. Morning|Tell me the weather, news and my calendar", singleLine = false, minLines = 3
            )
        }

        // ---------------- Safety ----------------
        SectionTitle("Safety")
        GlassCard(Modifier.fillMaxWidth()) {
            SettingRow("Skip approval for screen actions", "Not recommended. Calls, texts and deleting files always ask.") {
                GlassSwitch(s.autoApprove) { v -> vm.update { it.copy(autoApprove = v) } }
            }
        }

        // ---------------- Privacy dashboard ----------------
        SectionTitle("Privacy dashboard")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("What is stored on this phone", style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "$convoCount conversations · $msgCount messages · ${facts.size} remembered facts · ${notes.size} notes",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your messages are sent to ${if (s.provider == "gemini") "Google Gemini" else "Anthropic Claude"} to get answers. " +
                    "Photos, screenshots and screen text are only sent when you ask me to look. Nothing is sold or shared with anyone else.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted
            )
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.height(4.dp))
            // reading `tick` makes this block refresh when returning from system settings
            key(tick) {
                permItems.forEach { p ->
                    val ok = ctx.granted(p.perms)
                    SettingRow(p.label, p.why) {
                        if (ok) Text("✓ On", color = c.success, style = MaterialTheme.typography.labelLarge)
                        else GlassChip("Grant", false, { onRequestPermissions(p.perms.toTypedArray()) })
                    }
                }
                SettingRow("Screen control", "Lets me tap and type for you (Accessibility)") {
                    if (PhoneControlService.instance != null) Text("✓ On", color = c.success, style = MaterialTheme.typography.labelLarge)
                    else GlassChip("Enable", false, { ctx.openSettings(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) })
                }
                val filesOk = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
                else ctx.granted(listOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                SettingRow("All files access", "Find and manage files") {
                    if (filesOk) Text("✓ On", color = c.success, style = MaterialTheme.typography.labelLarge)
                    else GlassChip("Allow", false, {
                        if (Build.VERSION.SDK_INT >= 30) {
                            ctx.startActivity(
                                Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else onRequestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlineButton("Open app permission settings", {
                ctx.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            })
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Your data", style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Delete chats", { vm.deleteAllConversations() })
                OutlineButton("Clear memory", { vm.clearFacts(); vm.clearNotes() })
            }
            Spacer(Modifier.height(10.dp))
            GradientButton("Erase everything", { confirmErase = true }, danger = true)
        }

        // ---------------- About ----------------
        SectionTitle("About")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("DIPESH AI  v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = c.text)
            Text("Your personal AI assistant.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase everything?") },
            text = { Text("This deletes all chats, memory, notes, routines and your saved API keys. It cannot be undone.") },
            confirmButton = { TextButton({ vm.eraseEverything(); confirmErase = false }) { Text("Erase") } },
            dismissButton = { TextButton({ confirmErase = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun sliderColors(c: DipeshColors) = SliderDefaults.colors(
    thumbColor = c.violet, activeTrackColor = c.violet, inactiveTrackColor = c.glassBorderLo
)

@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalDipeshColors.current
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedTrackColor = c.violet,
            uncheckedThumbColor = c.textMuted, uncheckedTrackColor = c.glass, uncheckedBorderColor = c.glassBorderHi
        )
    )
}

fun Context.openSettings(action: String) {
    startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

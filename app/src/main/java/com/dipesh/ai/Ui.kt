package com.dipesh.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

private val Panel = Color(0xFF04262B)

@Composable
fun DipeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            onPrimary = Bg,
            background = Bg,
            onBackground = Cyan,
            surface = Panel,
            onSurface = Cyan,
            secondary = Orange
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) { content() }
    }
}

@Composable
fun App(vm: AssistantViewModel = viewModel()) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var showSettings by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the screen on while hands-free mode is listening.
    SideEffect { view.keepScreenOn = vm.handsFree }

    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pending?.invoke()
        pending = null
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun withMic(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pending = action
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DIPESH AI", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showSettings = true }) { Text("⚙ Settings") }
        }

        Hud(status = vm.status, modifier = Modifier.fillMaxWidth().height(220.dp))
        Text(
            vm.hint,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            fontSize = 13.sp,
            color = Cyan.copy(alpha = 0.8f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.messages) { Bubble(it) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hey Dipesh mode", modifier = Modifier.weight(1f), fontSize = 14.sp)
            Switch(
                checked = vm.handsFree,
                onCheckedChange = { on -> if (on) withMic { vm.setHandsFree(true) } else vm.setHandsFree(false) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) { vm.send(input); input = "" }
                })
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { withMic { vm.onMicTap() } },
                enabled = !vm.handsFree,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vm.status == Status.LISTENING) Orange else Cyan
                )
            ) {
                Text(
                    when (vm.status) {
                        Status.LISTENING, Status.SPEAKING -> "■ Stop"
                        else -> "🎤 Talk"
                    }
                )
            }
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val mine = m.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(if (mine) Color(0xFF0B3B40) else Panel, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(m.text, fontSize = 15.sp, color = if (mine) Color.White else Cyan)
        }
    }
}

private val LANGUAGES = listOf("English" to "en-US", "नेपाली (Nepali)" to "ne-NP", "हिन्दी (Hindi)" to "hi-IN")

@Composable
private fun SettingsDialog(vm: AssistantViewModel, onClose: () -> Unit) {
    var key by remember { mutableStateOf(vm.store.apiKey) }
    var city by remember { mutableStateOf(vm.store.city) }
    var lang by remember { mutableStateOf(vm.store.language) }
    val facts = remember { vm.store.facts() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Anthropic API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    label = { Text("Your city (for weather)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("Language", fontWeight = FontWeight.Bold)
                LANGUAGES.forEach { (label, code) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { lang = code },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = lang == code, onClick = { lang = code })
                        Text(label)
                    }
                }
                Text("What I remember (${facts.size})", fontWeight = FontWeight.Bold)
                Text(if (facts.isEmpty()) "Nothing yet. Tell me about yourself!" else facts.joinToString("\n• ", prefix = "• "), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.clearMemory(); onClose() }) { Text("Clear memory") }
                    OutlinedButton(onClick = { vm.clearChat(); onClose() }) { Text("Clear chat") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.saveSettings(key, city, lang); onClose() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

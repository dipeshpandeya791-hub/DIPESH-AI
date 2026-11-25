package com.dipesh.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dipesh.ai.AssistantViewModel
import kotlinx.coroutines.delay

private data class Tab(val route: String, val glyph: String, val label: String)

private val tabs = listOf(
    Tab("home", "◉", "Home"),
    Tab("chat", "💬", "Chat"),
    Tab("settings", "⚙", "Settings")
)

@Composable
fun AppRoot(vm: AssistantViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    DipeshTheme(settings.theme) {
        AppBackground { RootContent(vm) }
    }
}

@Composable
private fun RootContent(vm: AssistantViewModel) {
    val ctx = LocalContext.current
    val c = LocalDipeshColors.current
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    // Keep the screen on while the assistant is listening hands-free.
    SideEffect { view.keepScreenOn = vm.handsFree || vm.voiceConversation }

    // ---- permission plumbing ----
    var pendingMic by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingMic?.invoke() else vm.banner = "Microphone permission is needed for voice. You can still type."
        pendingMic = null
    }
    val requestMic: (() -> Unit) -> Unit = { action ->
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingMic = action
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val multiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val denied = res.count { !it.value }
        if (denied > 0) vm.banner = "Some permissions were not granted. Those features stay off until you allow them."
    }

    fun goTab(target: String) {
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(
                navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 24 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(280)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(260)) { it / 24 } }
            ) {
                composable("home") {
                    HomeScreen(
                        vm, onOpenChat = { goTab("chat") },
                        onOpenConversation = { id -> vm.openConversation(id); goTab("chat") },
                        requestMic = requestMic
                    )
                }
                composable("chat") { ChatScreen(vm, onOpenHistory = { nav.navigate("history") }, requestMic = requestMic) }
                composable("history") {
                    HistoryScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id -> vm.openConversation(id); nav.popBackStack() })
                }
                composable("settings") { SettingsScreen(vm, onRequestPermissions = { multiLauncher.launch(it) }) }
            }

            // transient message (copied, errors, connection test…)
            val banner = vm.banner
            LaunchedEffect(banner) {
                if (banner != null) {
                    delay(4_000)
                    if (vm.banner == banner) vm.banner = null
                }
            }
            Banner(banner, Modifier.align(Alignment.TopCenter)) { vm.banner = null }
        }

        AnimatedVisibility(visible = !imeVisible && route in tabs.map { it.route }) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
                    .glass(RoundedCornerShape(28.dp), c).padding(6.dp)
            ) {
                tabs.forEach { t ->
                    val selected = route == t.route
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                            .then(
                                if (selected) Modifier.background(Brush.linearGradient(listOf(c.violet.copy(alpha = 0.55f), c.blue.copy(alpha = 0.55f))))
                                else Modifier
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                goTab(t.route)
                            }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(t.glyph, fontSize = 18.sp, color = if (selected) androidx.compose.ui.graphics.Color.White else c.textMuted)
                        Text(
                            t.label, style = MaterialTheme.typography.labelSmall,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else c.textMuted
                        )
                    }
                }
            }
        }
    }

    // approval prompt for calls, texts, screen actions and file changes
    vm.pendingConfirm?.let { conf ->
        AlertDialog(
            onDismissRequest = { vm.answerConfirm(false) },
            title = { Text("Allow this?") },
            text = { Text(conf.text) },
            confirmButton = { TextButton({ vm.answerConfirm(true) }) { Text("Allow") } },
            dismissButton = { TextButton({ vm.answerConfirm(false) }) { Text("Deny") } }
        )
    }
}


@Composable
private fun Banner(message: String?, modifier: Modifier, onDismiss: () -> Unit) {
    val c = LocalDipeshColors.current
    AnimatedVisibility(
        visible = message != null, modifier = modifier,
        enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }
    ) {
        GlassCard(
            Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(),
            shape = RoundedCornerShape(18.dp), onClick = onDismiss
        ) {
            Text(message.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = c.text)
        }
    }
}

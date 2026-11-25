package com.dipesh.ai.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipesh.ai.AssistantViewModel
import com.dipesh.ai.ImageUtil
import com.dipesh.ai.NotificationService
import com.dipesh.ai.PhoneControlService
import com.dipesh.ai.ScreenCapture
import com.dipesh.ai.ScreenCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class AppInfo(val label: String, val pkg: String)
private data class PickedFile(val uri: Uri, val name: String, val size: Long, val mime: String)

private val SHORTCUTS = listOf(
    "📶 Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
    "🔵 Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
    "🖥 Display" to Settings.ACTION_DISPLAY_SETTINGS,
    "🔊 Sound" to Settings.ACTION_SOUND_SETTINGS,
    "🔋 Battery saver" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
    "📱 Apps" to Settings.ACTION_APPLICATION_SETTINGS,
    "📍 Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
    "✈ Airplane mode" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
    "💾 Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
    "🌐 Language" to Settings.ACTION_LOCALE_SETTINGS,
    "🕐 Date & time" to Settings.ACTION_DATE_SETTINGS,
    "🔒 Security" to Settings.ACTION_SECURITY_SETTINGS,
    "♿ Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
    "⚙ All settings" to Settings.ACTION_SETTINGS
)

private fun Context.launchSafely(intent: Intent, onFail: () -> Unit) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        onFail()
    }
}

private fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .distinctBy { it.pkg }
        .filter { it.pkg != ctx.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun describe(ctx: Context, uri: Uri): PickedFile {
    var name = "file"
    var size = 0L
    ctx.contentResolver.query(uri, null, null, null, null)?.use {
        if (it.moveToFirst()) {
            val n = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s = it.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0) name = it.getString(n) ?: name
            if (s >= 0) size = it.getLong(s)
        }
    }
    return PickedFile(uri, name, size, ctx.contentResolver.getType(uri) ?: "application/octet-stream")
}

private fun prettySize(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024 -> "${b / 1024} KB"
    else -> "$b B"
}

@Composable
private fun ModuleHeader(icon: String, title: String, subtitle: String) {
    val c = LocalDipeshColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * Control Center: every module here does a real thing, and each one asks Android (and you) for
 * permission first. Nothing bypasses Android's security.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(
    vm: AssistantViewModel,
    onOpenCamera: () -> Unit,
    onOpenChat: () -> Unit,
    onRequestPermissions: (Array<String>) -> Unit
) {
    val c = LocalDipeshColors.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val fail = { vm.banner = "No app on this phone can do that." }

    // refresh permission/status rows when the user comes back from system settings
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---------- apps ----------
    val apps by produceState(emptyList<AppInfo>()) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }
    var search by remember { mutableStateOf("") }
    val shownApps = remember(apps, search) {
        if (search.isBlank()) apps.take(24) else apps.filter { it.label.contains(search, ignoreCase = true) }.take(40)
    }

    // ---------- screen capture ----------
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, ScreenCaptureService::class.java)
                    .putExtra(ScreenCaptureService.EXTRA_CODE, r.resultCode)
                    .putExtra(ScreenCaptureService.EXTRA_DATA, data)
                    .putExtra(ScreenCaptureService.EXTRA_DELAY, 4_000L)
            )
            vm.banner = "Switch to the screen you want me to see. Capturing in 4 seconds…"
            (ctx as? Activity)?.moveTaskToBack(true)
        } else {
            vm.banner = "Screen capture was cancelled. Nothing was recorded."
        }
    }

    // ---------- call ----------
    var callNumber by remember { mutableStateOf("") }
    var confirmCall by remember { mutableStateOf<String?>(null) }

    // ---------- message ----------
    var msgNumber by remember { mutableStateOf("") }
    var msgText by remember { mutableStateOf("") }

    // ---------- files ----------
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) picked = describe(ctx, uri)
    }
    val storageRoot = remember { Environment.getExternalStorageDirectory() }
    var folder by remember { mutableStateOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var filesTick by remember { mutableIntStateOf(0) }
    val filesOk = remember(tick) {
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    // ---------- notifications ----------
    val listenerOn = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
    val notifications by NotificationService.recent.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Text("Control Center", style = MaterialTheme.typography.headlineMedium, color = c.text)
            Text(
                "Real controls for your phone. Android asks before anything sensitive.",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted
            )
        }

        // ===== Camera & screen =====
        item {
            SectionTitle("Eyes")
            GlassCard(Modifier.fillMaxWidth()) {
                ModuleHeader("📷", "Camera", "Point at something and I'll tell you what it is")
                GradientButton("Open camera", {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenCamera()
                }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                ModuleHeader("🖥", "Screen understanding", "I take ONE screenshot after you allow it in Android's dialog")
                Text(
                    "You get 4 seconds to switch to the screen you want me to look at. Nothing is recorded or kept running.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted
                )
                Spacer(Modifier.height(10.dp))
                GradientButton("Capture my screen", {
                    ScreenCapture.prompt = "Here is a screenshot of my phone screen. Describe what is on it and help me with it."
                    val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projectionLauncher.launch(mpm.createScreenCaptureIntent())
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                SettingRow("Screen control", "Lets me tap, type and scroll for you (Accessibility)") {
                    if (remember(tick) { PhoneControlService.instance != null }) {
                        Text("✓ On", color = c.success, style = MaterialTheme.typography.labelLarge)
                    } else {
                        GlassChip("Enable", false, {
                            ctx.launchSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), fail)
                        })
                    }
                }
            }
        }

        // ===== Communication =====
        item {
            SectionTitle("Communicate")
            GlassCard(Modifier.fillMaxWidth()) {
                ModuleHeader("📞", "Call", "I'll always ask you to confirm first")
                GlassTextField(callNumber, { callNumber = it }, "Phone number")
                Spacer(Modifier.height(10.dp))
                GradientButton("Call", {
                    if (callNumber.filter { it.isDigit() }.length < 3) vm.banner = "Enter a phone number first."
                    else confirmCall = callNumber.trim()
                }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                ModuleHeader("💬", "Message", "Prepare a message, then send it with the app you choose")
                GlassTextField(msgNumber, { msgNumber = it }, "Phone number (optional for Share)")
                Spacer(Modifier.height(8.dp))
                GlassTextField(msgText, { msgText = it }, "Message", singleLine = false, minLines = 2)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("SMS", false, {
                        ctx.launchSafely(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + msgNumber.filter { it.isDigit() || it == '+' }))
                                .putExtra("sms_body", msgText), fail
                        )
                    })
                    GlassChip("WhatsApp", false, {
                        val digits = msgNumber.filter { it.isDigit() }
                        if (digits.length < 8) vm.banner = "WhatsApp needs the number with country code."
                        else ctx.launchSafely(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + URLEncoder.encode(msgText, "UTF-8"))), fail
                        )
                    })
                    GlassChip("Email", false, {
                        ctx.launchSafely(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_TEXT, msgText), fail
                        )
                    })
                    GlassChip("Share…", false, {
                        if (msgText.isBlank()) vm.banner = "Write a message first."
                        else ctx.launchSafely(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msgText), null), fail
                        )
                    })
                }
            }
        }

        // ===== Notifications =====
        item {
            SectionTitle("Notifications")
            GlassCard(Modifier.fillMaxWidth()) {
                ModuleHeader("🔔", "Notification manager", "Read, summarize and clear what's in your shade")
                if (!listenerOn) {
                    Text(
                        "Android needs you to switch on Notification access for DIPESH AI first. Nothing is read until you do.",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Turn on notification access", {
                        ctx.launchSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), fail)
                    }, Modifier.fillMaxWidth())
                } else if (notifications.isEmpty()) {
                    Text("No notifications right now. ✨", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                } else {
                    notifications.take(8).forEach { n ->
                        androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 4.dp)) {
                            Text(n.app, style = MaterialTheme.typography.labelSmall, color = c.violet)
                            Text(
                                listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(" — "),
                                style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientButton("Summarize", {
                            val text = notifications.take(15).joinToString("\n") { "- ${it.app}: ${it.title} ${it.text}" }
                            vm.send("Summarize my current notifications and tell me which ones matter:\n$text")
                            onOpenChat()
                        }, Modifier.weight(1f))
                        OutlineButton("Clear all", {
                            NotificationService.instance?.cancelAllNotifications()
                            vm.banner = "Notifications cleared."
                        }, Modifier.weight(1f))
                    }
                    Text(
                        "Summarize sends these notification texts to your AI provider.",
                        style = MaterialTheme.typography.labelSmall, color = c.textMuted, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // ===== Files =====
        item {
            SectionTitle("Files")
            GlassCard(Modifier.fillMaxWidth()) {
                ModuleHeader("📎", "Choose a file", "Pick a file and ask me about it")
                GradientButton("Choose file…", { filePicker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
                picked?.let { f ->
                    Spacer(Modifier.height(10.dp))
                    Text(f.name, style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text("${prettySize(f.size)} · ${f.mime}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("Ask AI about it", true, {
                            scope.launch {
                                when {
                                    f.mime.startsWith("image/") -> {
                                        val b64 = withContext(Dispatchers.IO) { ImageUtil.encodeUri(ctx, f.uri) }
                                        if (b64 == null) vm.banner = "I couldn't read that image."
                                        else { vm.send("What is in this image? (${f.name})", b64); onOpenChat() }
                                    }
                                    f.mime.startsWith("text/") || f.mime in setOf("application/json", "application/xml", "text/csv") -> {
                                        val body = withContext(Dispatchers.IO) {
                                            ctx.contentResolver.openInputStream(f.uri)?.bufferedReader()?.use { r ->
                                                val buf = CharArray(12_000)
                                                String(buf, 0, maxOf(r.read(buf), 0))
                                            }
                                        }
                                        if (body.isNullOrBlank()) vm.banner = "That file looks empty."
                                        else { vm.send("Here is the file \"${f.name}\":\n$body\n\nSummarize it and tell me what it contains."); onOpenChat() }
                                    }
                                    else -> vm.banner = "I can read text files and images. Use Open or Share for other types."
                                }
                            }
                        })
                        GlassChip("Open", false, {
                            ctx.launchSafely(
                                Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, f.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), fail
                            )
                        })
                        GlassChip("Share", false, {
                            ctx.launchSafely(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, f.uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null
                                ), fail
                            )
                        })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                ModuleHeader("🗂", "File manager", "Browse, open and delete files on your phone")
                if (!filesOk) {
                    Text(
                        "Android needs 'All files access' to browse shared storage. You can switch it off any time.",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Allow file access", {
                        if (Build.VERSION.SDK_INT >= 30) {
                            ctx.launchSafely(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)), fail
                            )
                        } else {
                            onRequestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        }
                    }, Modifier.fillMaxWidth())
                } else {
                    val rel = folder.path.removePrefix(storageRoot.path).ifEmpty { "/" }
                    Text("📍 $rel", style = MaterialTheme.typography.labelLarge, color = c.violet)
                    Spacer(Modifier.height(6.dp))
                    val entries = remember(folder, filesTick, tick) {
                        folder.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.take(60) ?: emptyList()
                    }
                    if (folder.path != storageRoot.path) {
                        Text(
                            "⬆  Up", color = c.text, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { folder.parentFile?.let { folder = it } }.padding(vertical = 8.dp)
                        )
                    }
                    if (entries.isEmpty()) Text("This folder is empty.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    entries.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (f.isDirectory) folder = f
                                else ctx.launchSafely(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(
                                        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f),
                                        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
                                    ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), fail
                                )
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (f.isDirectory) "📁" else "📄")
                            Spacer(Modifier.width(10.dp))
                            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (f.isFile) {
                                    Text(
                                        "${prettySize(f.length())} · " + SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(f.lastModified())),
                                        style = MaterialTheme.typography.labelSmall, color = c.textMuted
                                    )
                                }
                            }
                            Text("🗑", modifier = Modifier.clickable { deleteTarget = f }.padding(8.dp))
                        }
                    }
                }
            }
        }

        // ===== System shortcuts =====
        item {
            SectionTitle("System shortcuts")
            GlassCard(Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SHORTCUTS.forEach { (label, action) ->
                        GlassChip(label, false, { ctx.launchSafely(Intent(action), fail) })
                    }
                }
                Text(
                    "Android doesn't let apps flip Wi-Fi or Bluetooth silently, so these open the right settings page for you.",
                    style = MaterialTheme.typography.labelSmall, color = c.textMuted, modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        // ===== Apps =====
        item {
            SectionTitle("Apps")
            GlassTextField(search, { search = it }, "Search ${apps.size} installed apps")
            Spacer(Modifier.height(8.dp))
        }
        items(shownApps, key = { it.pkg }) { app ->
            val ctxLocal = LocalContext.current
            val icon = remember(app.pkg) {
                try {
                    ctxLocal.packageManager.getApplicationIcon(app.pkg).toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val launch = ctxLocal.packageManager.getLaunchIntentForPackage(app.pkg)
                    if (launch != null) ctxLocal.launchSafely(launch, fail) else vm.banner = "Can't open ${app.label}."
                }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Text(app.label, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (search.isBlank() && apps.size > 24) {
            item { Text("Search to find the other ${apps.size - 24} apps.", style = MaterialTheme.typography.labelSmall, color = c.textMuted, modifier = Modifier.padding(top = 6.dp)) }
        }
    }

    // ---------- dialogs ----------
    confirmCall?.let { number ->
        AlertDialog(
            onDismissRequest = { confirmCall = null },
            title = { Text("Call this number?") },
            text = { Text(number) },
            confirmButton = {
                TextButton({
                    confirmCall = null
                    val digits = number.filter { it.isDigit() || it == '+' }
                    val canCall = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                    ctx.launchSafely(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$digits")), fail)
                    if (!canCall) vm.banner = "Opened the dialer. Allow 'Phone' permission in Settings for one-tap calls."
                }) { Text("Call") }
            },
            dismissButton = { TextButton({ confirmCall = null }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete permanently?") },
            text = { Text(if (f.isDirectory) "The folder \"${f.name}\" and everything inside it." else f.name) },
            confirmButton = {
                TextButton({
                    val ok = f.deleteRecursively()
                    deleteTarget = null
                    filesTick++
                    vm.banner = if (ok) "Deleted ${f.name}." else "Couldn't delete ${f.name}."
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

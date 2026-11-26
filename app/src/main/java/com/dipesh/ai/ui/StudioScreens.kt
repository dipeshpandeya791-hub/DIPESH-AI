package com.dipesh.ai.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipesh.ai.AssistantViewModel
import com.dipesh.ai.studio.ApiHub
import com.dipesh.ai.studio.BundleParser
import com.dipesh.ai.studio.Export
import com.dipesh.ai.studio.FileBundle
import com.dipesh.ai.studio.HubError
import com.dipesh.ai.studio.ProjectLibrary
import com.dipesh.ai.studio.TaskCsv
import com.dipesh.ai.studio.TaskJson
import com.dipesh.ai.studio.Templates
import com.dipesh.ai.studio.Zipper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Module(val route: String, val icon: String, val title: String, val subtitle: String)

val studioModules: List<Pair<String, String>> = listOf(
    "studio/agent" to "🤖 Agent Builder", "studio/website" to "🌐 Website Studio", "studio/video" to "🎬 Video Studio",
    "studio/api" to "🛰 Free API Hub", "studio/tasks" to "✅ Tasks", "studio/library" to "🗂 Project Library"
)

private val modules = listOf(
    Module("studio/agent", "🤖", "Agent Builder", "Generate a Python agent project: plan-act-observe loop, tools, tests, safety notes"),
    Module("studio/website", "🌐", "Website Studio", "HTML, CSS and JavaScript site, from a template or written by your AI"),
    Module("studio/video", "🎬", "Video Studio", "Remotion, FFmpeg or MoviePy project templates (render on a computer)"),
    Module("studio/api", "🛰", "Free API Hub", "Weather, currency, countries, holidays, Wikipedia, books, dictionary, translation, earthquakes, air quality"),
    Module("studio/tasks", "✅", "Tasks", "To-do list with CSV export and JSON backup"),
    Module("studio/videomaker", "🎞", "Video Maker", "Make a 9:16 slideshow video on your phone from captions and pictures"),
    Module("studio/offline", "🧮", "Offline utilities", "Calculator, unit converter, password generator, text tools, date maths"),
    Module("studio/library", "🗂", "Project Library", "Saved projects, ZIP export, delete")
)

@Composable
internal fun ScreenHeader(title: String, onBack: (() -> Unit)?) {
    val c = LocalDipeshColors.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) Text("←", Modifier.clickable(onClick = onBack).padding(end = 14.dp, top = 4.dp, bottom = 4.dp),
            style = MaterialTheme.typography.headlineSmall, color = c.cyan)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        content(); Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun StudioHomeScreen(onOpen: (String) -> Unit) {
    val c = LocalDipeshColors.current
    Page {
        ScreenHeader("Studio", null)
        Text("Build, look things up and stay organised. Everything here is free and works from templates or free public APIs.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.height(12.dp))
        modules.forEach { m ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp), onClick = { onOpen(m.route) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.icon, fontSize = 30.sp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(m.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(m.subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
            }
        }
    }
}

// ============================================================ Builders (agent / website / video)
@Composable
fun BuilderScreen(kind: String, vm: AssistantViewModel, onBack: () -> Unit) {
    val c = LocalDipeshColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = when (kind) { "agent" -> "Agent Builder"; "website" -> "Website Studio"; else -> "Video Studio" }
    val libType = when (kind) { "agent" -> "agents"; "website" -> "websites"; else -> "videos" }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(if (kind == "website") "jarvis" else "remotion") }
    var bundle by remember { mutableStateOf<FileBundle?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun template(): FileBundle = when (kind) {
        "agent" -> Templates.agent(name, desc)
        "website" -> Templates.website(name, desc, style)
        else -> Templates.video(style, name, desc)
    }

    Page {
        ScreenHeader(title, onBack)
        GlassCard(Modifier.fillMaxWidth()) {
            GlassTextField(name, { name = it }, "Project name")
            Spacer(Modifier.height(8.dp))
            GlassTextField(desc, { desc = it }, if (kind == "agent") "What should the agent do?" else "Describe it", singleLine = false, minLines = 3)
            if (kind == "website") {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Futuristic dark", style == "jarvis", { style = "jarvis" })
                    GlassChip("Clean light", style == "clean", { style = "clean" })
                }
            }
            if (kind == "video") {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Templates.VIDEO_KINDS.forEach { k -> GlassChip(k.replaceFirstChar { it.uppercase() }.let { if (k == "ffmpeg") "FFmpeg" else if (k == "moviepy") "MoviePy" else it }, style == k, { style = k }) }
                }
                Spacer(Modifier.height(6.dp))
                Text("These are project templates. DIPESH AI on the phone does not render video; run the project on a computer.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Generate from template", {
                    bundle = template(); selected = "README.md"; message = "Template ready: ${bundle!!.size} files. Nothing has been run."
                })
                OutlineButton(if (busy) "AI is writing…" else "Generate with AI", {
                    if (busy) return@OutlineButton
                    busy = true; message = "Asking your AI to write the files…"
                    scope.launch {
                        try {
                            val text = vm.askForFiles(Templates.aiPrompt(
                                when (kind) { "agent" -> "Python AI agent"; "website" -> "static website (HTML, CSS, JavaScript)"; else -> "$style video editing" },
                                name.ifBlank { "My project" }, desc.ifBlank { "A simple project." }, if (kind == "website") "Style: $style." else ""
                            ))
                            val ai = BundleParser.parse(text)
                            val base = template()
                            if (ai.size == 0) {
                                bundle = base; message = "The AI didn't return usable files, so the template was used instead."
                            } else {
                                ai.files.forEach { (p, content) -> base.put(p, content) }
                                bundle = base; selected = "README.md"
                                message = "AI wrote ${ai.size} file(s) merged with the template" + if (ai.rejected.isNotEmpty()) " (${ai.rejected.size} unsafe or duplicate file(s) refused)." else "."
                            }
                        } catch (e: Exception) {
                            bundle = template(); message = "The AI couldn't be reached (${e.message?.take(120)}). Template used instead."
                        } finally { busy = false; selected = "README.md" }
                    }
                })
            }
        }
        message?.let { Text(it, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = c.cyan) }

        bundle?.let { b ->
            SectionTitle("Files (${b.size})")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                b.files.keys.forEach { f -> GlassChip(f, selected == f, { selected = f }) }
            }
            Spacer(Modifier.height(8.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Text(b.files[selected].orEmpty().take(6000).ifBlank { "(empty file)" }, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = c.text)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Save to library", {
                    val saved = vm.library.save(libType, name.ifBlank { title }, b)
                    message = "Saved to Project Library as \"$saved\"."
                })
                OutlineButton("Share ZIP", { zipAndShare(ctx, b, name.ifBlank { title }) { message = it } })
                OutlineButton("Save ZIP to Downloads", { zipAndSave(ctx, b, name.ifBlank { title }) { message = it } })
            }
            Text("Generated code is never run by DIPESH AI. Read it first and run it yourself.", Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

private fun makeZip(ctx: Context, files: Map<String, String>, name: String): File {
    val f = Export.cacheFile(ctx, com.dipesh.ai.studio.SafePath.slug(name) + ".zip")
    f.outputStream().use { Zipper.write(files, name, it) }
    return f
}

private fun zipAndShare(ctx: Context, b: FileBundle, name: String, say: (String) -> Unit) = try {
    Export.share(ctx, makeZip(ctx, b.files, name), "application/zip"); say("ZIP ready to share.")
} catch (e: Exception) { say("Couldn't create the ZIP (${e.message}).") }

private fun zipAndSave(ctx: Context, b: FileBundle, name: String, say: (String) -> Unit) = try {
    say(Export.saveToDownloads(ctx, makeZip(ctx, b.files, name), "application/zip"))
} catch (e: Exception) { say("Couldn't create the ZIP (${e.message}).") }

// ============================================================ API Hub
private sealed class HubState {
    object Idle : HubState()
    object Loading : HubState()
    class Ok(val text: String) : HubState()
    class Err(val text: String) : HubState()
}

@Composable
fun ApiHubScreen(onBack: () -> Unit) {
    val c = LocalDipeshColors.current
    val scope = rememberCoroutineScope()
    var service by remember { mutableStateOf(ApiHub.services.first()) }
    var input by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<HubState>(HubState.Idle) }
    Page {
        ScreenHeader("Free API Hub", onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ApiHub.services.forEach { s ->
                GlassChip("${s.icon} ${s.title}", service.id == s.id, { service = s; input = ""; state = HubState.Idle })
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            GlassTextField(input, { input = it }, service.hint + if (service.example.isNotEmpty()) "  (e.g. ${service.example})" else "")
            Spacer(Modifier.height(10.dp))
            GradientButton("Look it up", {
                if (state is HubState.Loading) return@GradientButton
                state = HubState.Loading
                val sid = service.id; val q = input
                scope.launch {
                    state = try {
                        HubState.Ok(withContext(Dispatchers.IO) { ApiHub.query(sid, q) })
                    } catch (e: HubError) { HubState.Err(e.message ?: "Something went wrong.") }
                    catch (e: Exception) { HubState.Err("Unexpected problem: ${e.message?.take(100)}") }
                }
            })
        }
        Spacer(Modifier.height(10.dp))
        when (val s = state) {
            HubState.Idle -> Text("Pick a service, type a query and tap Look it up.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            HubState.Loading -> Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = c.cyan)
            is HubState.Ok -> GlassCard(Modifier.fillMaxWidth()) { Text(s.text, color = c.text, style = MaterialTheme.typography.bodyMedium) }
            is HubState.Err -> GlassCard(Modifier.fillMaxWidth()) { Text(s.text, color = c.danger, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(12.dp))
        Text("Source: ${service.attribution}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Text("These are free public services. They can change, rate-limit or stop at any time, and their data is not guaranteed.",
            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

// ============================================================ Tasks
@Composable
fun TasksScreen(vm: AssistantViewModel, onBack: () -> Unit) {
    val c = LocalDipeshColors.current
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) message = try {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val list = TaskJson.fromJson(text); vm.replaceTasks(list); "Restored ${list.size} tasks."
        } catch (e: Exception) { "That file isn't a DIPESH AI task backup." }
    }
    Page {
        ScreenHeader("Tasks", onBack)
        GlassCard(Modifier.fillMaxWidth()) {
            GlassTextField(title, { title = it }, "New task")
            Spacer(Modifier.height(8.dp))
            GlassTextField(due, { due = it }, "Due date (optional, e.g. 2026-12-01)")
            Spacer(Modifier.height(10.dp))
            GradientButton("Add task", { vm.addTask(title, due); title = ""; due = "" })
        }
        Spacer(Modifier.height(10.dp))
        if (vm.tasks.isEmpty()) Text("No tasks yet.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        vm.tasks.forEach { t ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (t.done) "☑" else "☐", Modifier.clickable { vm.toggleTask(t.id) }.padding(end = 12.dp), fontSize = 22.sp, color = c.cyan)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = if (t.done) c.textMuted else c.text, style = MaterialTheme.typography.bodyLarge)
                        if (t.due.isNotBlank()) Text("Due ${t.due}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    Text("✕", Modifier.clickable { vm.deleteTask(t.id) }.padding(8.dp), color = c.danger)
                }
            }
        }
        SectionTitle("Export and backup")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Export CSV", {
                val f = Export.cacheFile(ctx, "dipesh-tasks.csv"); f.writeText(TaskCsv.toCsv(vm.tasks.toList()))
                message = Export.saveToDownloads(ctx, f, "text/csv")
            })
            OutlineButton("Share CSV", {
                val f = Export.cacheFile(ctx, "dipesh-tasks.csv"); f.writeText(TaskCsv.toCsv(vm.tasks.toList())); Export.share(ctx, f, "text/csv")
            })
            OutlineButton("Backup JSON", {
                val f = Export.cacheFile(ctx, "dipesh-tasks-backup.json"); f.writeText(TaskJson.toJson(vm.tasks.toList()))
                message = Export.saveToDownloads(ctx, f, "application/json")
            })
            OutlineButton("Restore backup", { picker.launch("*/*") })
            OutlineButton("Clear done", { vm.clearDoneTasks() })
        }
        message?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = c.cyan) }
    }
}

// ============================================================ Project library
@Composable
fun LibraryScreen(vm: AssistantViewModel, onBack: () -> Unit) {
    val c = LocalDipeshColors.current
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<com.dipesh.ai.studio.ProjectInfo?>(null) }
    val projects = remember(refresh) { vm.library.list() }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("This removes the project from this phone. Exported ZIP files are not affected.") },
            confirmButton = { TextButton({ vm.library.delete(p.type, p.name); toDelete = null; refresh++ }) { Text("Delete") } },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancel") } }
        )
    }
    Page {
        ScreenHeader("Project Library", onBack)
        if (projects.isEmpty()) Text("No saved projects yet. Generate one in Agent Builder, Website Studio or Video Studio and tap Save to library.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        projects.forEach { p ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text("${when (p.type) { "agents" -> "🤖"; "websites" -> "🌐"; else -> "🎬" }} ${p.name}", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("${p.type.removeSuffix("s")} · ${p.files} files", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Share ZIP", {
                        try { Export.share(ctx, vm.library.exportZip(p.type, p.name, Export.cacheFile(ctx, p.name + ".zip")), "application/zip") }
                        catch (e: Exception) { message = "ZIP failed: ${e.message}" }
                    })
                    OutlineButton("Save to Downloads", {
                        message = try { Export.saveToDownloads(ctx, vm.library.exportZip(p.type, p.name, Export.cacheFile(ctx, p.name + ".zip")), "application/zip") }
                        catch (e: Exception) { "ZIP failed: ${e.message}" }
                    })
                    OutlineButton("Delete", { toDelete = p })
                }
            }
        }
        message?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = c.cyan) }
    }
}

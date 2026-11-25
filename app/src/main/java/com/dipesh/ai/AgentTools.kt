package com.dipesh.ai

import android.content.Context
import com.dipesh.ai.net.ApiClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The autonomous coding / systems agent: Termux shell, GitHub, JavaScript sandbox, workspace files,
 * remote skills, generic free-API calls with a secret vault, and self-diagnosis. All free.
 */
class AgentTools(context: Context, private val store: Store, private val ask: (String) -> Boolean) {
    private val ctx = context.applicationContext
    private val gh = GitHubClient(store)

    fun schema(): JSONArray = JSONArray().apply {
        put(toolDef("termux_run",
            "Run a shell command in Termux (free Linux environment on the phone: python, node, git, ssh, ffmpeg, curl). Returns exit code, stdout and stderr. The user is asked to approve.",
            mapOf(
                "command" to ("string" to "Shell command, e.g. 'python3 script.py' or 'git status'."),
                "workdir" to ("string" to "Optional working directory (Termux path). Default is the Termux home."),
                "environment" to ("string" to "Optional: 'termux' (default) or 'proot:ubuntu' / 'proot:debian' for a full Linux distro."),
                "timeout_seconds" to ("integer" to "Optional, default 60, max 300.")
            ), listOf("command")))
        put(toolDef("termux_setup_help", "Explain the one-time free Termux setup so the assistant can run shell commands."))
        put(toolDef("run_javascript",
            "Run JavaScript in a safe built-in sandbox (no network or files, 10 s). Use console.log for output; the last expression is returned. Great for calculations, parsing and testing logic.",
            mapOf("code" to ("string" to "JavaScript source.")), listOf("code")))
        put(toolDef("read_text_file", "Read a text/code file. Relative paths are in the DIPESH AI workspace.",
            mapOf("path" to ("string" to "File path (or folder to list).")), listOf("path")))
        put(toolDef("write_text_file",
            "Create or overwrite a text/code file (append=true adds to the end). Relative paths are in the workspace; writes elsewhere need approval.",
            mapOf(
                "path" to ("string" to "File path."),
                "content" to ("string" to "Full text to write."),
                "append" to ("boolean" to "Append instead of overwrite.")
            ), listOf("path", "content")))
        put(toolDef("workspace_path", "Tell where the agent workspace folder is (projects and pulled repositories live here)."))

        val repo = "repo" to ("string" to "owner/name, or just name for the user's own repo.")
        put(toolDef("github_list_repos", "List the user's GitHub repositories."))
        put(toolDef("github_list_dir", "List files in a GitHub repo folder.",
            mapOf(repo, "path" to ("string" to "Folder path, empty for root."), "ref" to ("string" to "Optional branch/tag.")), listOf("repo")))
        put(toolDef("github_read_file", "Read a file from a GitHub repo.",
            mapOf(repo, "path" to ("string" to "File path."), "ref" to ("string" to "Optional branch/tag.")), listOf("repo", "path")))
        put(toolDef("github_write_file", "Create or update a file in a GitHub repo as a commit. The user is asked to approve.",
            mapOf(repo, "path" to ("string" to "File path in repo."), "content" to ("string" to "Full new file content."),
                "message" to ("string" to "Commit message."), "branch" to ("string" to "Optional branch, default is the repo's default branch.")),
            listOf("repo", "path", "content")))
        put(toolDef("github_delete_file", "Delete a file from a GitHub repo (commit). The user is asked to approve.",
            mapOf(repo, "path" to ("string" to "File path."), "message" to ("string" to "Commit message."), "branch" to ("string" to "Optional branch.")),
            listOf("repo", "path")))
        put(toolDef("github_create_branch", "Create a branch.",
            mapOf(repo, "branch" to ("string" to "New branch name."), "from" to ("string" to "Optional base branch.")), listOf("repo", "branch")))
        put(toolDef("github_create_pr", "Open a pull request.",
            mapOf(repo, "title" to ("string" to "Title."), "head" to ("string" to "Branch with the changes."),
                "base" to ("string" to "Optional target branch."), "body" to ("string" to "Description.")), listOf("repo", "title", "head")))
        put(toolDef("github_commits", "Show recent commits (version history).",
            mapOf(repo, "path" to ("string" to "Optional file path."), "branch" to ("string" to "Optional branch.")), listOf("repo")))
        put(toolDef("github_issues", "List open issues.", mapOf(repo), listOf("repo")))
        put(toolDef("github_create_issue", "Create an issue.",
            mapOf(repo, "title" to ("string" to "Title."), "body" to ("string" to "Details.")), listOf("repo", "title")))
        put(toolDef("github_create_repo", "Create a new repository.",
            mapOf("name" to ("string" to "Repo name."), "private" to ("boolean" to "Private repo?"), "description" to ("string" to "Description.")), listOf("name")))
        put(toolDef("github_build_status", "Show the latest CI (GitHub Actions) runs of a repo. Use it to check if a build passed.", mapOf(repo), listOf("repo")))
        put(toolDef("github_failed_log", "Explain why a CI run failed: failing steps and the end of the log. Use it to debug and fix code.",
            mapOf(repo, "run_id" to ("string" to "Run id from github_build_status.")), listOf("repo", "run_id")))
        put(toolDef("github_pull_repo", "Download a repo into the workspace as a local working copy to read, edit and run.",
            mapOf(repo, "ref" to ("string" to "Optional branch/tag.")), listOf("repo")))
        put(toolDef("github_push_file", "Commit a local workspace file to a repo. The user is asked to approve.",
            mapOf(repo, "local_path" to ("string" to "File in the workspace."), "repo_path" to ("string" to "Destination path in the repo."),
                "message" to ("string" to "Commit message."), "branch" to ("string" to "Optional branch.")), listOf("repo", "local_path")))

        put(toolDef("install_skill",
            "Load a new skill (instruction file) from a GitHub repo or link, e.g. 'owner/repo' or 'owner/repo/skills/tool/SKILL.md'. The user is asked to approve.",
            mapOf("source" to ("string" to "owner/repo[/path] or https link to a markdown file.")), listOf("source")))
        put(toolDef("list_skills", "List installed skills."))
        put(toolDef("remove_skill", "Remove an installed skill.", mapOf("name" to ("string" to "Skill name.")), listOf("name")))
        put(toolDef("update_skills", "Re-download all installed skills from their sources."))

        put(toolDef("http_request",
            "Call any web API over https (free APIs, webhooks). Use {{secret:NAME}} in url, headers or body to insert a stored secret without seeing it. Non-GET calls and calls with secrets need approval.",
            mapOf(
                "method" to ("string" to "GET, POST, PUT, PATCH or DELETE."),
                "url" to ("string" to "https URL."),
                "headers" to ("string" to "Optional headers, one 'Name: value' per line."),
                "body" to ("string" to "Optional request body (usually JSON).")
            ), listOf("url")))
        put(toolDef("self_check",
            "Diagnose the assistant itself: internet, API key, Termux, GitHub token, storage and screen-control permissions. Use it whenever something fails repeatedly, then fix what it reports."))
    }

    val names: Set<String> by lazy {
        val a = schema(); (0 until a.length()).map { a.getJSONObject(it).getString("name") }.toSet()
    }

    fun run(name: String, input: JSONObject): String? {
        if (name !in names) return null
        return when (name) {
            "termux_run" -> {
                val cmd = input.getString("command")
                if (!ask("Run this in Termux?\n\n${cmd.take(400)}")) "The user declined to run that command."
                else TermuxRunner.run(ctx, cmd, input.optString("workdir"), input.optString("environment").ifBlank { "termux" },
                    input.optInt("timeout_seconds", 60))
            }
            "termux_setup_help" -> TermuxRunner.SETUP_HELP
            "run_javascript" -> JsSandbox.run(input.getString("code"))
            "read_text_file" -> readText(input.getString("path"))
            "write_text_file" -> writeText(input.getString("path"), input.getString("content"), input.optBoolean("append"))
            "workspace_path" -> "The workspace is ${Workspace.dir(ctx).absolutePath}" +
                if (!Workspace.hasAllFiles()) " (turn on 'All files access' in the Control tab to use shared storage that Termux can also reach)." else "."
            "github_list_repos" -> gh.listRepos()
            "github_list_dir" -> gh.listDir(input.getString("repo"), input.optString("path"), input.optString("ref"))
            "github_read_file" -> gh.readFile(input.getString("repo"), input.getString("path"), input.optString("ref"))
            "github_write_file" -> {
                val r = input.getString("repo"); val p = input.getString("path")
                if (!ask("Commit changes to $p in $r${input.optString("branch").let { if (it.isBlank()) "" else " (branch $it)" }}?\n\n${input.optString("message")}")) "The user declined the commit."
                else gh.writeFile(r, p, input.getString("content"), input.optString("message"), input.optString("branch"))
            }
            "github_delete_file" -> {
                if (!ask("Delete ${input.getString("path")} from ${input.getString("repo")} on GitHub?")) "The user declined."
                else gh.deleteFile(input.getString("repo"), input.getString("path"), input.optString("message"), input.optString("branch"))
            }
            "github_create_branch" -> gh.createBranch(input.getString("repo"), input.getString("branch"), input.optString("from"))
            "github_create_pr" -> gh.createPr(input.getString("repo"), input.getString("title"), input.getString("head"), input.optString("base"), input.optString("body"))
            "github_commits" -> gh.commits(input.getString("repo"), input.optString("path"), input.optString("branch"))
            "github_issues" -> gh.issues(input.getString("repo"))
            "github_create_issue" -> gh.createIssue(input.getString("repo"), input.getString("title"), input.optString("body"))
            "github_create_repo" -> gh.createRepo(input.getString("name"), input.optBoolean("private"), input.optString("description"))
            "github_build_status" -> gh.runs(input.getString("repo"))
            "github_failed_log" -> gh.failedLog(input.getString("repo"), input.getString("run_id"))
            "github_pull_repo" -> gh.pull(ctx, input.getString("repo"), input.optString("ref"))
            "github_push_file" -> {
                if (!ask("Commit ${input.getString("local_path")} to ${input.getString("repo")} on GitHub?")) "The user declined the commit."
                else gh.pushLocal(ctx, input.getString("repo"), input.getString("local_path"), input.optString("repo_path"),
                    input.optString("message"), input.optString("branch"))
            }
            "install_skill" -> {
                val src = input.getString("source")
                if (!ask("Install the skill from $src? Skills add instructions the assistant will follow.")) "The user declined."
                else Skills.install(store, gh, src)
            }
            "list_skills" -> Skills.load(store).let { l ->
                if (l.isEmpty()) "No skills installed." else l.joinToString("\n") { "${it.name} (${if (it.enabled) "on" else "off"}) from ${it.source}" }
            }
            "remove_skill" -> {
                val n = input.getString("name")
                val l = Skills.load(store)
                if (l.none { it.name.equals(n, true) }) "No skill named $n."
                else { Skills.save(store, l.filterNot { it.name.equals(n, true) }); "Removed $n." }
            }
            "update_skills" -> Skills.update(store, gh)
            "http_request" -> httpRequest(input)
            "self_check" -> selfCheck()
            else -> null
        }
    }

    private fun readText(path: String): String {
        val f = Workspace.resolve(ctx, path)
        if (!f.exists()) return "No such file: ${f.absolutePath}. Use read_text_file on the folder to list what exists."
        if (f.isDirectory) return f.listFiles()?.sortedBy { it.name }?.joinToString("\n") { (if (it.isDirectory) "[dir] " else "") + it.name }
            ?.ifBlank { "(empty folder)" } ?: "Cannot list that folder."
        if (f.length() > 2_000_000) return "That file is too large to read (${f.length()} bytes)."
        val t = f.readText()
        return if (t.length > 20000) t.take(20000) + "\n...[truncated, ${t.length} chars]" else t
    }

    private fun writeText(path: String, content: String, append: Boolean): String {
        val f = Workspace.resolve(ctx, path)
        if (!Workspace.inside(ctx, f) && !ask("Write to ${f.absolutePath}?")) return "The user declined."
        if (!Workspace.inside(ctx, f) && !Workspace.hasAllFiles()) return "I can't write outside the workspace without 'All files access' (Control tab)."
        f.parentFile?.mkdirs()
        if (append) f.appendText(content) else f.writeText(content)
        return "Saved ${f.absolutePath} (${content.length} characters)."
    }

    private fun secrets(): Map<String, String> =
        store.settings.value.secrets.lineSequence().mapNotNull { l ->
            val i = l.indexOf('='); if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 1).trim()
        }.toMap()

    private fun httpRequest(input: JSONObject): String {
        val method = input.optString("method", "GET").uppercase()
        val vault = secrets()
        var usesSecret = false
        fun fill(s: String): String = Regex("\\{\\{secret:([A-Za-z0-9_]+)}}").replace(s) {
            usesSecret = true
            vault[it.groupValues[1]] ?: throw IllegalArgumentException("No secret named ${it.groupValues[1]}. Add it in Settings > Developer agent.")
        }
        val rawUrl = input.getString("url")
        val url = try { fill(rawUrl) } catch (e: IllegalArgumentException) { return e.message ?: "Missing secret." }
        if (!url.startsWith("https://")) return "Only https:// addresses are allowed here. (For devices on your home network use the smart-device tools.)"
        val body = try { fill(input.optString("body")) } catch (e: IllegalArgumentException) { return e.message ?: "Missing secret." }
        val headers = try { fill(input.optString("headers")) } catch (e: IllegalArgumentException) { return e.message ?: "Missing secret." }
        if ((method != "GET" || usesSecret) && !ask("Send a $method request to ${rawUrl.take(120)}?")) return "The user declined."
        val b = Request.Builder().url(url).header("User-Agent", "DipeshAI")
        headers.lineSequence().forEach { l ->
            val i = l.indexOf(':'); if (i > 0) b.header(l.substring(0, i).trim(), l.substring(i + 1).trim())
        }
        val rb = if (method == "GET") null else ApiClient.jsonBody(body.ifBlank { "{}" })
        b.method(method, if (method == "GET" || method == "DELETE" && body.isBlank()) null else rb)
        return try {
            ApiClient.okHttp.newCall(b.build()).execute().use {
                var text = it.body?.string().orEmpty()
                vault.values.filter { v -> v.length > 3 }.forEach { v -> text = text.replace(v, "***") }
                "HTTP ${it.code}\n" + text.take(6000)
            }
        } catch (e: Exception) { "Request failed: ${e.message}. Check the address and your internet connection." }
    }

    private fun selfCheck(): String {
        val s = store.settings.value
        val sb = StringBuilder()
        fun line(ok: Boolean, text: String) { sb.append(if (ok) "OK: " else "PROBLEM: ").append(text).append('\n') }
        val online = try {
            ApiClient.okHttp.newCall(Request.Builder().url("https://www.gstatic.com/generate_204").build()).execute().use { it.code in 200..399 }
        } catch (_: Exception) { false }
        line(online, if (online) "Internet reachable." else "No internet. Check Wi-Fi or mobile data.")
        val key = if (s.provider == "claude") s.claudeKey else if (s.provider == "gemini") s.geminiKey else s.openaiKey
        line(key.isNotBlank() || s.provider == "openai", "AI provider '${s.provider}' key " + if (key.isNotBlank()) "is set." else "is missing: add it in Settings > AI & API configuration.")
        line(TermuxRunner.installed(ctx), if (TermuxRunner.installed(ctx)) "Termux installed." else "Termux missing. ${TermuxRunner.NOT_INSTALLED}")
        if (TermuxRunner.installed(ctx)) line(TermuxRunner.hasPermission(ctx), if (TermuxRunner.hasPermission(ctx)) "Termux command permission granted." else "Termux permission not granted (App settings > Permissions).")
        line(s.githubToken.isNotBlank(), if (s.githubToken.isNotBlank()) "GitHub token set." else "No GitHub token (public repos still readable; writing needs a free token in Settings).")
        line(Workspace.hasAllFiles(), if (Workspace.hasAllFiles()) "All-files access granted; workspace at ${Workspace.dir(ctx).absolutePath}." else "All-files access off: files stay in the app's own folder. Enable in the Control tab.")
        line(PhoneControlService.instance != null, if (PhoneControlService.instance != null) "Screen control (accessibility) on." else "Screen control is off: needed to tap, read the screen and auto-type. Enable it in Settings.")
        line(NotificationService.instance != null, if (NotificationService.instance != null) "Notification access on." else "Notification access off (only needed to read notifications).")
        return sb.toString().trim()
    }
}

package com.dipesh.ai

import android.util.Base64
import com.dipesh.ai.net.ApiClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/** Thin GitHub REST client (free API, free personal access token). Blocking: call off the main thread. */
class GitHubClient(private val store: Store) {
    private val token get() = store.settings.value.githubToken.trim()
    val hasToken get() = token.isNotEmpty()
    private var login: String? = null

    private fun req(method: String, path: String, body: JSONObject? = null): Pair<Int, String> {
        val url = if (path.startsWith("http")) path else "https://api.github.com$path"
        val b = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DipeshAI")
        if (hasToken) b.header("Authorization", "Bearer $token")
        val rb = body?.let { ApiClient.jsonBody(it.toString()) }
        when (method) {
            "GET" -> b.get()
            "POST" -> b.post(rb ?: ApiClient.jsonBody("{}"))
            "PUT" -> b.put(rb ?: ApiClient.jsonBody("{}"))
            "PATCH" -> b.patch(rb ?: ApiClient.jsonBody("{}"))
            "DELETE" -> if (rb != null) b.delete(rb) else b.delete()
        }
        ApiClient.okHttp.newCall(b.build()).execute().use { return it.code to it.body?.string().orEmpty() }
    }

    private fun fail(code: Int, text: String): String {
        val msg = try { JSONObject(text).optString("message") } catch (_: Exception) { text.take(120) }
        return when (code) {
            401 -> "GitHub rejected the token (401). Create a new free token at github.com/settings/tokens (scope: repo, workflow) and paste it in Settings > Developer agent."
            403 -> "GitHub refused (403): $msg. If this is a rate limit, add a token in Settings; if it is permissions, the token needs the 'repo' scope."
            404 -> "Not found (404). Check the owner/repo name, path and branch. Private repos need a token with 'repo' scope."
            409, 422 -> "GitHub could not apply that ($code): $msg"
            else -> "GitHub error $code: $msg"
        }
    }

    private fun me(): String? {
        login?.let { return it }
        if (!hasToken) return null
        val (c, t) = req("GET", "/user")
        if (c != 200) return null
        login = JSONObject(t).optString("login"); return login
    }

    /** "owner/name" or just "name" (owned by the token's user). */
    fun fullName(repo: String): String {
        val r = repo.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        if (r.contains('/')) return r
        return (me() ?: throw IllegalArgumentException("Give the repo as owner/name, or add a GitHub token in Settings.")) + "/" + r
    }

    private fun enc(path: String) = path.trim('/').split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    fun listRepos(): String {
        if (!hasToken) return "No GitHub token set. Add a free personal access token in Settings > Developer agent."
        val (c, t) = req("GET", "/user/repos?sort=updated&per_page=30")
        if (c != 200) return fail(c, t)
        val a = JSONArray(t)
        return (0 until a.length()).joinToString("\n") {
            val o = a.getJSONObject(it)
            "${o.getString("full_name")} (${if (o.optBoolean("private")) "private" else "public"}) ${o.optString("description", "").takeIf { d -> d != "null" }.orEmpty()}"
        }.ifBlank { "No repositories found." }
    }

    fun listDir(repo: String, path: String, ref: String): String {
        val r = fullName(repo)
        val (c, t) = req("GET", "/repos/$r/contents/${enc(path)}" + if (ref.isNotBlank()) "?ref=$ref" else "")
        if (c != 200) return fail(c, t)
        if (t.trimStart().startsWith("{")) return "That path is a file, not a folder."
        val a = JSONArray(t)
        return (0 until a.length()).joinToString("\n") {
            val o = a.getJSONObject(it); (if (o.getString("type") == "dir") "[dir] " else "") + o.getString("path")
        }
    }

    /** Returns the file text, or null when missing (used by the skill installer). */
    fun readText(repo: String, path: String, ref: String): Pair<String?, String> {
        val r = fullName(repo)
        val (c, t) = req("GET", "/repos/$r/contents/${enc(path)}" + if (ref.isNotBlank()) "?ref=$ref" else "")
        if (c != 200) return null to fail(c, t)
        if (t.trimStart().startsWith("[")) return null to "That path is a folder."
        val o = JSONObject(t)
        val text = String(Base64.decode(o.optString("content").replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
        return text to ""
    }

    fun readFile(repo: String, path: String, ref: String): String {
        val (text, err) = readText(repo, path, ref)
        return text?.let { if (it.length > 20000) it.take(20000) + "\n...[truncated, ${it.length} chars]" else it } ?: err
    }

    private fun defaultBranch(r: String): String {
        val (c, t) = req("GET", "/repos/$r")
        return if (c == 200) JSONObject(t).optString("default_branch", "main") else "main"
    }

    /** Creates or updates one file as a commit. */
    fun writeFile(repo: String, path: String, content: String, message: String, branch: String): String {
        if (!hasToken) return "No GitHub token set. Add a free personal access token in Settings > Developer agent."
        val r = fullName(repo)
        val br = branch.ifBlank { defaultBranch(r) }
        val (gc, gt) = req("GET", "/repos/$r/contents/${enc(path)}?ref=$br")
        val body = JSONObject()
            .put("message", message.ifBlank { "Update $path via DIPESH AI" })
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", br)
        if (gc == 200 && gt.trimStart().startsWith("{")) body.put("sha", JSONObject(gt).optString("sha"))
        val (c, t) = req("PUT", "/repos/$r/contents/${enc(path)}", body)
        if (c != 200 && c != 201) return fail(c, t)
        val commit = JSONObject(t).optJSONObject("commit")
        return "Committed $path to $r@$br (${commit?.optString("sha", "")?.take(7)})."
    }

    fun deleteFile(repo: String, path: String, message: String, branch: String): String {
        if (!hasToken) return "No GitHub token set."
        val r = fullName(repo); val br = branch.ifBlank { defaultBranch(r) }
        val (gc, gt) = req("GET", "/repos/$r/contents/${enc(path)}?ref=$br")
        if (gc != 200) return fail(gc, gt)
        val body = JSONObject().put("message", message.ifBlank { "Delete $path via DIPESH AI" })
            .put("sha", JSONObject(gt).optString("sha")).put("branch", br)
        val (c, t) = req("DELETE", "/repos/$r/contents/${enc(path)}", body)
        return if (c == 200) "Deleted $path from $r@$br." else fail(c, t)
    }

    fun createBranch(repo: String, branch: String, from: String): String {
        if (!hasToken) return "No GitHub token set."
        val r = fullName(repo)
        val base = from.ifBlank { defaultBranch(r) }
        val (c, t) = req("GET", "/repos/$r/git/ref/heads/$base")
        if (c != 200) return fail(c, t)
        val sha = JSONObject(t).getJSONObject("object").getString("sha")
        val (c2, t2) = req("POST", "/repos/$r/git/refs", JSONObject().put("ref", "refs/heads/$branch").put("sha", sha))
        return if (c2 == 201) "Created branch $branch from $base." else fail(c2, t2)
    }

    fun createPr(repo: String, title: String, head: String, base: String, body: String): String {
        if (!hasToken) return "No GitHub token set."
        val r = fullName(repo)
        val (c, t) = req("POST", "/repos/$r/pulls", JSONObject().put("title", title).put("head", head)
            .put("base", base.ifBlank { defaultBranch(r) }).put("body", body))
        return if (c == 201) "Pull request opened: " + JSONObject(t).optString("html_url") else fail(c, t)
    }

    fun commits(repo: String, path: String, branch: String): String {
        val r = fullName(repo)
        val q = buildString {
            append("?per_page=10")
            if (path.isNotBlank()) append("&path=").append(enc(path))
            if (branch.isNotBlank()) append("&sha=").append(branch)
        }
        val (c, t) = req("GET", "/repos/$r/commits$q")
        if (c != 200) return fail(c, t)
        val a = JSONArray(t)
        return (0 until a.length()).joinToString("\n") {
            val o = a.getJSONObject(it)
            o.getString("sha").take(7) + " " + o.getJSONObject("commit").getString("message").lineSequence().first()
        }.ifBlank { "No commits." }
    }

    fun issues(repo: String): String {
        val r = fullName(repo)
        val (c, t) = req("GET", "/repos/$r/issues?state=open&per_page=15")
        if (c != 200) return fail(c, t)
        val a = JSONArray(t)
        return (0 until a.length()).joinToString("\n") {
            val o = a.getJSONObject(it); "#${o.getInt("number")} ${o.getString("title")}"
        }.ifBlank { "No open issues." }
    }

    fun createIssue(repo: String, title: String, body: String): String {
        if (!hasToken) return "No GitHub token set."
        val (c, t) = req("POST", "/repos/${fullName(repo)}/issues", JSONObject().put("title", title).put("body", body))
        return if (c == 201) "Issue created: " + JSONObject(t).optString("html_url") else fail(c, t)
    }

    fun createRepo(name: String, private: Boolean, description: String): String {
        if (!hasToken) return "No GitHub token set."
        val (c, t) = req("POST", "/user/repos", JSONObject().put("name", name).put("private", private)
            .put("description", description).put("auto_init", true))
        return if (c == 201) "Repository created: " + JSONObject(t).optString("html_url") else fail(c, t)
    }

    /** Latest CI runs: lets the agent notice a failed build, read why, fix the code and push again. */
    fun runs(repo: String): String {
        val r = fullName(repo)
        val (c, t) = req("GET", "/repos/$r/actions/runs?per_page=5")
        if (c != 200) return fail(c, t)
        val a = JSONObject(t).getJSONArray("workflow_runs")
        return (0 until a.length()).joinToString("\n") {
            val o = a.getJSONObject(it)
            "run ${o.getLong("id")}: ${o.optString("name")} on ${o.optString("head_branch")} - ${o.optString("status")}/${o.optString("conclusion")}"
        }.ifBlank { "No workflow runs." }
    }

    /** Names the failing steps and quotes the tail of their log. */
    fun failedLog(repo: String, runId: String): String {
        val r = fullName(repo)
        val (c, t) = req("GET", "/repos/$r/actions/runs/$runId/jobs")
        if (c != 200) return fail(c, t)
        val jobs = JSONObject(t).getJSONArray("jobs")
        val sb = StringBuilder()
        for (i in 0 until jobs.length()) {
            val j = jobs.getJSONObject(i)
            if (j.optString("conclusion") != "failure") continue
            sb.append("Job ${j.optString("name")} failed. Failed steps: ")
            val steps = j.optJSONArray("steps") ?: JSONArray()
            for (k in 0 until steps.length()) {
                val s = steps.getJSONObject(k)
                if (s.optString("conclusion") == "failure") sb.append(s.optString("name")).append("; ")
            }
            val (lc, lt) = req("GET", "/repos/$r/actions/jobs/${j.getLong("id")}/logs")
            if (lc == 200) sb.append("\nLog tail:\n").append(lt.takeLast(3500)) else sb.append("\n(log needs a token with repo scope)")
        }
        return sb.toString().ifBlank { "No failed jobs in that run." }
    }

    /** Downloads a repo into the workspace (a real working copy you can read, edit and run in Termux). */
    fun pull(ctx: android.content.Context, repo: String, ref: String): String {
        val r = fullName(repo)
        val dest = File(Workspace.dir(ctx), "repos/" + r.substringAfter('/'))
        val b = Request.Builder().url("https://api.github.com/repos/$r/zipball" + if (ref.isNotBlank()) "/$ref" else "")
            .header("User-Agent", "DipeshAI")
        if (hasToken) b.header("Authorization", "Bearer $token")
        ApiClient.okHttp.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) return fail(resp.code, resp.body?.string().orEmpty())
            if (dest.exists()) dest.deleteRecursively()
            dest.mkdirs()
            var files = 0
            ZipInputStream(resp.body!!.byteStream()).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val rel = e.name.substringAfter('/', "")
                    if (rel.isNotEmpty() && !e.isDirectory) {
                        val out = File(dest, rel)
                        if (out.canonicalPath.startsWith(dest.canonicalPath + File.separator)) {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zin.copyTo(it) }
                            files++
                        }
                    }
                    e = zin.nextEntry
                }
            }
            return "Pulled $r into ${dest.absolutePath} ($files files). Edit with write_text_file and run with termux_run or run_javascript."
        }
    }

    /** Pushes a local file from the workspace back to the repo as a commit. */
    fun pushLocal(ctx: android.content.Context, repo: String, localPath: String, repoPath: String, message: String, branch: String): String {
        val f = Workspace.resolve(ctx, localPath)
        if (!f.isFile) return "No such local file: ${f.absolutePath}"
        if (f.length() > 900_000) return "File too large for the contents API (limit about 1 MB)."
        return writeFile(repo, repoPath.ifBlank { localPath.trim('/') }, f.readText(), message, branch)
    }
}

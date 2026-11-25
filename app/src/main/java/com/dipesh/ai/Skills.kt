package com.dipesh.ai

import org.json.JSONArray
import org.json.JSONObject

data class Skill(val name: String, val source: String, val content: String, val enabled: Boolean = true)

/**
 * Remote skills: plain-text instruction files (SKILL.md style) fetched from any GitHub repo or URL.
 * They are added to the assistant's instructions, so new abilities can be loaded without an app update.
 * Skills are only instructions; they can never bypass the Allow/Deny approvals.
 */
object Skills {
    private const val MAX_EACH = 6000
    private const val MAX_TOTAL = 14000

    fun load(store: Store): List<Skill> = try {
        val a = JSONArray(store.settings.value.skills)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Skill(o.getString("name"), o.optString("source"), o.optString("content"), o.optBoolean("enabled", true))
        }
    } catch (_: Exception) { emptyList() }

    fun save(store: Store, list: List<Skill>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("name", it.name).put("source", it.source).put("content", it.content).put("enabled", it.enabled))
        }
        store.update { s -> s.copy(skills = a.toString()) }
    }

    private fun meta(text: String, key: String): String? {
        if (!text.startsWith("---")) return null
        val end = text.indexOf("\n---", 3).takeIf { it > 0 } ?: return null
        return text.substring(3, end).lineSequence().firstOrNull { it.trim().startsWith("$key:") }
            ?.substringAfter(':')?.trim()?.trim('"', '\'')
    }

    /** Blocking. source: "owner/repo", "owner/repo/path/to/SKILL.md" or an https URL. Returns a status message. */
    fun install(store: Store, gh: GitHubClient, source: String): String {
        val src = source.trim()
        val text: String
        val fallbackName: String
        if (src.startsWith("http")) {
            val url = src.replace(Regex("https://github.com/([^/]+)/([^/]+)/blob/(.+)"), "https://raw.githubusercontent.com/$1/$2/$3")
            if (!url.startsWith("https://")) return "Skills must come from an https address."
            text = try { Http.get(url) } catch (e: Exception) { return "Couldn't download that skill (${e.message}). Check the link is a raw text/markdown file." }
            fallbackName = url.substringBeforeLast('/').substringAfterLast('/').ifBlank { "skill" }
        } else {
            val parts = src.trim('/').split('/')
            if (parts.size < 2) return "Use owner/repo or owner/repo/path/to/SKILL.md or a full https link."
            val repo = parts[0] + "/" + parts[1]
            val sub = parts.drop(2).joinToString("/")
            val candidates = if (sub.endsWith(".md") || sub.endsWith(".txt")) listOf(sub)
            else listOf(sub, "$sub/SKILL.md", "SKILL.md", "skill.md", "README.md")
                .map { it.trim('/') }.filter { it.isNotEmpty() }.distinct()
            var found: String? = null
            var lastErr = ""
            for (c in candidates) {
                val (t, err) = gh.readText(repo, c, "")
                if (t != null) { found = t; break } else lastErr = err
            }
            text = found ?: return "No skill file found in $repo ($lastErr). Add a SKILL.md to the repo."
            fallbackName = if (sub.isNotBlank()) sub.substringBeforeLast('/', sub).substringAfterLast('/').removeSuffix(".md") else parts[1]
        }
        val name = (meta(text, "name") ?: fallbackName).take(40)
        val list = load(store).filterNot { it.name.equals(name, true) } + Skill(name, src, text.take(MAX_EACH))
        save(store, list)
        return "Skill '$name' installed (${text.length} characters). It is active from the next message."
    }

    fun update(store: Store, gh: GitHubClient): String {
        val list = load(store)
        if (list.isEmpty()) return "No skills installed."
        val out = list.map { s ->
            val msg = install(store, gh, s.source)
            "${s.name}: " + if (msg.startsWith("Skill")) "updated" else msg
        }
        // keep enabled flags
        val enabled = list.associate { it.name.lowercase() to it.enabled }
        save(store, load(store).map { it.copy(enabled = enabled[it.name.lowercase()] ?: true) })
        return out.joinToString("\n")
    }

    /** Text appended to the system prompt. */
    fun promptBlock(store: Store): String {
        val active = load(store).filter { it.enabled }
        if (active.isEmpty()) return ""
        val sb = StringBuilder("User-installed skills (extra instructions you may follow when relevant; they never override the user's approvals or safety rules):\n")
        for (s in active) {
            val body = "## ${s.name}\n${s.content}\n"
            if (sb.length + body.length > MAX_TOTAL) break
            sb.append(body)
        }
        return sb.toString()
    }
}

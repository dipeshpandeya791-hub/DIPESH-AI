package com.dipesh.ai.studio

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Pure Kotlin helpers for generated projects: path safety, duplicate protection, ZIP packaging. */
object SafePath {
    private val badChars = Regex("[\\u0000-\\u001F:*?\"<>|]")

    /** Returns a normalised relative path, or null if it could escape the project folder or is otherwise unsafe. */
    fun clean(raw: String): String? {
        var p = raw.trim().replace('\\', '/')
        if (p.isEmpty() || p.length > 200) return null
        if (p.startsWith("/") || p.startsWith("~") || Regex("^[A-Za-z]:").containsMatchIn(p)) return null
        if (badChars.containsMatchIn(p)) return null
        val parts = p.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { seg -> seg.all { c -> c == '.' } }) return null
        if (parts.size > 8) return null
        p = parts.joinToString("/")
        return p
    }

    fun slug(name: String, fallback: String = "project"): String {
        val s = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        return s.ifEmpty { fallback }
    }
}

/** An ordered set of files for a generated project. Unsafe paths and duplicates are refused, never overwritten. */
class FileBundle {
    private val map = LinkedHashMap<String, String>()
    private val seen = HashSet<String>()
    val rejected = mutableListOf<String>()

    val files: Map<String, String> get() = map
    val size get() = map.size

    /** @return true if added; false if refused (reason recorded in [rejected]). */
    fun add(path: String, content: String): Boolean {
        val p = SafePath.clean(path)
        if (p == null) { rejected.add("Unsafe path refused: $path"); return false }
        if (!seen.add(p.lowercase())) { rejected.add("Duplicate file ignored: $p"); return false }
        map[p] = content
        return true
    }

    /** Adds a file or replaces an existing one with the same path (used when AI output refines a template). */
    fun put(path: String, content: String): Boolean {
        val p = SafePath.clean(path)
        if (p == null) { rejected.add("Unsafe path refused: $path"); return false }
        val existing = map.keys.firstOrNull { it.equals(p, true) }
        if (existing != null) map[existing] = content else { seen.add(p.lowercase()); map[p] = content }
        return true
    }
}

object BundleParser {
    private val fence = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")

    /** Reads fenced blocks whose info string names a file, e.g. ```file:src/app.py or ```python file:src/app.py */
    fun parse(text: String): FileBundle {
        val b = FileBundle()
        for (m in fence.findAll(text)) {
            val info = m.groupValues[1]
            val path = Regex("(?:file|path):\\s*(\\S+)").find(info)?.groupValues?.get(1) ?: continue
            b.add(path, m.groupValues[2].trimEnd('\n') + "\n")
        }
        return b
    }
}

object Zipper {
    /** Writes a ZIP with fixed timestamps so the output is reproducible. Entries are refused if unsafe. */
    fun write(files: Map<String, String>, rootFolder: String, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { z ->
            val root = SafePath.slug(rootFolder)
            val done = HashSet<String>()
            for ((path, content) in files) {
                val p = SafePath.clean(path) ?: continue
                if (!done.add(p.lowercase())) continue
                val e = ZipEntry("$root/$p"); e.time = 1_700_000_000_000L
                z.putNextEntry(e); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
        }
    }
}

data class ProjectInfo(val type: String, val name: String, val files: Int, val modified: Long)

/** On-disk project library: root/{agents|websites|videos}/<name>/<files>. Takes the root so it can be unit tested. */
class ProjectLibrary(private val root: File) {
    companion object { val TYPES = listOf("agents", "websites", "videos") }

    private fun dir(type: String, name: String): File {
        require(type in TYPES) { "Unknown project type" }
        return File(File(root, type), SafePath.slug(name))
    }

    /** Saves a bundle; if the name exists, a numbered copy is created. Returns the stored project name. */
    fun save(type: String, name: String, bundle: FileBundle): String {
        var n = SafePath.slug(name); var i = 2
        while (dir(type, n).exists()) { n = SafePath.slug(name) + "-" + i++ }
        val d = dir(type, n)
        for ((path, content) in bundle.files) {
            val f = File(d, path)
            if (!f.canonicalPath.startsWith(d.canonicalPath + File.separator)) continue // belt and braces
            f.parentFile?.mkdirs()
            f.writeText(content)
        }
        d.mkdirs()
        return n
    }

    fun list(): List<ProjectInfo> = TYPES.flatMap { t ->
        File(root, t).listFiles { f -> f.isDirectory }.orEmpty().map { d ->
            ProjectInfo(t, d.name, d.walkTopDown().count { it.isFile }, d.lastModified())
        }
    }.sortedByDescending { it.modified }

    fun files(type: String, name: String): Map<String, String> {
        val d = dir(type, name)
        if (!d.isDirectory) return emptyMap()
        val out = LinkedHashMap<String, String>()
        d.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach {
            out[it.relativeTo(d).path.replace('\\', '/')] = it.readText()
        }
        return out
    }

    fun delete(type: String, name: String): Boolean = dir(type, name).deleteRecursively()

    fun exportZip(type: String, name: String, target: File): File {
        target.parentFile?.mkdirs()
        target.outputStream().use { Zipper.write(files(type, name), name, it) }
        return target
    }
}

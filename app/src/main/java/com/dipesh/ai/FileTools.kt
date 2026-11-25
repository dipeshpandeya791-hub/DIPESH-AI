package com.dipesh.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Find, open and manage files in the phone's shared storage. Needs "All files access". */
class FileTools(context: Context, private val ask: (String) -> Boolean) {
    private val ctx = context.applicationContext
    private val root: File = Environment.getExternalStorageDirectory()

    fun schema(): JSONArray = JSONArray().apply {
        put(d("find_files", "Search the phone's storage for files whose name contains some text.",
            mapOf(
                "query" to ("string" to "Part of the file name."),
                "folder" to ("string" to "Optional folder to search in, e.g. Download or DCIM.")
            ), listOf("query")))
        put(d("list_folder", "List the files and folders in a folder (path relative to shared storage, e.g. Download).",
            mapOf("path" to ("string" to "Folder path. Empty for the top level.")), emptyList()))
        put(d("open_file", "Open a file in the right app.",
            mapOf("path" to ("string" to "File path.")), listOf("path")))
        put(d("create_folder", "Create a folder.",
            mapOf("path" to ("string" to "New folder path.")), listOf("path")))
        put(d("move_file", "Move or rename a file or folder. The user is asked to approve.",
            mapOf(
                "from" to ("string" to "Current path."),
                "to" to ("string" to "New path, or a destination folder.")
            ), listOf("from", "to")))
        put(d("delete_file", "Permanently delete a file or folder. The user is asked to approve.",
            mapOf("path" to ("string" to "Path to delete.")), listOf("path")))
    }

    private fun d(
        name: String,
        desc: String,
        props: Map<String, Pair<String, String>>,
        required: List<String>
    ): JSONObject {
        val p = JSONObject()
        props.forEach { (k, v) -> p.put(k, JSONObject().put("type", v.first).put("description", v.second)) }
        return JSONObject().put("name", name).put("description", desc).put(
            "input_schema",
            JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
        )
    }

    fun run(name: String, input: JSONObject): String? {
        if (name !in NAMES) return null
        if (!hasAccess()) return NO_ACCESS
        return when (name) {
            "find_files" -> find(input.getString("query"), input.optString("folder"))
            "list_folder" -> list(input.optString("path"))
            "open_file" -> open(input.getString("path"))
            "create_folder" -> {
                val f = resolve(input.getString("path"))
                if (f.mkdirs() || f.isDirectory) "Folder ready: ${rel(f)}" else "I couldn't create that folder."
            }
            "move_file" -> move(input.getString("from"), input.getString("to"))
            "delete_file" -> delete(input.getString("path"))
            else -> null
        }
    }

    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun resolve(p: String): File {
        val f = if (p.startsWith("/")) File(p) else File(root, p)
        val c = f.canonicalFile
        val base = root.canonicalPath
        require(c.path == base || c.path.startsWith("$base/")) { "That path is outside the phone's shared storage." }
        return c
    }

    private fun rel(f: File) = f.path.removePrefix(root.canonicalPath).trimStart('/').ifEmpty { "/" }

    private fun describe(f: File): String {
        val date = SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(f.lastModified()))
        return if (f.isDirectory) "[folder] ${rel(f)}" else "${rel(f)} (${size(f.length())}, $date)"
    }

    private fun size(b: Long): String = when {
        b > 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
        b > 1024 -> "%d KB".format(b / 1024)
        else -> "$b B"
    }

    private fun find(query: String, folder: String): String {
        val start = if (folder.isBlank()) root else resolve(folder)
        val deadline = System.currentTimeMillis() + 8_000
        val hits = ArrayList<File>()
        start.walkTopDown()
            .onEnter { !it.name.startsWith(".") && it.name != "Android" }
            .takeWhile { System.currentTimeMillis() <= deadline && hits.size < 20 }
            .forEach {
                if (it.isFile && it.name.contains(query, ignoreCase = true)) hits.add(it)
            }
        return if (hits.isEmpty()) "No files found matching \"$query\"." else hits.joinToString("\n") { describe(it) }
    }

    private fun list(path: String): String {
        val dir = if (path.isBlank()) root else resolve(path)
        if (!dir.isDirectory) return "That isn't a folder."
        val items = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        if (items.isEmpty()) return "The folder is empty."
        val shown = items.take(40).joinToString("\n") { describe(it) }
        return if (items.size > 40) "$shown\n…and ${items.size - 40} more." else shown
    }

    private fun open(path: String): String {
        val f = resolve(path)
        if (!f.isFile) return "I couldn't find that file."
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "Opening ${f.name}."
    }

    private fun move(from: String, to: String): String {
        val src = resolve(from)
        if (!src.exists()) return "I couldn't find $from."
        var dst = resolve(to)
        if (dst.isDirectory) dst = File(dst, src.name)
        if (!ask("Move ${rel(src)} to ${rel(dst)}?")) return "The user declined."
        dst.parentFile?.mkdirs()
        if (src.renameTo(dst)) return "Moved to ${rel(dst)}."
        if (src.isFile) {
            src.copyTo(dst, overwrite = false)
            src.delete()
            return "Moved to ${rel(dst)}."
        }
        return "I couldn't move that folder."
    }

    private fun delete(path: String): String {
        val f = resolve(path)
        if (f.path == root.canonicalPath) return "I won't delete the whole storage."
        if (!f.exists()) return "I couldn't find $path."
        val what = if (f.isDirectory) "the folder ${rel(f)} and everything inside it" else rel(f)
        if (!ask("Permanently delete $what?")) return "The user declined."
        return if (f.deleteRecursively()) "Deleted." else "I couldn't delete it."
    }

    companion object {
        private val NAMES = setOf("find_files", "list_folder", "open_file", "create_folder", "move_file", "delete_file")
        const val NO_ACCESS =
            "File access is not granted. Tell the user to open Settings in the app and tap 'Allow file access'."
    }
}

package com.dipesh.ai.studio

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TaskItem(val id: Long, val title: String, val done: Boolean = false, val due: String = "", val created: Long = System.currentTimeMillis())

object TaskCsv {
    /** Escapes a cell and neutralises spreadsheet formula injection (cells starting with = + - @). */
    private fun cell(s: String): String {
        var v = s.replace("\r", " ").replace("\n", " ")
        if (v.isNotEmpty() && v[0] in "=+-@") v = "'$v"
        return if (v.any { it == ',' || it == '"' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    fun toCsv(tasks: List<TaskItem>): String =
        "id,title,done,due,created\n" + tasks.joinToString("\n") { "${it.id},${cell(it.title)},${it.done},${cell(it.due)},${it.created}" } + "\n"
}

object TaskJson {
    fun toJson(tasks: List<TaskItem>): String {
        val a = JSONArray()
        tasks.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("done", it.done).put("due", it.due).put("created", it.created)) }
        return JSONObject().put("app", "DIPESH AI").put("version", 1).put("tasks", a).toString(2)
    }

    /** Throws on invalid input; used by backup restore. */
    fun fromJson(text: String): List<TaskItem> {
        val a = JSONObject(text).getJSONArray("tasks")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            TaskItem(o.getLong("id"), o.getString("title").take(300), o.optBoolean("done"), o.optString("due"), o.optLong("created"))
        }
    }
}

class TaskRepo(private val file: File) {
    fun load(): List<TaskItem> = try { if (file.exists()) TaskJson.fromJson(file.readText()) else emptyList() } catch (_: Exception) { emptyList() }
    fun save(tasks: List<TaskItem>) { file.parentFile?.mkdirs(); file.writeText(TaskJson.toJson(tasks)) }
}

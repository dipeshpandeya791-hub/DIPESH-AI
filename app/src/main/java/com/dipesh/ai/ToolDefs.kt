package com.dipesh.ai

import android.content.Context
import android.os.Build
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Builds one tool definition in the shape used by the schema (Claude style; Gemini derives from it). */
fun toolDef(
    name: String,
    desc: String,
    props: Map<String, Pair<String, String>> = emptyMap(),
    required: List<String> = emptyList()
): JSONObject {
    val p = JSONObject()
    props.forEach { (k, v) -> p.put(k, JSONObject().put("type", v.first).put("description", v.second)) }
    return JSONObject().put("name", name).put("description", desc).put(
        "input_schema",
        JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
    )
}

/** Where the agent keeps projects, pulled repos and generated files. */
object Workspace {
    fun hasAllFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    fun dir(ctx: Context): File {
        val d = if (hasAllFiles() && Build.VERSION.SDK_INT >= 23)
            File(Environment.getExternalStorageDirectory(), "DipeshAI/workspace")
        else File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "workspace")
        d.mkdirs()
        return d
    }

    /** Resolves a user/agent path: relative paths live in the workspace. */
    fun resolve(ctx: Context, path: String): File {
        val p = path.trim()
        return if (p.startsWith("/")) File(p) else File(dir(ctx), p)
    }

    fun inside(ctx: Context, f: File): Boolean =
        f.canonicalPath.startsWith(dir(ctx).canonicalPath)
}

package com.dipesh.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything DIPESH AI can *do*: phone control, info lookups, memory and notes. */
class Tools(context: Context, private val store: Store) {
    private val ctx = context.applicationContext

    /** Set by the UI: shows an Allow/Deny prompt and returns the user's answer (blocking). */
    var confirm: (String) -> Boolean = { false }
    private val phone = PhoneTools(context, store) { confirm(it) }
    private val files = FileTools(context) { confirm(it) }
    private val agent = AgentTools(context, store) { confirm(it) }

    // ---------- Tool definitions sent to Claude ----------
    fun schema(): JSONArray {
        val all = baseSchema()
        for (extra in listOf(phone.schema(), files.schema(), agent.schema())) {
            for (i in 0 until extra.length()) all.put(extra.getJSONObject(i))
        }
        return all
    }

    private fun baseSchema(): JSONArray = JSONArray().apply {
        put(def("get_weather", "Current weather and 3-day forecast for a city.",
            mapOf("city" to ("string" to "City name. Omit to use the user's saved city."))))
        put(def("get_news", "Latest news headlines, optionally about a topic.",
            mapOf("topic" to ("string" to "Topic to search for. Omit for top headlines."))))
        put(def("search_web", "Search the internet for current information and get an answer.",
            mapOf("query" to ("string" to "What to look up.")), listOf("query")))
        put(def("save_routine", "Save an automation routine: a named list of steps to run later on request.",
            mapOf(
                "name" to ("string" to "Routine name, e.g. good morning."),
                "steps" to ("string" to "The steps in plain language, separated by semicolons.")
            ), listOf("name", "steps")))
        put(def("run_routine", "Run a saved routine by name.",
            mapOf("name" to ("string" to "Routine name.")), listOf("name")))
        put(def("list_routines", "List saved routines."))
        put(def("delete_routine", "Delete a saved routine.",
            mapOf("name" to ("string" to "Routine name.")), listOf("name")))
        put(def("save_note", "Save a note for the user.",
            mapOf("text" to ("string" to "Note text.")), listOf("text")))
        put(def("list_notes", "Read back the user's saved notes."))
        put(def("remember_fact", "Remember a lasting fact about the user (name, preferences, people, etc.).",
            mapOf("fact" to ("string" to "The fact, as a short sentence.")), listOf("fact")))
        put(def("set_reminder", "Show a notification after a number of minutes.",
            mapOf(
                "minutes" to ("integer" to "Minutes from now."),
                "text" to ("string" to "What to remind the user about.")
            ), listOf("minutes", "text")))
        put(def("set_alarm", "Set a clock alarm on the phone.",
            mapOf(
                "hour" to ("integer" to "Hour, 0-23."),
                "minute" to ("integer" to "Minute, 0-59."),
                "label" to ("string" to "Alarm label.")
            ), listOf("hour", "minute")))
        put(def("open_app", "Open an installed app by its name.",
            mapOf("name" to ("string" to "App name, e.g. YouTube, Camera, Settings.")), listOf("name")))
        put(def("flashlight", "Turn the phone flashlight on or off.",
            mapOf("on" to ("boolean" to "true for on, false for off.")), listOf("on")))
        put(def("set_volume", "Set media volume as a percentage.",
            mapOf("percent" to ("integer" to "0 to 100.")), listOf("percent")))
        put(def("device_status", "Battery level and charging state."))
        put(def("web_search", "Open a Google search in the browser.",
            mapOf("query" to ("string" to "Search query.")), listOf("query")))
        put(def("dial", "Open the phone dialer with a number (does not place the call).",
            mapOf("number" to ("string" to "Phone number.")), listOf("number")))
    }

    /** Same tools in Gemini's functionDeclarations format. */
    fun geminiDeclarations(): JSONArray {
        val src = schema()
        val out = JSONArray()
        for (i in 0 until src.length()) {
            val tool = src.getJSONObject(i)
            val decl = JSONObject()
                .put("name", tool.getString("name"))
                .put("description", tool.getString("description"))
            val inputSchema = tool.getJSONObject("input_schema")
            val props = inputSchema.getJSONObject("properties")
            if (props.length() > 0) { // Gemini rejects empty parameter objects
                val params = JSONObject().put("type", "object").put("properties", props)
                val req = inputSchema.getJSONArray("required")
                if (req.length() > 0) params.put("required", req)
                decl.put("parameters", params)
            }
            out.put(decl)
        }
        return out
    }

    private fun def(
        name: String,
        desc: String,
        props: Map<String, Pair<String, String>> = emptyMap(),
        required: List<String> = emptyList()
    ): JSONObject {
        val p = JSONObject()
        props.forEach { (k, v) ->
            p.put(k, JSONObject().put("type", v.first).put("description", v.second))
        }
        return JSONObject()
            .put("name", name)
            .put("description", desc)
            .put(
                "input_schema",
                JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
            )
    }

    // ---------- Execute a tool call from Claude ----------
    fun run(name: String, input: JSONObject): String = try {
        when (name) {
            "get_weather" -> {
                val city = input.optString("city").ifBlank { store.city }
                if (city.isBlank()) "No city given. Ask the user which city." else WeatherService.report(city)
            }
            "search_web" -> SearchService.search(store, input.getString("query"))
            "save_routine" -> { store.saveRoutine(input.getString("name"), input.getString("steps")); "Routine saved." }
            "delete_routine" -> { store.deleteRoutine(input.getString("name")); "Routine deleted." }
            "list_routines" -> store.routines().let { r ->
                if (r.isEmpty()) "No routines saved yet." else r.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            }
            "run_routine" -> {
                val steps = store.routines()[input.getString("name").trim().lowercase()]
                if (steps == null) "There is no routine with that name."
                else "Routine steps: $steps. Carry out each step now using your tools, then briefly summarize what you did."
            }
            "get_news" -> NewsService.headlines(input.optString("topic"))
            "save_note" -> { store.addNote(input.getString("text")); "Note saved." }
            "list_notes" -> store.notes().let { n ->
                if (n.isEmpty()) "No notes yet." else n.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
            }
            "remember_fact" -> { store.addFact(input.getString("fact")); "Remembered." }
            "set_reminder" -> {
                val m = input.getInt("minutes").coerceIn(1, 60 * 24 * 7)
                ReminderReceiver.schedule(ctx, m, input.getString("text"))
                "Reminder set for $m minutes from now."
            }
            "set_alarm" -> setAlarm(input.getInt("hour"), input.getInt("minute"), input.optString("label"))
            "open_app" -> openApp(input.getString("name"))
            "flashlight" -> flashlight(input.getBoolean("on"))
            "set_volume" -> setVolume(input.getInt("percent"))
            "device_status" -> deviceStatus()
            "web_search" -> view("https://www.google.com/search?q=" + URLEncoder.encode(input.getString("query"), "UTF-8"))
                .let { "Opened search." }
            "dial" -> {
                val num = input.getString("number").filter { it.isDigit() || it == '+' }
                launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
                "Opened the dialer with $num."
            }
            else -> phone.run(name, input) ?: files.run(name, input) ?: agent.run(name, input) ?: "Unknown tool: $name"
        }
    } catch (e: Exception) {
        "Tool error: ${e.message ?: e.javaClass.simpleName}"
    }

    // ---------- Instant local commands (no API call needed) ----------
    fun local(text: String): String? {
        val t = text.lowercase(Locale.ROOT).trim().trimEnd('.', '?', '!')
        return when {
            Regex("what('s| is) the time|what time is it|current time").containsMatchIn(t) ->
                SimpleDateFormat("'It is' h:mm a.", Locale.US).format(Date())
            Regex("what('s| is) (the )?(date|day)( today)?|today'?s date").containsMatchIn(t) ->
                SimpleDateFormat("'Today is' EEEE, d MMMM yyyy.", Locale.US).format(Date())
            Regex("battery").containsMatchIn(t) && t.length < 40 -> deviceStatus()
            Regex("(turn )?(on|off) (the )?(flash ?light|torch)").find(t) != null ->
                flashlight(t.contains(" on "))
            else -> {
                val m = Regex("^(please )?open (the )?(.+)$").find(t)
                if (m != null) {
                    val r = openApp(m.groupValues[3])
                    if (r.startsWith("Opening")) r else null
                } else null
            }
        }
    }

    // ---------- Device actions ----------
    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private fun view(url: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0)
        val wanted = name.trim()
        val match = apps.firstOrNull { it.loadLabel(pm).toString().equals(wanted, true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(wanted, true) }
            ?: return "I couldn't find an app called $wanted."
        val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "I couldn't open ${match.loadLabel(pm)}."
        launch(launchIntent)
        return "Opening ${match.loadLabel(pm)}."
    }

    private fun flashlight(on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "This phone has no flashlight."
        cm.setTorchMode(id, on)
        return if (on) "Flashlight on." else "Flashlight off."
    }

    private fun setVolume(percent: Int): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val p = percent.coerceIn(0, 100)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, p * max / 100, AudioManager.FLAG_SHOW_UI)
        return "Volume set to $p percent."
    }

    private fun deviceStatus(): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "Battery status unavailable."
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0) level * 100 / scale else -1
        return "Battery is at $pct percent" + if (charging) " and charging." else "."
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            .putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "DIPESH AI" })
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        launch(i)
        return "Alarm set for %02d:%02d.".format(hour, minute)
    }
}

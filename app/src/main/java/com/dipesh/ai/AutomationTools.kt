package com.dipesh.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Device(val name: String, val type: String, val host: String, val port: Int, val user: String, val secret: String, val mac: String, val extra: String)

/**
 * Hardware & automation, all free/open source: smart TVs (Roku, Android TV/Fire TV via adb), smart plugs and lights
 * (Tasmota, Shelly, WLED, Home Assistant), IP camera feeds, your PC/Mac/Linux box (SSH through Termux, Wake-on-LAN),
 * real .xlsx spreadsheets and automatic typing into any app.
 */
class AutomationTools(context: Context, private val store: Store, private val ask: (String) -> Boolean) {
    private val ctx = context.applicationContext

    fun schema(): JSONArray = JSONArray().apply {
        put(toolDef("device_list", "List the saved smart devices, TVs, cameras and computers."))
        put(toolDef("device_add",
            "Save a device on the home network. type: roku, androidtv (Android TV / Fire TV via adb), tasmota, shelly, wled, homeassistant, ipcam, computer (SSH), http (generic).",
            mapOf(
                "name" to ("string" to "Short name, e.g. 'living room tv'."),
                "type" to ("string" to "roku | androidtv | tasmota | shelly | wled | homeassistant | ipcam | computer | http"),
                "host" to ("string" to "IP address or hostname on the local network."),
                "port" to ("integer" to "Optional port (defaults per type)."),
                "user" to ("string" to "Optional SSH user for computers."),
                "secret" to ("string" to "Optional name of a vault secret (e.g. the Home Assistant token)."),
                "mac" to ("string" to "Optional MAC address for Wake-on-LAN."),
                "extra" to ("string" to "Optional: camera snapshot path or stream URL, etc.")
            ), listOf("name", "type", "host")))
        put(toolDef("device_remove", "Forget a saved device.", mapOf("name" to ("string" to "Device name.")), listOf("name")))
        put(toolDef("device_control",
            "Control a saved device. roku: key (value=Home/Select/Up/Down/Left/Right/Back/Play/VolumeUp/VolumeDown/VolumeMute/PowerOff), launch (value=app name), apps. " +
                "androidtv: on, off, key (home/back/up/down/left/right/select/volume_up/volume_down/mute/play_pause), launch (value=package). " +
                "tasmota/shelly: on, off, toggle, status. wled: on, off, toggle, brightness (0-255), color (#RRGGBB), status. " +
                "homeassistant: call (value='light.turn_on entity_id=light.kitchen brightness=200'), state (value=entity id), entities. " +
                "ipcam: snapshot (saves a photo to the workspace), view (opens the live stream). " +
                "computer: run (value=shell command over SSH), wake (Wake-on-LAN), status. http: get/post (value=path, body=JSON).",
            mapOf(
                "name" to ("string" to "Device name."),
                "action" to ("string" to "Action as listed."),
                "value" to ("string" to "Optional value for the action."),
                "body" to ("string" to "Optional request body (http devices).")
            ), listOf("name", "action")))
        put(toolDef("wake_on_lan", "Wake a computer or TV with a Wake-on-LAN magic packet (needs its MAC address).",
            mapOf("mac_or_name" to ("string" to "MAC address like AA:BB:CC:DD:EE:FF, or a saved device name.")), listOf("mac_or_name")))
        put(toolDef("create_spreadsheet",
            "Create a real Excel .xlsx file in the workspace from rows (formulas like =SUM(B2:B9) work). Free, no Excel needed to create it.",
            mapOf(
                "path" to ("string" to "File name, e.g. budget.xlsx"),
                "rows" to ("string" to "JSON array of rows, e.g. [[\"Item\",\"Cost\"],[\"Tea\",5]] or CSV text."),
                "sheet_name" to ("string" to "Optional sheet name."),
                "header" to ("boolean" to "Make the first row bold (default true).")
            ), listOf("path", "rows")))
        put(toolDef("type_lines",
            "Automatically type several lines into the focused input field of the app on screen (chat messages, notes, Excel cell box), pressing Enter/Send after each. The user is asked to approve first and must have focused the field. Max 50 lines.",
            mapOf(
                "lines" to ("string" to "JSON array of strings to type, one per entry."),
                "submit" to ("string" to "enter (default), send_button or none."),
                "delay_ms" to ("integer" to "Pause between lines, 300-5000 (default 800).")
            ), listOf("lines")))
    }

    val names: Set<String> by lazy {
        val a = schema(); (0 until a.length()).map { a.getJSONObject(it).getString("name") }.toSet()
    }

    // ---------- saved devices ----------
    private fun devices(): List<Device> = try {
        val a = JSONArray(store.settings.value.devices)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Device(o.getString("name"), o.getString("type"), o.getString("host"), o.optInt("port", 0), o.optString("user"),
                o.optString("secret"), o.optString("mac"), o.optString("extra"))
        }
    } catch (_: Exception) { emptyList() }

    private fun saveDevices(list: List<Device>) {
        val a = JSONArray()
        list.forEach { d ->
            a.put(JSONObject().put("name", d.name).put("type", d.type).put("host", d.host).put("port", d.port)
                .put("user", d.user).put("secret", d.secret).put("mac", d.mac).put("extra", d.extra))
        }
        store.update { it.copy(devices = a.toString()) }
    }

    private fun find(name: String): Device? =
        devices().firstOrNull { it.name.equals(name.trim(), true) } ?: devices().firstOrNull { it.name.contains(name.trim(), true) }

    private fun secret(name: String): String =
        store.settings.value.secrets.lineSequence().firstOrNull { it.substringBefore('=').trim() == name }
            ?.substringAfter('=')?.trim().orEmpty()

    fun run(name: String, input: JSONObject): String? {
        if (name !in names) return null
        return when (name) {
            "device_list" -> devices().let { l ->
                if (l.isEmpty()) "No devices saved yet. Ask the user for the device type and its IP address, then call device_add."
                else l.joinToString("\n") { "${it.name}: ${it.type} at ${it.host}" }
            }
            "device_add" -> {
                val type = input.getString("type").lowercase().trim()
                val types = setOf("roku", "androidtv", "tasmota", "shelly", "wled", "homeassistant", "ipcam", "computer", "http")
                if (type !in types) "Unknown type '$type'. Use one of: ${types.joinToString()}."
                else {
                    val d = Device(input.getString("name").trim(), type, input.getString("host").trim(), input.optInt("port", 0),
                        input.optString("user"), input.optString("secret"), input.optString("mac"), input.optString("extra"))
                    saveDevices(devices().filterNot { it.name.equals(d.name, true) } + d)
                    "Saved ${d.name} (${d.type}) at ${d.host}."
                }
            }
            "device_remove" -> {
                val n = input.getString("name")
                if (devices().none { it.name.equals(n, true) }) "No device named $n."
                else { saveDevices(devices().filterNot { it.name.equals(n, true) }); "Removed $n." }
            }
            "device_control" -> {
                val d = find(input.getString("name")) ?: return "No saved device called '${input.getString("name")}'. Use device_list, or device_add to save it."
                try { control(d, input.getString("action").lowercase().trim(), input.optString("value"), input.optString("body")) }
                catch (e: java.net.SocketTimeoutException) { "${d.name} did not answer. Check it is on, on the same Wi-Fi, and the IP ${d.host} is still correct (routers can change it)." }
                catch (e: java.net.ConnectException) { "Couldn't connect to ${d.name} at ${d.host}. The IP may have changed, the device is off, or the port is wrong." }
                catch (e: Exception) { "Device error: ${e.message}" }
            }
            "wake_on_lan" -> {
                val arg = input.getString("mac_or_name")
                val mac = find(arg)?.mac?.takeIf { it.isNotBlank() } ?: arg
                wol(mac)
            }
            "create_spreadsheet" -> {
                val raw = input.getString("path").trim()
                val rel = if (raw.endsWith(".xlsx", true)) raw else "$raw.xlsx"
                val f = Workspace.resolve(ctx, rel)
                if (!Workspace.inside(ctx, f) && !ask("Save the spreadsheet to ${f.absolutePath}?")) "The user declined."
                else try {
                    val rows = Xlsx.parseRows(input.getString("rows"))
                    if (rows.isEmpty()) "There were no rows to write."
                    else {
                        Xlsx.write(f, rows, input.optString("sheet_name").ifBlank { "Sheet1" }, if (input.has("header")) input.optBoolean("header", true) else true)
                        "Spreadsheet saved: ${f.absolutePath} (${rows.size} rows). Open it with open_file or share it."
                    }
                } catch (e: Exception) { "I couldn't build the spreadsheet (${e.message}). Send rows as a JSON array of arrays or as CSV." }
            }
            "type_lines" -> typeLines(input)
            else -> null
        }
    }

    // ---------- device actions ----------
    private fun lan(d: Device, method: String, path: String, port: Int, body: String? = null, headers: Map<String, String> = emptyMap()) =
        LanHttp.request(method, d.host, if (d.port > 0) d.port else port, path, headers, body)

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun control(d: Device, a: String, v: String, body: String): String = when (d.type) {
        "roku" -> when (a) {
            "key" -> lan(d, "POST", "/keypress/${q(v.ifBlank { "Home" })}", 8060).let { if (it.code in 200..204) "Sent ${v.ifBlank { "Home" }} to ${d.name}." else "Roku answered ${it.code}." }
            "apps" -> rokuApps(d).joinToString("\n") { it.second }.ifBlank { "No apps listed." }
            "launch" -> {
                val id = v.takeIf { it.all(Char::isDigit) && it.isNotEmpty() } ?: rokuApps(d).firstOrNull { it.second.contains(v, true) }?.first
                if (id == null) "I couldn't find an app called '$v' on ${d.name}. Use action apps to list them."
                else lan(d, "POST", "/launch/$id", 8060).let { if (it.code in 200..204) "Launched $v on ${d.name}." else "Roku answered ${it.code}." }
            }
            "on" -> control(d, "key", "PowerOn", "")
            "off" -> control(d, "key", "PowerOff", "")
            else -> "Roku supports: key, launch, apps, on, off."
        }
        "tasmota" -> {
            val cmd = when (a) { "on" -> "Power On"; "off" -> "Power Off"; "toggle" -> "Power Toggle"; "status" -> "Status 0"; else -> v }
            if (cmd.isBlank()) "Tasmota supports on, off, toggle, status." else lan(d, "GET", "/cm?cmnd=${q(cmd)}", 80).text.take(1500)
        }
        "shelly" -> when (a) {
            "on", "off", "toggle" -> {
                val g2 = lan(d, "GET", "/rpc/Switch.${if (a == "toggle") "Toggle?id=0" else "Set?id=0&on=${a == "on"}"}", 80)
                if (g2.code == 404) lan(d, "GET", "/relay/0?turn=$a", 80).text.take(800) else g2.text.take(800)
            }
            "status" -> lan(d, "GET", "/rpc/Shelly.GetStatus", 80).let { if (it.code == 404) lan(d, "GET", "/status", 80).text else it.text }.take(1500)
            else -> "Shelly supports on, off, toggle, status."
        }
        "wled" -> when (a) {
            "on" -> lan(d, "POST", "/json/state", 80, """{"on":true}""").text
            "off" -> lan(d, "POST", "/json/state", 80, """{"on":false}""").text
            "toggle" -> lan(d, "POST", "/json/state", 80, """{"on":"t"}""").text
            "brightness" -> lan(d, "POST", "/json/state", 80, """{"on":true,"bri":${v.toIntOrNull()?.coerceIn(0, 255) ?: 128}}""").text
            "color" -> {
                val h = v.trim().removePrefix("#")
                if (h.length != 6 || h.toLongOrNull(16) == null) "Give the colour as #RRGGBB."
                else lan(d, "POST", "/json/state", 80, """{"on":true,"seg":[{"col":[[${h.substring(0, 2).toInt(16)},${h.substring(2, 4).toInt(16)},${h.substring(4, 6).toInt(16)}]]}]}""").text
            }
            "status" -> lan(d, "GET", "/json/state", 80).text.take(1500)
            else -> "WLED supports on, off, toggle, brightness, color, status."
        }.take(600)
        "homeassistant" -> {
            val tok = secret(d.secret)
            if (tok.isBlank()) "Home Assistant needs a long-lived access token saved in Settings > Developer agent > Secrets (NAME=token), and the device's secret field set to NAME."
            else {
                val h = mapOf("Authorization" to "Bearer $tok")
                when (a) {
                    "call" -> {
                        val parts = v.trim().split(Regex("\\s+"))
                        val svc = parts.firstOrNull().orEmpty()
                        if (!svc.contains('.')) "Use value like 'light.turn_on entity_id=light.kitchen'."
                        else {
                            val o = JSONObject()
                            parts.drop(1).forEach { kv ->
                                val i = kv.indexOf('='); if (i > 0) { val x = kv.substring(i + 1); o.put(kv.substring(0, i), x.toDoubleOrNull()?.let { n -> if (n % 1.0 == 0.0) n.toLong() else n } ?: x) }
                            }
                            val r = lan(d, "POST", "/api/services/${svc.substringBefore('.')}/${svc.substringAfter('.')}", 8123, o.toString(), h)
                            if (r.code in 200..299) "Done: $svc." else "Home Assistant answered ${r.code}: ${r.text.take(200)}"
                        }
                    }
                    "state" -> lan(d, "GET", "/api/states/${v.trim()}", 8123, null, h).text.take(1200)
                    "entities" -> JSONArray(lan(d, "GET", "/api/states", 8123, null, h).text).let { arr ->
                        (0 until minOf(arr.length(), 60)).joinToString("\n") { arr.getJSONObject(it).let { o -> o.getString("entity_id") + " = " + o.getString("state") } }
                    }
                    else -> "Home Assistant supports call, state, entities."
                }
            }
        }
        "androidtv" -> adb(d, a, v)
        "ipcam" -> camera(d, a)
        "computer" -> computer(d, a, v)
        else -> { // http
            val m = if (a == "post") "POST" else "GET"
            val r = lan(d, m, v.ifBlank { "/" }, 80, body.ifBlank { null })
            "HTTP ${r.code}\n" + r.text.take(2000)
        }
    }

    private fun rokuApps(d: Device): List<Pair<String, String>> =
        Regex("<app id=\"(\\d+)\"[^>]*>([^<]+)</app>").findAll(lan(d, "GET", "/query/apps", 8060).text).map { it.groupValues[1] to it.groupValues[2] }.toList()

    private fun adb(d: Device, a: String, v: String): String {
        val keys = mapOf("home" to "KEYCODE_HOME", "back" to "KEYCODE_BACK", "up" to "KEYCODE_DPAD_UP", "down" to "KEYCODE_DPAD_DOWN",
            "left" to "KEYCODE_DPAD_LEFT", "right" to "KEYCODE_DPAD_RIGHT", "select" to "KEYCODE_DPAD_CENTER", "volume_up" to "KEYCODE_VOLUME_UP",
            "volume_down" to "KEYCODE_VOLUME_DOWN", "mute" to "KEYCODE_VOLUME_MUTE", "play_pause" to "KEYCODE_MEDIA_PLAY_PAUSE", "menu" to "KEYCODE_MENU")
        val target = "${d.host}:${if (d.port > 0) d.port else 5555}"
        val sh = when (a) {
            "on" -> "input keyevent KEYCODE_WAKEUP"
            "off" -> "input keyevent KEYCODE_SLEEP"
            "key" -> "input keyevent " + (keys[v.lowercase()] ?: return "Unknown key. Use: ${keys.keys.joinToString()}.")
            "launch" -> "monkey -p ${v.filter { it.isLetterOrDigit() || it == '.' || it == '_' }} -c android.intent.category.LAUNCHER 1"
            else -> return "Android TV supports on, off, key, launch."
        }
        if (!ask("Send '$a ${v}' to ${d.name} using adb over Wi-Fi (through Termux)?")) return "The user declined."
        LanHttp.resolveLocal(d.host)
        val cmd = "command -v adb >/dev/null || { echo 'adb missing: run pkg install -y android-tools'; exit 127; }; " +
            "adb connect $target >/dev/null 2>&1; adb -s $target shell $sh"
        return TermuxRunner.run(ctx, cmd, "", "termux", 30) +
            "\n(Note: enable 'Network debugging / ADB debugging' in the TV's developer options and accept the pairing prompt once.)"
    }

    private fun camera(d: Device, a: String): String {
        val path = d.extra.ifBlank { "/snapshot.jpg" }
        return when (a) {
            "snapshot" -> {
                val r = if (path.startsWith("http")) {
                    val u = Uri.parse(path)
                    LanHttp.request("GET", u.host ?: d.host, if (u.port > 0) u.port else 80, (u.encodedPath ?: "/") + (u.encodedQuery?.let { "?$it" } ?: ""))
                } else lan(d, "GET", path, 80)
                if (r.code != 200 || r.body.size < 100) "The camera answered ${r.code}. Set the snapshot path in the device's extra field (common: /snapshot.jpg, /image.jpg, /cgi-bin/snapshot.cgi)."
                else {
                    val f = Workspace.resolve(ctx, "camera/${d.name.filter { it.isLetterOrDigit() }}-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.jpg")
                    f.parentFile?.mkdirs(); f.writeBytes(r.body)
                    "Snapshot saved to ${f.absolutePath}."
                }
            }
            "view" -> {
                val url = if (path.startsWith("http")) path else "http://${d.host}:${if (d.port > 0) d.port else 80}$path"
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opened the camera feed."
            }
            else -> "Cameras support snapshot and view."
        }
    }

    private fun computer(d: Device, a: String, v: String): String = when (a) {
        "wake" -> wol(d.mac)
        "run", "status" -> {
            val cmd = if (a == "status") "echo online; uname -a 2>/dev/null || ver" else v
            if (cmd.isBlank()) "Give the command to run in value."
            else if (!ask("Run on ${d.name} (${d.host})?\n\n${cmd.take(400)}")) "The user declined."
            else {
                LanHttp.resolveLocal(d.host)
                val user = d.user.ifBlank { "root" }
                val port = if (d.port > 0) d.port else 22
                val ssh = "command -v ssh >/dev/null || { echo 'ssh missing: run pkg install -y openssh'; exit 127; }; " +
                    "ssh -p $port -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=8 $user@${d.host} '" + cmd.replace("'", "'\\''") + "'"
                TermuxRunner.run(ctx, ssh, "", "termux", 60).let {
                    if (it.contains("Permission denied (publickey")) it + "\nDiagnosis: set up key login once in Termux: ssh-keygen -t ed25519, then ssh-copy-id $user@${d.host}."
                    else it
                }
            }
        }
        else -> "Computers support run, status, wake."
    }

    private fun wol(macText: String): String {
        val hex = macText.filter { it.isLetterOrDigit() }
        if (hex.length != 12 || hex.toLongOrNull(16) == null) return "That is not a valid MAC address. Use the form AA:BB:CC:DD:EE:FF (find it in the device's network settings)."
        val mac = ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val data = ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { mac[it % 6] }
        DatagramSocket().use { s ->
            s.broadcast = true
            for (port in intArrayOf(9, 7)) s.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), port))
        }
        return "Wake-on-LAN packet sent to $macText. It works only if Wake-on-LAN is enabled in the device's BIOS/settings and it is on the same Wi-Fi/network."
    }

    // ---------- automatic typing ----------
    private fun typeLines(input: JSONObject): String {
        val svc = PhoneControlService.instance ?: return PhoneTools.NO_SCREEN_CONTROL
        val arr = try { JSONArray(input.getString("lines")) } catch (_: Exception) { return "Send lines as a JSON array of strings." }
        val lines = (0 until arr.length()).map { arr.get(it).toString() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return "There was nothing to type."
        if (lines.size > 50) return "That's more than 50 lines. Split it into smaller batches."
        val mode = input.optString("submit", "enter").lowercase().let { if (it in setOf("enter", "send_button", "none")) it else "enter" }
        val delay = input.optLong("delay_ms", 800).coerceIn(300, 5000)
        val preview = lines.take(3).joinToString(" | ") { it.take(40) }
        if (!ask("Type ${lines.size} line(s) into the app on screen${if (mode != "none") " and press ${if (mode == "enter") "Enter" else "Send"} after each" else ""}?\n\n$preview${if (lines.size > 3) " …" else ""}")) return "The user declined."
        var done = 0
        for (line in lines) {
            if (!svc.typeText(line)) return "Typed $done of ${lines.size} lines, then no text field was focused. Ask the user to tap the field and try again (spreadsheet grids aren't normal fields; use create_spreadsheet for Excel data instead)."
            Thread.sleep(250)
            if (!svc.submit(mode)) return "Typed $done of ${lines.size}; couldn't press Enter/Send for line ${done + 1}."
            done++
            Thread.sleep(delay)
        }
        return "Typed $done line(s)."
    }
}

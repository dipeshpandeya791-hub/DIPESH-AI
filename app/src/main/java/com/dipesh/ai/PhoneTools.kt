package com.dipesh.ai

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Deep phone access: contacts, calls, SMS, calendar, location, media and screen control. */
class PhoneTools(context: Context, private val store: Store, private val ask: (String) -> Boolean) {
    private val ctx = context.applicationContext

    fun schema(): JSONArray = JSONArray().apply {
        put(d("find_contact", "Look up a contact's phone number by name.",
            mapOf("name" to ("string" to "Contact name or part of it.")), listOf("name")))
        put(d("make_call", "Place a phone call to a contact name or number. The user is asked to approve.",
            mapOf("target" to ("string" to "Contact name or phone number.")), listOf("target")))
        put(d("send_sms", "Send a text message to a contact name or number. The user is asked to approve.",
            mapOf(
                "target" to ("string" to "Contact name or phone number."),
                "text" to ("string" to "Message text.")
            ), listOf("target", "text")))
        put(d("read_calendar", "List upcoming calendar events.",
            mapOf("days" to ("integer" to "How many days ahead, default 3."))))
        put(d("add_calendar_event", "Open a pre-filled new calendar event for the user to save.",
            mapOf(
                "title" to ("string" to "Event title."),
                "start" to ("string" to "Start time as yyyy-MM-dd HH:mm (24h, local time).")
            ), listOf("title", "start")))
        put(d("whatsapp_message", "Open WhatsApp with a message to a contact ready to send. Set send=true to also press Send (needs screen control).",
            mapOf(
                "target" to ("string" to "Contact name or phone number with country code."),
                "text" to ("string" to "Message text."),
                "send" to ("boolean" to "true to press Send automatically; the user is asked to approve.")
            ), listOf("target", "text")))
        put(d("compose_sms", "Open the SMS app with a message ready to send (no permission needed).",
            mapOf(
                "target" to ("string" to "Contact name or phone number."),
                "text" to ("string" to "Message text.")
            ), listOf("target", "text")))
        put(d("compose_email", "Open the email app with a draft ready.",
            mapOf(
                "to" to ("string" to "Email address (optional)."),
                "subject" to ("string" to "Subject."),
                "text" to ("string" to "Body text.")
            ), listOf("text")))
        put(d("share_text", "Open Android's share sheet so the user can send text via any app.",
            mapOf("text" to ("string" to "Text to share.")), listOf("text")))
        put(d("read_notifications", "Read the user's current notifications (needs notification access; the user is asked to approve).",
            emptyMap()))
        put(d("clear_notifications", "Dismiss all clearable notifications (needs notification access; the user is asked to approve).",
            emptyMap()))
        put(d("get_location", "Get the phone's last known location and address.", emptyMap()))
        put(d("navigate", "Start map navigation or search to a place.",
            mapOf("destination" to ("string" to "Place or address.")), listOf("destination")))
        put(d("open_camera", "Open the camera to take a photo.", emptyMap()))
        put(d("media_control", "Control music/video playback.",
            mapOf("action" to ("string" to "One of: play_pause, next, previous.")), listOf("action")))
        put(d("open_settings", "Open a phone settings page, e.g. to switch Wi-Fi or Bluetooth.",
            mapOf("page" to ("string" to "One of: wifi, bluetooth, display, sound, battery, apps, location, airplane, accessibility, main.")),
            listOf("page")))
        put(d("read_screen", "Read the text currently on the phone screen (needs screen control enabled).",
            emptyMap()))
        put(d("tap_text", "Tap a button or item on screen by its visible text (needs screen control).",
            mapOf("text" to ("string" to "Visible text of the item to tap.")), listOf("text")))
        put(d("type_text", "Type text into the currently focused input field (needs screen control).",
            mapOf("text" to ("string" to "Text to enter.")), listOf("text")))
        put(d("scroll_screen", "Scroll the current screen (needs screen control).",
            mapOf("direction" to ("string" to "down or up.")), listOf("direction")))
        put(d("press_button", "Press a system button (needs screen control).",
            mapOf("button" to ("string" to "One of: back, home, recents, notifications, quick_settings, lock_screen, screenshot.")),
            listOf("button")))
    }

    private fun d(
        name: String,
        desc: String,
        props: Map<String, Pair<String, String>>,
        required: List<String> = emptyList()
    ): JSONObject {
        val p = JSONObject()
        props.forEach { (k, v) -> p.put(k, JSONObject().put("type", v.first).put("description", v.second)) }
        return JSONObject().put("name", name).put("description", desc).put(
            "input_schema",
            JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
        )
    }

    /** Returns null if [name] is not one of these tools. */
    fun run(name: String, input: JSONObject): String? = when (name) {
        "find_contact" -> {
            val hits = findContacts(input.getString("name"))
            if (hits.isEmpty()) "No contact found." else hits.take(5).joinToString("\n") { "${it.first}: ${it.second}" }
        }
        "make_call" -> makeCall(input.getString("target"))
        "send_sms" -> sendSms(input.getString("target"), input.getString("text"))
        "read_calendar" -> readCalendar(input.optInt("days", 3).coerceIn(1, 30))
        "add_calendar_event" -> addEvent(input.getString("title"), input.getString("start"))
        "whatsapp_message" -> whatsapp(input.getString("target"), input.getString("text"), input.optBoolean("send", false))
        "compose_sms" -> composeSms(input.getString("target"), input.getString("text"))
        "compose_email" -> composeEmail(input.optString("to"), input.optString("subject"), input.getString("text"))
        "share_text" -> {
            start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, input.getString("text")), null))
            "Opened the share sheet."
        }
        "read_notifications" -> readNotifications()
        "clear_notifications" -> clearNotifications()
        "get_location" -> location()
        "navigate" -> navigate(input.getString("destination"))
        "open_camera" -> { start(Intent(MediaStore.ACTION_IMAGE_CAPTURE)); "Camera opened." }
        "media_control" -> media(input.getString("action"))
        "open_settings" -> openSettings(input.getString("page"))
        "read_screen" -> screen("read what is on your screen and share it with the AI") { it.readScreen() }
        "tap_text" -> input.getString("text").let { t ->
            screen("tap \"$t\" on your screen") { if (it.tapText(t)) "Tapped $t." else "I couldn't find $t on screen." }
        }
        "type_text" -> input.getString("text").let { t ->
            screen("type \"$t\" into the selected field") { if (it.typeText(t)) "Typed it." else "No text field is selected." }
        }
        "scroll_screen" -> screen("scroll the screen") {
            if (it.scroll(input.getString("direction") != "up")) "Scrolled." else "Nothing to scroll."
        }
        "press_button" -> {
            val svc = PhoneControlService.instance
            if (svc == null) NO_SCREEN_CONTROL
            else if (svc.press(input.getString("button"))) "Done." else "That button isn't available."
        }
        else -> null
    }

    // ---------- helpers ----------
    private fun need(permission: String, what: String) {
        if (ContextCompat.checkSelfPermission(ctx, permission) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException(
                "Permission for $what is not granted. Tell the user to open Settings in the app and tap 'Grant phone permissions'."
            )
        }
    }

    private fun start(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private fun screen(action: String, block: (PhoneControlService) -> String): String {
        val svc = PhoneControlService.instance ?: return NO_SCREEN_CONTROL
        if (!store.autoApprove && !ask("Allow DIPESH AI to $action?")) return "The user declined."
        return block(svc)
    }

    private fun findContacts(name: String): List<Pair<String, String>> {
        need(Manifest.permission.READ_CONTACTS, "contacts")
        val out = ArrayList<Pair<String, String>>()
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use { while (it.moveToNext()) out.add((it.getString(0) ?: "") to (it.getString(1) ?: "")) }
        return out
    }

    /** Returns (label, number) or an error string as the second element of a null-label pair. */
    private fun resolve(target: String): Pair<String, String>? {
        val digits = target.filter { it.isDigit() || it == '+' }
        if (digits.length >= 5 && digits.length >= target.count { it.isLetter() } + 5) return digits to digits
        return findContacts(target).firstOrNull()
    }

    private fun makeCall(target: String): String {
        need(Manifest.permission.CALL_PHONE, "phone calls")
        val (label, number) = resolve(target) ?: return "I couldn't find $target in your contacts."
        if (!ask("Call $label ($number)?")) return "The user declined."
        start(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        return "Calling $label."
    }

    private fun sendSms(target: String, text: String): String {
        need(Manifest.permission.SEND_SMS, "text messages")
        val (label, number) = resolve(target) ?: return "I couldn't find $target in your contacts."
        if (!ask("Send this text to $label ($number)?\n\n\"$text\"")) return "The user declined."
        @Suppress("DEPRECATION")
        val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
        return "Message sent to $label."
    }

    private fun whatsapp(target: String, text: String, send: Boolean): String {
        val (label, number) = resolve(target) ?: return "I couldn't find $target in your contacts."
        val digits = number.filter { it.isDigit() }
        if (digits.length < 8) return "That number looks too short for WhatsApp. I need it with the country code."
        if (send && PhoneControlService.instance != null &&
            !ask("Send this WhatsApp message to $label?\n\n\"$text\"")
        ) return "The user declined."
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + URLEncoder.encode(text, "UTF-8")))
        if (ctx.packageManager.getLaunchIntentForPackage("com.whatsapp") != null) i.setPackage("com.whatsapp")
        start(i)
        if (!send) return "WhatsApp is open with your message to $label ready. Tap Send to send it."
        val svc = PhoneControlService.instance
            ?: return "WhatsApp is open with the message ready. Turn on screen control so I can press Send, or tap it yourself."
        repeat(10) {
            Thread.sleep(700)
            if (svc.tapText("Send")) return "Message sent to $label."
        }
        return "I opened the chat but couldn't find the Send button. Please tap it."
    }

    private fun composeSms(target: String, text: String): String {
        val number = resolve(target)?.second ?: return "I couldn't find $target in your contacts."
        start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", text))
        return "The message app is open with your text ready. Tap Send to send it."
    }

    private fun composeEmail(to: String, subject: String, text: String): String {
        start(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + to.trim()))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, text)
        )
        return "Your email app is open with the draft ready."
    }

    private fun readNotifications(): String {
        NotificationService.instance ?: return NO_NOTIFICATIONS
        if (!store.autoApprove && !ask("Allow DIPESH AI to read your current notifications and share them with the AI?")) return "The user declined."
        val items = NotificationService.recent.value
        if (items.isEmpty()) return "There are no notifications right now."
        return items.take(15).joinToString("\n") { "${it.app}: ${it.title} ${it.text}".trim() }
    }

    private fun clearNotifications(): String {
        val svc = NotificationService.instance ?: return NO_NOTIFICATIONS
        if (!ask("Dismiss all your notifications?")) return "The user declined."
        svc.cancelAllNotifications()
        return "Notifications cleared."
    }

    private fun readCalendar(days: Int): String {
        need(Manifest.permission.READ_CALENDAR, "the calendar")
        val now = System.currentTimeMillis()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now)
        ContentUris.appendId(b, now + days * 86_400_000L)
        val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.US)
        val lines = ArrayList<String>()
        ctx.contentResolver.query(
            b.build(),
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
            null, null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use {
            while (it.moveToNext() && lines.size < 15) {
                lines.add("${fmt.format(Date(it.getLong(1)))}: ${it.getString(0) ?: "(no title)"}")
            }
        }
        return if (lines.isEmpty()) "No events in the next $days days." else lines.joinToString("\n")
    }

    private fun addEvent(title: String, startTime: String): String {
        val millis = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(startTime)?.time
            ?: return "I couldn't understand the time $startTime."
        start(
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, millis + 3_600_000L)
        )
        return "Opened a new event for you to save."
    }

    private fun location(): String {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) need(Manifest.permission.ACCESS_FINE_LOCATION, "location")
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        @Suppress("MissingPermission")
        val best = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            ?: return "I can't get a location fix. Make sure location is switched on."
        var place = ""
        try {
            @Suppress("DEPRECATION")
            val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(best.latitude, best.longitude, 1)?.firstOrNull()
            if (a != null) place = a.getAddressLine(0) ?: ""
        } catch (_: Exception) {
        }
        return "Location: %.5f, %.5f (accuracy %d m). %s".format(best.latitude, best.longitude, best.accuracy.toInt(), place).trim()
    }

    private fun navigate(dest: String): String {
        val q = URLEncoder.encode(dest, "UTF-8")
        try {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")))
        } catch (_: ActivityNotFoundException) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
        }
        return "Navigating to $dest."
    }

    private fun media(action: String): String {
        val code = when (action) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "Unknown media action."
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "Done."
    }

    private fun openSettings(page: String): String {
        val action = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        start(Intent(action))
        return "Opened $page settings. Android doesn't let apps flip this switch directly, so tap it there" +
            (if (PhoneControlService.instance != null) " or ask me to tap it." else ".")
    }

    companion object {
        const val NO_NOTIFICATIONS =
            "Notification access is not enabled. Tell the user to open the Control tab and tap 'Turn on notification access'."
        const val NO_SCREEN_CONTROL =
            "Screen control is not enabled. Tell the user to open Settings in the app and tap 'Enable screen control', then turn on DIPESH AI in Accessibility."
    }
}

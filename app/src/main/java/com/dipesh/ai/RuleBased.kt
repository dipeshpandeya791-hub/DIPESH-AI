package com.dipesh.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tiny offline fallback so the assistant still answers a few things when no AI key is set or the AI is unreachable. */
object RuleBased {
    sealed class Intent {
        object Time : Intent()
        object Date : Intent()
        object Greeting : Intent()
        object ShowFeatures : Intent()
        object HideFeatures : Intent()
        class OpenApp(val name: String) : Intent()
        class Flashlight(val on: Boolean) : Intent()
    }

    fun parse(text: String): Intent? {
        val t = text.trim().lowercase().trimEnd('.', '!', '?')
        if (t.isEmpty()) return null
        if (Regex("(show|display|open|list|reveal|expand).{0,20}(features|capabilities|abilities)|what can you do|what are your (features|capabilities)|show everything|features dikhao|फीचर्स? (दिखाओ|दिखा)|सुविधा(हरू)? देखाउ|क्षमता").containsMatchIn(t)) return Intent.ShowFeatures
        if (Regex("(hide|close|dismiss|exit|collapse).{0,20}(features|capabilities|abilities|overlay|panel|display|hologram)|^(close|hide) (it|this)$|^(close|hide)$").containsMatchIn(t)) return Intent.HideFeatures
        Regex("^(?:please )?(?:open|launch|start) (.+)$").find(t)?.let { return Intent.OpenApp(it.groupValues[1].trim()) }
        if (Regex("flashlight|torch").containsMatchIn(t))
            return Intent.Flashlight(!Regex("\\b(off|disable|close)\\b").containsMatchIn(t))
        if (Regex("what(?:'s| is)? the time|what time|current time|^time$").containsMatchIn(t)) return Intent.Time
        if (Regex("what(?:'s| is)? (?:the )?(?:date|day)|today'?s date|^date$").containsMatchIn(t)) return Intent.Date
        if (Regex("^(hi|hello|hey|namaste|namaskar)\\b").containsMatchIn(t)) return Intent.Greeting
        return null
    }

    fun timeText(now: Date = Date()): String = "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(now) + "."
    fun dateText(now: Date = Date()): String = "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now) + "."
}

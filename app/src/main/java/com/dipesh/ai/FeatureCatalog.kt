package com.dipesh.ai

data class Feature(val category: String, val icon: String, val title: String, val desc: String, val route: String? = null)

/** Everything the app can do, grouped for the holographic features overlay. Routes open the matching screen. */
object FeatureCatalog {
    val routes = setOf("chat", "control", "settings", "camera", "studio/agent", "studio/website", "studio/video", "studio/videomaker",
        "studio/api", "studio/tasks", "studio/offline", "studio/library")

    val all = listOf(
        Feature("Talk & listen", "🎙", "Voice chat", "Talk hands-free; say \"Hey Dipesh\" while the mic is on", "chat"),
        Feature("Talk & listen", "🌍", "Languages & voices", "Any language your phone's speech engine has, with voices per situation", "settings"),
        Feature("See & understand", "📷", "Camera vision", "Point the camera and ask what you see", "camera"),
        Feature("See & understand", "🖥", "Screen reading", "Understand what is on your screen (with your consent)", "control"),
        Feature("Control the phone", "📱", "Apps & settings", "Open apps, flashlight, volume, alarms", "control"),
        Feature("Control the phone", "📞", "Calls & messages", "Call, text and WhatsApp after you approve", "control"),
        Feature("Control the phone", "📂", "Files", "Find, open and organise files", "control"),
        Feature("Control the phone", "⌨", "Auto-typing", "Type lines into the app on screen after you approve", "chat"),
        Feature("Build & code", "🤖", "Agent Builder", "Generate a Python agent project with safety built in", "studio/agent"),
        Feature("Build & code", "🌐", "Website Studio", "HTML, CSS and JavaScript sites, exported as ZIP", "studio/website"),
        Feature("Build & code", "🎬", "Video templates", "Remotion, FFmpeg and MoviePy projects", "studio/video"),
        Feature("Build & code", "🎞", "Video Maker", "Make a 9:16 slideshow video on the phone", "studio/videomaker"),
        Feature("Build & code", "💻", "Coding agent", "Run Termux commands and JavaScript with approval", "chat"),
        Feature("Build & code", "🐙", "GitHub", "Pull repos, commit, open pull requests, read build logs", "chat"),
        Feature("Smart devices", "📺", "TVs & smart plugs", "Roku, Android TV, Tasmota, Shelly, WLED, Home Assistant", "chat"),
        Feature("Smart devices", "🖧", "Computers", "Wake-on-LAN and SSH commands via Termux", "chat"),
        Feature("Smart devices", "📊", "Excel files", "Create real .xlsx spreadsheets with formulas", "chat"),
        Feature("Knowledge & tools", "🛰", "Free API Hub", "Weather, currency, countries, holidays, Wikipedia and more", "studio/api"),
        Feature("Knowledge & tools", "✅", "Tasks", "To-do list with CSV export and backup", "studio/tasks"),
        Feature("Knowledge & tools", "🧮", "Offline utilities", "Calculator, converter, passwords, text and dates", "studio/offline"),
        Feature("Knowledge & tools", "🗂", "Project Library", "Saved projects and ZIP export", "studio/library"),
        Feature("Knowledge & tools", "🧠", "Memory & settings", "Facts it remembers, AI provider, floating widget", "settings")
    )

    val categories: List<String> get() = all.map { it.category }.distinct()
}

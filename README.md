# DIPESH AI

A JARVIS-style personal AI assistant for **Android**. Talk to it, type to it, or just say **"Hey Dipesh"**. It speaks back, remembers you, and can actually do things on your phone.

Built with Kotlin and Jetpack Compose. The brain is **Google Gemini** (default, `gemini-3.8-flash`) with function calling. Anthropic Claude is available as an alternative in Settings.

## Features
| Area | What it does |
|---|---|
| Voice | Push-to-talk and hands-free **"Hey Dipesh"** mode, spoken replies. English, Nepali and Hindi |
| HUD | Animated JARVIS ring display that reacts to listening / thinking / speaking |
| Live info | Weather and 3-day forecast (Open-Meteo), news headlines (Google News), date and time |
| Phone control | Open apps, flashlight, volume, battery, alarms, media keys, camera, maps navigation, settings pages |
| Deep access | Contacts, phone calls, SMS, calendar, location, and **screen control** (read screen, tap, type, scroll, back/home) through Android Accessibility. Calls and texts always ask for your approval first |
| Memory | Remembers facts about you, saves notes, keeps chat history, sets reminders (notifications) |
| Privacy | Settings, memory and history stay on your phone. Only your messages go to the AI service |

## Get the APK (no Android Studio needed)
Every push builds the app on GitHub:
1. Open the **Actions** tab, then the latest **Build Android APK** run.
2. Download the artifact **DipeshAI-debug-apk** and unzip it.
3. Copy `app-debug.apk` to your phone and install it (allow "install unknown apps").

## First run
1. Open the app, tap **Settings**, choose the AI provider and paste your **Gemini API key** (free key at aistudio.google.com/apikey), set your city and language, then Save.
2. Tap **Talk**, or switch on **Hey Dipesh mode** and say: *"Hey Dipesh, what's the weather?"*

Without a key the app still works for time, date, battery, flashlight and opening apps.

## Try saying
- "Hey Dipesh, open YouTube"
- "What's the weather in Pokhara?"
- "Give me today's news about football"
- "Remind me in 20 minutes to drink water"
- "Set an alarm for 6:30"
- "Remember that my sister's name is Sita"
- "Set volume to 40 percent"

## Build it yourself
Open this folder in Android Studio (Koala or newer, JDK 17) and press Run. Or from a terminal with Gradle 8.9: `gradle assembleDebug`.

## Project layout
```
app/src/main/java/com/dipesh/ai/
  MainActivity.kt        entry point
  Ui.kt, Hud.kt          screens and animated HUD
  AssistantViewModel.kt  listening / thinking / speaking state machine, wake word
  Voice.kt               speech recognition + text-to-speech
  Brain.kt               Gemini (default) and Claude APIs with tool-use loop
  Tools.kt               phone actions, notes, memory
  Services.kt            weather and news
  ReminderReceiver.kt    reminder notifications
desktop/                 earlier Python desktop version
```

## Camera, WhatsApp, files, search, routines
- **Camera vision:** tap 📷 or say "what is this?", take a photo and the AI identifies it.
- **WhatsApp:** "Message Sita on WhatsApp: I'll be late" opens the chat ready to send; add "and send it" (with screen control on) to press Send for you.
- **Files:** find, open, move, create and delete files after you tap **Settings > Allow file access**. Deleting and moving always ask.
- **Internet search:** real answers (Google-grounded with Gemini).
- **Automation routines:** "Save a routine called good morning: tell me the weather; read the news; read my calendar", then say "run good morning".
- **Wake words:** "Hey Dipesh" or "Hey Arena" (editable in Settings).

## Phone access
In the app: **Settings > Grant phone permissions** (contacts, calls, SMS, calendar, location) and **Settings > Enable screen control** (turn on DIPESH AI under Accessibility). Every call, text and screen action shows an Allow / Deny prompt unless you switch that off in Settings.

## Known limits
- Android does not let any app silently toggle Wi-Fi, Bluetooth or airplane mode; DIPESH opens the right settings page and can tap the switch for you with screen control on.
- Reading notifications and files are not included yet.
- "Hey Dipesh" works while the app is open (screen stays on). Always-on background wake word needs a dedicated wake-word engine and is a future step.
- Android speech recognition may beep between listens in hands-free mode; that is the system engine.
- Nepali speech output depends on the phone having a Nepali text-to-speech voice installed.
- The API key is stored in the app's private storage. Don't share the APK after entering a key.

## License
MIT

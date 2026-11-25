# DIPESH AI

A premium, futuristic AI assistant for **Android**. Talk to it, type to it, show it things with the camera, and let it help run your phone, always with Android's permissions and your approval.

Built with Kotlin, Jetpack Compose and Material 3. The brain is **Google Gemini** (default) with function calling, and **Anthropic Claude** is available as an alternative.

> Languages: **English, Nepali (नेपाली), Hindi (हिन्दी)**. Wake words: "Hey Dipesh" / "Hey Arena" (editable).

---

## What's inside

### Look and feel
- Deep black, midnight blue and violet palette with glass cards, glowing gradients and soft shadows
- **Dark and light themes** (or follow the system), responsive for phones, foldables and tablets (two-column home on wide screens)
- Smooth page transitions, haptic feedback on key interactions

### Home
- **Animated AI orb** that changes behaviour for idle, listening, thinking and speaking
- **Sound-reactive visualizer**: reacts to your real microphone level while you talk, and animates while the assistant speaks
- Glowing microphone button, status chip, personalised greeting, live date and time
- Quick action cards (editable) and recent conversations

### Voice
- Speech-to-text (Android `SpeechRecognizer`) and text-to-speech (`TextToSpeech`)
- **Hands-free "Hey Dipesh / Hey Arena" mode** while the app is open
- **Voice conversation mode** in chat: talk back and forth naturally
- Listening, thinking, speaking and idle states, tap to stop speaking or interrupt
- Voice settings: language, speaking speed, pitch and voice selection, with preview

### Chat
- Modern bubbles, **streaming responses**, typing indicator
- **Markdown** (headings, bold, italic, lists, inline code, code blocks)
- Copy and **regenerate** buttons, error and retry states
- **Chat history** stored on the device (Room), with delete
- Photos and files can be attached to a question

### Control Center (every module is real)
| Module | What it does | Permission model |
|---|---|---|
| Apps | Search and open any installed app | none |
| Camera | Live **CameraX** preview, capture and ask the AI what it sees | camera permission, asked when you open it |
| Screen understanding | One screenshot with **MediaProjection** after you accept Android's dialog; you get 4 seconds to switch screens | Android consent dialog every time, short foreground service |
| Call | Dial a number | you confirm first, then `CALL_PHONE` (or the dialer if not granted) |
| Message | Prepare SMS, WhatsApp, email or share-sheet messages | uses Android intents, you press Send |
| Notification manager | Read, summarize and clear notifications | you switch on Notification access in Android settings |
| Files | Pick a file and ask about it; browse, open and delete shared storage | file picker, or "All files access" that you grant |
| System shortcuts | Wi-Fi, Bluetooth, display, sound, battery, location and more | opens the Android settings page |
| Screen control | Let the AI tap, type and scroll for you | Accessibility service you switch on |

### The AI can act (with tool use)
Weather and forecast, news, internet search (Google-grounded with Gemini), notes, reminders, alarms, calendar, location, contacts, calls, SMS, WhatsApp, flashlight, volume, media keys, navigation, files, notifications, automation **routines** ("save a routine called good morning..."), and screen control. Calls, texts, deletions and screen actions always ask first.

### Settings
Profile, appearance, API configuration (with a connection test), **AI personality** presets plus custom instructions, voice settings, **conversation memory** with user controls (view, add, delete, turn off), quick commands, safety options, and a **privacy dashboard** (what is stored, permission status with grant buttons, what leaves the device, erase everything).

---

## Get the app

Every push to `main` is built by GitHub Actions:

1. Open the **Actions** tab, then the latest **Build Android (APK + AAB)** run.
2. Download an artifact:
   - `DipeshAI-debug-apk`: install directly on a phone
   - `DipeshAI-release-apk`: release build, installable
   - `DipeshAI-release-aab`: Android App Bundle for the Play Console
3. Install the APK (allow "install unknown apps").

Pushing a tag like `v2.0.0` also attaches the release APK and AAB to a GitHub Release.

### First run
1. Open **Settings > AI & API configuration**.
2. Pick **Google Gemini** and paste your key (free key at [aistudio.google.com/apikey](https://aistudio.google.com/apikey)). Use **Test connection**.
3. Optional: set your name, city and language, then tap the orb and talk.

Without a key the app still handles time, date, battery, flashlight, opening apps and the Control Center.

---

## Build it yourself

Requirements: Android Studio Koala or newer (or Gradle 8.9), JDK 17, Android SDK 34.

```bash
gradle testDebugUnitTest      # unit tests
gradle assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
gradle assembleRelease        # app/build/outputs/apk/release/
gradle bundleRelease          # app/build/outputs/bundle/release/app-release.aab
```

### Signing a release for the Play Store
Release builds use the debug key unless you provide a keystore. Add these as **GitHub repository secrets** (or environment variables locally):

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | your `.jks` file encoded with `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Locally set `KEYSTORE_FILE` to the `.jks` path plus the three passwords above.

---

## Architecture

MVVM with unidirectional data flow.

```
app/src/main/java/com/dipesh/ai/
  MainActivity.kt, DipeshApp.kt     entry points, app-wide singletons
  AssistantViewModel.kt             voice state machine, chat, streaming, approvals
  Brain.kt                          Gemini + Claude clients, streaming, tool-use loop, model fallback
  Tools.kt, PhoneTools.kt,          everything the AI can do on the phone
  FileTools.kt, Services.kt,
  Search.kt
  Voice.kt                          SpeechRecognizer + TextToSpeech wrapper
  PhoneControlService.kt            Accessibility service (screen control)
  ScreenCaptureService.kt           one-shot MediaProjection screenshot
  NotificationService.kt            NotificationListenerService
  ReminderReceiver.kt, ImageUtil.kt
  data/                             Room database, DataStore settings, Keystore-encrypted keys, migration
  net/                              Retrofit/OkHttp clients, connectivity monitor
  ui/                               Compose screens: Home, Chat, Control, Camera, Settings, theme, orb, markdown
```

| Concern | Choice |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| State | ViewModel, Kotlin Coroutines and Flow |
| Storage | Room (chats, memory, notes, routines), DataStore (settings) |
| Networking | Retrofit and OkHttp, streamed responses |
| Camera | CameraX |
| Voice | SpeechRecognizer, TextToSpeech |
| Screen capture | MediaProjection |

## Privacy and security
- API keys are **encrypted with the Android Keystore (AES-GCM)** before being stored. The app never logs them.
- Chats, memory and notes stay on your phone. Only what you send (your messages, photos you capture, text you ask it to summarize) goes to your chosen AI provider.
- No analytics or tracking. Backups are disabled.
- DIPESH never bypasses Android security: every sensitive capability uses an official permission, consent dialog or system settings switch, and can be turned off at any time.
- Keep your API key private. Do not share the APK after entering a key, and never commit keys to Git.

## Known limits
- "Hey Dipesh" works while the app is open. A background, always-on wake word needs a dedicated on-device wake-word engine and is not included.
- Android does not allow apps to silently switch Wi-Fi, Bluetooth or airplane mode, so DIPESH opens the right settings page (and can tap the switch for you when screen control is on).
- Text-to-speech in Nepali depends on a Nepali voice being installed on the phone.
- Speech recognition uses the phone's own engine; it may beep between listens in hands-free mode and may need internet for Nepali or Hindi.
- Screen understanding captures one screenshot per approval (an Android 14+ requirement).

## License
MIT

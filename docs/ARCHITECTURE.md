# DIPESH AI - System Design (v3.6.0)

What is verified lives in `docs/FEATURE_STATUS.md`. This document explains how the app is put together and why. Nothing here has been measured on a physical device yet; numbers marked "assumed" are estimates.

## 1. Requirements

**Functional**
- Voice-first assistant in Nepali, Hindi and English: a mic that stays on, a wake word, natural speech output.
- AI brain (Gemini) with tools: phone control, files, calls/SMS, search, camera vision, GitHub/coding agent, automation.
- Offline for common things: calculator, units, timers, expenses, shopping list, battery, apps.
- A floating Yeti pet over other apps and a holographic home screen.
- Free to run: no paid SDKs or APIs (Gemini free tier, key-less public APIs, on-device processing).

**Non-functional**
- Battery: idle cost near zero; animation pauses when the screen is off.
- Memory: low (assumed target under 150 MB) so Redmi-class phones do not kill it.
- Privacy: keys and personal data stay on the phone; keys are encrypted.
- Reliability: the mic must never silently stop; failures must say why.
- Safety: nothing sensitive (call, SMS, file change, screen action, generated code) runs without approval.

**Constraints**: one developer, native Android (Kotlin, Compose), Redmi 12 (MIUI/HyperOS aggressive battery management), free-tier rate limits, builds verified only through GitHub Actions (no local SDK).

## 2. High-level design

```
 +---------------------------- Android app process -----------------------------+
 |  UI (Compose)          ViewModel                         Services            |
 |  Home / Chat / Studio  AssistantViewModel                VoiceKeepAlive      |
 |  Settings / Features   state, hint, tasks, progress      (mic foreground)    |
 |  HoloStage             send() router                     FloatingWidget      |
 |                             |                            (Yeti overlay)      |
 |                             v                            Phone/Notif/Screen  |
 |      send() routing, first match wins                          ^             |
 |      1 Persona mode phrases                                    | WidgetBus   |
 |      2 LocalSkills (offline)                                   v             |
 |      3 RuleBased intents                                 YetiView/YetiBrain  |
 |      4 Brain (Gemini / OpenAI-compatible) + Tools                            |
 |             |                  |                                             |
 |             v                  v                                             |
 |      Tools (approval gate)   Voice: SpeechRecognizer in,                     |
 |      phone, files, agent     Gemini TTS or device TTS out                    |
 |             |                                                                |
 |      Room DB, DataStore, SharedPreferences, Keystore SecretBox               |
 +------------------------------------------------------------------------------+
        |                    |                      |
   Gemini API          Gemini TTS API        Key-less public APIs
```

Key idea: **route cheap and local first, ask the AI last.** The first stage that understands a message answers it. This saves free-tier quota, makes common commands instant and offline, and leaves the AI for open-ended requests.

## 3. Components and data

| Component | Responsibility | Storage |
|---|---|---|
| `AssistantViewModel` | State machine (idle, listening, thinking, speaking), routing, voice modes, timers, progress | memory + below |
| `Brain` / `Tools` | Gemini or OpenAI-compatible calls, tool loop, per-turn tool filtering, retry without tools on HTTP 400 | none |
| `LocalSkills` | Pure parser for offline skills | small prefs stores |
| `Voice` | Recognizer lifecycle, watchdog, language fallback, natural TTS with device fallback | none |
| `VoiceKeepAliveService` | Microphone foreground service for background listening | none |
| `FloatingWidgetService`, `YetiView`, `YetiBrain` | Transparent overlay creature and its behavior state machine | prefs (position, size) |
| `WidgetBus` | In-process bridge so the overlay reads assistant state | memory |
| Room | Conversations, messages, notes, facts | SQLite |
| `SecretBox` | Gemini/GitHub keys encrypted with Android Keystore | DataStore |
| `studio/*` | Templates, project library, API hub, tasks, offline tools, progress | files, prefs |

**Data model**: `Conversation(id, title, updatedAt)` 1-n `Message(id, conversationId, role, text, createdAt)`. Settings are one immutable data class in DataStore. Small features (expenses, shopping, progress, pet) use private SharedPreferences with JSON, which is fine at this size.

## 4. Deep dives

**Voice pipeline.** `SpeechRecognizer` runs in short sessions. In a voice mode every outcome restarts listening (result, silence, no match, busy, network error); only a missing recognizer or permission stops it. A watchdog restarts a hung recognizer (25 s idle, 60 s once speech starts). Language errors fall back Nepali, then Hindi, then English, and the user is told. Repeated errors show the reason on screen. Speech output prefers Gemini TTS (30 voices, tone prompts, sentence-chunked with a fetch thread ahead of playback) and falls back to device TTS when there is no key, no network, or a rate limit (90 s cool-down on 429; 10 min on 400/403/404; 20 s otherwise).

**Brain.** The system prompt is persona text (auto language, modes, guardrails) plus memory facts. Tools are filtered per turn by keywords. A 400 from Gemini retries once without tools. With no key or no network, `RuleBased` still answers a few things.

**Overlay pet.** `YetiBrain` is a pure state machine (about 22 acts) with injected random source and clock, so it is unit-tested. It outputs a target pose; `YetiView` blends toward it with frame-rate-independent smoothing and draws on a Canvas. The window is only as big as the creature. Frame delay is lower when resting or asleep, and the loop stops when the screen is off.

**Holographic stage.** `HoloMath` does rotation, perspective projection and ring geometry; `HoloStage` draws two layers (behind and in front of the avatar) so rings pass around it. Camera is slow auto-orbit plus accelerometer tilt plus horizontal drag.

**Safety gate.** Every tool with side effects goes through an approval prompt. Generated code is never run automatically. The shell fallback runs as the app user in the app's private folder.

## 5. Scale and reliability

- **Load**: one user, one device. "Scale" means battery, memory and API quota, not servers.
- **Gemini free tier** (assumed roughly 10-15 requests/minute for text, lower for TTS preview): local-first routing and the TTS fallback keep the app usable at the limit.
- **Process death**: HyperOS may kill background apps. Mitigation: foreground services with a visible notification; the user sets battery to "No restrictions".
- **Failure handling**: the generate path is wrapped so an exception returns the app to listening; recognizer errors are classified; TTS and brain both have fallbacks.
- **Monitoring**: only on-screen messages. No crash reporting (a deliberate no-cost, no-tracking choice).
- **CI**: GitHub Actions builds, runs unit tests (pure logic only), and produces debug APK, release APK and AAB.

## 6. Trade-offs

| Decision | Chosen | Alternative | Why / cost |
|---|---|---|---|
| Routing | Local rules before AI | AI for everything | Instant, offline, saves quota; cost: literal phrasing |
| Yeti rendering | Procedural Canvas 2.5D | Blender model + Filament | Buildable and testable here, tiny APK; cost: less realistic |
| Home stage | Compose Canvas pseudo-3D | OpenGL/Filament scene | No new dependency; cost: no real lighting or reflections |
| Small data | SharedPreferences JSON | Room for everything | Simple; cost: no queries |
| Natural voice | Gemini TTS preview model | Device TTS only | Far more natural, free; cost: preview may change, rate limits |
| Pet window | Sized to creature | Full-screen overlay | Does not block apps; cost: its rectangle catches touches |
| Wake word | Recognizer loop in a service | On-device keyword model | No new SDK; cost: battery, depends on Google speech service |

## 7. Known risks

1. Nothing is device-tested since v3.1. Highest risk: overlay permission flow, animation frame rate on Redmi 12.
2. Mic reliability on Xiaomi depends on battery settings and the Google speech service.
3. Gemini preview TTS can change model names or limits without notice.
4. Unit tests cover only pure logic; ViewModel, services and UI are untested.
5. `AssistantViewModel` is large (routing, voice, timers, progress, tasks) and is the main maintainability risk.

## 8. What I would revisit as it grows

- Split `AssistantViewModel` into a router, a voice controller and feature controllers.
- Move expenses, shopping and progress into Room once they need queries, charts or export.
- Add instrumented UI tests and a device test checklist; add an opt-in local log file users can share.
- Replace the recognizer-loop wake word with an on-device keyword spotter if battery measurements are poor.
- If the Yeti must look real, author a glTF model and rig in Blender and render with Filament; `YetiBrain` poses can drive bones.
- Replace regex phrase matching with a small per-language intent table.

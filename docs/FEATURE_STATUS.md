# DIPESH AI 3.6.0 - Feature status (honest report)

**How to read this.** "CI-verified" means the code compiles in GitHub Actions and, where noted, is covered by automated unit tests that passed in the same run. **Nothing has been tested on a physical Android device by the developer tooling used to build this**, so every screen, permission flow, voice feature and network call needs a real-phone check by you. Features are not marked "works" unless noted.

## Download and install
1. GitHub repo > **Actions** > latest green run > **Artifacts** > `DipeshAI-debug-apk` (installable debug build), `DipeshAI-release-apk`, `DipeshAI-release-aab`.
2. Unzip, copy the `.apk` to the phone, open it, allow "Install unknown apps" for your file manager or browser.
3. Open DIPESH AI > Settings > AI & API and add a free Gemini key (https://aistudio.google.com/apikey). Free tiers have quotas and can change. Or choose a free Groq/OpenRouter key, or Ollama (no key).

## CI-verified with automated tests
| Area | What the tests prove |
|---|---|
| Unsafe-path protection | `..`, absolute, drive-letter, `~`, backslash tricks and control characters are refused (`SafePath`). |
| Duplicate-file protection | Case-insensitive duplicates are ignored, never overwritten. |
| Project templates | Agent, website, Remotion, FFmpeg and MoviePy templates produce multi-file projects with README, safe paths, placeholder-only `.env.example`; HTML in user text is escaped. |
| ZIP export | Entries live under one root folder; unsafe and duplicate entries are skipped; library save/list/export/delete round-trip. |
| AI file parsing | Fenced `file:` blocks are parsed; unsafe paths in AI output are refused. |
| Free API Hub | Query parsers and response parsers for all 10 services (against sample JSON, not live servers). |
| Tasks | CSV export escaping and spreadsheet-formula-injection guard; JSON backup/restore round-trip. |
| Rule-based fallback | Time, date, greeting, open app, flashlight intents are recognised. |

## Implemented, compiles in CI, NOT verified on a device
- **Chat, history, streaming, Gemini/Claude/OpenAI-compatible providers**, model fallback and retry.
- **Voice**: speech recognition, text-to-speech, wake words, hands-free mode, background listening service (needs your phone's speech service; Xiaomi phones often need the Google app).
- **Phone integration**: open apps, camera, gallery/file picker, screenshot (MediaProjection with consent), accessibility screen control, calls/SMS/WhatsApp with approval.
- **Vision**: camera and screenshot images sent to a vision-capable configured provider.
- **Agent Builder / Website Studio / Video Studio screens**: template generation, optional "Generate with AI", file preview, Save to library, Share ZIP, Save ZIP to Downloads.
- **Free API Hub** live calls to Open-Meteo, Frankfurter, REST Countries, Nager.Date, Wikipedia, Open Library, Free Dictionary API, MyMemory, USGS, Open-Meteo Air Quality (loading and error states included). Endpoints were written from public documentation and were **not called from a test run**; any of them may have changed.
- **Tasks screen**, CSV export / JSON backup to Downloads, restore from file; **Project Library** screen.
- **JARVIS HUD** on Home: system status, voice log, current tasks, assistant options, animated orb.
- **Developer agent**: Termux commands, GitHub tools, JavaScript sandbox, remote skills, secrets vault, smart-device control, Excel `.xlsx` writer, auto-typing, self-check.

## Limits and things that are NOT built
- **Video**: Video Studio generates Remotion/FFmpeg/MoviePy project templates (run those on a computer). Separately, **Video Maker** renders a simple slideshow MP4 on the phone (MediaCodec, 720x1280, 30 fps, no audio, fade + zoom + captions). It is basic, not a full editor.
- **Generated code is never run by the app** (agent, website and video projects). Termux/JS execution by the chat assistant always asks for approval first.
- **Rule-based fallback** covers only a handful of commands; it is not a full offline assistant.
- **Offline utilities** now include calculator, unit converter, password generator, text stats/Base64 and date maths (unit-tested pure logic).
- **AI-written projects** depend on the model following the file format; if it does not, the template is used instead.
- **Currency**: Frankfurter has no NPR. Holidays: Nager.Date does not cover every country. Translation: MyMemory free tier is limited to roughly 5,000 characters per day.
- Public APIs are free today and not guaranteed to stay free, unlimited or available.
- The wake word works only while the app/microphone service is running; Android blocks silent Wi-Fi/Bluetooth toggles; screen capture needs consent each time.

## Permissions
Microphone, camera, notifications, contacts/phone/SMS (only when you use those tools), calendar, location (weather/maps), "All files access" (optional), Accessibility service (optional, for screen control and auto-typing), Notification access (optional), Termux "Run commands" (optional), foreground microphone service while hands-free is on.

## Security notes
API keys, GitHub token and secrets are encrypted with the Android Keystore. Exported ZIPs contain only generated files, never keys. `.env.example` files contain placeholders only. No analytics or hidden surveillance.

## Added in 3.2.0 (compile- and unit-test-verified only; NOT tested on a physical device)
- **Floating glass orb**: replaced in 3.3.0 by the Yeti pet (below).
- **Holographic command center**: translucent HUD panels, predictive analytics graphs (your real requests/tasks per day with a dashed simple linear-trend projection - not a guarantee) and the central orb.
- **"Show features" voice/text command**: expands an animated overlay listing every capability grouped by category; "close features" or Back dismisses it. Tapping a card opens that screen.
- Animations, overlay permission flow, MediaCodec encoding and the orb service are UI/hardware dependent and still need physical-device testing.

## Added in 3.3.0 (compile- and unit-test-verified only; NOT tested on a physical device, e.g. Redmi 12)
- **Floating Yeti pet** (replaces the orb): transparent overlay showing only the creature, no bubble or panel. 22 animation states (idle, sit, walk, run, jump, dance, wave, climb at screen edges, sleep, wake, listen, think, speak with mouth sync, happy, excited, curious, surprised, sad, confused, laugh, celebrate, being carried), smooth spring blending, blinking, glancing, breathing, self-chosen idle behaviour, sleeps after about 75 s idle. Tap = start/stop a voice conversation inside the running app (mic stays on until tapped again), drag anywhere, long-press = tickle. Reacts to success, errors and completed tasks. Settings: show/hide, size, headphones, wander, sleep. Position is remembered and kept on screen after rotation. Animation pauses when the screen is off and slows when the pet rests.
- **Honest limits of the Yeti**: it is a procedurally drawn, lit and shaded character animated in 2.5D on a Canvas, not a Blender-modelled mesh rendered with Filament; no 3D model file or skeletal rig exists in this build. Fur is drawn tufts, not simulated fur. Looks and performance on real hardware are unverified. It does not restart after a reboot, and the overlay needs the "Display over other apps" permission.
- **Natural Gemini voices**: 30 prebuilt Gemini voices and 9 delivery tones (Natural, Neutral, Joyful, Excited, Calm, Empathetic, Dramatic, Suspenseful, Storyteller) via Gemini text-to-speech using your Gemini key. Speech is generated sentence by sentence so playback starts early, and falls back to the phone's own voice if the key is missing, you are offline, or the free limit (HTTP 429) is hit. The TTS model name (gemini-2.5-flash-preview-tts) is a preview model and may change; free-tier limits apply and are not guaranteed. Not tested on a device.

## Added in 3.4.0 (compile- and unit-test-verified only; NOT tested on a physical device)
- **3D holographic stage on Home**: glowing "DIPESH AI ASSISTANT" title with moving light gradient; the avatar orb sits inside three tilted energy rings that pass behind and in front of it (real perspective maths, depth-sorted); 84 depth-faded particles; perspective lab-floor grid; data streams with moving packets; 8 orbiting feature icons; light sweep; camera parallax from phone tilt (accelerometer) plus horizontal finger drag that springs back; startup sequence (rings snap open, particles fly out, flash) plays once per app launch. Rings, glow and streams react to listening (mic level), thinking (faster dashed rings), speaking (speech loudness).
- **Honest limits**: this is Compose Canvas pseudo-3D (projection maths, additive glows), not Unreal Engine 5, not ray-traced, not a 4K render. Target is smooth animation (it redraws every frame), but frame rate on a Redmi 12 is unmeasured. Reflections/shadows are painted approximations.
- **Voice input diagnostics**: the screen now shows a live "Hearing: ..." transcript, spells out errors (e.g. no internet for speech recognition, language not downloaded), and falls back from Nepali to Hindi to English when the phone's recogniser lacks the language.

## Added in 3.5.0 (compile- and unit-test-verified only; NOT tested on a physical device)
Offline smart skills: understood from speech or typing before the AI is asked, so they work with no internet or API key and use no AI quota. English phrasing only for now.
- **Calculator**: "what is 25 times 4", "15 percent of 2400", "2 to the power of 10".
- **Unit converter**: "convert 5 km to miles", "100 celsius in fahrenheit" (length, mass, volume, speed, temperature, data).
- **Timers**: "set a timer for 10 minutes", cancel, time left. Timers of a minute or more also schedule a backup notification (inexact alarm, may fire a little late); timers under a minute exist only while the app process is alive.
- **Focus Mode**: "start focus 25 minutes". The Home screen shows a countdown, the floating Yeti sits quietly instead of wandering, and celebrates with a spoken message when done.
- **Spoken expense tracker**: "I spent 500 on lunch", "how much did I spend this week". Amounts are shown as "Rs"; stored privately on the phone; no export screen yet (CSV maths exists in code only).
- **Daily briefing**: "good morning" / "daily briefing" reads date, weather (needs the city set in Settings and internet; otherwise skipped), open tasks, today's spending and a low-battery warning.
- **Battery status**: "battery level".
- Known limits: patterns are matched literally, so unusual wording falls through to the AI; Nepali/Hindi phrasing is not matched offline yet.

## Added in 3.6.0 (compile- and unit-test-verified only; NOT tested on a physical device)
- **Voice shopping list**: "add milk and rice to my shopping list", "what's on my shopping list", "remove milk from my shopping list", "clear my shopping list". Stored privately on the phone.
- **Fun & games** (offline): flip a coin, roll a dice or an N-sided die, random number between two values, "pick between A or B", jokes (the Yeti laughs). Joke list is small and built in.
- **Nepali/Hindi phrases for some skills**: timers and focus ("५ मिनेट टाइमर", "२५ मिनेट फोकस", "2 घण्टा टाइमर"), morning greetings for the briefing ("सुप्रभात", "शुभ प्रभात"), and battery ("बैटरी कति छ"). Devanagari digits are understood. Calculator, conversions, expenses and the shopping list are still English only.
- **Yeti levels, XP, streak and badges**: +1 XP per message, +2 per expense, +5 per timer, +10 per finished task, +25 per focus session. Levels from Snow Pup to Yeti Legend, a daily-use streak, 8 badges, shown in a "Yeti level" panel on Home. Level-ups make the Yeti celebrate. Stored privately on the phone; no cloud sync or leaderboard.

# DIPESH AI

A JARVIS-style personal voice assistant: animated HUD, voice or text input, instant built-in commands, and an LLM brain (Claude) for everything else.

## Features
- Glowing animated HUD (tkinter) that changes with state: standing by, listening, processing, speaking
- Voice in (microphone) and voice out (text-to-speech), with automatic typed fallback
- Offline skills: time, date, open sites, web search, play on YouTube, notes, system info, launch allow-listed apps
- Anything else goes to Claude through the Anthropic API (conversation memory included)
- Safe by design: speech can never run arbitrary shell commands

## Quick start
```bash
git clone https://github.com/dipeshpandeya791-hub/dipesh-ai.git
cd dipesh-ai
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env                                  # then paste your ANTHROPIC_API_KEY
python -m dipesh_ai
```
Flags: `--no-mic` (type instead of talk), `--no-gui` (terminal only).

Microphone note: `pip install pyaudio` is needed for speech input (on Linux: `sudo apt install portaudio19-dev` first; on Linux, tkinter needs `sudo apt install python-tk`).

## Try saying
"What time is it", "Open YouTube", "Search for python tutorials", "Play lo-fi music", "Note buy milk", "Launch calculator", or any question. "Goodbye" exits.

## Project layout
```
dipesh_ai/
  main.py       wiring + main loop
  hud.py        animated HUD
  voice.py      speech recognition + TTS
  commands.py   offline skills (add your own here)
  brain.py      Claude API client
tests/          pytest
```

## Roadmap ideas
Wake word ("Hey Dipesh"), smart-home control, calendar/email skills, local LLM option, better TTS voice.

## License
MIT

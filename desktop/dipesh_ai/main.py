"""DIPESH AI entry point.  Run:  python -m dipesh_ai   (add --no-gui or --no-mic as needed)"""
from __future__ import annotations

import argparse
import queue
import threading
from pathlib import Path

from . import commands
from .brain import Brain
from .voice import Voice


def load_env() -> None:
    import os
    env = Path(".env")
    if env.exists():
        for line in env.read_text().splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.split("=", 1)
                os.environ.setdefault(k.strip(), v.strip().strip('"'))


def respond(text: str, brain: Brain) -> str:
    return commands.handle(text) or brain.ask(text)


def main() -> None:
    ap = argparse.ArgumentParser(description="DIPESH AI voice assistant")
    ap.add_argument("--no-gui", action="store_true", help="terminal only")
    ap.add_argument("--no-mic", action="store_true", help="type instead of speaking")
    args = ap.parse_args()
    load_env()

    brain, voice = Brain(), Voice(use_mic=not args.no_mic)
    hud = None
    if not args.no_gui:
        from .hud import HUD
        hud = HUD()

    typed: queue.Queue[str] = queue.Queue()
    if hud:
        hud.on_text(typed.put)
    set_state = hud.set_state if hud else (lambda *a, **k: None)

    def loop() -> None:
        voice.speak("DIPESH AI online. How can I help?")
        while True:
            set_state("listening", "Listening...")
            try:
                text = typed.get_nowait() if hud and not typed.empty() else voice.listen()
            except queue.Empty:
                text = ""
            if not text:
                continue
            set_state("thinking", f"You: {text}")
            if commands.is_exit(text):
                set_state("speaking", "Goodbye.")
                voice.speak("Goodbye.")
                break
            reply = respond(text, brain)
            set_state("speaking", reply)
            voice.speak(reply)
        if hud:
            hud.close()

    t = threading.Thread(target=loop, daemon=True)
    t.start()
    if hud:
        hud.run()
    else:
        t.join()


if __name__ == "__main__":
    main()

"""Built-in skills: fast, offline commands that run before the LLM is asked."""
from __future__ import annotations

import datetime as dt
import os
import platform
import re
import subprocess
import sys
import urllib.parse
import webbrowser
from pathlib import Path

NOTES_FILE = Path.home() / ".dipesh_ai_notes.txt"

SITES = {
    "youtube": "https://www.youtube.com",
    "github": "https://github.com",
    "google": "https://www.google.com",
    "gmail": "https://mail.google.com",
    "maps": "https://maps.google.com",
}


def _time(_: str) -> str:
    return dt.datetime.now().strftime("It is %I:%M %p.")


def _date(_: str) -> str:
    return dt.datetime.now().strftime("Today is %A, %B %d, %Y.")


def _open(text: str) -> str | None:
    m = re.search(r"\bopen\s+(.+)", text)
    if not m:
        return None
    target = m.group(1).strip().rstrip(".")
    if target in SITES:
        webbrowser.open(SITES[target])
        return f"Opening {target}."
    if re.match(r"^[\w.-]+\.[a-z]{2,}$", target):
        webbrowser.open("https://" + target)
        return f"Opening {target}."
    return None


def _search(text: str) -> str | None:
    m = re.search(r"\b(?:search|google|look up)\s+(?:for\s+)?(.+)", text)
    if not m:
        return None
    q = m.group(1).strip()
    webbrowser.open("https://www.google.com/search?q=" + urllib.parse.quote(q))
    return f"Searching the web for {q}."


def _play(text: str) -> str | None:
    m = re.search(r"\bplay\s+(.+)", text)
    if not m:
        return None
    q = m.group(1).strip()
    webbrowser.open("https://www.youtube.com/results?search_query=" + urllib.parse.quote(q))
    return f"Playing {q} on YouTube."


def _note(text: str) -> str | None:
    m = re.search(r"\b(?:note|remember|remind me)\s+(?:that\s+|to\s+)?(.+)", text)
    if not m:
        return None
    with NOTES_FILE.open("a", encoding="utf-8") as f:
        f.write(f"{dt.datetime.now():%Y-%m-%d %H:%M}  {m.group(1).strip()}\n")
    return "Noted."


def _system(_: str) -> str:
    return f"Running {platform.system()} {platform.release()}, Python {platform.python_version()}, on {os.cpu_count()} cores."


def _launch_app(text: str) -> str | None:
    m = re.search(r"\b(?:launch|start|run)\s+(.+)", text)
    if not m:
        return None
    app = m.group(1).strip().rstrip(".")
    allowed = {"calculator": {"Windows": "calc", "Darwin": "open -a Calculator", "Linux": "gnome-calculator"},
               "notepad": {"Windows": "notepad", "Darwin": "open -a TextEdit", "Linux": "gedit"}}
    cmd = allowed.get(app, {}).get(platform.system())
    if not cmd:
        return None  # only allow-listed apps; never run arbitrary shell from speech
    subprocess.Popen(cmd.split())
    return f"Launching {app}."


def handle(text: str) -> str | None:
    """Return a reply if a built-in command matches, else None (fall through to LLM)."""
    t = text.lower().strip()
    if re.search(r"\bwhat('?s| is) the time\b|\bcurrent time\b|\btime is it\b", t):
        return _time(t)
    if re.search(r"\b(today'?s date|what day|what is the date|what'?s the date)\b", t):
        return _date(t)
    if re.search(r"\b(system|computer) (info|status)\b", t):
        return _system(t)
    for fn in (_open, _play, _search, _launch_app, _note):
        reply = fn(t)
        if reply:
            return reply
    return None


def is_exit(text: str) -> bool:
    return bool(re.search(r"\b(goodbye|shut ?down|exit|quit|bye)\b", text.lower()))

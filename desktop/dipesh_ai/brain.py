"""LLM brain. Uses the Anthropic Messages API over plain HTTPS (no SDK needed)."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request

API_URL = "https://api.anthropic.com/v1/messages"
DEFAULT_MODEL = "claude-sonnet-5-5"

SYSTEM_PROMPT = (
    "You are DIPESH AI, a personal voice assistant in the style of JARVIS. "
    "Be concise (1-3 short sentences), warm and a little witty. Your replies are spoken aloud, "
    "so avoid markdown, lists and emojis."
)


class Brain:
    def __init__(self, api_key: str | None = None, model: str | None = None):
        self.api_key = api_key or os.environ.get("ANTHROPIC_API_KEY", "")
        self.model = model or os.environ.get("DIPESH_MODEL", DEFAULT_MODEL)
        self.history: list[dict] = []

    @property
    def online(self) -> bool:
        return bool(self.api_key)

    def ask(self, text: str) -> str:
        if not self.online:
            return ("My language brain is offline. Set ANTHROPIC_API_KEY in the .env file "
                    "and I can answer anything.")
        self.history.append({"role": "user", "content": text})
        self.history = self.history[-20:]  # keep short memory
        body = json.dumps({
            "model": self.model,
            "max_tokens": 300,
            "system": SYSTEM_PROMPT,
            "messages": self.history,
        }).encode()
        req = urllib.request.Request(API_URL, data=body, method="POST", headers={
            "content-type": "application/json",
            "x-api-key": self.api_key,
            "anthropic-version": "2023-06-01",
        })
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                data = json.load(r)
            reply = "".join(b.get("text", "") for b in data.get("content", [])).strip()
        except urllib.error.HTTPError as e:
            self.history.pop()
            return f"The API returned error {e.code}. Check your key and model name."
        except Exception as e:  # network down, timeout...
            self.history.pop()
            return f"I couldn't reach my brain: {type(e).__name__}."
        self.history.append({"role": "assistant", "content": reply})
        return reply or "I have nothing to say to that."

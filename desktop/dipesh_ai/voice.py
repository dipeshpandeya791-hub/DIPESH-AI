"""Speech in/out. Falls back to keyboard + print if voice libraries are missing."""
from __future__ import annotations

try:
    import speech_recognition as sr
except ImportError:  # optional
    sr = None
try:
    import pyttsx3
except ImportError:  # optional
    pyttsx3 = None


class Voice:
    def __init__(self, use_mic: bool = True):
        self.use_mic = use_mic and sr is not None
        self.rec = sr.Recognizer() if self.use_mic else None
        self.engine = pyttsx3.init() if pyttsx3 else None
        if self.engine:
            self.engine.setProperty("rate", 175)

    def listen(self) -> str:
        """Block until the user says (or types) something. Returns '' on silence."""
        if not self.use_mic:
            try:
                return input("you> ").strip()
            except EOFError:
                return "exit"
        try:
            with sr.Microphone() as src:
                self.rec.adjust_for_ambient_noise(src, duration=0.3)
                audio = self.rec.listen(src, timeout=8, phrase_time_limit=12)
            return self.rec.recognize_google(audio)  # free web recognizer
        except Exception:  # timeout / unintelligible / offline
            return ""

    def speak(self, text: str) -> None:
        print(f"DIPESH> {text}")
        if self.engine:
            self.engine.say(text)
            self.engine.runAndWait()

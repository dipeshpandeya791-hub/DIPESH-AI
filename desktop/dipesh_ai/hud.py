"""Animated Iron-Man style HUD drawn with tkinter (ships with Python)."""
from __future__ import annotations

import math
import queue
import tkinter as tk

BG, CYAN, DIM, ORANGE = "#02161a", "#19f0d0", "#0b6b60", "#ff8a2a"
STATES = {"idle": "STANDING BY", "listening": "LISTENING", "thinking": "PROCESSING", "speaking": "SPEAKING"}


class HUD:
    def __init__(self, title: str = "DIPESH AI", size: int = 560):
        self.size = size
        self.root = tk.Tk()
        self.root.title(title)
        self.root.configure(bg=BG)
        self.canvas = tk.Canvas(self.root, width=size, height=size + 120, bg=BG, highlightthickness=0)
        self.canvas.pack()
        self.state = "idle"
        self.caption = "Say something, or type below."
        self.angle = 0.0
        self.events: queue.Queue = queue.Queue()
        self.entry_cb = None
        self.entry = tk.Entry(self.root, bg="#04262b", fg=CYAN, insertbackground=CYAN,
                              relief="flat", font=("Consolas", 12))
        self.entry.pack(fill="x", padx=16, pady=(0, 14), ipady=6)
        self.entry.bind("<Return>", self._submit)
        self.root.after(33, self._tick)

    # thread-safe API -------------------------------------------------
    def set_state(self, state: str, caption: str | None = None) -> None:
        self.events.put((state, caption))

    def on_text(self, cb) -> None:
        self.entry_cb = cb

    def run(self) -> None:
        self.root.mainloop()

    def close(self) -> None:
        self.events.put(("quit", None))

    # internals -------------------------------------------------------
    def _submit(self, _):
        text = self.entry.get().strip()
        self.entry.delete(0, "end")
        if text and self.entry_cb:
            self.entry_cb(text)

    def _tick(self):
        try:
            while True:
                state, caption = self.events.get_nowait()
                if state == "quit":
                    self.root.destroy()
                    return
                self.state = state
                if caption is not None:
                    self.caption = caption
        except queue.Empty:
            pass
        self._draw()
        self.root.after(33, self._tick)

    def _draw(self):
        c, s = self.canvas, self.size
        c.delete("all")
        cx = cy = s / 2
        speed = {"idle": 0.6, "listening": 1.6, "thinking": 4.0, "speaking": 2.2}[self.state]
        self.angle = (self.angle + speed) % 360
        pulse = 1 + 0.04 * math.sin(math.radians(self.angle * 3)) if self.state == "speaking" else 1
        color = ORANGE if self.state == "thinking" else CYAN
        for i, (r, w, ext, direction) in enumerate([(210, 3, 70, 1), (180, 8, 40, -1), (150, 2, 120, 1), (120, 5, 90, -1)]):
            r *= pulse
            c.create_oval(cx - r, cy - r, cx + r, cy + r, outline=DIM, width=1)
            for k in range(3):
                start = self.angle * direction * (1 + i * 0.3) + k * 120
                c.create_arc(cx - r, cy - r, cx + r, cy + r, start=start, extent=ext,
                             style="arc", outline=color, width=w)
        r = 70 * pulse
        c.create_oval(cx - r, cy - r, cx + r, cy + r, outline=color, width=2)
        c.create_text(cx, cy - 8, text="DIPESH", fill=color, font=("Consolas", 22, "bold"))
        c.create_text(cx, cy + 20, text="A.I.", fill=color, font=("Consolas", 14))
        c.create_text(cx, 22, text=STATES[self.state], fill=color, font=("Consolas", 12, "bold"))
        c.create_text(cx, s + 50, text=self.caption[:140], fill=CYAN, width=s - 40,
                      font=("Consolas", 11), justify="center")

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dipesh_ai import commands  # noqa: E402
from dipesh_ai.brain import Brain  # noqa: E402


def test_time_and_date():
    assert commands.handle("what is the time").startswith("It is")
    assert "Today is" in commands.handle("what's the date")


def test_unknown_falls_through():
    assert commands.handle("tell me a joke about cats") is None


def test_no_arbitrary_launch():
    assert commands.handle("run rm -rf /") is None


def test_exit():
    assert commands.is_exit("ok goodbye")
    assert not commands.is_exit("what time is it")


def test_brain_offline_message():
    assert "offline" in Brain(api_key="").ask("hi").lower()

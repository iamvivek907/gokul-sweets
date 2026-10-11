"""Independent Windows kitchen alarm. Never prints or completes an order."""
import ctypes
import json
import math
import os
from pathlib import Path
import struct
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
import wave

import agent


SOUND_PROFILES = ("BUZZER", "BELL", "CHIME")


def sound_samples(profile, rate=16000):
    """Two-second repeating patterns with short fades to avoid abrupt clicks."""
    if profile not in (*SOUND_PROFILES, "FAULT"):
        raise ValueError("Unknown kitchen alarm sound")
    samples = []
    for n in range(rate * 2):
        t = n / rate
        value = 0.0
        if profile in ("BUZZER", "FAULT") and t < .5:
            fade = min(1.0, t / .005, (.5 - t) / .005)
            value = .67 * fade * math.sin(2 * math.pi * (880 if profile == "BUZZER" else 440) * t)
        elif profile == "BELL" and t < 1.2:
            fade = min(1.0, t / .005, (1.2 - t) / .01)
            value = fade * math.exp(-4 * t) * (
                .5 * math.sin(2 * math.pi * 1046.5 * t) +
                .17 * math.sin(2 * math.pi * 2093 * t) +
                .08 * math.sin(2 * math.pi * 3139.5 * t))
        elif profile == "CHIME" and t < .75:
            note = min(2, int(t / .25))
            elapsed = t - note * .25
            fade = min(1.0, elapsed / .005, (.25 - elapsed) / .01)
            value = .65 * fade * math.exp(-5 * elapsed) * math.sin(
                2 * math.pi * (660, 880, 1108.7)[note] * elapsed)
        samples.append(round(32767 * value))
    return samples


class SoundChoices:
    def prepare(self, root):
        self.profiles = {}
        for profile in (*SOUND_PROFILES, "FAULT"):
            path = root / ("alarm-" + profile.lower() + ".wav")
            samples = sound_samples(profile)
            with wave.open(str(path), "wb") as wav:
                wav.setparams((1, 2, 16000, len(samples), "NONE", "not compressed"))
                wav.writeframes(struct.pack("<" + "h" * len(samples), *samples))
            self.profiles[profile] = path
        self.paths = {"fault": self.profiles["FAULT"]}
        self.profile = None
        self.select("BUZZER")

    def select(self, profile):
        if profile not in SOUND_PROFILES:
            raise ValueError("Unknown kitchen alarm sound")
        if profile == self.profile:
            return
        self.profile = profile
        self.paths.update(due=self.profiles[profile], test=self.profiles[profile])
        # A running Mac loop must restart when its selected WAV changes.
        if hasattr(self, "mode"):
            self.mode = None


class WindowsSound(SoundChoices):
    def __init__(self, root):
        if sys.platform != "win32":
            raise RuntimeError("Native kitchen sound requires Windows")
        import winsound
        self.sound = winsound
        self.prepare(root)

    def play(self, mode):
        # Reassert playback each poll so interruptions are retried automatically.
        if mode is None:
            self.sound.PlaySound(None, 0)
            flags = 0x80000000
        else:
            self.sound.PlaySound(str(self.paths[mode]), self.sound.SND_FILENAME |
                                 self.sound.SND_ASYNC | self.sound.SND_LOOP | self.sound.SND_NODEFAULT)
            flags = 0x80000001  # prevent automatic sleep while the alarm is enabled
        if not ctypes.windll.kernel32.SetThreadExecutionState(flags):
            raise RuntimeError("Windows wake request failed")


class MacSound(SoundChoices):
    """macOS Core Audio through the system afplay utility; reassert after interruption."""
    def __init__(self, root):
        self.process = None
        self.mode = None
        self.prepare(root)

    def play(self, mode):
        if mode == self.mode and self.process is not None and self.process.poll() is None:
            return
        if self.process is not None and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=3)
        self.process = None
        self.mode = mode
        if mode is not None:
            self.process = subprocess.Popen(
                ["/bin/sh", "-c",
                 """trap 'kill "$audio" 2>/dev/null; exit' TERM INT; """
                 'while kill -0 "$2" 2>/dev/null; do '
                 '/usr/bin/afplay "$1" & audio=$!; wait "$audio" || exit; done',
                 "gokul-sound", str(self.paths[mode]), str(os.getpid())])
            try:
                self.process.wait(timeout=.15)
            except subprocess.TimeoutExpired:
                return
            raise RuntimeError("macOS audio playback failed to start")


def supervise(cfg, path, stop=None):
    """Retry startup/lock failures as well as failures inside the polling loop."""
    if cfg["station"] != "KITCHEN":
        return
    while stop is None or not stop.is_set():
        try:
            run(cfg, path)
        except Exception as exc:
            print("Kitchen alarm restarting:", type(exc).__name__, flush=True)
        if stop is None:
            time.sleep(5)
        elif stop.wait(5):
            return


class AlarmState:
    def __init__(self, path, identity):
        self.path = Path(str(path) + ".alarm.json")
        self.identity = identity
        self.enabled = False
        self.due = False
        self.test_receipt = 0
        self.test_until = 0
        self.status = "STARTING"
        try:
            saved = json.loads(self.path.read_text())
            if not isinstance(saved, dict):
                raise ValueError("Invalid alarm cache")
            if saved.get("identity") == identity:
                self.enabled = saved.get("enabled") is True
                self.due = saved.get("due") is True
                receipt = saved.get("testReceipt", 0)
                self.test_receipt = receipt if type(receipt) is int and receipt >= 0 else 0
        except (OSError, ValueError):
            pass

    def save(self):
        temporary = Path(str(self.path) + ".tmp")
        temporary.write_text(json.dumps({"identity": self.identity, "enabled": self.enabled,
                                        "due": self.due, "testReceipt": self.test_receipt}))
        os.replace(temporary, self.path)

    def update(self, response, sound, now):
        if (type(response.get("enabled")) is not bool or
                any(type(response.get(k)) is not int or response[k] < 0
                    for k in ("needsPreparation", "readyOverdue", "testSequence"))):
            raise ValueError("Invalid alarm snapshot")
        profile = response.get("soundProfile", "BUZZER")
        if profile not in SOUND_PROFILES:
            raise ValueError("Unknown kitchen alarm sound")
        sound.select(profile)
        if self.enabled and not response["enabled"]:
            self.test_until = 0
        self.enabled = response["enabled"]
        self.due = self.enabled and response["needsPreparation"] + response["readyOverdue"] > 0
        sequence = response["testSequence"]
        if sequence > self.test_receipt:
            # Receipt records OS acceptance, not whether speakers were heard.
            sound.play("test")
            self.test_receipt = sequence
            self.test_until = now + 10
        if now < self.test_until:
            mode, self.status = "test", "TESTING"
        elif self.due:
            mode, self.status = "due", "RINGING"
        elif self.enabled:
            mode, self.status = None, "IDLE"
        else:
            mode, self.status = None, "DISABLED"
        sound.play(mode)
        # Keep the computer awake while enabled, even before new work arrives.
        if self.enabled and sys.platform == "win32":
            if not ctypes.windll.kernel32.SetThreadExecutionState(0x80000001):
                raise RuntimeError("Windows wake request failed")
        self.save()

    def failed(self, sound, forbidden=False):
        if forbidden:
            self.enabled = self.due = False
            self.test_until = 0
            self.save()
        # Never silently assume work resolved after a failed check. A different
        # repeating tone asks the operator to check connectivity and the queue.
        sound.play("fault" if self.enabled else None)
        self.status = "OFFLINE" if self.enabled else "DISABLED"


def run(cfg, path):
    if cfg["station"] != "KITCHEN":
        return
    api = agent.Api(cfg)
    state = AlarmState(path, dict(api.identity(), api_url=cfg["api_url"]))
    with agent.exclusive_profile(str(path) + ".alarm"):
        sound = None
        try:
            while True:
                try:
                    if sound is None:
                        sound = (MacSound if sys.platform == "darwin" else WindowsSound)(Path(path).parent)
                    response = api.post("alarm", dict(api.identity(), status=state.status,
                                                      testReceipt=state.test_receipt, soundVersion=1))
                    state.update(response, sound, time.monotonic())
                except Exception as exc:
                    try:
                        if sound is not None:
                            state.failed(sound, isinstance(exc, HTTPError) and exc.code in (401, 403, 404, 409))
                            if not isinstance(exc, (HTTPError, URLError, TimeoutError, ValueError)):
                                state.status = "ERROR"
                        else:
                            state.status = "ERROR"
                            # Report missing audio without starting a second scheduler.
                            api.post("alarm", dict(api.identity(), status="ERROR", testReceipt=state.test_receipt, soundVersion=1))
                    except Exception:
                        state.status = "ERROR"
                    print("Kitchen alarm needs attention:", type(exc).__name__, flush=True)
                time.sleep(5)
        finally:
            if sound is not None:
                sound.play(None)

"""Independent Windows kitchen alarm. Never prints or completes an order."""
import ctypes
import json
import math
import os
from pathlib import Path
import struct
import subprocess
import signal
import sys
import time
from urllib.error import HTTPError, URLError
import wave

import agent


class WindowsSound:
    def __init__(self, root):
        if sys.platform != "win32":
            raise RuntimeError("Native kitchen sound requires Windows")
        import winsound
        self.sound = winsound
        self.paths = {}
        for name, frequency in (("due", 880), ("fault", 440), ("test", 660)):
            path = root / ("alarm-" + name + ".wav")
            samples = [int(22000 * math.sin(2 * math.pi * frequency * n / 16000))
                       if n < 8000 else 0 for n in range(32000)]
            with wave.open(str(path), "wb") as wav:
                wav.setparams((1, 2, 16000, len(samples), "NONE", "not compressed"))
                wav.writeframes(struct.pack("<" + "h" * len(samples), *samples))
            self.paths[name] = path

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


class MacSound:
    """macOS Core Audio through the system afplay utility; reassert after interruption."""
    def __init__(self, root):
        self.paths = {}
        self.process = None
        self.mode = None
        for name, frequency in (("due", 880), ("fault", 440), ("test", 660)):
            path = root / ("alarm-" + name + ".wav")
            samples = [int(22000 * math.sin(2 * math.pi * frequency * n / 16000))
                       if n < 8000 else 0 for n in range(32000)]
            with wave.open(str(path), "wb") as wav:
                wav.setparams((1, 2, 16000, len(samples), "NONE", "not compressed"))
                wav.writeframes(struct.pack("<" + "h" * len(samples), *samples))
            self.paths[name] = path

    def play(self, mode):
        if mode == self.mode and self.process is not None and self.process.poll() is None:
            return
        if self.process is not None and self.process.poll() is None:
            os.killpg(self.process.pid, signal.SIGTERM)
            self.process.wait(timeout=3)
        self.process = None
        self.mode = mode
        if mode is not None:
            self.process = subprocess.Popen(
                ["/bin/sh", "-c", 'while /usr/bin/afplay "$1"; do :; done',
                 "gokul-sound", str(self.paths[mode])], start_new_session=True)


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
                                                      testReceipt=state.test_receipt))
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
                            api.post("alarm", dict(api.identity(), status="ERROR", testReceipt=state.test_receipt))
                    except Exception:
                        state.status = "ERROR"
                    print("Kitchen alarm needs attention:", type(exc).__name__, flush=True)
                time.sleep(5)
        finally:
            if sound is not None:
                sound.play(None)

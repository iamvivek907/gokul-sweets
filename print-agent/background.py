"""Windows background entry point. The secret is encrypted for the installing shop account."""
import logging
from logging.handlers import RotatingFileHandler
import os
from pathlib import Path
import sys
import time

import agent


class LogOutput:
    def __init__(self, handler):
        self.handler = handler
        self.logger = logging.getLogger("gokul.print-agent")
        self.logger.setLevel(logging.INFO)
        self.logger.addHandler(handler)

    def write(self, message):
        if message.strip():
            self.logger.info(message.rstrip())
        return len(message)

    def flush(self):
        self.handler.flush()


def main():
    import win32crypt
    root = Path(__file__).resolve().parent
    os.chdir(root)
    # pythonw has no console; keep a bounded diagnostic file without payloads or credentials.
    handler = RotatingFileHandler(root / "agent.log", maxBytes=1024 * 1024,
                                  backupCount=1, encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(asctime)s %(message)s"))
    try:
        sys.stdout = sys.stderr = LogOutput(handler)
        key = win32crypt.CryptUnprotectData((root / "key.dpapi").read_bytes(), None, None, None, 0)[1]
        os.environ["PRINT_AGENT_API_KEY"] = key.decode("utf-8")
        while True:
            try:
                cfg = agent.load_config(root / "config.local.json")
                agent.managed(cfg, root / "config.local.json")
            except Exception as exc:
                print("Background agent waiting:", type(exc).__name__, flush=True)
            time.sleep(15)
    finally:
        handler.close()


if __name__ == "__main__":
    main()

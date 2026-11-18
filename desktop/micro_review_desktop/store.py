"""What the add-on keeps between runs (pairing code, today's skips, snooze) and its log, in the add-on's
user_files folder, which Anki keeps when the add-on is updated.
"""
from __future__ import annotations

import datetime
import json
import os
import threading
from typing import Any, Dict, List

_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "user_files")
_STATE = os.path.join(_FOLDER, "state.json")
_LOG = os.path.join(_FOLDER, "log.txt")
_MAX_LOG_LINES = 300
_lock = threading.Lock()


def load() -> Dict[str, Any]:
    try:
        with open(_STATE, encoding="utf-8") as f:
            data = json.load(f)
            return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save(state: Dict[str, Any]) -> None:
    os.makedirs(_FOLDER, exist_ok=True)
    tmp = _STATE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=1)
    os.replace(tmp, _STATE)


def log(message: str) -> None:
    line = "%s %s" % (datetime.datetime.now().strftime("%m-%d %H:%M:%S"), message)
    print("Micro Review:", message)
    with _lock:
        try:
            os.makedirs(_FOLDER, exist_ok=True)
            lines = read_log()
            lines.append(line)
            with open(_LOG, "w", encoding="utf-8") as f:
                f.write("\n".join(lines[-_MAX_LOG_LINES:]) + "\n")
        except OSError:
            pass


def read_log() -> List[str]:
    try:
        with open(_LOG, encoding="utf-8") as f:
            return [line.rstrip("\n") for line in f if line.strip()]
    except OSError:
        return []

"""Telling the phone that the PC is in use, and that it synced, through ntfy.sh (a free public publish/subscribe
relay), and whether the PC is online. Both block: run them off the main thread.
"""
from __future__ import annotations

import socket
import urllib.request

RELAY = "https://ntfy.sh"
_ONLINE_HOSTS = (("ntfy.sh", 443), ("1.1.1.1", 443), ("8.8.8.8", 443))


def is_online(timeout: float = 4.0) -> bool:
    for host in _ONLINE_HOSTS:
        try:
            socket.create_connection(host, timeout=timeout).close()
            return True
        except OSError:
            continue
    return False


def post_pause(topic: str, seconds: int, timeout: float = 8.0) -> None:
    """The phone stays paused `seconds` from now. Raises on failure."""
    _post(topic, str(int(seconds)), timeout)


def post_synced(topic: str, timeout: float = 8.0) -> None:
    """The PC's answers are on AnkiWeb: the phone syncs to get them (on its sync topic). Raises on failure."""
    _post(topic, "synced", timeout)


def _post(topic: str, message: str, timeout: float) -> None:
    request = urllib.request.Request(
        "%s/%s" % (RELAY, topic),
        data=message.encode("ascii"),
        method="POST",
        headers={
            # Silent if anyone ever subscribes to the topic in the ntfy app.
            "Priority": "min",
            "Title": "Micro Review",
            "User-Agent": "MicroReviewDesktop",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        response.read()

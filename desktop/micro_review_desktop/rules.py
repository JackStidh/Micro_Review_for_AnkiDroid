"""Pure logic of Micro Review Desktop: no Anki, no Qt, no Windows calls, so it can be unit tested anywhere.

Anki 2.1.49 runs Python 3.9: no newer syntax here.
"""
from __future__ import annotations

import datetime
import ntpath
import secrets
from dataclasses import dataclass, field
from typing import Iterable, List, Optional, Sequence, Tuple

# Answer buttons, as on the phone.
AGAIN = 1
HARD = 2
GOOD = 3
EASY = 4

# Unicode isolates Anki puts around numbers in interval texts ("<⁨10⁩m").
_DIRECTION_MARKS = {"⁦", "⁧", "⁨", "⁩"}

PAIRING_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"  # no l, o, 0, 1: easy to type on a phone
PAIRING_LENGTH = 16


def ease_for(button: int, button_count: int) -> Optional[int]:
    """The ease Anki expects for one of the four buttons, given the card's button count (2, 3 or 4)."""
    if button_count >= 4:
        return button if button in (AGAIN, HARD, GOOD, EASY) else None
    if button_count == 3:
        return {AGAIN: 1, GOOD: 2, EASY: 3}.get(button)
    return {AGAIN: 1, GOOD: 2}.get(button)


def shown_buttons(button_count: int, pass_fail_only: bool) -> List[int]:
    """Fail and Pass (Again and Good) with "Pass / Fail only", otherwise the buttons the card has."""
    if pass_fail_only:
        return [AGAIN, GOOD]
    if button_count >= 4:
        return [AGAIN, HARD, GOOD, EASY]
    if button_count == 3:
        return [AGAIN, GOOD, EASY]
    return [AGAIN, GOOD]


def clean_interval(text: Optional[str]) -> Optional[str]:
    """ "<⁨10⁩m" -> "<10m"; None or blank -> None."""
    if text is None:
        return None
    cleaned = "".join(c for c in text if c not in _DIRECTION_MARKS).strip()
    return cleaned or None


@dataclass
class ActiveClock:
    """Time of actual PC use since the last prompt: only seconds when the user was active count."""

    seconds: float = 0.0

    def tick(self, elapsed: float, active: bool) -> None:
        # A long gap between two ticks (sleep, a frozen UI) isn't use.
        if active and 0 < elapsed <= 60:
            self.seconds += elapsed

    def due(self, interval_minutes: int) -> bool:
        return interval_minutes > 0 and self.seconds >= interval_minutes * 60

    def minutes_left(self, interval_minutes: int) -> int:
        left = max(0.0, interval_minutes * 60 - self.seconds)
        return int((left + 59) // 60)

    def reset(self) -> None:
        self.seconds = 0.0


@dataclass
class PhonePause:
    """What to tell the phone, which stays paused until `pause_seconds` after the PC's last input.

    Each message says how many seconds the phone stays paused from the moment the relay receives it; the phone
    follows the latest message. While the PC is used, a message every `refresh_seconds` keeps the phone paused a
    little longer than until the next one (if the PC sleeps without a word, the phone resumes soon after). When the
    use stops, one message gives the exact remainder: `pause_seconds` after the last input. A few messages an hour.
    """

    pause_seconds: int = 300
    refresh_seconds: int = 240
    margin_seconds: int = 120
    # No input for this long: the use has stopped (the pause runs out from the last input).
    stopped_after: int = 60
    told_in_use: bool = False
    sent_at: float = 0.0

    def message(self, now: float, idle_seconds: float) -> Optional[int]:
        """Seconds to send now, or None. `now`: epoch seconds; `idle_seconds`: since the last mouse or keyboard input."""
        if self.pause_seconds <= 0:
            return None
        if idle_seconds < self.stopped_after:
            if not self.told_in_use or now - self.sent_at >= self.refresh_seconds:
                return self.pause_seconds + self.margin_seconds
            return None
        if self.told_in_use:
            # 0: the phone resumes now.
            return max(0, int(round(self.pause_seconds - idle_seconds)))
        return None

    def sent(self, now: float, idle_seconds: float) -> None:
        self.told_in_use = idle_seconds < self.stopped_after
        self.sent_at = now

    def final(self, idle_seconds: float) -> Optional[int]:
        """When the add-on stops (Anki quits): the remainder, if the phone was told the PC is in use."""
        if not self.told_in_use or self.pause_seconds <= 0:
            return None
        return max(0, int(round(self.pause_seconds - idle_seconds)))


def skips_left(per_day: int, used_day: Optional[str], used: int, today: str) -> int:
    """Emergency skips left today. `used_day` is the day `used` was counted on (ISO date)."""
    return max(0, per_day - (used if used_day == today else 0))


def until_morning(now: datetime.datetime, morning_hour: int) -> datetime.datetime:
    """The next time the clock shows `morning_hour`:00 strictly after `now` ("snooze until tomorrow")."""
    hour = min(max(morning_hour, 0), 23)
    today = now.replace(hour=hour, minute=0, second=0, microsecond=0)
    return today if today > now else today + datetime.timedelta(days=1)


def new_pairing_code() -> str:
    return "".join(secrets.choice(PAIRING_ALPHABET) for _ in range(PAIRING_LENGTH))


def display_code(code: str) -> str:
    return "-".join(code[i : i + 4] for i in range(0, len(code), 4))


def topic(code: str) -> str:
    """The relay topic the phone reads (the phone's PcPairing.topic)."""
    return "mrf-" + code


def sync_topic(code: str) -> str:
    """The relay topic where the PC says it synced its answers and which cards it reviewed (PcPairing.syncTopic)."""
    return topic(code) + "-sync"


# The relay takes messages up to 4096 bytes: 200 cards ("1712345678901:0," each) fit.
SYNCED_CARDS_MAX = 200

# The relay keeps messages 12 hours: older reviews can't matter to the phone.
SYNCED_LOOKBACK_MS = 12 * 3600 * 1000


def synced_message(cards: Iterable[Tuple[int, int]]) -> str:
    """"synced" and the cards reviewed here (note ID, card ordinal), latest first, each once, at most
    SYNCED_CARDS_MAX: the phone passes over them until it syncs (PcPairing.syncedCards). A plain "synced" when
    there are none.
    """
    keys: List[str] = []
    for nid, ordinal in cards:
        key = "%d:%d" % (nid, ordinal)
        if key not in keys:
            keys.append(key)
            if len(keys) >= SYNCED_CARDS_MAX:
                break
    return "synced " + ",".join(keys) if keys else "synced"


def reviews_since(told_phone_ms: Optional[int], now_ms: int) -> int:
    """From when (revlog ID, epoch milliseconds) to list the reviews for the phone: its last message, at most
    SYNCED_LOOKBACK_MS back."""
    return max(int(told_phone_ms or 0), now_ms - SYNCED_LOOKBACK_MS)


def wants_pull(logged_in: bool, enabled: bool, now: float, synced_at: float, tried_at: float, max_age: float) -> bool:
    """Whether to sync before showing cards: the phone's latest answers, so a card isn't reviewed twice.

    Not when the last sync, or the last try (a failing sync doesn't hold the cards back), is recent.
    """
    return logged_in and enabled and now - max(synced_at, tried_at) > max_age


def anki_program(argv0: str, executable: str) -> Optional[str]:
    """Anki's own program on Windows, to start it with Windows, or None when Anki runs from a Python install.

    Anki 26.05 and later (Briefcase installer): argv[0] is Anki.exe and sys.executable the bundled pythonw.exe.
    Anki 2.1.x (PyInstaller): both are anki.exe. The 25.07-25.09 launcher runs Anki from a Python
    environment: neither is Anki's program.
    """
    for path in (argv0, executable):
        name = ntpath.basename(path or "").lower()
        if name.endswith(".exe") and not name.startswith("python"):
            return path
    return None


@dataclass
class GameRule:
    """A process that counts as a game: by name, and optionally only with a window title containing `title`."""

    process: str
    title: Optional[str] = None

    @staticmethod
    def parse(entry: str) -> Optional["GameRule"]:
        """ "Minecraft.Windows.exe", or "javaw.exe:Minecraft" (javaw.exe with "Minecraft" in a window title)."""
        entry = entry.strip()
        if not entry:
            return None
        if ":" in entry:
            process, title = entry.split(":", 1)
            return GameRule(process.strip().lower(), title.strip().lower() or None)
        return GameRule(entry.lower())


@dataclass
class GameSigns:
    """What the PC shows that may mean a game is being played."""

    steam_app_id: int = 0
    # (process name, window title) of the windows that are open.
    windows: Sequence[Tuple[str, str]] = field(default_factory=list)
    exclusive_fullscreen: bool = False
    foreground_fullscreen: bool = False
    foreground_process: Optional[str] = None


def game_reason(signs: GameSigns, rules: Iterable[GameRule], fullscreen_counts: bool, browsers: Iterable[str]) -> Optional[str]:
    """Why a game seems to be running (for the log), or None.

    Steam says which game runs; the listed processes are games wherever their window is; a full-screen app in
    front counts too, unless it is a browser (a full-screen video isn't a game).
    """
    if signs.steam_app_id:
        return "steam game %d" % signs.steam_app_id
    for rule in rules:
        for process, title in signs.windows:
            if process.lower() == rule.process and (rule.title is None or rule.title in title.lower()):
                return process if rule.title is None else "%s (%s)" % (process, title)
    if fullscreen_counts:
        if signs.exclusive_fullscreen:
            return "full-screen game"
        browser_names = {b.lower() for b in browsers}
        front = (signs.foreground_process or "").lower()
        if signs.foreground_fullscreen and front and front not in browser_names:
            return "full screen: %s" % front
    return None

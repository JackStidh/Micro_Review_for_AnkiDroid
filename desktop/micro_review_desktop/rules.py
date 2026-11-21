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


# A few minutes without input: up and away from the desk, the sitting time starts over.
AWAY_SECONDS = 180
# No input for this long while the stand-up prompt shows: stood up, the break counts.
STOOD_UP_SECONDS = 45


def stepped_away(idle_seconds: float, shown_seconds: float) -> bool:
    """While the stand-up prompt shows (for `shown_seconds`): no input for STOOD_UP_SECONDS since some input after it
    appeared (not just a quiet stretch that was already running), or no input long enough to be away anyway."""
    return (idle_seconds >= STOOD_UP_SECONDS and idle_seconds <= shown_seconds) or idle_seconds >= AWAY_SECONDS


# The spell breaker on the PC: a stretch in distracting windows (a site in a browser tab's title, an app).
DISTRACTION_GRACE_SECONDS = 90
SPELL_MORE_SECONDS = 5 * 60
_SPELL_HOLD = (0, 3, 8, 15)


def spell_hold_seconds(strikes: int) -> int:
    """How long "5 more minutes" must be held: longer each time it's chosen in the same stretch."""
    return _SPELL_HOLD[max(0, min(strikes, len(_SPELL_HOLD) - 1))]


def distraction(title: Optional[str], process: Optional[str], words: Iterable[str]) -> Optional[str]:
    """The distraction in front, if any: the first of `words` in the window's title or its process name, as
    configured (case ignored)."""
    text = ((title or "") + " " + (process or "")).lower()
    for word in words:
        if word and word.strip().lower() in text:
            return word.strip()
    return None


@dataclass
class DistractionClock:
    """A stretch in distracting windows: started at `started`, last seen at `last_seen` (monotonic seconds); short
    exits (DISTRACTION_GRACE_SECONDS) don't end it. `next_at` the next prompt (0: the threshold after the start),
    `strikes` "5 more" chosen, `exempt` "on purpose"."""

    started: float = 0.0
    last_seen: float = 0.0
    what: Optional[str] = None
    next_at: float = 0.0
    strikes: int = 0
    exempt: bool = False

    def active(self, now: float) -> bool:
        return self.started > 0 and now - self.last_seen < DISTRACTION_GRACE_SECONDS

    def tick(self, now: float, what: Optional[str], idle_seconds: float) -> None:
        if what is not None and idle_seconds < AWAY_SECONDS:
            if not self.active(now):
                self.started, self.next_at, self.strikes, self.exempt = now, 0.0, 0, False
            self.last_seen, self.what = now, what
        elif not self.active(now):
            self.started = 0.0

    def due(self, now: float, minutes: int, in_front: bool) -> bool:
        if minutes <= 0 or not in_front or not self.active(now) or self.exempt:
            return False
        at = self.next_at or self.started + minutes * 60
        return now >= at

    def minutes(self, now: float) -> int:
        return int((now - self.started) // 60)

    def more(self, now: float) -> None:
        self.next_at = now + SPELL_MORE_SECONDS
        self.strikes += 1

    def reset(self) -> None:
        self.started = 0.0


@dataclass
class SittingClock:
    """How long the user has been sitting at the PC: since the last break. Unlike ActiveClock, time without input
    counts (reading, a video) until AWAY_SECONDS, which is a break; so is a long gap between ticks (sleep)."""

    seconds: float = 0.0

    def tick(self, elapsed: float, idle_seconds: float, away_seconds: float = AWAY_SECONDS) -> None:
        if elapsed > 60 or idle_seconds >= away_seconds:
            self.seconds = 0.0
        elif elapsed > 0:
            self.seconds += elapsed

    def due(self, minutes: int) -> bool:
        return minutes > 0 and self.seconds >= minutes * 60

    def minutes(self) -> int:
        return int(self.seconds // 60)

    def reset(self) -> None:
        self.seconds = 0.0


@dataclass(frozen=True)
class DeskMove:
    """A stand-up break's exercise, the same as Micro Review Focus's desk breaks (MovePlan.DESK, strings.xml)."""

    id: str
    name: str
    dose: str
    how: str


DESK_MOVES = (
    DeskMove("standing_extension", "Standing back extension", "10 reps",
             "Feet apart, hands on your lower back. Lean back gently from the waist, hips forward, as far as is "
             "comfortable, and come back up. The quick undo after sitting."),
    DeskMove("walk_break", "Walk", "2 minutes",
             "Get up and walk: to the kitchen, around the room, up the stairs. Stand tall, feet pointing forward."),
    DeskMove("chin_tuck", "Chin tuck", "10 reps, 5 s holds",
             "Standing tall: glide your head straight back, making a double chin, eyes level, until the back of your "
             "neck lengthens. Hold, relax."),
    DeskMove("pec_stretch", "Doorway chest stretch", "2 \u00d7 30 s",
             "Forearm on a door frame, elbow at shoulder height. Step through gently until the front of your chest "
             "stretches. Ribs down, no arching."),
    DeskMove("hinge", "Hip hinge practice", "10 reps",
             "Stand with soft knees. Push your hips back as if closing a car door behind you, chest forward, back "
             "straight. Come up by squeezing your glutes."),
    DeskMove("calf_raise", "Calf raise", "15 reps",
             "Stand tall, a hand on the desk for balance. Rise onto your toes, pause, lower slowly."),
    DeskMove("standing_hip_flexor", "Standing hip flexor stretch", "30 s per side",
             "Long step stance, back heel up. Tuck your tailbone under and squeeze the back glute until the front of "
             "that hip stretches. Stay tall."),
    DeskMove("wall_angel", "Wall angel", "8 reps",
             "Head, upper back and hips against a wall, feet a step out. Arms in a W against the wall: slide them up "
             "to a Y and back down, only as far as they stay on the wall."),
    DeskMove("scap_squeeze", "Shoulder blade squeeze", "10 reps, 3 s holds",
             "Arms by your sides. Draw your shoulder blades back and down, toward your back pockets, without "
             "shrugging or arching. Hold, relax."),
)


def desk_move(count: int) -> DeskMove:
    """The stand-up break after `count` breaks: each in turn."""
    return DESK_MOVES[count % len(DESK_MOVES)]


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

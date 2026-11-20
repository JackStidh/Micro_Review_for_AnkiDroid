import datetime
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from micro_review_desktop import rules  # noqa: E402
from micro_review_desktop.rules import AGAIN, EASY, GOOD, HARD, ActiveClock, GameRule, GameSigns, PhonePause  # noqa: E402


def test_ease_follows_the_button_count():
    assert rules.ease_for(GOOD, 4) == 3
    assert rules.ease_for(GOOD, 3) == 2
    assert rules.ease_for(GOOD, 2) == 2
    assert rules.ease_for(AGAIN, 2) == 1
    assert rules.ease_for(HARD, 3) is None
    assert rules.ease_for(EASY, 2) is None


def test_pass_fail_shows_two_buttons():
    assert rules.shown_buttons(4, True) == [AGAIN, GOOD]
    assert rules.shown_buttons(4, False) == [AGAIN, HARD, GOOD, EASY]
    assert rules.shown_buttons(3, False) == [AGAIN, GOOD, EASY]


def test_intervals_lose_their_direction_marks():
    assert rules.clean_interval("<⁨10⁩m") == "<10m"
    assert rules.clean_interval("⁨4⁩d") == "4d"
    assert rules.clean_interval("  ") is None
    assert rules.clean_interval(None) is None


def test_only_active_time_counts():
    clock = ActiveClock()
    for _ in range(239):
        clock.tick(5, active=True)
    clock.tick(5, active=False)
    assert not clock.due(20)
    clock.tick(5, active=True)
    assert clock.due(20)
    assert clock.minutes_left(20) == 0
    clock.reset()
    assert clock.minutes_left(20) == 20


def test_a_long_gap_is_not_use():
    clock = ActiveClock()
    clock.tick(3600, active=True)
    clock.tick(-5, active=True)
    assert clock.seconds == 0


def test_the_phone_is_told_when_use_starts_and_every_few_minutes():
    pause = PhonePause(pause_seconds=300, refresh_seconds=240, margin_seconds=120, stopped_after=60)
    now = 1_000_000.0
    # Use starts: paused until a little after the next word.
    assert pause.message(now, idle_seconds=0) == 420
    pause.sent(now, idle_seconds=0)
    # Still in use a minute later: nothing new to say.
    assert pause.message(now + 60, idle_seconds=2) is None
    # Four minutes later: again.
    assert pause.message(now + 240, idle_seconds=0) == 420
    pause.sent(now + 240, idle_seconds=0)


def test_when_use_stops_the_phone_gets_the_exact_remainder():
    pause = PhonePause(pause_seconds=300, stopped_after=60)
    now = 1_000_000.0
    pause.sent(now, idle_seconds=0)
    # A minute without input: the pause ends 5 minutes after the last input, 4 minutes from now.
    assert pause.message(now + 61, idle_seconds=61) == 239
    pause.sent(now + 61, idle_seconds=61)
    # Nothing more while idle.
    assert pause.message(now + 200, idle_seconds=200) is None
    # Use again: paused again.
    assert pause.message(now + 300, idle_seconds=0) == 420


def test_an_idle_pc_says_nothing_and_quitting_says_the_remainder():
    pause = PhonePause(pause_seconds=300)
    assert pause.message(1_000_000.0, idle_seconds=500) is None
    assert pause.final(idle_seconds=10) is None
    pause.sent(1_000_000.0, idle_seconds=0)
    assert pause.final(idle_seconds=10) == 290
    assert pause.final(idle_seconds=1000) == 0
    assert PhonePause(pause_seconds=0).message(1_000_000.0, idle_seconds=0) is None


def test_one_emergency_skip_per_day():
    assert rules.skips_left(1, None, 0, "2026-11-18") == 1
    assert rules.skips_left(1, "2026-11-18", 1, "2026-11-18") == 0
    assert rules.skips_left(1, "2026-11-18", 1, "2026-11-19") == 1
    assert rules.skips_left(0, None, 0, "2026-11-18") == 0


def test_until_tomorrow_is_the_next_morning():
    evening = datetime.datetime(2026, 11, 18, 21, 30)
    assert rules.until_morning(evening, 7) == datetime.datetime(2026, 11, 19, 7, 0)
    night = datetime.datetime(2026, 11, 19, 1, 0)
    assert rules.until_morning(night, 7) == datetime.datetime(2026, 11, 19, 7, 0)


def test_pairing_codes_match_the_phone():
    code = rules.new_pairing_code()
    assert len(code) == 16 and all(c in rules.PAIRING_ALPHABET for c in code)
    assert rules.display_code("k3p9x2mq7rtvw8de") == "k3p9-x2mq-7rtv-w8de"
    assert rules.topic("k3p9x2mq7rtvw8de") == "mrf-k3p9x2mq7rtvw8de"


def test_game_rules_are_read_from_the_settings():
    assert GameRule.parse("Minecraft.Windows.exe") == GameRule("minecraft.windows.exe")
    assert GameRule.parse("javaw.exe:Minecraft") == GameRule("javaw.exe", "minecraft")
    assert GameRule.parse("  ") is None


RULES = [GameRule.parse("Minecraft.Windows.exe"), GameRule.parse("javaw.exe:Minecraft")]
BROWSERS = ["chrome.exe", "msedge.exe", "firefox.exe"]


def reason(**signs):
    return rules.game_reason(GameSigns(**signs), RULES, True, BROWSERS)


def test_nothing_running_is_no_game():
    assert reason() is None
    assert reason(windows=[("javaw.exe", "Some Java tool"), ("chrome.exe", "YouTube")]) is None


def test_steam_minecraft_and_fullscreen_are_games():
    assert reason(steam_app_id=730) == "steam game 730"
    assert reason(windows=[("javaw.exe", "Minecraft 1.21.4")]) == "javaw.exe (Minecraft 1.21.4)"
    assert reason(windows=[("Minecraft.Windows.exe", "Minecraft")]) == "Minecraft.Windows.exe"
    assert reason(exclusive_fullscreen=True) == "full-screen game"
    assert reason(foreground_fullscreen=True, foreground_process="eldenring.exe") == "full screen: eldenring.exe"


def test_a_fullscreen_browser_is_not_a_game():
    assert reason(foreground_fullscreen=True, foreground_process="chrome.exe") is None
    assert rules.game_reason(GameSigns(exclusive_fullscreen=True), RULES, False, BROWSERS) is None


def test_anki_program_for_each_installer():
    # Anki 26.05+ (Briefcase): argv[0] is the program, sys.executable the bundled Python.
    assert rules.anki_program(r"C:\Users\u\AppData\Local\Programs\Anki\Anki.exe",
                              r"C:\Users\u\AppData\Local\Programs\Anki\pythonw.exe") == r"C:\Users\u\AppData\Local\Programs\Anki\Anki.exe"
    # Anki 2.1.x (PyInstaller): both are anki.exe.
    assert rules.anki_program(r"C:\Program Files\Anki\anki.exe", r"C:\Program Files\Anki\anki.exe") == r"C:\Program Files\Anki\anki.exe"
    # Anki run from Python (the 25.07 launcher, a pip install): no program to start.
    assert rules.anki_program("-c", r"C:\x\.venv\Scripts\pythonw.exe") is None
    assert rules.anki_program("", "") is None


def test_sync_topic():
    assert rules.sync_topic("k3p9x2mq7rtvw8de") == "mrf-k3p9x2mq7rtvw8de-sync"


def test_pull_before_cards():
    # Never synced: sync first.
    assert rules.wants_pull(True, True, now=1000.0, synced_at=-1e9, tried_at=-1e9, max_age=60)
    # Just synced, or just tried (a failing sync doesn't hold the cards back): the cards now.
    assert not rules.wants_pull(True, True, now=1000.0, synced_at=980.0, tried_at=-1e9, max_age=60)
    assert not rules.wants_pull(True, True, now=1000.0, synced_at=-1e9, tried_at=990.0, max_age=60)
    # Not logged in, or syncing turned off.
    assert not rules.wants_pull(False, True, now=1000.0, synced_at=-1e9, tried_at=-1e9, max_age=60)
    assert not rules.wants_pull(True, False, now=1000.0, synced_at=-1e9, tried_at=-1e9, max_age=60)


def test_synced_message_lists_the_cards_once_latest_first():
    assert rules.synced_message([]) == "synced"
    assert rules.synced_message([(1712345678901, 0), (1712345678902, 1), (1712345678901, 0)]) == (
        "synced 1712345678901:0,1712345678902:1"
    )


def test_synced_message_fits_the_relay():
    message = rules.synced_message((1712345678901 + i, 12) for i in range(1000))
    assert message.count(",") == rules.SYNCED_CARDS_MAX - 1
    assert len(message.encode("ascii")) < 4096


def test_reviews_since_the_last_message_within_the_relays_12_hours():
    now = 100 * 3600 * 1000
    assert rules.reviews_since(None, now) == now - rules.SYNCED_LOOKBACK_MS
    assert rules.reviews_since(now - 60_000, now) == now - 60_000
    assert rules.reviews_since(1, now) == now - rules.SYNCED_LOOKBACK_MS


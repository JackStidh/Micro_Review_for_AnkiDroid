"""Micro Review Desktop: every so often, a few cards over whatever you're doing on the PC.

Runs inside Anki: in the tray, started with Windows. Counts the time the PC is actually used; after the interval,
asks for a few cards (prompt.py) unless a game is running, Anki is snoozed or the PC is offline. While the PC is
used, tells the phone (Micro Review Focus) to pause, through the relay (relay.py).
"""
from __future__ import annotations

import datetime
import sys
import time
from typing import Any, Dict, Optional

from aqt import gui_hooks
from aqt.qt import QAction, QIcon, QMenu, QSystemTrayIcon, QTimer

from . import relay, rules, store, windows

ADDON = __name__.split(".")[0]
TICK_MS = 5000
ONLINE_CHECK_SECONDS = 60
GAME_CHECK_SECONDS = 30

DEFAULTS = {
    "interval_minutes": 20,
    "cards_per_prompt": 5,
    "decks": [],
    "pass_fail_only": True,
    "show_intervals": True,
    "play_audio": False,
    "skips_per_day": 1,
    "require_internet": True,
    "idle_after_seconds": 120,
    "pause_phone_while_in_use": True,
    "phone_pause_minutes": 5,
    "gaming_shutoff": True,
    "game_processes": ["Minecraft.Windows.exe", "javaw.exe:Minecraft"],
    "fullscreen_counts_as_game": True,
    "browsers": ["chrome.exe", "msedge.exe", "firefox.exe", "opera.exe", "brave.exe", "vivaldi.exe"],
    "start_with_windows": True,
    "start_hidden": True,
    "keep_running_when_closed": True,
    "sync_after_cards": True,
    "morning_hour": 7,
}  # type: Dict[str, Any]


class Controller:
    def __init__(self, mw):
        self.mw = mw
        self.state = store.load()
        self.clock = rules.ActiveClock()
        self.phone = rules.PhonePause()
        self.prompt = None
        self.tray = None  # type: Optional[QSystemTrayIcon]
        self.online = None  # type: Optional[bool]
        self.online_checked = 0.0
        self.game = None  # type: Optional[str]
        self.game_checked = 0.0
        self.phone_busy = False
        self.phone_retry_at = 0.0
        self.allow_quit = False
        self.hidden_once = False
        self.last_tick = time.monotonic()
        self.timer = QTimer(mw)
        self.timer.timeout.connect(self.tick)

    # Settings and state

    def cfg(self) -> Dict[str, Any]:
        config = dict(DEFAULTS)
        config.update(self.mw.addonManager.getConfig(ADDON) or {})
        return config

    def save_cfg(self, config: Dict[str, Any]) -> None:
        self.mw.addonManager.writeConfig(ADDON, config)
        self.apply_autostart()

    def _save_state(self) -> None:
        try:
            store.save(self.state)
        except OSError as error:
            store.log("can't save state: %r" % error)

    def pairing_code(self) -> str:
        code = self.state.get("pairing_code")
        if not code:
            code = rules.new_pairing_code()
            self.state["pairing_code"] = code
            self._save_state()
        return code

    def new_pairing_code(self) -> str:
        self.state["pairing_code"] = rules.new_pairing_code()
        self._save_state()
        self.phone = rules.PhonePause(pause_seconds=self.cfg()["phone_pause_minutes"] * 60)
        return self.state["pairing_code"]

    def skips_left(self) -> int:
        today = datetime.date.today().isoformat()
        return rules.skips_left(self.cfg()["skips_per_day"], self.state.get("skip_day"), self.state.get("skips_used", 0), today)

    def use_skip(self) -> bool:
        if self.skips_left() <= 0:
            return False
        today = datetime.date.today().isoformat()
        used = self.state.get("skips_used", 0) if self.state.get("skip_day") == today else 0
        self.state["skip_day"] = today
        self.state["skips_used"] = used + 1
        self._save_state()
        return True

    def snoozed_until(self) -> float:
        return float(self.state.get("snooze_until", 0))

    def snooze(self, minutes: Optional[int]) -> None:
        """For `minutes`, or until tomorrow morning when None."""
        if minutes is None:
            until = rules.until_morning(datetime.datetime.now(), self.cfg()["morning_hour"]).timestamp()
        else:
            until = time.time() + minutes * 60
        self.state["snooze_until"] = until
        self._save_state()
        store.log("snoozed until %s" % datetime.datetime.fromtimestamp(until).strftime("%a %H:%M"))
        self._update_tray()

    def resume(self) -> None:
        self.state["snooze_until"] = 0
        self._save_state()
        store.log("resumed")
        self._update_tray()

    # Life cycle

    def start(self) -> None:
        cfg = self.cfg()
        self.phone = rules.PhonePause(pause_seconds=cfg["phone_pause_minutes"] * 60)
        self.pairing_code()
        self._setup_tray()
        self.apply_autostart()
        self.last_tick = time.monotonic()
        self.timer.start(TICK_MS)
        store.log("started (Anki %s, %s)" % (_anki_version(), "Windows" if windows.IS_WINDOWS else sys.platform))
        if cfg["start_hidden"] and _started_with_windows() and not self.hidden_once and self.tray is not None:
            self.hidden_once = True
            QTimer.singleShot(1500, self.mw.hide)

    def stop(self) -> None:
        self.timer.stop()
        # The phone shouldn't stay paused for a PC that is going away.
        seconds = self.phone.final(windows.idle_seconds())
        topic = self._phone_topic()
        if seconds is not None and topic is not None:
            try:
                relay.post_pause(topic, seconds, timeout=3)
            except Exception:
                pass
        if self.prompt is not None:
            self.prompt.finish()

    # The tick

    def tick(self) -> None:
        now = time.monotonic()
        elapsed = now - self.last_tick
        self.last_tick = now
        try:
            self._tick(now, elapsed)
        except Exception as error:
            store.log("tick failed: %r" % error)

    def _tick(self, now: float, elapsed: float) -> None:
        cfg = self.cfg()
        idle = windows.idle_seconds()
        active = idle < cfg["idle_after_seconds"]
        self._check_online(now)
        self._tell_phone(idle)
        self._update_tray()
        if self.prompt is not None or self.mw.col is None:
            return
        if time.time() < self.snoozed_until():
            return
        if cfg["require_internet"] and not self.online:
            return
        if self._studying_in_anki():
            # Reviewing in Anki itself: that's the point.
            self.clock.reset()
            return
        self.clock.tick(elapsed, active)
        if not self.clock.due(cfg["interval_minutes"]) or not active:
            return
        if cfg["gaming_shutoff"]:
            if now - self.game_checked > GAME_CHECK_SECONDS:
                self._check_game(now)
                return
            if self.game:
                return
        self.show_prompt()

    def _studying_in_anki(self) -> bool:
        return self.mw.state == "review" and self.mw.isVisible() and self.mw.isActiveWindow()

    def _check_online(self, now: float) -> None:
        if now - self.online_checked < ONLINE_CHECK_SECONDS:
            return
        self.online_checked = now

        def done(future) -> None:
            try:
                online = future.result()
            except Exception:
                online = False
            if online != self.online:
                store.log("online" if online else "offline: no cards and no phone pause until the PC is online")
            self.online = online

        self.mw.taskman.run_in_background(relay.is_online, done)

    def _check_game(self, now: float) -> None:
        self.game_checked = now
        cfg = self.cfg()
        game_rules = [r for r in (rules.GameRule.parse(e) for e in cfg["game_processes"]) if r is not None]

        def done(future) -> None:
            try:
                reason = rules.game_reason(future.result(), game_rules, cfg["fullscreen_counts_as_game"], cfg["browsers"])
            except Exception as error:
                store.log("game check failed: %r" % error)
                reason = None
            if reason != self.game:
                store.log("game: %s, cards wait" % reason if reason else "no game")
            self.game = reason
            # Checked: if nothing is in the way, the next tick shows the cards.

        self.mw.taskman.run_in_background(windows.game_signs, done)

    # The phone

    def _phone_topic(self) -> Optional[str]:
        cfg = self.cfg()
        if not cfg["pause_phone_while_in_use"]:
            return None
        return rules.topic(self.pairing_code())

    def _tell_phone(self, idle: float) -> None:
        topic = self._phone_topic()
        if topic is None or self.phone_busy or not self.online or time.time() < self.phone_retry_at:
            return
        now = time.time()
        seconds = self.phone.message(now, idle)
        if seconds is None:
            return
        self.phone_busy = True

        def done(future) -> None:
            self.phone_busy = False
            try:
                future.result()
                self.phone.sent(now, idle)
            except Exception as error:
                store.log("couldn't tell the phone: %r" % error)
                self.phone_retry_at = time.time() + 60

        self.mw.taskman.run_in_background(lambda: relay.post_pause(topic, seconds), done)

    # The cards

    def show_prompt(self, session: bool = False) -> None:
        """The prompt's cards, or (`session`) cards for as long as wanted."""
        from .prompt import PROMPT, SESSION, CardPrompt

        if self.prompt is not None or self.mw.col is None:
            return
        cfg = self.cfg()
        prompt = CardPrompt(self, SESSION if session else PROMPT, cfg["cards_per_prompt"])
        self.prompt = prompt
        try:
            shown = prompt.start()
        except Exception as error:
            store.log("can't show cards: %r" % error)
            shown = False
        if not shown:
            self.prompt = None
            prompt.deleteLater()
            self.clock.reset()
            store.log("cards due, but nothing to review")
            return
        store.log("cards shown" if not session else "study session started")

    def prompt_closed(self, answered: int) -> None:
        self.prompt = None
        self.clock.reset()
        store.log("cards done: %d answered" % answered)
        if hasattr(self.mw, "reset"):
            try:
                self.mw.reset()
            except Exception:
                pass
        if answered and self.cfg()["sync_after_cards"]:
            QTimer.singleShot(1500, self._sync)
        self._update_tray()

    def _sync(self) -> None:
        try:
            if self.mw.pm.sync_auth() and self.prompt is None:
                self.mw.on_sync_button_clicked()
        except Exception as error:
            store.log("sync failed: %r" % error)

    # Status

    def status(self) -> str:
        cfg = self.cfg()
        snooze = self.snoozed_until()
        if time.time() < snooze:
            return "Snoozed until %s" % datetime.datetime.fromtimestamp(snooze).strftime("%a %H:%M")
        if cfg["require_internet"] and self.online is False:
            return "Offline: paused"
        if self.game:
            return "Game running (%s): cards wait" % self.game
        return "Cards in %d min of use" % self.clock.minutes_left(cfg["interval_minutes"])

    # Tray, window and Windows

    def _setup_tray(self) -> None:
        if self.tray is not None or not QSystemTrayIcon.isSystemTrayAvailable():
            return
        icon = self.mw.windowIcon()
        if icon.isNull():
            icon = QIcon(":/icons/anki.png")
        tray = QSystemTrayIcon(icon, self.mw)
        menu = QMenu(self.mw)
        self.status_action = QAction("", menu)
        self.status_action.setEnabled(False)
        menu.addAction(self.status_action)
        menu.addSeparator()
        add_action(menu, "Study now", lambda: self.show_prompt(session=True))
        snooze = menu.addMenu("Snooze")
        for label, minutes in (("15 minutes", 15), ("1 hour", 60), ("3 hours", 180)):
            add_action(snooze, label, lambda m=minutes: self.snooze(m))
        add_action(snooze, "Until tomorrow", lambda: self.snooze(None))
        self.resume_action = add_action(menu, "Resume", self.resume)
        menu.addSeparator()
        add_action(menu, "Open Anki", self.open_anki)
        add_action(menu, "Micro Review settings…", self.open_settings)
        menu.addSeparator()
        add_action(menu, "Quit Anki", self.quit)
        tray.setContextMenu(menu)
        tray.activated.connect(self._tray_activated)
        tray.show()
        self.tray = tray
        self._update_tray()

    def _tray_activated(self, reason) -> None:
        if reason in (QSystemTrayIcon.ActivationReason.Trigger, QSystemTrayIcon.ActivationReason.DoubleClick):
            self.open_anki()

    def _update_tray(self) -> None:
        if self.tray is None:
            return
        status = self.status()
        self.tray.setToolTip("Micro Review: " + status)
        self.status_action.setText(status)
        self.resume_action.setVisible(time.time() < self.snoozed_until())

    def open_anki(self) -> None:
        self.mw.show()
        self.mw.showNormal()
        self.mw.raise_()
        self.mw.activateWindow()

    def open_settings(self) -> None:
        from .settings import SettingsDialog

        SettingsDialog(self).exec()

    def quit(self) -> None:
        self.allow_quit = True
        self.mw.close()

    def close_event(self, original, event) -> None:
        """The window's close button hides it in the tray; File › Exit, Quit and Windows shutting down still quit."""
        cfg = self.cfg()
        if (
            not self.allow_quit
            and cfg["keep_running_when_closed"]
            and self.tray is not None
            and event.spontaneous()
            and self.mw.state != "profileManager"
        ):
            event.ignore()
            self.mw.hide()
            if not self.state.get("told_tray"):
                self.tray.showMessage("Micro Review", "Anki keeps running here, for your cards. Quit it from this icon.")
                self.state["told_tray"] = True
                self._save_state()
            return
        original(event)

    def apply_autostart(self) -> None:
        if not windows.IS_WINDOWS or self.mw.pm is None or not getattr(self.mw.pm, "name", None):
            return
        wanted = windows.autostart_command(self.mw.pm.name) if self.cfg()["start_with_windows"] else None
        if wanted is None and self.cfg()["start_with_windows"]:
            return  # Not Anki's installed program: nothing to start.
        if windows.autostart() != wanted:
            if windows.set_autostart(wanted):
                store.log("start with Windows: %s" % ("on" if wanted else "off"))


def add_action(menu, text: str, handler) -> QAction:
    """A menu entry, in a way that works with both Qt5 and Qt6 builds of Anki."""
    action = QAction(text, menu)
    action.triggered.connect(lambda *_: handler())
    menu.addAction(action)
    return action


def _started_with_windows() -> bool:
    """The autostart command names the profile (-p); opening Anki by hand doesn't."""
    return any(arg in ("-p", "--profile") or arg.startswith("--profile=") for arg in sys.argv[1:])


def _anki_version() -> str:
    try:
        from anki.buildinfo import version

        return version
    except Exception:
        return "?"


_controller = None  # type: Optional[Controller]


def setup(mw) -> None:
    global _controller
    controller = Controller(mw)
    _controller = controller

    original = mw.closeEvent
    mw.closeEvent = lambda event: controller.close_event(original, event)
    # Windows signing out or shutting down: quit for real.
    mw.app.commitDataRequest.connect(lambda *_: setattr(controller, "allow_quit", True))

    add_action(mw.form.menuTools, "Micro Review Desktop…", controller.open_settings)
    mw.addonManager.setConfigAction(ADDON, controller.open_settings)

    gui_hooks.profile_did_open.append(controller.start)
    gui_hooks.profile_will_close.append(controller.stop)

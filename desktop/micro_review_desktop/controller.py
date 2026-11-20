"""Micro Review Desktop: every so often, a few cards over whatever you're doing on the PC.

Runs inside Anki: in the tray, started with Windows. Counts the time the PC is actually used (input in the last 30
seconds); after the interval, asks for a few cards (prompt.py) unless a game is running, Anki is snoozed or the PC is
offline. While the PC is used, tells the phone (Micro Review Focus) to pause, through the relay (relay.py).

Syncs with AnkiWeb before the cards (the phone's latest answers, so a card isn't reviewed twice) and after them, in
the background, without Anki's sync window; after a sync it tells the phone, which then syncs too.
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
# Cards shortly after a sync don't need another one first.
PULL_MAX_AGE_SECONDS = 60

DEFAULTS = {
    "interval_minutes": 20,
    "cards_per_prompt": 5,
    "decks": [],
    "pass_fail_only": True,
    "show_intervals": True,
    "play_audio": False,
    "skips_per_day": 1,
    "require_internet": True,
    "input_within_seconds": 30,
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
        self.syncing = False
        self.sync_again = None  # type: Optional[bool]
        self.synced_at = -1e9
        self.pull_tried_at = -1e9
        self.own_sync_finishing = False
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
        config = dict(config)
        config.pop("idle_after_seconds", None)  # Replaced by input_within_seconds.
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
        # With Anki's window hidden in the tray, a closing card window is the last one open: Qt would then quit Anki.
        # Anki quits through its own path (File › Exit, the tray's Quit), which doesn't rely on this.
        self.mw.app.setQuitOnLastWindowClosed(False)
        self.apply_autostart()
        self.last_tick = time.monotonic()
        self.timer.start(TICK_MS)
        store.log("started (Anki %s, %s)" % (_anki_version(), "Windows" if windows.IS_WINDOWS else sys.platform))
        if cfg["start_hidden"] and _started_with_windows() and not self.hidden_once and self.tray is not None:
            self.hidden_once = True
            QTimer.singleShot(1500, self.mw.hide)

    def stop(self) -> None:
        self.timer.stop()
        self.mw.app.setQuitOnLastWindowClosed(True)
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
        active = idle < cfg["input_within_seconds"]
        self._check_online(now)
        self._tell_phone(idle)
        self._update_tray()
        if self.prompt is not None or self.mw.col is None or self.syncing:
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
        if rules.wants_pull(self._logged_in(), cfg["sync_after_cards"], now, self.synced_at, self.pull_tried_at,
                            PULL_MAX_AGE_SECONDS) and self._can_sync_quietly():
            # The phone's latest answers first; the cards come at a next tick, if you're still there.
            self.pull_tried_at = now
            self.sync_quietly("before cards", tell_phone=False)
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
            QTimer.singleShot(1500, lambda: self.sync_quietly("after cards", tell_phone=True))
        self._update_tray()

    # Syncing

    def _logged_in(self) -> bool:
        try:
            return bool(self.mw.pm.sync_auth())
        except Exception:
            return False

    def _can_sync_quietly(self) -> bool:
        """Anki 2.1.66 and later sync collection and media in one backend call (older ones: Anki's own sync)."""
        try:
            import inspect

            return len(inspect.signature(self.mw.col.sync_collection).parameters) >= 2
        except Exception:
            return False

    def sync_quietly(self, reason: str, tell_phone: bool) -> None:
        """Syncs with AnkiWeb in the background: no sync window, nothing comes to the front.

        A full sync (asked for by AnkiWeb after a note type change, for example) isn't done here: it needs a choice,
        so it's left to Anki's Sync button, with a message in the tray. `tell_phone`: the PC's answers are new, the
        phone syncs to get them.
        """
        if self.mw.col is None or not self._logged_in():
            return
        if self.syncing:
            self.sync_again = bool(self.sync_again) or tell_phone
            return
        if not self._can_sync_quietly():
            if self.prompt is None:
                try:
                    self.mw.on_sync_button_clicked()
                except Exception as error:
                    store.log("sync failed: %r" % error)
            return
        auth = self.mw.pm.sync_auth()
        try:
            media = bool(self.mw.pm.media_syncing_enabled())
        except Exception:
            media = False
        col = self.mw.col
        self.syncing = True
        gui_hooks.sync_will_start()

        def done(future) -> None:
            self.syncing = False
            synced = False
            try:
                out = future.result()
                self.mw.pm.set_host_number(out.host_number)
                if out.new_endpoint:
                    self.mw.pm.set_current_sync_url(out.new_endpoint)
                if out.required == out.NO_CHANGES:
                    synced = True
                    self.synced_at = time.monotonic()
                    if media:
                        self.mw.media_syncer.start_monitoring()
                else:
                    store.log("sync (%s): AnkiWeb asks for a full sync, left to Anki's Sync button" % reason)
                    if self.tray is not None:
                        self.tray.showMessage("Micro Review", "AnkiWeb asks for a full sync: open Anki and click Sync.")
            except Exception as error:
                store.log("sync (%s) failed: %r" % (reason, error))
                _forget_bad_login(self.mw, error)
            if self.mw.col is not None:
                try:
                    self.mw.col._load_scheduler()  # The scheduler version may have changed.
                    self.mw.col.models._clear_cache()
                    self.own_sync_finishing = True
                    gui_hooks.sync_did_finish()
                    self.mw.reset()
                except Exception as error:
                    store.log("after sync: %r" % error)
                finally:
                    self.own_sync_finishing = False
            if synced and tell_phone:
                self.tell_phone_synced()
            again, self.sync_again = self.sync_again, None
            if again is not None:
                QTimer.singleShot(1000, lambda: self.sync_quietly("again", tell_phone=again))

        self.mw.taskman.run_in_background(lambda: col.sync_collection(auth, media), done)

    def on_anki_synced(self) -> None:
        """A sync from Anki itself (its button, or at opening and closing): it may carry answers given in Anki."""
        if not self.own_sync_finishing:
            self.tell_phone_synced()

    def tell_phone_synced(self) -> None:
        if not self.online or self.mw.pm is None:
            return
        topic = rules.sync_topic(self.pairing_code())

        def done(future) -> None:
            try:
                future.result()
            except Exception as error:
                store.log("couldn't tell the phone about the sync: %r" % error)

        self.mw.taskman.run_in_background(lambda: relay.post_synced(topic), done)

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


def _forget_bad_login(mw, error: Exception) -> None:
    """As Anki does: a refused login is forgotten, so Anki asks for it again."""
    try:
        from anki.errors import SyncError, SyncErrorKind

        if isinstance(error, SyncError) and error.kind is SyncErrorKind.AUTH:
            mw.pm.clear_sync_auth()
    except Exception:
        pass


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
    gui_hooks.sync_did_finish.append(controller.on_anki_synced)
    gui_hooks.profile_will_close.append(controller.stop)

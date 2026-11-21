"""The settings window: Tools › Micro Review Desktop, the tray icon, or the add-on's Config button."""
from __future__ import annotations

import datetime

from aqt.qt import (
    QApplication,
    QCheckBox,
    QDialog,
    QDialogButtonBox,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QPlainTextEdit,
    QPushButton,
    QScrollArea,
    QSpinBox,
    QVBoxLayout,
    QWidget,
    Qt,
)

from . import rules, store, windows


class SettingsDialog(QDialog):
    def __init__(self, controller):
        super().__init__(controller.mw)
        self.controller = controller
        self.cfg = controller.cfg()
        self.setWindowTitle("Micro Review Desktop")
        self.resize(560, 720)

        outer = QVBoxLayout(self)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        body = QWidget()
        layout = QVBoxLayout(body)
        scroll.setWidget(body)
        outer.addWidget(scroll)

        status = QLabel(controller.status())
        status.setStyleSheet("font-weight: bold;")
        layout.addWidget(status)

        # Pairing
        pairing = QGroupBox("Your phone")
        form = QVBoxLayout(pairing)
        form.addWidget(QLabel("In Micro Review Focus on your phone: gear icon › Your PC › Enter code."))
        row = QHBoxLayout()
        self.code_label = QLabel(rules.display_code(controller.pairing_code()))
        self.code_label.setStyleSheet("font-family: monospace; font-size: 20px; font-weight: bold;")
        self.code_label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        copy = QPushButton("Copy")
        copy.clicked.connect(lambda: QApplication.clipboard().setText(self.code_label.text()))
        new = QPushButton("New code")
        new.setToolTip("Unpairs the phone: enter the new code there")
        new.clicked.connect(self._new_code)
        row.addWidget(self.code_label)
        row.addStretch(1)
        row.addWidget(copy)
        row.addWidget(new)
        form.addLayout(row)
        self.pause_phone = self._check(form, "Pause the phone while I use this PC", "pause_phone_while_in_use",
                                       "No cards and no gate on the phone until a few minutes after your last mouse or "
                                       "keyboard input here. Sent through ntfy.sh, a free relay: only a number of seconds.")
        phone_row = QFormLayout()
        self.phone_minutes = self._spin(1, 60, "phone_pause_minutes", " min")
        phone_row.addRow("For", self.phone_minutes)
        form.addLayout(phone_row)
        layout.addWidget(pairing)

        # Cards
        cards = QGroupBox("Cards")
        form = QFormLayout(cards)
        self.interval = self._spin(1, 240, "interval_minutes", " min of use")
        form.addRow("Cards every", self.interval)
        self.count = self._spin(1, 50, "cards_per_prompt", " cards")
        form.addRow("Each time", self.count)
        self.skips = self._spin(0, 10, "skips_per_day", " per day")
        form.addRow("Emergency skips", self.skips)
        self.idle = self._spin(10, 300, "input_within_seconds", " s")
        self.idle.setToolTip("No mouse or keyboard input for this long: you're away, the time doesn't count and no cards come.")
        form.addRow("Away after", self.idle)
        self.stand_up = self._spin(0, 120, "stand_up_minutes", " min of sitting")
        self.stand_up.setSpecialValueText("Off")
        self.stand_up.setToolTip("A small window in the corner with a desk exercise. Stepping away from the keyboard "
                                 "for a few minutes counts as a break; during a game, a notification instead.")
        form.addRow("Stand up after", self.stand_up)
        self.spell = self._spin(0, 180, "spell_minutes", " min in a distraction")
        self.spell.setSpecialValueText("Off")
        self.spell.setToolTip("After this long with a distracting site or app in front (YouTube, Reddit... in the "
                              "window's title; the list is in the add-on's config), a prompt in the middle of the "
                              "screen. Choosing more time gets harder each time.")
        form.addRow("Break the spell after", self.spell)
        self.decks = QListWidget()
        self.decks.setMinimumHeight(120)
        chosen = list(self.cfg["decks"])
        names = sorted(d.name for d in controller.mw.col.decks.all_names_and_ids()) if controller.mw.col else []
        for name in chosen + [n for n in names if n not in chosen]:
            if name not in names:
                continue
            item = QListWidgetItem(name)
            item.setFlags(item.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            item.setCheckState(Qt.CheckState.Checked if name in chosen else Qt.CheckState.Unchecked)
            self.decks.addItem(item)
        form.addRow(QLabel("Decks (none checked: Anki's current deck)"))
        form.addRow(self.decks)
        box = QVBoxLayout()
        self.pass_fail = self._check(box, "Pass / Fail only (Pass = Good, Fail = Again)", "pass_fail_only")
        self.intervals = self._check(box, "Show when the card comes back (Pass · 4d)", "show_intervals")
        self.audio = self._check(box, "Play the card's audio", "play_audio")
        self.sync = self._check(box, "Sync with AnkiWeb before and after the cards", "sync_after_cards")
        form.addRow(box)
        layout.addWidget(cards)

        # When cards wait
        waits = QGroupBox("When cards wait")
        box = QVBoxLayout(waits)
        self.internet = self._check(box, "Only while the PC is online", "require_internet")
        self.gaming = self._check(box, "Never during a game (Steam games and the processes below)", "gaming_shutoff")
        self.fullscreen = self._check(box, "Full-screen apps count as games (not browsers)", "fullscreen_counts_as_game")
        box.addWidget(QLabel('Game processes, one per line ("javaw.exe:Minecraft": only with that window title):'))
        self.games = QPlainTextEdit("\n".join(self.cfg["game_processes"]))
        self.games.setMaximumHeight(80)
        box.addWidget(self.games)
        layout.addWidget(waits)

        # Windows
        system = QGroupBox("Windows")
        box = QVBoxLayout(system)
        self.autostart = self._check(box, "Start Anki with Windows", "start_with_windows")
        if windows.IS_WINDOWS and windows.anki_executable() is None:
            self.autostart.setEnabled(False)
            self.autostart.setToolTip("Only for Anki installed with its installer.")
        self.hidden = self._check(box, "When started with Windows, stay in the tray", "start_hidden")
        self.keep = self._check(box, "Closing the window keeps Anki in the tray", "keep_running_when_closed")
        layout.addWidget(system)

        # Diagnostics
        log = QGroupBox("Recent events")
        box = QVBoxLayout(log)
        self.log = QPlainTextEdit("\n".join(reversed(store.read_log()[-80:])))
        self.log.setReadOnly(True)
        self.log.setMinimumHeight(140)
        box.addWidget(self.log)
        copy_log = QPushButton("Copy diagnostics")
        copy_log.clicked.connect(self._copy_diagnostics)
        box.addWidget(copy_log)
        layout.addWidget(log)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Save | QDialogButtonBox.StandardButton.Cancel)
        study = buttons.addButton("Study now", QDialogButtonBox.ButtonRole.ActionRole)
        study.clicked.connect(self._study_now)
        buttons.accepted.connect(self._save)
        buttons.rejected.connect(self.reject)
        outer.addWidget(buttons)

    def _check(self, layout, text, key, tooltip=None) -> QCheckBox:
        box = QCheckBox(text)
        box.setChecked(bool(self.cfg[key]))
        if tooltip:
            box.setToolTip(tooltip)
        layout.addWidget(box)
        return box

    def _spin(self, low, high, key, suffix) -> QSpinBox:
        spin = QSpinBox()
        spin.setRange(low, high)
        spin.setValue(int(self.cfg[key]))
        spin.setSuffix(suffix)
        return spin

    def _new_code(self) -> None:
        self.code_label.setText(rules.display_code(self.controller.new_pairing_code()))

    def _copy_diagnostics(self) -> None:
        c = self.controller
        lines = [
            "Micro Review Desktop, Anki %s, %s" % (_version(), "Windows" if windows.IS_WINDOWS else "not Windows"),
            "Status: %s" % c.status(),
            "Online: %s, game: %s, idle: %.0f s" % (c.online, c.game, windows.idle_seconds()),
            "Phone told in use: %s, last told %s" % (
                c.phone.told_in_use,
                datetime.datetime.fromtimestamp(c.phone.sent_at).strftime("%H:%M:%S") if c.phone.sent_at else "never"),
            "Autostart: %s" % windows.autostart(),
            "",
        ] + list(reversed(store.read_log()[-80:]))
        QApplication.clipboard().setText("\n".join(lines))

    def _study_now(self) -> None:
        self._save(close=False)
        self.accept()
        self.controller.show_prompt(session=True)

    def _save(self, close: bool = True) -> None:
        cfg = dict(self.cfg)
        cfg["pause_phone_while_in_use"] = self.pause_phone.isChecked()
        cfg["phone_pause_minutes"] = self.phone_minutes.value()
        cfg["interval_minutes"] = self.interval.value()
        cfg["cards_per_prompt"] = self.count.value()
        cfg["skips_per_day"] = self.skips.value()
        cfg["input_within_seconds"] = self.idle.value()
        cfg["stand_up_minutes"] = self.stand_up.value()
        cfg["spell_minutes"] = self.spell.value()
        cfg["decks"] = [self.decks.item(i).text() for i in range(self.decks.count())
                        if self.decks.item(i).checkState() == Qt.CheckState.Checked]
        cfg["pass_fail_only"] = self.pass_fail.isChecked()
        cfg["show_intervals"] = self.intervals.isChecked()
        cfg["play_audio"] = self.audio.isChecked()
        cfg["sync_after_cards"] = self.sync.isChecked()
        cfg["require_internet"] = self.internet.isChecked()
        cfg["gaming_shutoff"] = self.gaming.isChecked()
        cfg["fullscreen_counts_as_game"] = self.fullscreen.isChecked()
        cfg["game_processes"] = [line.strip() for line in self.games.toPlainText().splitlines() if line.strip()]
        cfg["start_with_windows"] = self.autostart.isChecked()
        cfg["start_hidden"] = self.hidden.isChecked()
        cfg["keep_running_when_closed"] = self.keep.isChecked()
        self.controller.save_cfg(cfg)
        self.controller.phone.pause_seconds = cfg["phone_pause_minutes"] * 60
        store.log("settings saved")
        if close:
            self.accept()


def _version() -> str:
    try:
        from anki.buildinfo import version

        return version
    except Exception:
        return "?"

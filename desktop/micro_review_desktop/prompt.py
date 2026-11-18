"""The card window: a few cards before going on, every so often; or more, for as long as wanted."""
from __future__ import annotations

import time
from typing import Dict, Optional

from aqt.qt import (
    QDialog,
    QGuiApplication,
    QCursor,
    QHBoxLayout,
    QKeySequence,
    QLabel,
    QMenu,
    QPushButton,
    QShortcut,
    QSizePolicy,
    QTimer,
    QToolButton,
    QVBoxLayout,
    Qt,
)
from aqt.webview import AnkiWebView

from . import rules, store
from .cards import CardSource, card_html
from .controller import add_action

PROMPT = "prompt"
SESSION = "session"

# A card's buttons wait this long after the window appears: a key or click meant for another app mustn't answer it.
APPEAR_LOCK_MS = 1000
# After the prompt's cards, "Keep going" stays this long.
AUTO_CLOSE_MS = 15000


class CardPrompt(QDialog):
    """In PROMPT mode, `required` cards (Pass or Fail, each counts; Bury doesn't), then "Keep going" or "Done".
    In SESSION mode, cards until "Done". It stays on top and can't be closed: answer, snooze or skip.

    It appears without taking the keyboard, so typing goes on in the app in use until the window is clicked.
    """

    def __init__(self, controller, mode: str, required: int):
        super().__init__(None)
        self.controller = controller
        self.mw = controller.mw
        self.mode = mode
        self.required = max(1, required)
        self.answered = 0
        self.card = None
        self.revealed = False
        self.choosing = False
        self.closing = False
        self.locked_until = 0.0
        self.source = CardSource(self.mw, controller.cfg()["decks"])

        self.setWindowTitle("Micro Review")
        self.setWindowFlags(
            Qt.WindowType.Window
            | Qt.WindowType.WindowStaysOnTopHint
            | Qt.WindowType.CustomizeWindowHint
            | Qt.WindowType.WindowTitleHint
        )
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating, True)
        self.setAttribute(Qt.WidgetAttribute.WA_DeleteOnClose, True)
        self.resize(600, 640)
        self._build()
        self._shortcuts()

    # Layout

    def _build(self) -> None:
        layout = QVBoxLayout(self)
        top = QHBoxLayout()
        self.title = QLabel()
        self.title.setStyleSheet("font-weight: bold; font-size: 15px;")
        self.progress = QLabel()
        self.bury_button = QPushButton("Bury")
        self.bury_button.setFlat(True)
        self.bury_button.setToolTip("Put this card away until tomorrow (doesn't count as an answer)")
        self.bury_button.clicked.connect(self.bury)
        top.addWidget(self.title)
        top.addWidget(self.progress)
        top.addStretch(1)
        top.addWidget(self.bury_button)
        layout.addLayout(top)

        self.web = AnkiWebView(parent=self, title="micro review card")
        self.web.set_bridge_command(self._on_bridge, self)
        self.web.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        layout.addWidget(self.web, 1)

        answers = QHBoxLayout()
        self.show_answer_button = QPushButton("Show answer")
        self.show_answer_button.setMinimumHeight(44)
        self.show_answer_button.clicked.connect(self.reveal)
        answers.addWidget(self.show_answer_button)
        self.answer_buttons = {}  # type: Dict[int, QPushButton]
        colors = {rules.AGAIN: "#d9534f", rules.HARD: "#f0ad4e", rules.GOOD: "#5cb85c", rules.EASY: "#3c8dbc"}
        for button in (rules.AGAIN, rules.HARD, rules.GOOD, rules.EASY):
            widget = QPushButton()
            widget.setMinimumHeight(44)
            widget.setStyleSheet("QPushButton { color: white; background: %s; border-radius: 6px; font-weight: bold; }"
                                 "QPushButton:disabled { background: #999; }" % colors[button])
            widget.clicked.connect(lambda _=False, b=button: self.answer(b))
            answers.addWidget(widget)
            self.answer_buttons[button] = widget
        layout.addLayout(answers)

        bottom = QHBoxLayout()
        self.snooze_button = QToolButton()
        self.snooze_button.setText("Snooze")
        self.snooze_button.setPopupMode(QToolButton.ToolButtonPopupMode.InstantPopup)
        menu = QMenu(self.snooze_button)
        for label, minutes in (("15 minutes", 15), ("1 hour", 60), ("3 hours", 180)):
            add_action(menu, label, lambda m=minutes: self.snooze(m))
        add_action(menu, "Until tomorrow", lambda: self.snooze(None))
        self.snooze_button.setMenu(menu)
        self.skip_button = QPushButton()
        self.skip_button.clicked.connect(self.skip)
        self.keep_going_button = QPushButton("Keep going")
        self.keep_going_button.clicked.connect(self.keep_going)
        self.done_button = QPushButton("Done")
        self.done_button.clicked.connect(self.finish)
        bottom.addWidget(self.snooze_button)
        bottom.addWidget(self.skip_button)
        bottom.addStretch(1)
        bottom.addWidget(self.keep_going_button)
        bottom.addWidget(self.done_button)
        layout.addLayout(bottom)

        self.auto_close = QTimer(self)
        self.auto_close.setSingleShot(True)
        self.auto_close.timeout.connect(self._auto_close)

    def _shortcuts(self) -> None:
        """As in Anki's reviewer: space, 1 (Again / Fail), 3 (Good / Pass), - (bury). Only once the window is clicked."""

        def add(key, handler):
            shortcut = QShortcut(QKeySequence(key), self)
            shortcut.setContext(Qt.ShortcutContext.WindowShortcut)
            shortcut.activated.connect(handler)

        add("Space", lambda: self.answer(rules.GOOD) if self.revealed else self.reveal())
        add("Return", lambda: self.answer(rules.GOOD) if self.revealed else self.reveal())
        add("1", lambda: self.answer(rules.AGAIN))
        add("2", lambda: self.answer(rules.HARD))
        add("3", lambda: self.answer(rules.GOOD))
        add("4", lambda: self.answer(rules.EASY))
        add("-", self.bury)

    # Showing

    def start(self) -> bool:
        """Shows the window with the first card. False (nothing shown) when no card is due."""
        self.card = self.source.next_card()
        if self.card is None:
            self.source.restore()
            return False
        self._place()
        self.locked_until = time.monotonic() + APPEAR_LOCK_MS / 1000.0
        QTimer.singleShot(APPEAR_LOCK_MS + 20, self._render)
        self._show_card()
        self.show()
        self.raise_()
        return True

    def _place(self) -> None:
        screen = QGuiApplication.screenAt(QCursor.pos()) or QGuiApplication.primaryScreen()
        if screen is None:
            return
        area = screen.availableGeometry()
        self.move(area.x() + (area.width() - self.width()) // 2, area.y() + (area.height() - self.height()) // 2)

    def _locked(self) -> bool:
        return time.monotonic() < self.locked_until

    def _show_card(self) -> None:
        self.revealed = False
        self._render_card()
        self._play(answer=False)
        self._render()

    def _render_card(self) -> None:
        if self.card is None:
            message = "All done: %d answered." % self.answered if self.choosing else "Nothing more is due."
            if self.choosing:
                message += " Keep going?"
            self.web.stdHtml('<div style="text-align:center; margin-top:30%%; font-size:22px;">%s</div>' % message, context=self)
            return
        self.web.stdHtml(card_html(self.mw, self.card, self.revealed), css=["css/reviewer.css"], context=self)

    def _render(self) -> None:
        cfg = self.controller.cfg()
        usable = not self._locked()
        card = self.card
        if self.mode == PROMPT:
            self.title.setText("Micro Review")
            self.progress.setText("%d of %d" % (min(self.answered, self.required), self.required))
        else:
            self.title.setText("Micro Review")
            self.progress.setText("%d answered" % self.answered)
        has_card = card is not None and not self.choosing
        self.bury_button.setVisible(has_card)
        self.bury_button.setEnabled(usable)
        self.show_answer_button.setVisible(has_card and not self.revealed)
        self.show_answer_button.setEnabled(usable)
        shown = rules.shown_buttons(self.source.button_count(card), cfg["pass_fail_only"]) if has_card else []
        names = {rules.AGAIN: "Fail" if cfg["pass_fail_only"] else "Again", rules.HARD: "Hard",
                 rules.GOOD: "Pass" if cfg["pass_fail_only"] else "Good", rules.EASY: "Easy"}
        for button, widget in self.answer_buttons.items():
            visible = has_card and self.revealed and button in shown
            widget.setVisible(visible)
            widget.setEnabled(usable)
            if visible:
                interval = self.source.interval(card, button) if cfg["show_intervals"] else None
                widget.setText(names[button] if interval is None else "%s · %s" % (names[button], interval))
        in_prompt = self.mode == PROMPT and not self.choosing
        self.snooze_button.setVisible(not self.choosing)
        self.snooze_button.setEnabled(usable)
        left = self.controller.skips_left()
        self.skip_button.setVisible(in_prompt and left > 0)
        self.skip_button.setText("Emergency skip (%d left today)" % left)
        self.skip_button.setEnabled(usable)
        self.keep_going_button.setVisible(self.choosing)
        self.done_button.setVisible(self.choosing or self.mode == SESSION)

    def _play(self, answer: bool) -> None:
        if self.card is None or not self.controller.cfg()["play_audio"]:
            return
        try:
            from aqt.sound import av_player

            av_player.play_tags(self.card.answer_av_tags() if answer else self.card.question_av_tags())
        except Exception:
            pass

    def _on_bridge(self, cmd: str):
        if cmd.startswith("play:") and self.card is not None:
            from aqt.sound import play_clicked_audio

            play_clicked_audio(cmd, self.card)
        return False

    # Actions

    def reveal(self) -> None:
        if self._locked() or self.card is None or self.revealed or self.choosing:
            return
        self.revealed = True
        self._render_card()
        self._play(answer=True)
        self._render()

    def answer(self, button: int) -> None:
        if self._locked() or self.card is None or not self.revealed or self.choosing:
            return
        cfg = self.controller.cfg()
        if button not in rules.shown_buttons(self.source.button_count(self.card), cfg["pass_fail_only"]):
            return
        try:
            if not self.source.answer(self.card, button):
                return
        except Exception as error:
            store.log("answer failed: %r" % error)
            # A prompt that can't record answers must not hold the PC hostage.
            self.finish()
            return
        self.answered += 1
        if self.mode == PROMPT and self.answered >= self.required:
            self.card = None
            self.choosing = True
            self._render_card()
            self._render()
            self.auto_close.start(AUTO_CLOSE_MS)
            return
        self._next()

    def bury(self) -> None:
        if self._locked() or self.card is None or self.choosing:
            return
        try:
            self.source.bury(self.card)
            store.log("card buried")
        except Exception as error:
            store.log("bury failed: %r" % error)
        self._next()

    def _next(self) -> None:
        self.card = self.source.next_card()
        if self.card is None and self.mode == PROMPT:
            # Nothing more is due: the prompt has asked what it could.
            self.finish()
            return
        self._show_card()

    def keep_going(self) -> None:
        self.auto_close.stop()
        self.mode = SESSION
        self.choosing = False
        self._next()

    def _auto_close(self) -> None:
        if self.choosing:
            self.finish()

    def snooze(self, minutes: Optional[int]) -> None:
        if self._locked():
            return
        self.controller.snooze(minutes)
        self.finish()

    def skip(self) -> None:
        if self._locked():
            return
        if self.controller.use_skip():
            store.log("emergency skip")
            self.finish()

    def finish(self) -> None:
        if self.closing:
            return
        self.closing = True
        self.auto_close.stop()
        try:
            from aqt.sound import av_player

            av_player.stop_and_clear_queue()
        except Exception:
            pass
        self.source.restore()
        self.controller.prompt_closed(self.answered)
        self.close()

    def closeEvent(self, event) -> None:
        # Not closed from its title bar or Alt+F4: answered, snoozed or skipped.
        if self.closing:
            self.web.cleanup() if hasattr(self.web, "cleanup") else None
            event.accept()
        else:
            event.ignore()

    def reject(self) -> None:
        # Escape doesn't close it either.
        if self.closing:
            super().reject()

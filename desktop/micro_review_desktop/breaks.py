"""The stand-up break: a small window in the corner after a stretch of sitting, with one desk exercise."""
from __future__ import annotations

import html

from aqt.qt import (
    QCursor,
    QFrame,
    QGuiApplication,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QVBoxLayout,
    QWidget,
    Qt,
)

from . import rules

# Its buttons wait this long after it appears: a click meant for another app mustn't close it.
APPEAR_LOCK_MS = 1000

STYLE = """
QFrame#panel {
    border-radius: 18px;
    border: 1px solid rgba(255, 255, 255, 200);
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #f4fbff, stop:0.5 #d6efff, stop:1 #a9dcff);
}
QLabel { color: #0d3550; }
QLabel#seal {
    color: white; font-size: 22px; font-weight: bold; border-radius: 22px;
    background: qradialgradient(cx:0.4, cy:0.3, radius:0.8, stop:0 #ff8a8a, stop:1 #c8102e);
    min-width: 44px; max-width: 44px; min-height: 44px; max-height: 44px;
}
QLabel#title { font-size: 17px; font-weight: bold; }
QLabel#move { font-size: 15px; font-weight: bold; }
QLabel#dose { color: #2a6f97; font-weight: bold; }
QPushButton {
    border-radius: 14px; padding: 7px 16px; font-weight: bold; color: white;
    border: 1px solid rgba(255, 255, 255, 160);
}
QPushButton#done { background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #7fe08a, stop:0.5 #3fb34f, stop:1 #2b8a3a); }
QPushButton#later { background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #ffd36e, stop:0.5 #f0a92a, stop:1 #c57f10); }
QPushButton:disabled { background: #9bb3c4; }
"""


class BreakPrompt(QWidget):
    """Top right, on top, without taking the keyboard. Done, or later; stepping away from the keyboard counts as
    done (the controller watches the input)."""

    def __init__(self, controller, move: rules.DeskMove, sat_minutes: int):
        super().__init__(None)
        self.controller = controller
        self.setWindowTitle("Micro Review: stand up")
        self.setWindowFlags(
            Qt.WindowType.Tool
            | Qt.WindowType.FramelessWindowHint
            | Qt.WindowType.WindowStaysOnTopHint
            | Qt.WindowType.WindowDoesNotAcceptFocus
        )
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating, True)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self.setAttribute(Qt.WidgetAttribute.WA_DeleteOnClose, True)
        self.setStyleSheet(STYLE)
        self.setFixedWidth(380)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(8, 8, 8, 8)
        panel = QFrame()
        panel.setObjectName("panel")
        outer.addWidget(panel)
        layout = QVBoxLayout(panel)
        layout.setContentsMargins(16, 14, 16, 14)
        layout.setSpacing(8)

        head = QHBoxLayout()
        seal = QLabel("立")
        seal.setObjectName("seal")
        seal.setAlignment(Qt.AlignmentFlag.AlignCenter)
        head.addWidget(seal)
        title = QLabel("Time to stand up<br><span style='font-weight: normal; font-size: 12px;'>"
                       "%d minutes sitting</span>" % sat_minutes)
        title.setObjectName("title")
        head.addWidget(title, 1)
        layout.addLayout(head)

        name = QLabel(html.escape(move.name))
        name.setObjectName("move")
        layout.addWidget(name)
        dose = QLabel(html.escape(move.dose))
        dose.setObjectName("dose")
        layout.addWidget(dose)
        how = QLabel(html.escape(move.how))
        how.setWordWrap(True)
        layout.addWidget(how)
        away = QLabel("<i>Or just step away from the desk: that counts too.</i>")
        away.setStyleSheet("color: #2a6f97; font-size: 11px;")
        layout.addWidget(away)

        buttons = QHBoxLayout()
        self.later_button = QPushButton("5 min")
        self.later_button.setObjectName("later")
        self.later_button.clicked.connect(lambda: self.controller.break_later())
        self.done_button = QPushButton("Done")
        self.done_button.setObjectName("done")
        self.done_button.clicked.connect(lambda: self.controller.break_done("done"))
        buttons.addStretch(1)
        buttons.addWidget(self.later_button)
        buttons.addWidget(self.done_button)
        layout.addLayout(buttons)

        for button in (self.later_button, self.done_button):
            button.setEnabled(False)
            button.setCursor(Qt.CursorShape.PointingHandCursor)

    def start(self) -> None:
        from aqt.qt import QTimer

        self.adjustSize()
        screen = QGuiApplication.screenAt(QCursor.pos()) or QGuiApplication.primaryScreen()
        if screen is not None:
            area = screen.availableGeometry()
            self.move(area.x() + area.width() - self.width() - 16, area.y() + 16)
        self.show()
        QTimer.singleShot(APPEAR_LOCK_MS, self._unlock)

    def _unlock(self) -> None:
        for button in (self.later_button, self.done_button):
            button.setEnabled(True)

    def closeEvent(self, event) -> None:
        self.controller.break_closed(self)
        super().closeEvent(event)

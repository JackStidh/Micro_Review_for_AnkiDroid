"""The cards a prompt asks, through Anki's own scheduler (2.1.49: v2, or v3 with its compatibility calls)."""
from __future__ import annotations

from typing import List, Optional

from . import rules


class CardSource:
    """Cards from the chosen decks in order, or Anki's current deck; Anki's current deck is put back afterwards."""

    def __init__(self, mw, deck_names: List[str]):
        self.mw = mw
        decks = mw.col.decks
        self.original = decks.get_current_id()
        ids = []
        for name in deck_names:
            deck_id = decks.id_for_name(name)
            if deck_id:
                ids.append(deck_id)
        self.deck_ids = ids or [self.original]

    @property
    def col(self):
        return self.mw.col

    def _reset(self) -> None:
        reset = getattr(self.col.sched, "reset", None)
        if reset is not None:
            reset()

    def _select(self, deck_id) -> None:
        if self.col.decks.get_current_id() != deck_id:
            self.col.decks.select(deck_id)
        self._reset()

    def next_card(self):
        """The next due card of the first deck that has one, its timer started; None when nothing is due."""
        for deck_id in self.deck_ids:
            self._select(deck_id)
            card = self.col.sched.getCard()
            if card is not None:
                card.start_timer()
                return card
        return None

    def button_count(self, card) -> int:
        return self.col.sched.answerButtons(card)

    def interval(self, card, button: int) -> Optional[str]:
        """When the card comes back with this answer: "<10m", "4d"."""
        ease = rules.ease_for(button, self.button_count(card))
        if ease is None:
            return None
        try:
            return rules.clean_interval(self.col.sched.nextIvlStr(card, ease, True))
        except Exception:
            return None

    def answer(self, card, button: int) -> bool:
        ease = rules.ease_for(button, self.button_count(card))
        if ease is None:
            return False
        self.col.sched.answerCard(card, ease)
        self.col.autosave()
        return True

    def bury(self, card) -> None:
        """Until tomorrow. Not an answer."""
        bury = getattr(self.col.sched, "bury_cards", None) or getattr(self.col.sched, "buryCards")
        bury([card.id])
        self._reset()

    def restore(self) -> None:
        """Anki's current deck as it was, and the changes saved."""
        try:
            if self.col.decks.get_current_id() != self.original:
                self.col.decks.select(self.original)
            self._reset()
            self.col.save()
        except Exception:
            pass


def card_html(mw, card, answer: bool) -> str:
    """The card as Anki's reviewer shows it: its template, styling, media and other add-ons' changes."""
    from aqt import gui_hooks

    text = card.answer() if answer else card.question()
    prepare = getattr(mw, "prepare_card_text_for_display", None)
    if prepare is not None:
        text = prepare(text)
    text = gui_hooks.card_will_show(text, card, "reviewAnswer" if answer else "reviewQuestion")
    return '<div id="qa" class="card card%d">%s</div>' % (card.ord + 1, text)

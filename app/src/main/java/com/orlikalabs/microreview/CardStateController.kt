package com.orlikalabs.microreview

import android.app.NotificationManager
import android.content.Context
import android.util.Log

/**
 * Single entry point for the displayed card: "move to the next card" and "check it's still in sync with
 * AnkiDroid". Both do ContentProvider I/O: call them off the main thread.
 *
 * The card is shown by the nudges and the app gate ([FocusCardController]). Its notification only exists while a
 * nudge is shown as a notification ([NudgeSession]): never on the lock screen.
 */
object CardStateController {
    private const val TAG = "Notifications"

    /** Serializes the updates: an answer and a check must not store their cards at the same time. */
    private val lock = Any()

    /**
     * Shows the next card from the selected decks. With no card left, the notification is removed: the review is
     * over, and the app shows "Bravo".
     *
     * @return the card now shown, or null if there is none (or AnkiDroid can't be read)
     */
    @JvmStatic
    fun showNextCard(context: Context, silent: Boolean): CardInfo? = synchronized(lock) {
        val anki = AnkiDroidHelper(context)
        val deckIds = DeckSelection.load(context)
        val card = try {
            anki.queryNextCard(deckIds)
        } catch (e: AnkiQueryException) {
            // Not "Bravo": the next card is unknown. The displayed (just answered) card stays stored, without its
            // notification so it isn't offered again; the next sync shows the actual next card.
            Log.e(TAG, "AnkiDroid can't be read, the next card is unknown", e)
            cancelNotification(context)
            return null
        }

        if (card != null) {
            showCard(context, anki, card, silent)
        } else {
            storeDone(context, anki, deckIds)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(Notifications.NOTIFICATION_ID)
        }
        card
    }

    /**
     * Brings the displayed card in line with AnkiDroid: shows the actual next card if the displayed one was
     * reviewed, edited or removed in AnkiDroid (or the deck order changed), and "Bravo" if no card is left.
     * Reviewing starts when the first deck is selected and stops when no deck is selected any more.
     */
    @JvmStatic
    fun syncWithAnkiDroid(context: Context): CardSync.Action = synchronized(lock) {
        val anki = AnkiDroidHelper(context)
        val state = anki.storedState
        val deckIds = DeckSelection.load(context)
        val displayed = state?.takeIf { it.hasCard() }
        val next: CardInfo? = try {
            if (deckIds.isNotEmpty()) anki.queryNextCard(deckIds) else null
        } catch (e: AnkiQueryException) {
            // Unknown is not "nothing due": keep what is displayed, the next sync tries again (#111).
            Log.e(TAG, "AnkiDroid can't be read, keeping the displayed card", e)
            if (displayed == null || deckIds.isEmpty()) return CardSync.Action.NONE
            displayed.toCardInfo()
        }

        val action = CardSync.decide(
            decksSelected = deckIds.isNotEmpty(),
            started = state != null,
            displayed = displayed?.let { CardSync.CardKey(it.noteID, it.cardOrd) },
            displayedQuestion = displayed?.question.orEmpty(),
            next = next?.let { CardSync.CardKey(it.noteID, it.cardOrd) },
            nextQuestion = next?.q.orEmpty(),
            // The card notification is only a nudge's: never posted again by a sync.
            notificationVisible = true,
            notificationHidden = true,
        )
        Log.i(TAG, "sync with AnkiDroid: $action")
        when (action) {
            CardSync.Action.SHOW_NEXT -> showCard(context, anki, next!!, silent = true)
            CardSync.Action.SHOW_DONE -> storeDone(context, anki, deckIds)
            CardSync.Action.STOP -> stop(context, anki)
            CardSync.Action.REPOST, CardSync.Action.NONE -> Unit
        }
        action
    }

    private fun showCard(context: Context, anki: AnkiDroidHelper, card: CardInfo, silent: Boolean) {
        Log.i(TAG, "next card found in deck ${card.deckId}")
        anki.storeState(card.deckId, card)
        if (post(context, card, card.deckName, silent)) {
            ReviewTimer.onCardShown(context, CardSync.CardKey(card.noteID, card.cardOrd))
        }
    }

    private fun storeDone(context: Context, anki: AnkiDroidHelper, deckIds: List<Long>) {
        Log.i(TAG, "no card to review in the selected decks")
        val emptyCard = CardInfo()
        emptyCard.cardOrd = -1
        emptyCard.noteID = -1
        anki.storeState(deckIds.firstOrNull() ?: -1L, emptyCard)
    }

    /** No deck selected: back to "not started", without a notification. */
    private fun stop(context: Context, anki: AnkiDroidHelper) {
        Log.i(TAG, "no deck selected, stopping")
        anki.clearState()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(Notifications.NOTIFICATION_ID)
    }

    /**
     * Posts the card's notification while a nudge is shown as a notification ([NudgeSession]); otherwise there is
     * none.
     *
     * @return whether the notification was posted
     */
    private fun post(context: Context, card: CardInfo, deckName: String, silent: Boolean): Boolean {
        if (NudgeSession.isActive(context)) {
            Notifications.create().showNotification(context, card, deckName, silent)
            return true
        }
        cancelNotification(context)
        return false
    }

    private fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Not the card just answered in a nudge: the "Keep going" prompt replaces it in place.
        if (!NudgeSession.isPrompting(context)) manager.cancel(Notifications.NOTIFICATION_ID)
    }
}

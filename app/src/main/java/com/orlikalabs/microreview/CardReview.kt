package com.orlikalabs.microreview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Reviewing the displayed card: from the card screen ([FocusCardController]) or a nudge's notification buttons.
 * Does ContentProvider I/O: call it off the main thread.
 */
object CardReview {
    private const val TAG = "Notifications"
    private const val PREFS = "com.ankidroid.companion.card_review"

    /** The card whose answer is shown, as "noteId:ord". A new card doesn't match, so the flag clears itself. */
    private const val KEY_REVEALED_CARD = "revealed_card"

    /** The page shown after "Previous" or "Next" ([KEY_PAGE]) and its card ([KEY_PAGE_CARD]); another card doesn't match. */
    private const val KEY_PAGE_CARD = "page_card"
    private const val KEY_PAGE = "page"

    /** The intent data of an answer button: the card it answers ([answeredCard]). */
    private const val CARD_SCHEME = "microreview"
    private const val CARD_AUTHORITY = "card"

    /** Answers one at a time: a second tap must see the card stored by the first one ([answer]). */
    private val answerLock = Any()

    /**
     * Answers the displayed card with [button] (one of [AnswerButtons]) and stores the next card. With no card
     * left, the notification is removed.
     *
     * Only if [card], the card the button was shown with, is still the displayed one: otherwise the tap came late
     * (a double tap, a tap queued while the previous answer was being recorded) and would answer a card the user
     * hasn't seen, or the same card twice. It is then ignored.
     *
     * @param fromNotification answered from a nudge's notification: "Keep going" is offered there afterwards
     * @return whether the answer was taken (false: ignored)
     */
    fun answer(context: Context, button: Int, card: CardSync.CardKey, fromNotification: Boolean = true): Boolean =
        synchronized(answerLock) {
            val anki = AnkiDroidHelper(context)
            val localState = anki.storedState?.takeIf { it.hasCard() }
            if (localState == null || CardSync.CardKey(localState.noteID, localState.cardOrd) != card) {
                Log.i(TAG, "answer for note ${card.noteId}, ord ${card.ord} ignored: it isn't the displayed card any more")
                return false
            }
            Log.i(TAG, "answering note ${localState.noteID}, ord ${localState.cardOrd}")
            // Measured now, before the AnkiDroid queries below.
            val timeTaken = ReviewTimer.timeTaken(context, card)
            val ease = AnswerButtons.easeFor(button, localState.buttonCount)
            if (ease == null) {
                Log.w(TAG, "button $button is not valid for a card with ${localState.buttonCount} buttons")
                return false
            }
            // The card may have been reviewed, buried or deleted in AnkiDroid since it was shown: answering it
            // again would record a second review. Null means it couldn't be checked: answer as before.
            if (anki.isCardDue(localState.deckId, localState.noteID, localState.cardOrd) == false) {
                Log.i(TAG, "card no longer due in AnkiDroid, not answering it")
            } else {
                Log.i(TAG, "time taken: $timeTaken ms")
                anki.reviewCard(localState.noteID, localState.cardOrd, ease, timeTaken)
                // On the phone only until AnkiDroid syncs: asked for at the next chance (AnkiSync).
                AnkiSync.onChanged(context)
                LifeStore.award(context, Progress.Event.CARD, Progress.Counter.CARDS)
            }

            // Answered from a nudge's notification: the next card waits for its own nudge, and "Keep going" is
            // offered meanwhile.
            val nudge = fromNotification && NudgeSession.isActive(context)
            if (nudge) NudgeSession.onAnswered(context)

            CardStateController.showNextCard(context, silent = true)
            if (nudge) {
                // With no card left, the notification was removed: nothing to keep going with.
                if (anki.storedState?.hasCard() == true) {
                    Notifications.create().showKeepGoingPrompt(context, localState.toCardInfo(), localState.deckName)
                }
                NudgeSession.onPromptDone(context)
            }
            true
        }

    /**
     * [intent], an answer button's, for [card]. The card is in the intent data, so each card gets its own
     * PendingIntent: one created for an earlier card keeps answering that card only.
     */
    fun forCard(intent: Intent, card: CardSync.CardKey): Intent =
        intent.setData(
            Uri.Builder().scheme(CARD_SCHEME).authority(CARD_AUTHORITY)
                .appendPath(card.noteId.toString()).appendPath(card.ord.toString()).build()
        )

    /** The card an answer button's [intent] answers ([forCard]), or null if it has none. */
    fun answeredCard(intent: Intent): CardSync.CardKey? {
        val data = intent.data?.takeIf { it.scheme == CARD_SCHEME && it.authority == CARD_AUTHORITY } ?: return null
        val noteId = data.pathSegments.getOrNull(0)?.toLongOrNull() ?: return null
        val ord = data.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return CardSync.CardKey(noteId, ord)
    }

    /** Shows the answer of the displayed card, in the notification too (updated in place, without alert). */
    fun revealAnswer(context: Context) {
        val state = AnkiDroidHelper(context).storedState?.takeIf { it.hasCard() } ?: return
        // The card is obviously seen: start the timer if no screen event did (it keeps running otherwise).
        ReviewTimer.onCardSeen(context, CardSync.CardKey(state.noteID, state.cardOrd))
        // The pages change with the answer: back to its first one.
        prefs(context).edit().putString(KEY_REVEALED_CARD, key(state.noteID, state.cardOrd)).remove(KEY_PAGE_CARD).apply()
        if (!NudgeSession.isActive(context)) return
        Notifications.create().showNotification(context, state.toCardInfo(), state.deckName, isSilent = true, replace = false)
    }

    /**
     * Buries the displayed card until tomorrow and stores the next card: AnkiDroid's "bury", not an answer. Only if
     * [card] is still the displayed one, like [answer]. Does ContentProvider I/O: off the main thread.
     *
     * @return whether it was buried
     */
    fun bury(context: Context, card: CardSync.CardKey): Boolean = synchronized(answerLock) {
        val anki = AnkiDroidHelper(context)
        val localState = anki.storedState?.takeIf { it.hasCard() }
        if (localState == null || CardSync.CardKey(localState.noteID, localState.cardOrd) != card) {
            Log.i(TAG, "bury for note ${card.noteId}, ord ${card.ord} ignored: it isn't the displayed card any more")
            return false
        }
        Log.i(TAG, "burying note ${localState.noteID}, ord ${localState.cardOrd}")
        anki.buryCard(localState.noteID, localState.cardOrd)
        AnkiSync.onChanged(context)
        ReviewTimer.clear(context)
        CardStateController.showNextCard(context, silent = true)
        true
    }

    /** Hides the answer again: a card withdrawn unanswered ([NudgeSession]) comes back as a question. */
    fun hideAnswer(context: Context) {
        prefs(context).edit().remove(KEY_REVEALED_CARD).remove(KEY_PAGE_CARD).apply()
    }

    /** "Previous" ([step] -1) or "Next" (1): another page of the displayed card, in the notification (updated in place, without alert). */
    fun showPage(context: Context, step: Int) {
        val state = AnkiDroidHelper(context).storedState?.takeIf { it.hasCard() } ?: return
        val card = state.toCardInfo()
        val revealed = isAnswerRevealed(context, card)
        moveToPage(context, card, revealed, step)
        if (!NudgeSession.isActive(context)) return
        Notifications.create().showNotification(context, card, state.deckName, isSilent = true, replace = false)
    }

    fun showPreviousPage(context: Context) = showPage(context, -1)

    fun showNextPage(context: Context) = showPage(context, 1)

    /** Moves [card] [step] pages on, staying within its pages. The card is obviously seen: as in [revealAnswer]. */
    fun moveToPage(context: Context, card: CardInfo, revealed: Boolean, step: Int) {
        ReviewTimer.onCardSeen(context, CardSync.CardKey(card.noteID, card.cardOrd))
        val pages = Notifications.cardPages(context, card, revealed)
        val page = (currentPage(context, card, pages) + step).coerceIn(pages.indices)
        prefs(context).edit().putString(KEY_PAGE_CARD, key(card.noteID, card.cardOrd)).putInt(KEY_PAGE, page).apply()
    }

    /**
     * The page of [card] to show, among its [pages]. Until "Previous" or "Next" is tapped: the answer's first page
     * after "Show answer"; otherwise the first page.
     */
    fun currentPage(context: Context, card: CardInfo, pages: List<CardPages.Page>): Int {
        val prefs = prefs(context)
        if (prefs.getString(KEY_PAGE_CARD, null) == key(card.noteID, card.cardOrd)) {
            return prefs.getInt(KEY_PAGE, 0).coerceIn(pages.indices)
        }
        return if (isAnswerRevealed(context, card)) CardPages.firstAnswerPage(pages) else 0
    }

    /** Whether the answer of [card] was revealed. */
    fun isAnswerRevealed(context: Context, card: CardInfo): Boolean =
        prefs(context).getString(KEY_REVEALED_CARD, null) == key(card.noteID, card.cardOrd)

    private fun key(noteId: Long, ord: Int) = "$noteId:$ord"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

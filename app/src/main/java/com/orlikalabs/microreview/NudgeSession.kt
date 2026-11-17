package com.orlikalabs.microreview

import android.app.NotificationManager
import android.content.Context
import android.util.Log

/**
 * A nudge: the current card offered while the phone is in use, when [UsageMonitorService] says one is due. By
 * default it takes the screen, over the app in use ([CardOverlay]). Otherwise, or without "Display over other apps",
 * it is a pop-up notification. With nudges on, the card notification is only posted for the length of a nudge
 * ([LockSession.mayPostNow]): it is
 * never on the lock screen, and it is withdrawn when it is no longer looked at (screen off, ignored, swiped), so
 * the time recorded with an answer is time actually spent on the card.
 *
 * After the answer, the notification offers "Keep going" for a few seconds ([Notifications.showKeepGoingPrompt]),
 * which opens the review screen for as many cards as wanted; otherwise the next card waits for the next nudge.
 */
object NudgeSession {
    private const val TAG = "Focus"
    private const val PREFS = "com.orlikalabs.microreview.nudge"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PROMPTING = "prompting"

    /** "Snooze" on a card. */
    const val ACTION_SNOOZE = "ACTION_NUDGE_SNOOZE"

    /** The card notification was swiped away. */
    const val ACTION_DISMISSED = "ACTION_NUDGE_DISMISSED"

    /** Whether a nudge's card is on screen, waiting for its answer. */
    fun isActive(context: Context): Boolean = FocusSettings.nudgesEnabled(context) && prefs(context).getBoolean(KEY_ACTIVE, false)

    /** Whether the answered card is being replaced by the "Keep going" prompt: its notification must not be removed. */
    fun isPrompting(context: Context): Boolean = prefs(context).getBoolean(KEY_PROMPTING, false)

    /** Whether a nudge is in progress: no other one starts meanwhile. */
    fun isBusy(context: Context): Boolean =
        isActive(context) || isPrompting(context) || FocusReviewActivity.isShown || CardOverlay.isShowing

    /**
     * Offers the current card, checked against AnkiDroid first. Does ContentProvider I/O: call it off the main thread.
     *
     * @return whether a card was offered; false when nothing is due
     */
    fun fire(context: Context): Boolean {
        try {
            CardStateController.syncWithAnkiDroid(context)
        } catch (e: Exception) {
            Log.e(TAG, "could not check the card before a nudge", e)
        }
        val state = AnkiDroidHelper(context).storedState?.takeIf { it.hasCard() }
        if (state == null) {
            FocusLog.i(context, "card due, but nothing to review")
            return false
        }
        if (FocusSettings.nudgeStyle(context) == FocusSettings.NudgeStyle.FULL_SCREEN && FocusState.canOpenOverApps(context)) {
            CardOverlay.request(context, FocusCardController.Mode.NUDGE)
            return true
        }
        FocusLog.i(context, "card posted as a notification")
        val card = state.toCardInfo()
        // A card shown before and withdrawn starts over: its answer hidden, its time from now.
        CardReview.hideAnswer(context)
        prefs(context).edit().putBoolean(KEY_ACTIVE, true).putBoolean(KEY_PROMPTING, false).commit()
        ReviewTimer.restart(context, CardSync.CardKey(card.noteID, card.cardOrd))
        Notifications.create().showNotification(context, card, state.deckName, isSilent = false)
        return true
    }

    /**
     * The card is no longer looked at and wasn't answered (screen off, ignored until its notification timed out,
     * swiped away, snoozed): it is withdrawn and the time spent doesn't count. It stays due and comes back with
     * the next nudge.
     */
    fun withdraw(context: Context) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_ACTIVE, false) && !prefs.getBoolean(KEY_PROMPTING, false)) return
        Log.i(TAG, "nudge withdrawn")
        prefs.edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_PROMPTING, false).commit()
        ReviewTimer.clear(context)
        CardReview.hideAnswer(context)
        LockSession.markHidden(context)
        notificationManager(context).cancel(Notifications.NOTIFICATION_ID)
    }

    /** The notification's card was tapped: it continues full screen ([FocusReviewActivity]), its timer running. */
    fun handOver(context: Context) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_PROMPTING, false).commit()
        LockSession.markHidden(context)
        notificationManager(context).cancel(Notifications.NOTIFICATION_ID)
    }

    /** The nudge's card is being answered: the next card waits for its own nudge. */
    fun onAnswered(context: Context) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_PROMPTING, true).commit()
    }

    /** The "Keep going" prompt is posted (it removes itself), or there was nothing to prompt for. */
    fun onPromptDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_PROMPTING, false).commit()
    }

    /** The notification can disappear without a word (timeout): a nudge without its notification is over. */
    fun reconcile(context: Context) {
        if (!prefs(context).getBoolean(KEY_ACTIVE, false)) return
        val visible = notificationManager(context).activeNotifications.any { it.id == Notifications.NOTIFICATION_ID }
        if (!visible) withdraw(context)
    }

    /** "Snooze" on the card: no card for the snooze length chosen in the settings. */
    fun snoozeFromCard(context: Context) {
        FocusState.snooze(context, FocusSettings.snoozeMinutes(context))
        withdraw(context)
        UsageMonitorService.refreshNotification(context)
    }

    /** Nudges were turned on or off: the card notification of the other way of reviewing is removed or comes back. */
    fun onModeChanged(context: Context) {
        prefs(context).edit().clear().commit()
        ReviewTimer.clear(context)
        if (FocusSettings.nudgesEnabled(context)) {
            LockSession.markHidden(context)
            notificationManager(context).cancel(Notifications.NOTIFICATION_ID)
        } else {
            // The next sync posts the card again.
            LockSession.clearHidden(context)
        }
    }

    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

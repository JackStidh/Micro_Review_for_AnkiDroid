package com.orlikalabs.microreview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

/**
 * Handles the buttons of a nudge shown as a notification ("Show answer", "Previous", "Next", the answer buttons,
 * Snooze) and of the monitor's notification.
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("Notifications", "onReceive called: ${intent?.action}")
        if (context == null)
            return
        val action = intent?.action ?: return
        val task: (Context) -> Unit = when (action) {
            Notifications.ACTION_SHOW_ANSWER -> CardReview::revealAnswer
            Notifications.ACTION_PREVIOUS_PAGE -> CardReview::showPreviousPage
            Notifications.ACTION_NEXT_PAGE -> CardReview::showNextPage
            NudgeSession.ACTION_SNOOZE -> NudgeSession::snoozeFromCard
            NudgeSession.ACTION_DISMISSED -> NudgeSession::withdraw
            else -> FocusActions.task(action) ?: answerTask(action, intent) ?: return
        }

        // Looking through several decks can take a while: don't block the main thread.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                task(appContext)
            } catch (e: Exception) {
                Log.e("Notifications", "could not handle $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** An answer button of the nudge's notification, for the card in [intent]. */
    private fun answerTask(action: String, intent: Intent): ((Context) -> Unit)? {
        val prefix = Notifications.ACTION_BUTTON_PREFIX
        if (!action.startsWith(prefix)) return null
        val button = action.removePrefix(prefix).toIntOrNull()?.takeIf { it in AnswerButtons.AGAIN..AnswerButtons.EASY } ?: return null
        val card = CardReview.answeredCard(intent)
        if (card == null) {
            Log.w("Notifications", "$action without its card, ignored")
            return null
        }
        return { c -> CardReview.answer(c, button, card) }
    }
}

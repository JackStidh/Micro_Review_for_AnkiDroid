package com.orlikalabs.microreview

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * The buttons of the monitor's notification ([UsageMonitorService]): snooze, resume, "I'm not driving". Sent to
 * [NotificationReceiver] like the card's buttons.
 */
object FocusActions {
    /** Followed by the number of minutes. */
    const val ACTION_SNOOZE_PREFIX = "ACTION_FOCUS_SNOOZE_"
    const val ACTION_SNOOZE_UNTIL_MORNING = "ACTION_FOCUS_SNOOZE_MORNING"
    const val ACTION_RESUME = "ACTION_FOCUS_RESUME"
    const val ACTION_NOT_DRIVING = "ACTION_FOCUS_NOT_DRIVING"

    /** How long "I'm not driving" holds. */
    const val NOT_DRIVING_MS = 60 * 60 * 1000L

    /** What [action] does, or null if it isn't one of these buttons. */
    fun task(action: String): ((Context) -> Unit)? = when {
        action == ACTION_SNOOZE_UNTIL_MORNING -> { context -> snoozed(context) { FocusState.snoozeUntilMorning(context) } }
        action == ACTION_RESUME -> { context ->
            FocusState.resume(context)
            UsageMonitorService.refreshNotification(context)
        }
        action == ACTION_NOT_DRIVING -> { context ->
            FocusSettings.setNotDrivingUntil(context, System.currentTimeMillis() + NOT_DRIVING_MS)
            UsageMonitorService.refreshNotification(context)
        }
        action.startsWith(ACTION_SNOOZE_PREFIX) -> action.removePrefix(ACTION_SNOOZE_PREFIX).toIntOrNull()?.let { minutes ->
            { context: Context -> snoozed(context) { FocusState.snooze(context, minutes) } }
        }
        else -> null
    }

    private fun snoozed(context: Context, snooze: () -> Unit) {
        snooze()
        // A card on screen goes away with the snooze.
        NudgeSession.withdraw(context)
        UsageMonitorService.refreshNotification(context)
    }

    fun intent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).setAction(action).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}

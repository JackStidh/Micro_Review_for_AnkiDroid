package com.orlikalabs.microreview

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import java.time.ZonedDateTime

/** The phone's state as far as cards are concerned: may one interrupt now, and what the monitor is allowed to see. */
object FocusState {

    /** Why no card may interrupt now ([Interruptions.Reason]), or null. May query Android Auto: call it off the main thread. */
    fun pauseReason(context: Context, now: Long = System.currentTimeMillis()): Interruptions.Reason? {
        val time = ZonedDateTime.now()
        return Interruptions.reason(
            snoozed = QuietTime.isSnoozed(FocusSettings.snoozeUntil(context), now),
            quietHours = QuietTime.isQuiet(FocusSettings.quietHours(context), time.hour * 60 + time.minute),
            driving = FocusSettings.pauseWhenDriving(context) && DrivingDetector.isDriving(context, now),
            doNotDisturb = FocusSettings.pauseOnDoNotDisturb(context) && isDoNotDisturbOn(context),
            inCall = FocusSettings.pauseInCall(context) && isInCall(context),
        )
    }

    /**
     * Whether the gate is lifted for now: while driving, and during a snooze only with "snooze also lifts the gate".
     * Quiet hours and Do Not Disturb don't lift it: they are about not being interrupted, and the gate only answers
     * the user's own tap on an app.
     */
    fun gatePaused(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        (FocusSettings.pauseWhenDriving(context) && DrivingDetector.isDriving(context, now)) ||
            (FocusSettings.snoozeLiftsGate(context) && QuietTime.isSnoozed(FocusSettings.snoozeUntil(context), now))

    fun snooze(context: Context, minutes: Int, now: Long = System.currentTimeMillis()) {
        FocusSettings.setSnoozeUntil(context, QuietTime.snoozeFor(minutes, now))
    }

    fun snoozeUntilMorning(context: Context) {
        FocusSettings.setSnoozeUntil(context, QuietTime.untilMorning(ZonedDateTime.now(), FocusSettings.morningMinute(context)))
    }

    fun resume(context: Context) {
        FocusSettings.setSnoozeUntil(context, 0)
    }

    private fun isDoNotDisturbOn(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val filter = manager.currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun isInCall(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return when (audio.mode) {
            AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
            else -> false
        }
    }

    /** Whether the screen is on and the phone unlocked: only then is a card actually seen. */
    fun isInUse(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    /** "Usage access": lets the monitor see which app is in front. Granted by the user in the system settings. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** "Display over other apps": lets the gate and a full-screen card open over the app in front. */
    fun canOpenOverApps(context: Context): Boolean = Settings.canDrawOverlays(context)
}

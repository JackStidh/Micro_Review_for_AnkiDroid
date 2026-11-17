package com.orlikalabs.microreview

/** Why no card may interrupt right now. Pure part of the decision; [FocusState] reads the phone's state. */
object Interruptions {
    enum class Reason { SNOOZED, QUIET_HOURS, DRIVING, DO_NOT_DISTURB, IN_CALL }

    /** The first reason that applies, in the order a user would expect to be told, or null if a card may come. */
    fun reason(snoozed: Boolean, quietHours: Boolean, driving: Boolean, doNotDisturb: Boolean, inCall: Boolean): Reason? = when {
        snoozed -> Reason.SNOOZED
        driving -> Reason.DRIVING
        inCall -> Reason.IN_CALL
        quietHours -> Reason.QUIET_HOURS
        doNotDisturb -> Reason.DO_NOT_DISTURB
        else -> null
    }

    /**
     * Whether the phone is in a moving vehicle according to the last activity report: entered and not left since,
     * and not so long ago that the "left" report was probably missed.
     */
    fun inVehicle(entered: Boolean, reportedAt: Long, now: Long, maxAgeMs: Long = VEHICLE_REPORT_MAX_AGE_MS): Boolean =
        entered && now - reportedAt in 0..maxAgeMs

    const val VEHICLE_REPORT_MAX_AGE_MS = 3 * 60 * 60 * 1000L
}

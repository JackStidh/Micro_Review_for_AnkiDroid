package com.orlikalabs.microreview

/** Why no card may interrupt right now. Pure part of the decision; [FocusState] reads the phone's state. */
object Interruptions {
    enum class Reason { SNOOZED, QUIET_HOURS, BLUETOOTH, DO_NOT_DISTURB, IN_CALL }

    /** The first reason that applies, in the order a user would expect to be told, or null if a card may come. */
    fun reason(
        snoozed: Boolean,
        quietHours: Boolean,
        doNotDisturb: Boolean,
        inCall: Boolean,
        bluetooth: Boolean = false,
    ): Reason? = when {
        snoozed -> Reason.SNOOZED
        bluetooth -> Reason.BLUETOOTH
        inCall -> Reason.IN_CALL
        quietHours -> Reason.QUIET_HOURS
        doNotDisturb -> Reason.DO_NOT_DISTURB
        else -> null
    }
}

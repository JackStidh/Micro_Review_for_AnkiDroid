package com.orlikalabs.microreview

/**
 * Pure logic of the app gate: opening one of the chosen apps first asks for a few cards. Answering them gives a
 * pass for that app, which lasts until another app is opened: there is no grace period, so closing the app and
 * opening it again asks for cards again.
 */
object GateRules {
    const val DEFAULT_CARDS = 5
    const val MAX_CARDS = 20
    const val DEFAULT_SKIPS_PER_DAY = 1

    /**
     * System windows that show up over an app without the user leaving it (permission dialogs, the share sheet):
     * they neither end the app's pass nor get gated.
     */
    val TRANSIENT_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.intentresolver",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    /** Never gated, even if chosen: AnkiDroid itself, the phone and its emergency dialer. */
    val NEVER_GATED = setOf(
        "com.ichi2.anki",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.emergency",
        "com.android.server.telecom",
    )

    /**
     * Whether [foreground], which just came to the front, must be gated.
     *
     * @param pass the app that holds a pass ([passAfter] with this same foreground change applied), or null
     * @param hasDueCard whether a card can be asked; with nothing due the gate stays open
     * @param paused whether the gate is lifted for now (Bluetooth, or snoozed with "snooze also lifts the gate")
     */
    fun mustGate(
        foreground: String,
        ownPackage: String,
        enabled: Boolean,
        gated: Set<String>,
        pass: String?,
        hasDueCard: Boolean,
        paused: Boolean,
    ): Boolean = enabled && !paused && hasDueCard &&
        foreground in gated &&
        foreground != ownPackage && foreground !in NEVER_GATED && foreground !in TRANSIENT_PACKAGES &&
        foreground != pass

    /**
     * The pass once [foreground] came to the front: kept while the user is in the app that holds it or under a
     * system window; lost as soon as any other app is opened, this one included (the gate is an overlay, not one
     * of its screens).
     */
    fun passAfter(pass: String?, foreground: String): String? =
        if (pass != null && (foreground == pass || foreground in TRANSIENT_PACKAGES)) pass else null

    /** Emergency skips left today. [usedDay] is the day (epoch day) [used] was counted on. */
    fun skipsLeft(perDay: Int, usedDay: Long, used: Int, today: Long): Int =
        (perDay - if (usedDay == today) used else 0).coerceAtLeast(0)

    /** Cards the gate asks for: [wanted], within 1..[MAX_CARDS]. */
    fun cardsRequired(wanted: Int): Int = wanted.coerceIn(1, MAX_CARDS)
}

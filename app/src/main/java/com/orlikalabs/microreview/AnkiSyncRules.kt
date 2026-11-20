package com.orlikalabs.microreview

/**
 * Pure part of [AnkiSync]: when to ask AnkiDroid to sync. Kept free of Android classes so it can be unit tested.
 */
object AnkiSyncRules {
    /** At most one request this often: answers come in small bursts, and AnkiDroid refuses more than one per 2 min. */
    const val MIN_INTERVAL_MS = 15 * 60_000L

    /** AnkiDroid shows in front for a moment after a request: that isn't the user leaving an app. */
    const val FLASH_MS = 15_000L

    /**
     * Whether to ask now: answers wait to be synced, the phone is plugged in (one sync when it charges, typically at
     * night, rather than syncs all day), and the last request is old enough (or in the future: clock change).
     */
    fun due(pending: Boolean, requestedAt: Long, now: Long, pluggedIn: Boolean, minIntervalMs: Long = MIN_INTERVAL_MS): Boolean =
        pending && pluggedIn && (now - requestedAt >= minIntervalMs || now < requestedAt)

    /** Whether AnkiDroid coming to the front at [now] is the request's own doing. */
    fun isFlash(requestedAt: Long, now: Long): Boolean = requestedAt != 0L && now - requestedAt in 0..FLASH_MS
}

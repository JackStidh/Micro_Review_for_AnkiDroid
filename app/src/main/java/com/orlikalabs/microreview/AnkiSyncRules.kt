package com.orlikalabs.microreview

/**
 * Pure part of [AnkiSync]: when to ask AnkiDroid to sync. Kept free of Android classes so it can be unit tested.
 *
 * The phone and the PC both review: each syncs right after its cards, and before the next ones gets what the other
 * synced, so the same card isn't reviewed on both.
 */
object AnkiSyncRules {
    /** AnkiDroid ignores sync requests less than 2 minutes apart. */
    const val MIN_GAP_MS = 2 * 60_000L

    /** AnkiDroid shows in front for a moment after a request: that isn't the user leaving an app. */
    const val FLASH_MS = 15_000L

    /** As a card screen closes: answers were given and AnkiDroid would take the request. */
    fun dueAfterCards(pending: Boolean, requestedAt: Long, now: Long): Boolean = pending && gapOver(requestedAt, now)

    /**
     * While the phone is in use, with no card screen open: the PC synced its answers since the phone's last sync
     * ([pcSyncedAt], from the relay; the next card here must not be one already reviewed there), or answers wait
     * (a set of cards right after another, when AnkiDroid would have ignored the request).
     */
    fun dueInUse(pending: Boolean, requestedAt: Long, now: Long, pcSyncedAt: Long): Boolean =
        (pending || pcSyncedAt > requestedAt) && gapOver(requestedAt, now)

    /** Whether AnkiDroid coming to the front at [now] is the request's own doing. */
    fun isFlash(requestedAt: Long, now: Long): Boolean = requestedAt != 0L && now - requestedAt in 0..FLASH_MS

    /** The last request is old enough, or in the future (the clock was set back). */
    private fun gapOver(requestedAt: Long, now: Long): Boolean = now - requestedAt >= MIN_GAP_MS || now < requestedAt
}

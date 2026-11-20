package com.orlikalabs.microreview

import com.orlikalabs.microreview.CardSync.CardKey

/**
 * Pure part of [AnkiSync]: when to ask AnkiDroid to sync, and which cards the PC reviewed since. Kept free of Android
 * classes so it can be unit tested.
 *
 * The phone and the PC both review. AnkiDroid can only sync with its screen in front for a moment, so the phone
 * only asks as a card screen closes, hidden under it. Between two card screens, the PC's answers aren't on the phone
 * yet: the PC sends the cards it reviewed with its "synced" message, and the phone passes over them until its next
 * sync ([skipped]), so the same card isn't reviewed on both.
 */
object AnkiSyncRules {
    /** AnkiDroid ignores sync requests less than 2 minutes apart (and posts an error notification). */
    const val MIN_GAP_MS = 2 * 60_000L

    /** AnkiDroid shows in front for a moment after a request: that isn't the user leaving an app. */
    const val FLASH_MS = 15_000L

    /** At most this many cards reviewed on the PC are kept: more is a whole session, synced long before. */
    const val MAX_PC_REVIEWED = 500

    /**
     * As a card screen closes: answers wait here, or the PC synced its answers since the phone's last sync
     * ([pcSyncedAt]), and AnkiDroid would take the request.
     */
    fun dueAfterCards(pending: Boolean, requestedAt: Long, now: Long, pcSyncedAt: Long = 0): Boolean =
        (pending || pcSyncedAt > requestedAt) && gapOver(requestedAt, now)

    /** Whether AnkiDroid coming to the front at [now] is the request's own doing. */
    fun isFlash(requestedAt: Long, now: Long): Boolean = requestedAt != 0L && now - requestedAt in 0..FLASH_MS

    /**
     * The cards to pass over: reviewed on the PC ([pcReviewed]: when the PC said so, by card) after the phone's last
     * sync request, so not on the phone yet.
     */
    fun skipped(pcReviewed: Map<CardKey, Long>, requestedAt: Long): Set<CardKey> =
        pcReviewed.filterValues { it > requestedAt }.keys

    /** [pcReviewed] with [keys] said at [time], without what the phone has synced since ([requestedAt]). */
    fun withPcReviewed(pcReviewed: Map<CardKey, Long>, keys: Collection<CardKey>, time: Long, requestedAt: Long): Map<CardKey, Long> {
        val all = LinkedHashMap<CardKey, Long>()
        pcReviewed.forEach { (key, at) -> if (at > requestedAt) all[key] = at }
        if (time > requestedAt) keys.forEach { key -> all[key] = maxOf(all[key] ?: 0L, time) }
        return all.entries.sortedByDescending { it.value }.take(MAX_PC_REVIEWED).associate { it.key to it.value }
    }

    /** "noteId:ord:time" entries, ";" separated. */
    fun encode(pcReviewed: Map<CardKey, Long>): String =
        pcReviewed.entries.joinToString(";") { (key, at) -> "${key.noteId}:${key.ord}:$at" }

    fun decode(text: String?): Map<CardKey, Long> {
        if (text.isNullOrEmpty()) return emptyMap()
        val all = LinkedHashMap<CardKey, Long>()
        for (entry in text.split(';')) {
            val parts = entry.split(':')
            if (parts.size != 3) continue
            val noteId = parts[0].toLongOrNull() ?: continue
            val ord = parts[1].toIntOrNull() ?: continue
            val at = parts[2].toLongOrNull() ?: continue
            all[CardKey(noteId, ord)] = at
        }
        return all
    }

    /** The last request is old enough, or in the future (the clock was set back). */
    private fun gapOver(requestedAt: Long, now: Long): Boolean = now - requestedAt >= MIN_GAP_MS || now < requestedAt
}

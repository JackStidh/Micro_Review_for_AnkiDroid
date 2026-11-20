package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiSyncRulesTest {
    private val minute = 60_000L

    @Test
    fun afterCardsOnlyWithAnswers() {
        assertTrue(AnkiSyncRules.dueAfterCards(pending = true, requestedAt = 0, now = 100 * minute))
        assertFalse(AnkiSyncRules.dueAfterCards(pending = false, requestedAt = 0, now = 100 * minute))
    }

    @Test
    fun notTwiceWithinAnkiDroidsTwoMinutes() {
        assertFalse(AnkiSyncRules.dueAfterCards(pending = true, requestedAt = 99 * minute, now = 100 * minute))
        assertTrue(AnkiSyncRules.dueAfterCards(pending = true, requestedAt = 98 * minute, now = 100 * minute))
    }

    @Test
    fun theirAnswersAsTheNextCardsClose() {
        // The PC synced after the phone's last sync: get its answers, even with none here.
        assertTrue(AnkiSyncRules.dueAfterCards(pending = false, requestedAt = 10 * minute, now = 100 * minute, pcSyncedAt = 50 * minute))
        // Already got them.
        assertFalse(AnkiSyncRules.dueAfterCards(pending = false, requestedAt = 60 * minute, now = 100 * minute, pcSyncedAt = 50 * minute))
        assertFalse(AnkiSyncRules.dueAfterCards(pending = false, requestedAt = 10 * minute, now = 100 * minute, pcSyncedAt = 0))
        assertFalse(AnkiSyncRules.dueAfterCards(pending = false, requestedAt = 99 * minute, now = 100 * minute, pcSyncedAt = 99 * minute + 1))
    }

    @Test
    fun cardsReviewedOnThePcArePassedOverUntilTheNextSync() {
        val a = CardSync.CardKey(1, 0)
        val b = CardSync.CardKey(2, 1)
        val reviewed = AnkiSyncRules.withPcReviewed(emptyMap(), listOf(a, b), time = 50 * minute, requestedAt = 10 * minute)
        assertEquals(setOf(a, b), AnkiSyncRules.skipped(reviewed, requestedAt = 10 * minute))
        // The phone synced after the PC said so: they're here now.
        assertEquals(emptySet<CardSync.CardKey>(), AnkiSyncRules.skipped(reviewed, requestedAt = 60 * minute))
        // A message older than the phone's last sync adds nothing, and drops what that sync brought.
        assertEquals(emptyMap<CardSync.CardKey, Long>(), AnkiSyncRules.withPcReviewed(reviewed, listOf(a), time = 55 * minute, requestedAt = 60 * minute))
    }

    @Test
    fun theLatestWordOnACardCounts() {
        val a = CardSync.CardKey(1, 0)
        val first = AnkiSyncRules.withPcReviewed(emptyMap(), listOf(a), time = 50 * minute, requestedAt = 0)
        val again = AnkiSyncRules.withPcReviewed(first, listOf(a), time = 70 * minute, requestedAt = 0)
        assertEquals(mapOf(a to 70 * minute), again)
        // Read again from the relay: the older message doesn't move it back.
        assertEquals(mapOf(a to 70 * minute), AnkiSyncRules.withPcReviewed(again, listOf(a), time = 50 * minute, requestedAt = 0))
    }

    @Test
    fun theListStaysSmall() {
        val many = (1..2_000L).map { CardSync.CardKey(it, 0) }
        val kept = AnkiSyncRules.withPcReviewed(emptyMap(), many, time = 50 * minute, requestedAt = 0)
        assertEquals(AnkiSyncRules.MAX_PC_REVIEWED, kept.size)
    }

    @Test
    fun storedAndReadBack() {
        val reviewed = mapOf(CardSync.CardKey(1712345678901, 0) to 50 * minute, CardSync.CardKey(2, 3) to 60 * minute)
        assertEquals(reviewed, AnkiSyncRules.decode(AnkiSyncRules.encode(reviewed)))
        assertEquals(emptyMap<CardSync.CardKey, Long>(), AnkiSyncRules.decode(null))
        assertEquals(mapOf(CardSync.CardKey(2, 3) to 7L), AnkiSyncRules.decode("junk;2:3:7;1:x:2"))
    }

    @Test
    fun aClockSetBackDoesntBlockForever() {
        assertTrue(AnkiSyncRules.dueAfterCards(pending = true, requestedAt = 200 * minute, now = 100 * minute))
    }

    @Test
    fun ankiDroidRightAfterARequestIsTheFlash() {
        assertTrue(AnkiSyncRules.isFlash(requestedAt = 100 * minute, now = 100 * minute + 3_000))
        assertFalse(AnkiSyncRules.isFlash(requestedAt = 100 * minute, now = 101 * minute))
        assertFalse(AnkiSyncRules.isFlash(requestedAt = 0, now = 3_000))
    }
}

package com.orlikalabs.microreview

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
    fun theirAnswersAfterThePcSynced() {
        // The PC synced after the phone's last sync: get its answers.
        assertTrue(AnkiSyncRules.dueInUse(pending = false, requestedAt = 10 * minute, now = 100 * minute, pcSyncedAt = 50 * minute))
        // Already got them.
        assertFalse(AnkiSyncRules.dueInUse(pending = false, requestedAt = 60 * minute, now = 100 * minute, pcSyncedAt = 50 * minute))
        assertFalse(AnkiSyncRules.dueInUse(pending = false, requestedAt = 10 * minute, now = 100 * minute, pcSyncedAt = 0))
    }

    @Test
    fun leftOverAnswersGoWhileInUse() {
        assertTrue(AnkiSyncRules.dueInUse(pending = true, requestedAt = 10 * minute, now = 100 * minute, pcSyncedAt = 0))
        assertFalse(AnkiSyncRules.dueInUse(pending = true, requestedAt = 99 * minute, now = 100 * minute, pcSyncedAt = 0))
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

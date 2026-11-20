package com.orlikalabs.microreview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiSyncRulesTest {
    private val minute = 60_000L

    @Test
    fun nothingToSyncNoRequest() {
        assertFalse(AnkiSyncRules.due(pending = false, requestedAt = 0, now = 100 * minute, pluggedIn = true))
    }

    @Test
    fun answersWaitingAskOnceInAWhile() {
        assertTrue(AnkiSyncRules.due(pending = true, requestedAt = 0, now = 100 * minute, pluggedIn = true))
        assertFalse(AnkiSyncRules.due(pending = true, requestedAt = 90 * minute, now = 100 * minute, pluggedIn = true))
        assertTrue(AnkiSyncRules.due(pending = true, requestedAt = 85 * minute, now = 100 * minute, pluggedIn = true))
    }

    @Test
    fun onlyWhilePluggedIn() {
        assertFalse(AnkiSyncRules.due(pending = true, requestedAt = 0, now = 100 * minute, pluggedIn = false))
    }

    @Test
    fun aClockSetBackDoesntBlockForever() {
        assertTrue(AnkiSyncRules.due(pending = true, requestedAt = 200 * minute, now = 100 * minute, pluggedIn = true))
    }

    @Test
    fun ankiDroidRightAfterARequestIsTheFlash() {
        assertTrue(AnkiSyncRules.isFlash(requestedAt = 100 * minute, now = 100 * minute + 3_000))
        assertFalse(AnkiSyncRules.isFlash(requestedAt = 100 * minute, now = 101 * minute))
        assertFalse(AnkiSyncRules.isFlash(requestedAt = 0, now = 3_000))
    }
}

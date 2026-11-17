package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeClockTest {
    private val instagram = "com.instagram.android"
    private val maps = "com.google.android.apps.maps"
    private val perApp = mapOf(instagram to 5, maps to NudgeClock.NEVER)

    /** [minutes] spent in [app], in ticks of one second. Returns whether a card became due on the last tick. */
    private fun NudgeClock.spend(app: String?, minutes: Int, defaultMinutes: Int = 20): Boolean {
        var due = false
        repeat(minutes * 60) { due = tick(app, 1_000, perApp, defaultMinutes) }
        return due
    }

    @Test
    fun defaultIntervalCountsTimeAcrossApps() {
        val clock = NudgeClock()
        assertFalse(clock.spend("a", 10))
        assertFalse(clock.spend("b", 9))
        assertTrue(clock.spend("c", 1))
    }

    @Test
    fun anAppWithItsOwnIntervalCountsItsOwnTime() {
        val clock = NudgeClock()
        assertFalse(clock.spend("a", 19))
        // 19 minutes elsewhere don't count for Instagram.
        assertFalse(clock.spend(instagram, 4))
        assertTrue(clock.spend(instagram, 1))
    }

    @Test
    fun resetStartsTheIntervalOver() {
        val clock = NudgeClock()
        assertTrue(clock.spend(instagram, 5))
        clock.reset(instagram, perApp)
        assertFalse(clock.spend(instagram, 4))
        assertTrue(clock.spend(instagram, 1))
    }

    @Test
    fun resettingOneAppKeepsTheOthersTime() {
        val clock = NudgeClock()
        assertFalse(clock.spend("a", 19))
        assertTrue(clock.spend(instagram, 5))
        clock.reset(instagram, perApp)
        assertTrue(clock.spend("a", 1))
    }

    @Test
    fun neverMeansNoCard() {
        val clock = NudgeClock()
        assertFalse(clock.spend(maps, 120))
        assertFalse(clock.spend("a", 120, defaultMinutes = NudgeClock.NEVER))
    }

    @Test
    fun aLongGapIsNotTimeOnThePhone() {
        val clock = NudgeClock()
        // The process was paused for an hour between two ticks: at most a few seconds count.
        assertFalse(clock.tick("a", 60 * 60_000L, perApp, 20))
        assertFalse(clock.tick("a", -5, perApp, 20))
    }

    @Test
    fun remainingTimeIsShownPerApp() {
        val clock = NudgeClock()
        assertEquals(5 * 60_000L, clock.remainingMs(instagram, perApp, 20))
        clock.spend(instagram, 2)
        assertEquals(3 * 60_000L, clock.remainingMs(instagram, perApp, 20))
        assertEquals(20 * 60_000L, clock.remainingMs("a", perApp, 20))
        assertNull(clock.remainingMs(maps, perApp, 20))
        assertNull(clock.remainingMs("a", perApp, NudgeClock.NEVER))
        clock.spend(instagram, 10)
        assertEquals(0L, clock.remainingMs(instagram, perApp, 20))
    }

    @Test
    fun unknownForegroundUsesTheDefault() {
        val clock = NudgeClock()
        assertFalse(clock.spend(null, 19))
        assertTrue(clock.spend(null, 1))
    }
}

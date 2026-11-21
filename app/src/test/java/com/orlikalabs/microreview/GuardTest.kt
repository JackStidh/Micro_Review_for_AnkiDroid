package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTest {
    private val min = 60_000L
    private val watched = setOf("insta", "tok")

    @Test
    fun aStretchAcrossWatchedAppsWithShortExits() {
        var s = Guard.Stretch()
        // The monitor looks every second; once a minute is enough here.
        for (m in 1L..9L) s = Guard.track(s, "insta", watched, m * min)
        s = Guard.track(s, "tok", watched, 10 * min)
        s = Guard.track(s, "messages", watched, 10 * min + 30_000) // a quick reply
        s = Guard.track(s, "insta", watched, 11 * min)
        assertEquals(min, s.startedAt)
        assertEquals(10, Guard.minutes(s, 11 * min))
        // Away for longer than the grace: a new stretch.
        s = Guard.track(s, "messages", watched, 20 * min)
        assertFalse(s.active(20 * min))
        s = Guard.track(s, "insta", watched, 21 * min)
        assertEquals(21 * min, s.startedAt)
    }

    @Test
    fun theSpellBreaksAtTheThresholdThenAfterFiveMore() {
        val s = Guard.Stretch(startedAt = 1, lastSeen = 20 * min, app = "insta")
        assertFalse(Guard.spellDue(s, true, 19 * min, 20))
        assertTrue(Guard.spellDue(s, true, 20 * min + 1, 20))
        assertFalse(Guard.spellDue(s, false, 20 * min + 1, 20))
        assertFalse(Guard.spellDue(s, true, 20 * min + 1, 0))
        val more = Guard.more(s, 20 * min + 1)
        assertFalse(Guard.spellDue(more.copy(lastSeen = 24 * min), true, 24 * min, 20))
        assertTrue(Guard.spellDue(more.copy(lastSeen = 26 * min), true, 26 * min, 20))
        assertFalse(Guard.spellDue(s.copy(exempt = true), true, 30 * min, 20))
    }

    @Test
    fun moreGetsHarderToChoose() {
        assertEquals(0L, Guard.holdMs(0))
        assertTrue(Guard.holdMs(1) < Guard.holdMs(2))
        assertEquals(Guard.holdMs(3), Guard.holdMs(9))
    }

    @Test
    fun aLongPhoneSession() {
        assertTrue(Guard.sessionDue(useStartedAt = 1, now = 61 * min, thresholdMin = 60, laterUntil = 0))
        assertFalse(Guard.sessionDue(1, 59 * min, 60, 0))
        assertFalse(Guard.sessionDue(1, 61 * min, 0, 0))
        assertFalse(Guard.sessionDue(1, 61 * min, 60, 70 * min))
    }

    @Test
    fun inactiveWhenTheStepsBarelyMove() {
        val h = 3600_000L
        val now = 14 * h
        val still = listOf(Guard.Sample(now - 3 * h, 1000), Guard.Sample(now - h, 1050), Guard.Sample(now, 1100))
        assertTrue(Guard.inactive(still, now, hour = 14, lastNudged = 0))
        val walked = listOf(Guard.Sample(now - 3 * h, 1000), Guard.Sample(now - h, 2500), Guard.Sample(now, 2600))
        assertFalse(Guard.inactive(walked, now, 14, 0))
        // Not enough history to tell.
        assertFalse(Guard.inactive(listOf(Guard.Sample(now - h, 1000), Guard.Sample(now, 1000)), now, 14, 0))
        // At night, or nudged a moment ago.
        assertFalse(Guard.inactive(still, now, hour = 23, lastNudged = 0))
        assertFalse(Guard.inactive(still, now, 14, lastNudged = now - h))
        // A restart: the counter starts over; its new count is the steps since.
        val restarted = listOf(Guard.Sample(now - 3 * h, 50_000), Guard.Sample(now - h, 40), Guard.Sample(now, 90))
        assertTrue(Guard.inactive(restarted, now, 14, 0))
    }

    @Test
    fun stepsSince() {
        val samples = listOf(Guard.Sample(0, 100), Guard.Sample(10, 300), Guard.Sample(20, 50), Guard.Sample(30, 80))
        assertEquals(280L, Guard.stepsSince(samples, 0))
        assertEquals(80L, Guard.stepsSince(samples, 15))
        assertEquals(samples, Guard.decodeSamples(Guard.encodeSamples(samples)))
    }

    @Test
    fun bedtime() {
        val half11 = 23 * 60 + 30
        assertTrue(Guard.pastBedtime(23 * 60 + 40, half11))
        assertTrue(Guard.pastBedtime(60, half11)) // 1:00
        assertFalse(Guard.pastBedtime(22 * 60, half11))
        assertFalse(Guard.pastBedtime(5 * 60, half11))
        assertFalse(Guard.pastBedtime(23 * 60 + 40, -1))
        // A bedtime after midnight.
        assertTrue(Guard.pastBedtime(45, 30))
        assertFalse(Guard.pastBedtime(23 * 60 + 50, 30))
        assertTrue(Guard.onTime(23 * 60 + 50, half11))
        assertFalse(Guard.onTime(30, half11))
    }

    @Test
    fun sleep() {
        val h = 3600_000L
        assertEquals(7 * h, Guard.sleepMs(10 * h, 17 * h))
        assertNull(Guard.sleepMs(10 * h, 11 * h))
        assertNull(Guard.sleepMs(0, 17 * h))
    }
}

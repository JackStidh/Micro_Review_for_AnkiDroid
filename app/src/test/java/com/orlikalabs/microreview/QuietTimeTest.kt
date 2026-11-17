package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class QuietTimeTest {
    private val night = QuietTime.Window(22 * 60, 7 * 60)
    private val lunch = QuietTime.Window(12 * 60, 13 * 60)

    @Test
    fun windowWithinTheDay() {
        assertFalse(QuietTime.isIn(lunch, 11 * 60 + 59))
        assertTrue(QuietTime.isIn(lunch, 12 * 60))
        assertTrue(QuietTime.isIn(lunch, 12 * 60 + 59))
        assertFalse(QuietTime.isIn(lunch, 13 * 60))
    }

    @Test
    fun windowPastMidnight() {
        assertTrue(QuietTime.isIn(night, 23 * 60))
        assertTrue(QuietTime.isIn(night, 0))
        assertTrue(QuietTime.isIn(night, 6 * 60 + 59))
        assertFalse(QuietTime.isIn(night, 7 * 60))
        assertFalse(QuietTime.isIn(night, 21 * 60 + 59))
    }

    @Test
    fun emptyWindowIsNeverQuiet() {
        assertFalse(QuietTime.isIn(QuietTime.Window(600, 600), 600))
        assertFalse(QuietTime.isQuiet(emptyList(), 600))
    }

    @Test
    fun anyWindowMakesItQuiet() {
        assertTrue(QuietTime.isQuiet(listOf(night, lunch), 12 * 60 + 30))
        assertFalse(QuietTime.isQuiet(listOf(night, lunch), 15 * 60))
    }

    @Test
    fun snoozeHoldsUntilItsEnd() {
        val until = QuietTime.snoozeFor(30, now = 1_000L)
        assertEquals(1_000L + 30 * 60_000L, until)
        assertTrue(QuietTime.isSnoozed(until, until - 1))
        assertFalse(QuietTime.isSnoozed(until, until))
        assertFalse(QuietTime.isSnoozed(0, 5))
    }

    @Test
    fun untilMorningIsTheNextMorning() {
        val zone = ZoneId.of("America/Chicago")
        val evening = ZonedDateTime.of(2026, 11, 17, 21, 0, 0, 0, zone)
        val nextMorning = ZonedDateTime.of(2026, 11, 18, 7, 0, 0, 0, zone)
        assertEquals(nextMorning.toInstant().toEpochMilli(), QuietTime.untilMorning(evening, 7 * 60))
        // After midnight, "tomorrow" is the morning that comes next.
        val lateNight = ZonedDateTime.of(2026, 11, 18, 1, 0, 0, 0, zone)
        assertEquals(nextMorning.toInstant().toEpochMilli(), QuietTime.untilMorning(lateNight, 7 * 60))
    }

    @Test
    fun windowsAreStoredAndReadBack() {
        assertEquals(listOf(lunch, night), QuietTime.parse(QuietTime.format(listOf(night, lunch, night))))
        assertEquals(emptyList<QuietTime.Window>(), QuietTime.parse(""))
        assertEquals(listOf(lunch), QuietTime.parse("oops,720-780,5-5,-3-9000"))
    }
}

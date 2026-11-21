package com.orlikalabs.microreview

import com.orlikalabs.microreview.MovePlan.Day
import com.orlikalabs.microreview.MovePlan.PainSpot
import com.orlikalabs.microreview.MovePlan.Phase
import com.orlikalabs.microreview.MovePlan.Session
import com.orlikalabs.microreview.MovePlan.Trend
import com.orlikalabs.microreview.MovePlan.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MovePlanTest {
    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun recoveryKeepsToTheRehabProgramThenPosture() {
        val parts = MovePlan.parts(MovePlan.session(Phase.RECOVERY, null, today))
        assertEquals(listOf("rehab", "posture"), parts.map { it.id })
        assertEquals(
            listOf("press_up", "bird_dog", "side_plank_knees", "curl_up", "bridge", "hinge"),
            parts[0].moves.map { it.id },
        )
        // Nothing loaded in recovery.
        assertTrue(parts.flatMap { it.moves }.none { it.kind == MovePlan.Kind.STRENGTH })
    }

    @Test
    fun rebuildEveryOtherDayAtMost() {
        assertEquals(Session.STRENGTH, MovePlan.session(Phase.REBUILD, null, today))
        assertEquals(Session.RECOVERY, MovePlan.session(Phase.REBUILD, today.minusDays(1), today))
        assertEquals(Session.STRENGTH, MovePlan.session(Phase.REBUILD, today.minusDays(2), today))
        assertEquals(Session.RECOVERY, MovePlan.session(Phase.RECOVERY, today.minusDays(9), today))
    }

    @Test
    fun loadsOnlyFromTheThirdWeek() {
        val start = LocalDate.of(2026, 12, 15)
        assertEquals(1, MovePlan.rebuildWeek(start, start))
        assertEquals(2, MovePlan.rebuildWeek(start, start.plusDays(13)))
        assertEquals(3, MovePlan.rebuildWeek(start, start.plusDays(14)))
        val early = MovePlan.parts(Session.STRENGTH, rebuildWeek = 2).flatMap { it.moves }.map { it.id }
        val later = MovePlan.parts(Session.STRENGTH, rebuildWeek = 3).flatMap { it.moves }.map { it.id }
        assertFalse("loaded_hinge" in early)
        assertTrue("loaded_hinge" in later && "carry" in later)
    }

    @Test
    fun deskBreaksGoRound() {
        assertEquals("standing_extension", MovePlan.deskMove(0).id)
        assertEquals("standing_extension", MovePlan.deskMove(MovePlan.DESK.size).id)
        assertEquals(MovePlan.DESK.last(), MovePlan.deskMove(-1))
    }

    @Test
    fun timedHolds() {
        assertEquals(12, MovePlan.BIRD_DOG.holds)
        assertEquals(5, MovePlan.CURL_UP.holds)
        assertEquals(0, MovePlan.PRESS_UP.holds)
    }

    @Test
    fun theLogIsStoredAndReadBack() {
        val days = listOf(
            Day(today.minusDays(1), morning = true, evening = "recovery", walkMinutes = 30, pain = PainSpot.THIGH, trend = Trend.BETTER),
            Day(today, evening = "strength", strength = true),
        )
        assertEquals(days, MovePlan.decode(MovePlan.encode(days)))
        assertEquals(emptyList<Day>(), MovePlan.decode(null))
        assertEquals(listOf(Day(today, morning = true)), MovePlan.decode("junk\n2026-11-20;m;p=NOPE;x=1"))
    }

    @Test
    fun theLogKeepsTwoMonths() {
        var days = emptyList<Day>()
        for (i in 0 until 100) days = MovePlan.update(days, today.plusDays(i.toLong())) { it.copy(walkMinutes = 10) }
        assertEquals(MovePlan.DAYS_KEPT, days.size)
        assertEquals(today.plusDays(99), days.last().date)
        val walked = MovePlan.update(days, today.plusDays(99)) { it.copy(walkMinutes = it.walkMinutes + 15) }
        assertEquals(25, walked.last().walkMinutes)
    }

    @Test
    fun backOffWhenThePainGoesBackDownAndStays() {
        fun day(back: Long, pain: PainSpot) = Day(today.minusDays(back), pain = pain)
        val better = listOf(day(6, PainSpot.THIGH), day(5, PainSpot.THIGH))
        val down = better + listOf(day(2, PainSpot.KNEE), day(1, PainSpot.KNEE), day(0, PainSpot.LOWER_LEG))
        assertEquals(Warning.BACK_OFF, MovePlan.warning(down, today))
        // Two days aren't "a few".
        assertNull(MovePlan.warning(better + listOf(day(1, PainSpot.KNEE), day(0, PainSpot.KNEE)), today))
        // Never higher than the knee: nothing moved back down.
        assertNull(MovePlan.warning(listOf(day(2, PainSpot.KNEE), day(1, PainSpot.KNEE), day(0, PainSpot.KNEE)), today))
    }

    @Test
    fun tooMuchWhenWorseTheMorningAfterStrength() {
        val days = listOf(Day(today.minusDays(1), strength = true, evening = "strength"), Day(today, trend = Trend.WORSE, pain = PainSpot.BUTTOCK))
        assertEquals(Warning.TOO_MUCH, MovePlan.warning(days, today))
        assertNull(MovePlan.warning(listOf(Day(today, trend = Trend.WORSE)), today))
    }

    @Test
    fun streakOfEvenings() {
        val days = listOf(
            Day(today.minusDays(3), evening = "recovery"),
            Day(today.minusDays(2), evening = "recovery"),
            Day(today.minusDays(1), evening = "rehab"),
        )
        assertEquals(3, MovePlan.streak(days, today))
        assertEquals(4, MovePlan.streak(days + Day(today, evening = "recovery"), today))
        assertEquals(0, MovePlan.streak(days, today.plusDays(2)))
    }

    @Test
    fun morningPrompt() {
        assertTrue(MovePlan.wakesUp(hour = 7, offMs = 7 * 3600_000L))
        assertFalse(MovePlan.wakesUp(hour = 7, offMs = 20 * 60_000L))
        assertFalse(MovePlan.wakesUp(hour = 2, offMs = 7 * 3600_000L))
        assertTrue(MovePlan.morningDue(null, armedToday = true, setAside = false, hour = 7, now = 100, laterUntil = 0))
        assertFalse(MovePlan.morningDue(Day(today, morning = true), true, false, 7, 100, 0))
        assertFalse(MovePlan.morningDue(Day(today, pain = PainSpot.NONE), true, false, 7, 100, 0))
        assertFalse(MovePlan.morningDue(null, true, setAside = true, hour = 7, now = 100, laterUntil = 0))
        assertFalse(MovePlan.morningDue(null, true, false, hour = 7, now = 100, laterUntil = 200))
        assertFalse(MovePlan.morningDue(null, true, false, hour = 14, now = 100, laterUntil = 0))
        assertFalse(MovePlan.morningDue(null, armedToday = false, setAside = false, hour = 7, now = 100, laterUntil = 0))
    }

    @Test
    fun eveningPrompt() {
        val seven = 19 * 60
        assertTrue(MovePlan.eveningDue(null, false, minuteOfDay = seven, eveningMinutes = seven, promptsToday = 0, now = 1, laterUntil = 0))
        assertFalse(MovePlan.eveningDue(null, false, seven - 1, seven, 0, 1, 0))
        assertFalse(MovePlan.eveningDue(Day(today, evening = "rehab"), false, seven, seven, 0, 1, 0))
        assertFalse(MovePlan.eveningDue(null, setAside = true, minuteOfDay = seven, eveningMinutes = seven, promptsToday = 0, now = 1, laterUntil = 0))
        assertFalse(MovePlan.eveningDue(null, false, seven, seven, MovePlan.EVENING_PROMPTS_MAX, 1, 0))
        assertFalse(MovePlan.eveningDue(null, false, seven, seven, 1, now = 1, laterUntil = 2))
    }
}

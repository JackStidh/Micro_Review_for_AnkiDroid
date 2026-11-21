package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Habits, Tasks and Progress: the pure parts of the lifestyle side. */
class LifeTest {
    private val today = LocalDate.of(2026, 11, 21)
    private val vitamin = Habits.Habit("vitamin_d", "Vitamin D", times = listOf(9 * 60))
    private val twice = Habits.Habit("fish", "Fish oil", times = listOf(9 * 60, 21 * 60))

    @Test
    fun tapsAndStreaks() {
        var days = emptyList<Habits.Day>()
        for (back in 3L downTo 0L) repeat(8) { days = Habits.tap(days, today.minusDays(back), "water") }
        assertEquals(4, Habits.streak(Habits.WATER, days, today))
        days = Habits.tap(days, today, "water", -1)
        // Today isn't done any more: the streak counts up to yesterday.
        assertEquals(3, Habits.streak(Habits.WATER, days, today))
        assertEquals(0, Habits.tap(emptyList(), today, "water", -1).first().count("water"))
    }

    @Test
    fun progressAndAllDone() {
        val day = Habits.Day(today, mapOf("water" to 4, "vitamin_d" to 1))
        assertEquals(0.75f, Habits.progress(listOf(Habits.WATER, vitamin), day), 0.001f)
        assertFalse(Habits.allDone(listOf(Habits.WATER, vitamin), day))
        assertTrue(Habits.allDone(listOf(vitamin), day))
    }

    @Test
    fun aTimedDoseIsRemindedOnceWhenLate() {
        val at = 9 * 60 + 5
        assertEquals(Habits.Reminder(vitamin, 9 * 60), Habits.due(listOf(vitamin), null, at, emptySet(), emptyMap(), 0))
        assertNull(Habits.due(listOf(vitamin), null, 8 * 60, emptySet(), emptyMap(), 0))
        assertNull(Habits.due(listOf(vitamin), null, at, setOf("vitamin_d@540"), emptyMap(), 0))
        assertNull(Habits.due(listOf(vitamin), Habits.Day(today, mapOf("vitamin_d" to 1)), at, emptySet(), emptyMap(), 0))
        // Too late: it's for tomorrow.
        assertNull(Habits.due(listOf(vitamin), null, 9 * 60 + Habits.DOSE_WINDOW_MIN + 1, emptySet(), emptyMap(), 0))
    }

    @Test
    fun theSecondDoseComesAtItsTime() {
        val morningTaken = Habits.Day(today, mapOf("fish" to 1))
        assertNull(Habits.due(listOf(twice), morningTaken, 15 * 60, emptySet(), emptyMap(), 0))
        assertEquals(21 * 60, Habits.due(listOf(twice), morningTaken, 21 * 60 + 1, emptySet(), emptyMap(), 0)?.doseTime)
    }

    @Test
    fun waterBehindItsPace() {
        val noon = 12 * 60
        // By noon, about 2.5 cups are expected: none is behind.
        assertEquals(Habits.Reminder(Habits.WATER, null), Habits.due(listOf(Habits.WATER), null, noon, emptySet(), emptyMap(), 10_000_000))
        assertNull(Habits.due(listOf(Habits.WATER), Habits.Day(today, mapOf("water" to 2)), noon, emptySet(), emptyMap(), 10_000_000))
        // Reminded a moment ago.
        assertNull(Habits.due(listOf(Habits.WATER), null, noon, emptySet(), mapOf("water" to 10_000_000L - 60_000), 10_000_000))
        // Early morning: not yet.
        assertNull(Habits.due(listOf(Habits.WATER), null, 8 * 60 + 30, emptySet(), emptyMap(), 10_000_000))
    }

    @Test
    fun habitsStoredAndReadBack() {
        val habits = listOf(Habits.WATER, twice.copy(name = "Fish | oil"))
        val back = Habits.decodeHabits(Habits.encodeHabits(habits))!!
        assertEquals(Habits.WATER, back[0])
        assertEquals("Fish / oil", back[1].name)
        assertEquals(listOf(540, 1260), back[1].times)
        assertNull(Habits.decodeHabits(null))
        val days = listOf(Habits.Day(today, mapOf("water" to 3, "fish" to 1)))
        assertEquals(days, Habits.decodeDays(Habits.encodeDays(days)))
        assertEquals("vitamin_d_2", Habits.newId("Vitamin D", setOf("vitamin_d")))
        assertEquals("habit", Habits.newId("！", emptySet()))
    }

    @Test
    fun tasksDueThenNudged() {
        val hour = 3600_000L
        val now = 100 * hour
        val due = Tasks.Task(1, "Call the bank", created = now - 10 * hour, due = now - hour)
        val undated = Tasks.Task(2, "Tidy the desk", created = now - 5 * hour)
        val fresh = Tasks.Task(3, "Buy rice", created = now - hour)
        assertEquals(due, Tasks.due(listOf(fresh, undated, due), now, hour = 12))
        val reminded = Tasks.update(listOf(fresh, undated, due), 1) { it.copy(remindedAt = now - hour) }
        assertEquals(undated, Tasks.due(reminded, now, hour = 12))
        // At night, only due tasks.
        assertNull(Tasks.due(reminded, now, hour = 23))
        // Snoozed.
        assertNull(Tasks.due(listOf(due.copy(snoozedUntil = now + hour)), now, 12))
        assertEquals(listOf(due), Tasks.overdue(listOf(due, undated), now))
    }

    @Test
    fun tasksOrderedAndStored() {
        val a = Tasks.Task(1, "Plain", created = 1)
        val b = Tasks.Task(2, "Starred", created = 2, starred = true)
        val c = Tasks.Task(3, "Due | soon & 50%", created = 3, due = 10)
        assertEquals(listOf(b, c, a), Tasks.open(listOf(a, b, c)))
        assertEquals(listOf(a, b, c), Tasks.decode(Tasks.encode(listOf(a, b, c))))
        val old = Tasks.Task(4, "Old", created = 0, done = 0)
        assertEquals(listOf(a), Tasks.tidy(listOf(a, old), Tasks.DONE_KEPT_MS + 1))
    }

    @Test
    fun ranks() {
        assertEquals("十級", Progress.rank(0).kanji)
        assertEquals("九級", Progress.rank(50).kanji)
        assertEquals("初段", Progress.rank(2200).kanji)
        assertEquals(1, Progress.rank(2200).number)
        assertTrue(Progress.rank(2200).isDan)
        assertEquals(1, Progress.rank(1700).number)
        assertFalse(Progress.rank(1700).isDan)
        assertEquals(10, Progress.rank(0).number)
        assertEquals("十段", Progress.rank(1_000_000).kanji)
        assertEquals(0.5f, Progress.toNext(25), 0.001f)
        assertEquals(1f, Progress.toNext(1_000_000), 0.001f)
        assertNull(Progress.next(1_000_000))
    }

    @Test
    fun pointsAreCappedPerDay() {
        assertEquals(2, Progress.points(Progress.Event.WATER_CUP, 0))
        assertEquals(0, Progress.points(Progress.Event.WATER_CUP, 24))
        assertEquals(1, Progress.points(Progress.Event.WATER_CUP, 23))
        val ledger = Progress.Ledger().add(today, Progress.Event.CARD, 2).add(today, Progress.Event.CARD, 2).add(today, Progress.Event.SESSION, 25)
        assertEquals(4, ledger.earned(today, Progress.Event.CARD))
        assertEquals(29, ledger.total(today))
        assertEquals(ledger, Progress.decodeLedger(Progress.encodeLedger(ledger)))
    }

    @Test
    fun achievements() {
        val stats = Progress.Stats(mapOf(Progress.Counter.CARDS to 150), bestWaterStreak = 7, xp = 10)
        val ids = Progress.newlyUnlocked(stats, setOf("first_step")).map { it.id }
        assertEquals(listOf("cards_100", "water_week"), ids)
        assertEquals(mapOf("a" to 1, "b" to 22), Progress.decodeCounters(Progress.encodeCounters(mapOf("a" to 1, "b" to 22))))
    }
}

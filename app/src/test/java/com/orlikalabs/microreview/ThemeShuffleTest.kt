package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ThemeShuffleTest {
    private val ids = listOf("sakura", "yoru", "mochi", "torii")

    @Test
    fun neverTheSameTwiceInARow() {
        val random = Random(7)
        var last = "sakura"
        repeat(200) {
            val next = ThemeShuffle.next(ids, emptySet(), last, random)
            assertNotEquals(last, next)
            last = next
        }
    }

    @Test
    fun everyTickedThemeComesUp() {
        val random = Random(1)
        val seen = (1..200).map { ThemeShuffle.next(ids, emptySet(), null, random) }.toSet()
        assertEquals(ids.toSet(), seen)
    }

    @Test
    fun onlyTheTickedOnes() {
        val random = Random(3)
        val seen = (1..100).map { ThemeShuffle.next(ids, setOf("yoru", "torii"), "sakura", random) }.toSet()
        assertEquals(setOf("mochi"), seen)
        // One ticked: it, even right after itself.
        assertEquals("mochi", ThemeShuffle.next(ids, setOf("sakura", "yoru", "torii"), "mochi", random))
        // None ticked (stored by an older version, say): all of them.
        assertTrue(ThemeShuffle.next(ids, ids.toSet(), null, random) in ids)
    }

    @Test
    fun theLastTickedThemeStays() {
        assertEquals(setOf("yoru"), ThemeShuffle.toggled(ids, emptySet(), "yoru"))
        assertEquals(emptySet<String>(), ThemeShuffle.toggled(ids, setOf("yoru"), "yoru"))
        val allButMochi = setOf("sakura", "yoru", "torii")
        assertEquals(allButMochi, ThemeShuffle.toggled(ids, allButMochi, "mochi"))
    }

    @Test
    fun aNewVisitAfterAMomentAway() {
        assertTrue(ThemeShuffle.isNewVisit(hiddenAt = -1, now = 10))
        assertFalse(ThemeShuffle.isNewVisit(hiddenAt = 10_000, now = 11_000)) // a rotation, Settings and back
        assertTrue(ThemeShuffle.isNewVisit(hiddenAt = 10_000, now = 12_000))
        assertTrue(ThemeShuffle.isNewVisit(hiddenAt = 10_000, now = 5_000)) // the clock was set back
    }

    @Test
    fun storedAndReadBack() {
        assertEquals("torii,yoru", ThemeShuffle.encode(setOf("yoru", "torii")))
        assertEquals(setOf("yoru", "torii"), ThemeShuffle.decode("yoru, torii,"))
        assertEquals(emptySet<String>(), ThemeShuffle.decode(null))
    }
}

package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewTimerTest {
    private val card = CardSync.CardKey(10, 0)

    @Test
    fun timeTakenIsTheTimeSinceTheCardWasSeen() {
        assertEquals(4_500L, ReviewTimer.timeTaken(ReviewTimer.Started(card, 1_000), card, now = 5_500))
    }

    @Test
    fun noTimerGivesZero() {
        assertEquals(0L, ReviewTimer.timeTaken(null, card, now = 5_500))
    }

    @Test
    fun timerOfAnotherCardIsNotUsed() {
        val other = ReviewTimer.Started(CardSync.CardKey(10, 1), 1_000)
        assertEquals(0L, ReviewTimer.timeTaken(other, card, now = 5_500))
    }

    @Test
    fun clockGoingBackGivesZero() {
        assertEquals(0L, ReviewTimer.timeTaken(ReviewTimer.Started(card, 9_000), card, now = 5_500))
    }
}

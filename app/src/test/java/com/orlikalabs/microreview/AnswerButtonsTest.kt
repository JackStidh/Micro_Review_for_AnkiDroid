package com.orlikalabs.microreview

import com.orlikalabs.microreview.AnswerButtons.AGAIN
import com.orlikalabs.microreview.AnswerButtons.EASY
import com.orlikalabs.microreview.AnswerButtons.GOOD
import com.orlikalabs.microreview.AnswerButtons.HARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerButtonsTest {

    @Test
    fun fourButtonsMapOneToOne() {
        assertEquals(listOf(AGAIN, HARD, GOOD, EASY), AnswerButtons.visibleButtons(4))
        assertEquals(1, AnswerButtons.easeFor(AGAIN, 4))
        assertEquals(2, AnswerButtons.easeFor(HARD, 4))
        assertEquals(3, AnswerButtons.easeFor(GOOD, 4))
        assertEquals(4, AnswerButtons.easeFor(EASY, 4))
    }

    @Test
    fun threeButtonsHaveNoHard() {
        assertEquals(listOf(AGAIN, GOOD, EASY), AnswerButtons.visibleButtons(3))
        assertEquals(1, AnswerButtons.easeFor(AGAIN, 3))
        assertNull(AnswerButtons.easeFor(HARD, 3))
        assertEquals(2, AnswerButtons.easeFor(GOOD, 3))
        assertEquals(3, AnswerButtons.easeFor(EASY, 3))
    }

    @Test
    fun twoButtonsAreAgainAndGood() {
        assertEquals(listOf(AGAIN, GOOD), AnswerButtons.visibleButtons(2))
        assertEquals(1, AnswerButtons.easeFor(AGAIN, 2))
        assertEquals(2, AnswerButtons.easeFor(GOOD, 2))
        assertNull(AnswerButtons.easeFor(HARD, 2))
        assertNull(AnswerButtons.easeFor(EASY, 2))
    }

    @Test
    fun unknownCountShowsAllButtons() {
        assertEquals(listOf(AGAIN, HARD, GOOD, EASY), AnswerButtons.visibleButtons(0))
        assertEquals(3, AnswerButtons.easeFor(GOOD, 0))
    }

    @Test
    fun passFailShowsAgainAndGoodOnly() {
        for (count in 2..4) assertEquals(listOf(AGAIN, GOOD), AnswerButtons.shownButtons(count, passFailOnly = true))
        assertEquals(AnswerButtons.visibleButtons(4), AnswerButtons.shownButtons(4, passFailOnly = false))
        // "Pass" is still Good for AnkiDroid, whatever the card's buttons.
        assertEquals(3, AnswerButtons.easeFor(GOOD, 4))
        assertEquals(2, AnswerButtons.easeFor(GOOD, 3))
        assertEquals(2, AnswerButtons.easeFor(GOOD, 2))
        assertEquals(1, AnswerButtons.easeFor(AGAIN, 4))
    }

    @Test
    fun nextReviewTextFollowsTheEase() {
        val four = listOf("<1m", "<6m", "<10m", "4d")
        assertEquals("<10m", AnswerButtons.nextReviewText(four, GOOD, 4))
        assertEquals("<1m", AnswerButtons.nextReviewText(four, AGAIN, 4))
        // Three buttons: Good is the second ease.
        assertEquals("1d", AnswerButtons.nextReviewText(listOf("<1m", "1d", "4d"), GOOD, 3))
        assertEquals("4d", AnswerButtons.nextReviewText(listOf("<\u20681\u2069m", "\u20684\u2069d"), GOOD, 2))
        assertEquals(null, AnswerButtons.nextReviewText(null, GOOD, 4))
        assertEquals(null, AnswerButtons.nextReviewText(listOf("<1m"), GOOD, 4))
        assertEquals(null, AnswerButtons.nextReviewText(listOf("", " ", "", ""), GOOD, 4))
    }
}

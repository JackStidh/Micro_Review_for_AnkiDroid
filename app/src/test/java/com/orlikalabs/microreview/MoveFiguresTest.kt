package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveFiguresTest {
    @Test
    fun everyExerciseHasAFigure() {
        for (move in MovePlan.ALL) {
            val figure = MoveFigures.of(move)
            assertTrue(move.id, figure != null)
            figure!!
            assertEquals(move.id, figure.frames.size, figure.holds.size)
            assertTrue(move.id, figure.frames.all { it.size == 14 })
        }
    }

    @Test
    fun standingFeetAreOnTheFloor() {
        val stand = MoveFigures.ALL.getValue("hinge").frames[0]
        val body = MoveFigures.body(stand)
        assertEquals(MoveFigures.FLOOR, body.ankleNear.y, 0.5f)
        // The head is above the shoulders, the shoulders above the hip.
        assertTrue(body.head.y < body.shoulder.y && body.shoulder.y < body.hip.y)
    }

    @Test
    fun nothingGoesThroughTheFloor() {
        for ((id, figure) in MoveFigures.ALL) {
            for (frame in figure.frames) {
                for (p in MoveFigures.body(frame, figure.front).points) assertTrue("$id: $p", p.y <= MoveFigures.FLOOR + 4.5f)
            }
        }
    }

    @Test
    fun holdsThenEasesToTheNextPose() {
        val figure = MoveFigures.ALL.getValue("press_up")
        val a = figure.frames[0]
        val b = figure.frames[1]
        assertTrue(MoveFigures.at(figure, 0).contentEquals(a))
        val halfway = MoveFigures.at(figure, figure.holds[0] + MoveFigures.MOVE_MS / 2L)
        assertEquals((a[2] + b[2]) / 2, halfway[2], 0.5f)
        assertTrue(MoveFigures.at(figure, figure.holds[0] + MoveFigures.MOVE_MS + 1L).contentEquals(b))
        // And round again.
        assertTrue(MoveFigures.at(figure, figure.cycleMs.toLong()).contentEquals(a))
    }

    @Test
    fun theViewHoldsEveryPose() {
        val figure = MoveFigures.ALL.getValue("bird_dog")
        val view = MoveFigures.viewBox(figure)
        for (frame in figure.frames) for (p in MoveFigures.body(frame).points) {
            assertTrue(p.x >= view[0] && p.x <= view[0] + view[2] && p.y >= view[1] && p.y <= view[1] + view[3])
        }
    }
}

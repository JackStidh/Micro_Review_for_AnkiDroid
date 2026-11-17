package com.orlikalabs.microreview

/** The names of the answer buttons: with "Pass / Fail only", Again is "Fail" and Good is "Pass". */
object AnswerLabels {
    /** The string resource naming [button] (one of [AnswerButtons]). */
    fun label(button: Int, passFailOnly: Boolean): Int = when (button) {
        AnswerButtons.AGAIN -> if (passFailOnly) R.string.fail else R.string.again
        AnswerButtons.HARD -> R.string.hard
        AnswerButtons.GOOD -> if (passFailOnly) R.string.pass else R.string.good
        else -> R.string.easy
    }
}

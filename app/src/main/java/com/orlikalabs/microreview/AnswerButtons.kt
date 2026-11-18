package com.orlikalabs.microreview

/**
 * Maps the notification's 4 buttons (1 = Again, 2 = Hard, 3 = Good, 4 = Easy) to the ease AnkiDroid expects,
 * which depends on how many answer buttons the card has (`FlashCardsContract.ReviewInfo.BUTTON_COUNT`):
 * - 4 buttons: Again = 1, Hard = 2, Good = 3, Easy = 4
 * - 3 buttons: Again = 1, Good = 2, Easy = 3 (no Hard)
 * - 2 buttons: Again = 1, Good = 2
 */
object AnswerButtons {
    const val AGAIN = 1
    const val HARD = 2
    const val GOOD = 3
    const val EASY = 4

    /** The buttons to show for a card with [buttonCount] answer buttons. Unknown counts show all 4. */
    @JvmStatic
    fun visibleButtons(buttonCount: Int): List<Int> = when (buttonCount) {
        2 -> listOf(AGAIN, GOOD)
        3 -> listOf(AGAIN, GOOD, EASY)
        else -> listOf(AGAIN, HARD, GOOD, EASY)
    }

    /**
     * The buttons to show when "Pass / Fail only" is on ([passFailOnly]): Again ("Fail") and Good ("Pass"), whatever
     * the card offers. Hard and Easy are left out; the ease sent is still [easeFor]'s, so Good stays Good.
     */
    @JvmStatic
    fun shownButtons(buttonCount: Int, passFailOnly: Boolean): List<Int> =
        if (passFailOnly) listOf(AGAIN, GOOD) else visibleButtons(buttonCount)

    /**
     * When the card comes back if answered with [button], from AnkiDroid's next review times (one per ease, in
     * order), cleaned of its direction marks: "4d", "<10m". Null if unknown.
     */
    @JvmStatic
    fun nextReviewText(times: List<String>?, button: Int, buttonCount: Int): String? {
        val ease = easeFor(button, buttonCount) ?: return null
        return times?.getOrNull(ease - 1)?.filterNot { it in DIRECTION_MARKS }?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Unicode isolates that Anki puts around numbers ("<\u206810\u2069m"). */
    private val DIRECTION_MARKS = setOf('\u2066', '\u2067', '\u2068', '\u2069')

    /** The ease to send to AnkiDroid when [button] is tapped, or null if that button isn't valid for the card. */
    @JvmStatic
    fun easeFor(button: Int, buttonCount: Int): Int? {
        val index = visibleButtons(buttonCount).indexOf(button)
        return if (index == -1) null else index + 1
    }
}

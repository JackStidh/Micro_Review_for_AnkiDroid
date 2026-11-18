package com.orlikalabs.microreview

/**
 * Pure part of "pause while my PC is in use": the pairing code shown by Micro Review Desktop (an Anki add-on), the
 * relay topic it gives, and what the PC's messages say. Kept free of Android classes so it can be unit tested.
 *
 * The PC posts to the topic, while it is used, how many seconds the phone should stay paused from the moment the
 * relay received the message ("300"). The phone reads the topic's recent messages ([PcActivity]).
 */
object PcPairing {
    /** Codes are 16 letters and digits, shown as four groups ("k3p9-x2mq-7rtv-w8de"). */
    const val CODE_LENGTH = 16
    private val CODE_CHARS = Regex("[a-z0-9]{$CODE_LENGTH}")
    private val EVENT_MESSAGE = Regex(""""event"\s*:\s*"message"""")
    private val TIME = Regex(""""time"\s*:\s*(\d+)""")
    private val SECONDS = Regex(""""message"\s*:\s*"(\d{1,5})"""")

    /** The code as stored: lowercase, without spaces or dashes; null if it isn't a code. */
    fun normalize(code: String): String? =
        code.lowercase().filter { it.isLetterOrDigit() }.takeIf { CODE_CHARS.matches(it) }

    /** "k3p9-x2mq-7rtv-w8de". */
    fun display(code: String): String = code.chunked(4).joinToString("-")

    /** The relay topic of a [normalize]d code. */
    fun topic(code: String): String = "mrf-$code"

    /**
     * Until when (epoch milliseconds) one line of the relay's JSON stream says the phone is paused, or 0 if the line
     * isn't such a message.
     */
    fun pausedUntil(line: String): Long {
        if (!EVENT_MESSAGE.containsMatchIn(line)) return 0
        val time = TIME.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return 0
        val seconds = SECONDS.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return 0
        return (time + seconds.coerceAtMost(MAX_PAUSE_SECONDS)) * 1000
    }

    /** A message can't pause the phone longer than this. */
    private const val MAX_PAUSE_SECONDS = 30 * 60L
}

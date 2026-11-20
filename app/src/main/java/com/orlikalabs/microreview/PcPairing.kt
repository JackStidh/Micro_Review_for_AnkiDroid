package com.orlikalabs.microreview

/**
 * Pure part of "pause while my PC is in use": the pairing code shown by Micro Review Desktop (an Anki add-on), the
 * relay topic it gives, and what the PC's messages say. Kept free of Android classes so it can be unit tested.
 *
 * The PC posts to the topic how many seconds the phone should stay paused from the moment the relay received the
 * message ("420" while it is used, the remainder when its use stops, "0" to resume). The phone reads the topic's
 * recent messages and follows the latest ([PcActivity]). After syncing its answers with AnkiWeb, the PC also posts
 * to a second topic ([syncTopic]) "synced" and the cards it reviewed since its last message ("synced 1712:0,1713:1",
 * note ID and card ordinal), for the phone to pass over them until it syncs too ([syncedCards]).
 */
object PcPairing {
    /** Codes are 16 letters and digits, shown as four groups ("k3p9-x2mq-7rtv-w8de"). */
    const val CODE_LENGTH = 16
    private val CODE_CHARS = Regex("[a-z0-9]{$CODE_LENGTH}")
    private val EVENT_MESSAGE = Regex(""""event"\s*:\s*"message"""")
    private val TIME = Regex(""""time"\s*:\s*(\d+)""")
    private val SECONDS = Regex(""""message"\s*:\s*"(\d{1,5})"""")
    private val SYNCED = Regex(""""message"\s*:\s*"synced(?: ([^"]*))?"""")

    /** The code as stored: lowercase, without spaces or dashes; null if it isn't a code. */
    fun normalize(code: String): String? =
        code.lowercase().filter { it.isLetterOrDigit() }.takeIf { CODE_CHARS.matches(it) }

    /** "k3p9-x2mq-7rtv-w8de". */
    fun display(code: String): String = code.chunked(4).joinToString("-")

    /** The relay topic of a [normalize]d code. */
    fun topic(code: String): String = "mrf-$code"

    /** The topic where the PC says it synced its answers (Micro Review Desktop's rules.sync_topic). */
    fun syncTopic(code: String): String = topic(code) + "-sync"

    /** When the relay received the message on one line of its JSON stream (epoch milliseconds), 0 if not a message. */
    fun messageTime(line: String): Long {
        if (!EVENT_MESSAGE.containsMatchIn(line)) return 0
        return (TIME.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return 0) * 1000
    }

    /**
     * The cards the PC reviewed, from one line of the sync topic's JSON stream: empty for a plain "synced" (an
     * older add-on, or no review since its last message), null if the line isn't such a message.
     */
    fun syncedCards(line: String): List<CardSync.CardKey>? {
        if (!EVENT_MESSAGE.containsMatchIn(line)) return null
        val cards = SYNCED.find(line)?.groupValues?.get(1) ?: return null
        return cards.split(',').mapNotNull { card ->
            val parts = card.split(':')
            val noteId = parts.getOrNull(0)?.toLongOrNull()
            val ord = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && noteId != null && ord != null) CardSync.CardKey(noteId, ord) else null
        }
    }

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

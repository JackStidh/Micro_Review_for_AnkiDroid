package com.orlikalabs.microreview

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Pause while my PC is in use": Micro Review Desktop, an Anki add-on on the user's PC, posts to a relay while the
 * PC is used (mouse or keyboard), and the phone pauses for 5 minutes after the PC's last use ([PcPairing]).
 *
 * The relay is ntfy.sh, a free public publish/subscribe service: the PC and the phone share a topic named after
 * the pairing code, and the messages only carry a number of seconds; the latest one counts (the PC says when its
 * use starts, every few minutes while it lasts, and when it stops). The phone reads it just before it would show
 * a card or a gate ([refresh], from the monitor's thread); [isInUse] only answers from the last read.
 *
 * The PC also says when it synced its answers with AnkiWeb, on a second topic: the phone reads it while it is in use
 * ([refreshPcSync]) and syncs too ([AnkiSync]), so its next card isn't one already reviewed on the PC.
 */
object PcActivity {
    const val RELAY = "https://ntfy.sh"
    private const val TIMEOUT_MS = 4_000

    /** Only the messages of the last few minutes can still pause. */
    private const val SINCE = "15m"

    /** The relay keeps messages 12 hours. */
    private const val SYNC_SINCE = "12h"

    @Volatile
    private var checkedAt = 0L

    @Volatile
    private var pcSyncCheckedAt = 0L

    @Volatile
    var pausedUntil = 0L
        private set

    /** Why the last read failed, for the diagnostics; null if it worked. */
    @Volatile
    var lastError: String? = null
        private set

    /** Whether the PC said it is in use, as last read. No network. */
    fun isInUse(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        FocusSettings.pauseWhenPcInUse(context) && FocusSettings.pcPairingCode(context) != null && now < pausedUntil

    /**
     * Reads the relay again if the last read is older than [maxAgeMs]. Network I/O: off the main thread. A failed
     * read keeps what was known: the phone isn't paused by an unreachable relay, nor unpaused by one.
     */
    fun refresh(context: Context, maxAgeMs: Long, now: Long = System.currentTimeMillis()) {
        if (!FocusSettings.pauseWhenPcInUse(context)) return
        val code = FocusSettings.pcPairingCode(context) ?: return
        if (now - checkedAt in 0 until maxAgeMs) return
        checkedAt = now
        // In the order the relay received them: the latest message counts.
        var until = 0L
        if (read(PcPairing.topic(code), SINCE) { line -> PcPairing.pausedUntil(line).takeIf { it != 0L }?.let { until = it } }) {
            pausedUntil = until
        }
    }

    /**
     * Reads when the PC last synced its answers ([AnkiSync.setPcSyncedAt]) if the last read is older than [maxAgeMs].
     * Network I/O: off the main thread.
     */
    fun refreshPcSync(context: Context, maxAgeMs: Long, now: Long = System.currentTimeMillis()) {
        val code = FocusSettings.pcPairingCode(context) ?: return
        if (now - pcSyncCheckedAt in 0 until maxAgeMs) return
        pcSyncCheckedAt = now
        var latest = 0L
        if (read(PcPairing.syncTopic(code), SYNC_SINCE) { line -> latest = maxOf(latest, PcPairing.messageTime(line)) }) {
            AnkiSync.setPcSyncedAt(context, latest)
        }
    }

    /** The topic's messages since [since], line by line; false (and [lastError]) if the relay couldn't be read. */
    private fun read(topic: String, since: String, onLine: (String) -> Unit): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$RELAY/$topic/json?poll=1&since=$since").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "MicroReview")
            }
            connection.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
            lastError = null
            true
        } catch (e: Exception) {
            lastError = e.toString()
            false
        } finally {
            connection?.disconnect()
        }
    }

    /** A new pairing: what was read for the old one doesn't count. */
    fun forget() {
        checkedAt = 0L
        pcSyncCheckedAt = 0L
        pausedUntil = 0L
        lastError = null
    }
}

package com.orlikalabs.microreview

/**
 * Pure logic of when the next card is due while the phone is in use ("nudges"). Time only counts while the screen
 * is on and unlocked: the caller reports it with [tick]. An app with its own interval ("a card every 5 minutes on
 * this app") counts the time spent in that app; every other app shares the default interval.
 *
 * Not thread safe: used from one thread.
 */
class NudgeClock {
    private val perAppMs = HashMap<String, Long>()
    private var sharedMs = 0L

    /**
     * [elapsedMs] more were spent in [foreground] (null if unknown). Returns whether a card is due now.
     *
     * @param perApp the apps with their own interval, in minutes; [NEVER] for an app that must not be interrupted
     * @param defaultMinutes the interval of every other app, [NEVER] for none
     */
    fun tick(foreground: String?, elapsedMs: Long, perApp: Map<String, Int>, defaultMinutes: Int): Boolean {
        val elapsed = elapsedMs.coerceIn(0, MAX_TICK_MS)
        val own = foreground?.let { perApp[it] }
        if (foreground != null && own != null) {
            if (own <= NEVER) return false
            val spent = (perAppMs[foreground] ?: 0L) + elapsed
            perAppMs[foreground] = spent
            return spent >= own * MS_PER_MINUTE
        }
        if (defaultMinutes <= NEVER) return false
        sharedMs += elapsed
        return sharedMs >= defaultMinutes * MS_PER_MINUTE
    }

    /**
     * How long until a card is due in [foreground], in milliseconds (0 when due), or null when no card comes there.
     * Shown to the user; [tick] decides.
     */
    fun remainingMs(foreground: String?, perApp: Map<String, Int>, defaultMinutes: Int): Long? {
        val own = foreground?.let { perApp[it] }
        if (foreground != null && own != null) {
            if (own <= NEVER) return null
            return (own * MS_PER_MINUTE - (perAppMs[foreground] ?: 0L)).coerceAtLeast(0)
        }
        if (defaultMinutes <= NEVER) return null
        return (defaultMinutes * MS_PER_MINUTE - sharedMs).coerceAtLeast(0)
    }

    /** A card was offered (or the user is studying anyway) while in [foreground]: its interval starts over. */
    fun reset(foreground: String?, perApp: Map<String, Int>) {
        if (foreground != null && perApp.containsKey(foreground)) perAppMs.remove(foreground) else sharedMs = 0
    }

    companion object {
        /** An interval of 0 minutes: no card. */
        const val NEVER = 0
        private const val MS_PER_MINUTE = 60_000L

        /** A longer gap between two ticks isn't time spent on the phone (the process was paused). */
        private const val MAX_TICK_MS = 10_000L
    }
}

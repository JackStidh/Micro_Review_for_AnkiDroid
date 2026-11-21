package com.orlikalabs.microreview

/**
 * The outside nudges for attention and the body ([LifePrompts]). Pure part:
 * - the spell breaker: a stretch in the watched (scrolling) apps longer than its threshold, or a long phone session
 *   in any app, interrupted by a prompt that gets harder to dismiss each time ("5 more minutes" held longer);
 * - inactivity: the step counter barely moving for two hours of the day;
 * - bedtime: the phone in use after bedtime; and last night's sleep, from the last use to the first.
 */
object Guard {
    // The spell breaker.

    /** Out of the watched apps for less than this, the stretch goes on (a message answered, a quick look). */
    const val EXIT_GRACE_MS = 90_000L

    /** "5 more minutes": how long before the next prompt. */
    const val MORE_MS = 5 * 60_000L

    /** How long "5 more minutes" must be held, by how many times it was chosen in the stretch. */
    private val HOLD_MS = longArrayOf(0L, 3_000L, 8_000L, 15_000L)

    fun holdMs(strikes: Int): Long = HOLD_MS[strikes.coerceIn(0, HOLD_MS.size - 1)]

    /**
     * A stretch in the watched apps: since [startedAt], last seen in one at [lastSeen]; [nextAt] the next prompt (0:
     * the threshold after the start); [strikes] "5 more" chosen; [exempt]: "on purpose", no more prompts in it.
     */
    data class Stretch(
        val startedAt: Long = 0L,
        val lastSeen: Long = 0L,
        val app: String? = null,
        val nextAt: Long = 0L,
        val strikes: Int = 0,
        val exempt: Boolean = false,
    ) {
        fun active(now: Long): Boolean = startedAt != 0L && now - lastSeen < EXIT_GRACE_MS
    }

    /** [app] in front at [now]: the stretch goes on, starts, or (after the grace) is over. */
    fun track(stretch: Stretch, app: String?, watched: Set<String>, now: Long): Stretch = when {
        app != null && app in watched ->
            if (stretch.active(now)) stretch.copy(lastSeen = now, app = app) else Stretch(startedAt = now, lastSeen = now, app = app)
        stretch.active(now) -> stretch
        else -> Stretch()
    }

    /** Minutes into the stretch. */
    fun minutes(stretch: Stretch, now: Long): Int = ((now - stretch.startedAt) / 60_000L).toInt()

    /** Whether to break the spell now: in a watched app, past the threshold (or "5 more" ran out), not exempt. */
    fun spellDue(stretch: Stretch, inWatchedApp: Boolean, now: Long, thresholdMin: Int): Boolean {
        if (thresholdMin <= 0 || !inWatchedApp || !stretch.active(now) || stretch.exempt) return false
        val at = if (stretch.nextAt != 0L) stretch.nextAt else stretch.startedAt + thresholdMin * 60_000L
        return now >= at
    }

    fun more(stretch: Stretch, now: Long) = stretch.copy(nextAt = now + MORE_MS, strikes = stretch.strikes + 1)

    // A long phone session, any app.

    /** The screen off this long ends a session; shorter, it's the same one. */
    const val SESSION_BREAK_MS = 2 * 60_000L
    const val SESSION_LATER_MS = 15 * 60_000L

    fun sessionDue(useStartedAt: Long, now: Long, thresholdMin: Int, laterUntil: Long): Boolean =
        thresholdMin > 0 && useStartedAt != 0L && now - useStartedAt >= thresholdMin * 60_000L && now >= laterUntil

    // Inactivity, from the step counter.

    /** A step counter reading: [count] steps since the phone started, at [time]. */
    data class Sample(val time: Long, val count: Long)

    const val INACTIVE_WINDOW_MS = 2 * 3600_000L
    const val INACTIVE_STEPS = 150L
    const val INACTIVE_FROM_HOUR = 9
    const val INACTIVE_UNTIL_HOUR = 21
    const val INACTIVE_GAP_MS = 2 * 3600_000L
    const val SAMPLES_KEPT_MS = 30 * 3600_000L

    /** Steps between two readings: the counter restarts with the phone. */
    private fun between(a: Sample, b: Sample): Long = if (b.count >= a.count) b.count - a.count else b.count

    /** Steps from [since] to the last reading. */
    fun stepsSince(samples: List<Sample>, since: Long): Long {
        val sorted = samples.sortedBy { it.time }
        var total = 0L
        for (i in 1 until sorted.size) if (sorted[i].time > since) total += between(sorted[i - 1], sorted[i])
        return total
    }

    /**
     * Inactive: during the day, readings covering the last two hours show fewer than [INACTIVE_STEPS] steps, and the
     * last inactivity nudge is [INACTIVE_GAP_MS] old.
     */
    fun inactive(samples: List<Sample>, now: Long, hour: Int, lastNudged: Long): Boolean {
        if (hour !in INACTIVE_FROM_HOUR until INACTIVE_UNTIL_HOUR || now - lastNudged < INACTIVE_GAP_MS) return false
        val since = now - INACTIVE_WINDOW_MS
        if (samples.none { it.time <= since }) return false
        val inWindow = samples.filter { it.time >= since }
        if (inWindow.isEmpty()) return false
        val before = samples.filter { it.time <= since }.maxByOrNull { it.time }!!
        var steps = 0L
        var previous = before
        for (s in inWindow.sortedBy { it.time }) {
            steps += between(previous, s)
            previous = s
        }
        return steps < INACTIVE_STEPS
    }

    /** Readings older than [SAMPLES_KEPT_MS] dropped, [sample] added. */
    fun withSample(samples: List<Sample>, sample: Sample): List<Sample> =
        (samples + sample).filter { sample.time - it.time <= SAMPLES_KEPT_MS }.sortedBy { it.time }

    fun encodeSamples(samples: List<Sample>): String = samples.joinToString(";") { "${it.time}:${it.count}" }

    fun decodeSamples(text: String?): List<Sample> = text.orEmpty().split(';').mapNotNull { s ->
        val (t, c) = s.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        Sample(t.toLongOrNull() ?: return@mapNotNull null, c.toLongOrNull() ?: return@mapNotNull null)
    }

    // Bedtime.

    /** Minutes after bedtime when "going to bed" still counts as on time. */
    const val BEDTIME_GRACE_MIN = 30
    const val BEDTIME_LATER_MS = 15 * 60_000L

    /**
     * Past bedtime ([bedtime], minutes after midnight; it may be after midnight) and before 4:00: the bedtime prompt,
     * unless answered tonight or put off.
     */
    fun pastBedtime(minuteOfDay: Int, bedtime: Int): Boolean {
        if (bedtime < 0) return false
        val night = if (minuteOfDay < 4 * 60) minuteOfDay + 24 * 60 else minuteOfDay
        val bed = if (bedtime < 4 * 60) bedtime + 24 * 60 else bedtime
        return night >= bed && night < 28 * 60
    }

    fun onTime(minuteOfDay: Int, bedtime: Int): Boolean {
        val night = if (minuteOfDay < 4 * 60) minuteOfDay + 24 * 60 else minuteOfDay
        val bed = if (bedtime < 4 * 60) bedtime + 24 * 60 else bedtime
        return night <= bed + BEDTIME_GRACE_MIN
    }

    /** Last night's sleep, from the last use to the first: only a plausible night counts (3 to 14 hours). */
    fun sleepMs(lastUseEnd: Long, firstUse: Long): Long? {
        val gap = firstUse - lastUseEnd
        return gap.takeIf { lastUseEnd > 0 && it in 3 * 3600_000L..14 * 3600_000L }
    }
}

package com.orlikalabs.microreview

import java.time.ZonedDateTime

/**
 * Pure logic of when cards must not interrupt: a snooze ("not for the next hour") and quiet hours (the same hours
 * every day, for example 22:00 to 07:00). Kept free of Android classes so it can be unit tested.
 */
object QuietTime {
    const val MINUTES_PER_DAY = 24 * 60

    /** Quiet from [start] to [end], in minutes since midnight. [end] before [start] goes past midnight. */
    data class Window(val start: Int, val end: Int)

    /** Whether [minuteOfDay] is in [window]. A window whose start equals its end is empty. */
    fun isIn(window: Window, minuteOfDay: Int): Boolean = when {
        window.start == window.end -> false
        window.start < window.end -> minuteOfDay >= window.start && minuteOfDay < window.end
        else -> minuteOfDay >= window.start || minuteOfDay < window.end
    }

    fun isQuiet(windows: List<Window>, minuteOfDay: Int): Boolean = windows.any { isIn(it, minuteOfDay) }

    /** Whether a snooze lasting until [until] (epoch milliseconds, 0 for none) still holds at [now]. */
    fun isSnoozed(until: Long, now: Long): Boolean = now < until

    /** The end of a snooze of [minutes] started at [now] (epoch milliseconds). */
    fun snoozeFor(minutes: Int, now: Long): Long = now + minutes.coerceAtLeast(0) * 60_000L

    /** The end of "until tomorrow": the next time the clock shows [morningMinute] strictly after [now]. */
    fun untilMorning(now: ZonedDateTime, morningMinute: Int): Long {
        val minute = morningMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        val today = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        // "Until tomorrow" tapped at 1 am still means this morning.
        val end = if (today.isAfter(now)) today else today.plusDays(1)
        return end.toInstant().toEpochMilli()
    }

    /** Valid windows, without duplicates, in the order of their start. */
    fun normalize(windows: List<Window>): List<Window> =
        windows.filter { it.start in 0 until MINUTES_PER_DAY && it.end in 0 until MINUTES_PER_DAY && it.start != it.end }
            .distinct()
            .sortedBy { it.start }

    /** Stored as "start-end,start-end". */
    fun parse(value: String): List<Window> = normalize(
        value.split(',').mapNotNull { part ->
            val ends = part.trim().split('-')
            val start = ends.getOrNull(0)?.toIntOrNull()
            val end = ends.getOrNull(1)?.toIntOrNull()
            if (ends.size == 2 && start != null && end != null) Window(start, end) else null
        }
    )

    fun format(windows: List<Window>): String = normalize(windows).joinToString(",") { "${it.start}-${it.end}" }
}

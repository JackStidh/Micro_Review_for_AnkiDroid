package com.orlikalabs.microreview

import java.time.LocalDate

/**
 * Daily habits, tapped as they're done: water by the cup, supplements at their times, anything else with a daily
 * count. Pure part: the habits, the day log, streaks, and which reminder is due (a timed dose that's late, or water
 * behind its pace through the day). Storage in [LifeStore], prompts through the monitor ([LifePrompts]).
 */
object Habits {
    /**
     * A habit: [name] as the user gave it, empty for a built-in one (named in `strings.xml`, [LifeText]); [target] taps a day; [times] (minutes after midnight) when doses are due, one tap each (then [target]
     * is their count); [pace]: reminded when behind an even pace through the waking day (water). [glyph]: a kanji.
     */
    data class Habit(
        val id: String,
        val name: String,
        val target: Int = 1,
        val times: List<Int> = emptyList(),
        val glyph: String = "習",
        val pace: Boolean = false,
    ) {
        val goal: Int get() = if (times.isNotEmpty()) times.size else target.coerceAtLeast(1)
    }

    val WATER = Habit("water", "", target = 8, glyph = "水", pace = true)

    /** Offered when adding one: a name, a glyph, its times or daily count. */
    val SUGGESTIONS = listOf(
        Habit("vitamin_d", "", times = listOf(9 * 60), glyph = "陽"),
        Habit("multivitamin", "", times = listOf(9 * 60), glyph = "薬"),
        Habit("omega_3", "", times = listOf(13 * 60), glyph = "魚"),
        Habit("magnesium", "", times = listOf(21 * 60 + 30), glyph = "眠"),
        Habit("creatine", "", times = listOf(9 * 60), glyph = "力"),
        Habit("protein", "", target = 1, glyph = "乳"),
        Habit("meals", "", target = 4, glyph = "食"),
        Habit("sunlight", "", times = listOf(9 * 60 + 30), glyph = "光"),
        Habit("bed", "", target = 1, glyph = "床"),
        Habit("read", "", target = 1, glyph = "読"),
        Habit("floss", "", times = listOf(22 * 60), glyph = "歯"),
        Habit("journal", "", target = 1, glyph = "記"),
        Habit("breathe", "", target = 1, glyph = "息"),
        Habit("tidy", "", target = 1, glyph = "掃"),
    )

    /** The day's taps by habit id. */
    data class Day(val date: LocalDate, val counts: Map<String, Int> = emptyMap()) {
        fun count(id: String): Int = counts[id] ?: 0
    }

    const val DAYS_KEPT = 120

    fun tap(days: List<Day>, date: LocalDate, id: String, delta: Int = 1): List<Day> {
        val day = days.firstOrNull { it.date == date } ?: Day(date)
        val count = (day.count(id) + delta).coerceAtLeast(0)
        val changed = day.copy(counts = day.counts + (id to count))
        return (days.filter { it.date != date } + changed).sortedBy { it.date }.takeLast(DAYS_KEPT)
    }

    fun done(habit: Habit, day: Day?): Boolean = (day?.count(habit.id) ?: 0) >= habit.goal

    /** Days in a row with [habit] done, ending today (or yesterday when today isn't done yet). */
    fun streak(habit: Habit, days: List<Day>, today: LocalDate): Int {
        val byDate = days.associateBy { it.date }
        var date = if (done(habit, byDate[today])) today else today.minusDays(1)
        var count = 0
        while (done(habit, byDate[date])) {
            count++
            date = date.minusDays(1)
        }
        return count
    }

    /** How much of the day's habits is done, 0 to 1. */
    fun progress(habits: List<Habit>, day: Day?): Float =
        if (habits.isEmpty()) 0f else habits.sumOf { minOf(day?.count(it.id) ?: 0, it.goal).toDouble() / it.goal }.toFloat() / habits.size

    fun allDone(habits: List<Habit>, day: Day?): Boolean = habits.isNotEmpty() && habits.all { done(it, day) }

    // Reminders.

    /** Water is paced over the waking day, from [PACE_FROM] to [PACE_UNTIL] (minutes after midnight). */
    const val PACE_FROM = 8 * 60
    const val PACE_UNTIL = 21 * 60

    /** How far behind the pace (in taps) before a reminder, and how long between two of them. */
    const val PACE_SLACK = 1.5f
    const val PACE_GAP_MS = 90 * 60_000L

    /** A dose more than this late isn't reminded any more: it's for tomorrow. */
    const val DOSE_WINDOW_MIN = 4 * 60

    /** A reminder that's due: [habit], and [doseTime] for a timed dose (null for the pace). */
    data class Reminder(val habit: Habit, val doseTime: Int?)

    /**
     * The reminder due at [minuteOfDay]: a timed dose not taken yet and not reminded yet ([reminded]: "id@minute"
     * keys of today's reminders), then a paced habit behind its pace and not reminded for [PACE_GAP_MS] ([lastPace]
     * by id, epoch ms).
     */
    fun due(
        habits: List<Habit>,
        day: Day?,
        minuteOfDay: Int,
        reminded: Set<String>,
        lastPace: Map<String, Long>,
        now: Long,
    ): Reminder? {
        for (habit in habits) {
            if (habit.times.isEmpty()) continue
            val taken = day?.count(habit.id) ?: 0
            val sorted = habit.times.sorted()
            // The doses due by now, beyond the ones taken: the earliest one still in its window.
            sorted.forEachIndexed { index, time ->
                if (index >= taken && minuteOfDay >= time && minuteOfDay - time <= DOSE_WINDOW_MIN && "${habit.id}@$time" !in reminded) {
                    return Reminder(habit, time)
                }
            }
        }
        for (habit in habits) {
            if (!habit.pace || minuteOfDay < PACE_FROM + 60 || minuteOfDay > PACE_UNTIL) continue
            val expected = habit.goal * (minuteOfDay - PACE_FROM).toFloat() / (PACE_UNTIL - PACE_FROM)
            val taken = day?.count(habit.id) ?: 0
            if (taken < habit.goal && expected - taken >= PACE_SLACK && now - (lastPace[habit.id] ?: 0L) >= PACE_GAP_MS) {
                return Reminder(habit, null)
            }
        }
        return null
    }

    // Storage: "id|name|target|t1,t2|glyph|pace" per habit, "date;id=n;id=n" per day.

    fun encodeHabits(habits: List<Habit>): String = habits.joinToString("\n") { h ->
        listOf(h.id, h.name.replace('|', '/').replace('\n', ' '), h.target, h.times.joinToString(","), h.glyph, if (h.pace) 1 else 0).joinToString("|")
    }

    fun decodeHabits(text: String?): List<Habit>? {
        if (text == null) return null
        return text.lines().mapNotNull { line ->
            val f = line.split('|')
            if (f.size < 6 || f[0].isEmpty()) return@mapNotNull null
            Habit(
                id = f[0],
                name = f[1],
                target = f[2].toIntOrNull() ?: 1,
                times = f[3].split(',').mapNotNull { it.trim().toIntOrNull() },
                glyph = f[4].ifEmpty { "習" },
                pace = f[5] == "1",
            )
        }
    }

    fun encodeDays(days: List<Day>): String = days.sortedBy { it.date }.takeLast(DAYS_KEPT).joinToString("\n") { day ->
        (listOf(day.date.toString()) + day.counts.filterValues { it > 0 }.map { (id, n) -> "$id=$n" }).joinToString(";")
    }

    fun decodeDays(text: String?): List<Day> = text.orEmpty().lines().mapNotNull { line ->
        val f = line.split(';')
        val date = runCatching { LocalDate.parse(f[0]) }.getOrNull() ?: return@mapNotNull null
        Day(date, f.drop(1).mapNotNull { kv -> kv.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toIntOrNull()?.let { k to it } } }.toMap())
    }

    /** An id for a new habit named [name], not among [taken]. */
    fun newId(name: String, taken: Set<String>): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "habit" }.take(24)
        if (base !in taken) return base
        var n = 2
        while ("${base}_$n" in taken) n++
        return "${base}_$n"
    }
}

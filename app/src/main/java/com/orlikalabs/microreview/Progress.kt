package com.orlikalabs.microreview

import java.time.LocalDate

/**
 * The game: experience points for everything done (a card, a cup of water, a session, a task, a doomscroll broken),
 * ranks like a martial art's (10級 up to 1級, then 初段 to 十段), and seals to unlock. Pure part: the points, the
 * ranks, the counters and which achievements they unlock. Storage in [LifeStore].
 */
object Progress {
    enum class Event(val xp: Int, val dailyCap: Int = Int.MAX_VALUE) {
        CARD(2, 200),
        WATER_CUP(2, 24),
        HABIT_TAP(4, 60),
        HABIT_DONE(10),
        ALL_HABITS(25),
        TASK_DONE(10, 150),
        TASK_ON_TIME(5, 75),
        CHECK_IN(5, 5),
        MORNING(10, 10),
        SESSION(25, 25),
        STRENGTH(15, 15),
        WALK_10(5, 30),
        STAND_UP(8, 64),
        SPELL_BROKEN(15, 60),
        BEDTIME(15, 15),
    }

    /** The ranks, from 10級 up, and the XP each takes. */
    data class Rank(val index: Int, val kanji: String, val xp: Int) {
        /** 10 down to 1 for the kyū ranks, 1 up to 10 for the dan ones. */
        val number: Int get() = if (isDan) index - 9 else 10 - index
        val isDan: Boolean get() = index >= 10
    }

    val RANKS: List<Rank> = listOf(
        "十級" to 0, "九級" to 50, "八級" to 120, "七級" to 220, "六級" to 350, "五級" to 520, "四級" to 740,
        "三級" to 1000, "二級" to 1320, "一級" to 1700, "初段" to 2200, "二段" to 2800, "三段" to 3500,
        "四段" to 4300, "五段" to 5200, "六段" to 6200, "七段" to 7300, "八段" to 8500, "九段" to 9800, "十段" to 11200,
    ).mapIndexed { index, (kanji, xp) -> Rank(index, kanji, xp) }

    fun rank(xp: Int): Rank = RANKS.last { xp >= it.xp }

    fun next(xp: Int): Rank? = RANKS.firstOrNull { it.xp > xp }

    /** How far from this rank to the next, 0 to 1 (1 at the top). */
    fun toNext(xp: Int): Float {
        val now = rank(xp)
        val next = next(xp) ?: return 1f
        return (xp - now.xp).toFloat() / (next.xp - now.xp)
    }

    /**
     * The XP [event] earns now, given what it already earned today ([earnedToday] by event): capped per day, so
     * hammering a button doesn't climb the ranks.
     */
    fun points(event: Event, earnedToday: Int): Int = (event.dailyCap - earnedToday).coerceIn(0, event.xp)

    // Counters and achievements.

    /** Lifetime counters, by name. */
    object Counter {
        const val CARDS = "cards"
        const val CUPS = "cups"
        const val TASKS = "tasks"
        const val SPELLS = "spells"
        const val STAND_UPS = "stand_ups"
        const val SESSIONS = "sessions"
        const val PERFECT_DAYS = "perfect_days"
        const val BEDTIMES = "bedtimes"
    }

    /** An achievement: [kanji] on its seal, unlocked when [reached] says so. Its texts are in `strings.xml`. */
    data class Achievement(val id: String, val kanji: String, val reached: (Stats) -> Boolean)

    /** What achievements look at: lifetime counters, best streaks, the XP. */
    data class Stats(
        val counters: Map<String, Int> = emptyMap(),
        val bestWaterStreak: Int = 0,
        val bestEveningStreak: Int = 0,
        val bestPerfectStreak: Int = 0,
        val xp: Int = 0,
    ) {
        fun count(name: String) = counters[name] ?: 0
    }

    val ACHIEVEMENTS = listOf(
        Achievement("first_step", "一歩") { it.xp > 0 },
        Achievement("cards_100", "百") { it.count(Counter.CARDS) >= 100 },
        Achievement("cards_1000", "千") { it.count(Counter.CARDS) >= 1000 },
        Achievement("cups_100", "滝") { it.count(Counter.CUPS) >= 100 },
        Achievement("water_week", "水") { it.bestWaterStreak >= 7 },
        Achievement("evening_week", "継") { it.bestEveningStreak >= 7 },
        Achievement("evening_month", "鉄") { it.bestEveningStreak >= 30 },
        Achievement("tasks_10", "済") { it.count(Counter.TASKS) >= 10 },
        Achievement("tasks_100", "成") { it.count(Counter.TASKS) >= 100 },
        Achievement("spells_10", "覚") { it.count(Counter.SPELLS) >= 10 },
        Achievement("stand_50", "立") { it.count(Counter.STAND_UPS) >= 50 },
        Achievement("bedtime_7", "眠") { it.count(Counter.BEDTIMES) >= 7 },
        Achievement("perfect_day", "完") { it.count(Counter.PERFECT_DAYS) >= 1 },
        Achievement("perfect_week", "極") { it.bestPerfectStreak >= 7 },
        Achievement("ikkyu", "級") { it.xp >= RANKS[9].xp },
        Achievement("shodan", "段") { it.xp >= RANKS[10].xp },
    )

    /** The achievements [stats] reach that aren't in [unlocked]. */
    fun newlyUnlocked(stats: Stats, unlocked: Set<String>): List<Achievement> =
        ACHIEVEMENTS.filter { it.id !in unlocked && it.reached(stats) }

    // The ledger: XP by day and event, for the daily caps and the history.

    data class Ledger(val days: Map<LocalDate, Map<Event, Int>> = emptyMap()) {
        fun earned(date: LocalDate, event: Event): Int = days[date]?.get(event) ?: 0

        fun total(date: LocalDate): Int = days[date]?.values?.sum() ?: 0

        fun add(date: LocalDate, event: Event, xp: Int): Ledger {
            val day = days[date].orEmpty()
            val kept = (days + (date to day + (event to (day[event] ?: 0) + xp))).entries.sortedBy { it.key }.takeLast(DAYS_KEPT)
            return Ledger(kept.associate { it.key to it.value })
        }
    }

    const val DAYS_KEPT = 60

    fun encodeLedger(ledger: Ledger): String = ledger.days.entries.sortedBy { it.key }.joinToString("\n") { (date, events) ->
        (listOf(date.toString()) + events.map { (e, xp) -> "${e.name}=$xp" }).joinToString(";")
    }

    fun decodeLedger(text: String?): Ledger = Ledger(
        text.orEmpty().lines().mapNotNull { line ->
            val f = line.split(';')
            val date = runCatching { LocalDate.parse(f[0]) }.getOrNull() ?: return@mapNotNull null
            date to f.drop(1).mapNotNull { kv ->
                val (name, xp) = kv.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val event = Event.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                xp.toIntOrNull()?.let { event to it }
            }.toMap()
        }.toMap(),
    )

    fun encodeCounters(counters: Map<String, Int>): String = counters.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decodeCounters(text: String?): Map<String, Int> = text.orEmpty().split(';').mapNotNull { kv ->
        kv.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toIntOrNull()?.let { k to it } }
    }.toMap()
}

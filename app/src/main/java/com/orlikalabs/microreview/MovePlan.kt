package com.orlikalabs.microreview

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Move: short exercise habits delivered like the cards, in small doses through the day. Pure part: the exercises,
 * the routines of each phase, the day's log, the warning rules and when a prompt is due. Kept free of Android classes
 * so it can be unit tested; the texts are in `strings.xml` ([MoveText]), the storage in [MoveStore].
 *
 * Two phases. Recovery is a back-recovery program for disc-related back and leg pain: extension (McKenzie press-ups
 * and standing extensions), spine-sparing core work (McGill: bird dog, side plank, curl-up), bridges, hip hinge
 * practice and walking, with posture, hip and foot work that loads nothing. Rebuild adds strength three days a week,
 * bodyweight first and light loads from the third week, the recovery set kept as the warm-up.
 */
object MovePlan {
    enum class Phase { RECOVERY, REBUILD }

    enum class Kind { REHAB, POSTURE, STRENGTH, DESK }

    /**
     * One exercise. [reps] per side when [sides]; [holdSeconds] per rep (0: reps only, nothing to time); [sets] of
     * them. The text in `strings.xml` gives ranges ("5–6 per side, 8–10 s"): these are the top of them, for the timer.
     */
    data class Move(
        val id: String,
        val kind: Kind,
        val reps: Int,
        val holdSeconds: Int = 0,
        val sides: Boolean = false,
        val sets: Int = 1,
    ) {
        /** The timed holds, all sides and sets: what the guided session counts. */
        val holds: Int get() = if (holdSeconds > 0) reps * sets * (if (sides) 2 else 1) else 0
    }

    // Recovery: the core program.
    val PRESS_UP = Move("press_up", Kind.REHAB, reps = 10)
    val BIRD_DOG = Move("bird_dog", Kind.REHAB, reps = 6, holdSeconds = 10, sides = true)
    val SIDE_PLANK_KNEES = Move("side_plank_knees", Kind.REHAB, reps = 5, holdSeconds = 10, sides = true)
    val CURL_UP = Move("curl_up", Kind.REHAB, reps = 5, holdSeconds = 10)
    val BRIDGE = Move("bridge", Kind.REHAB, reps = 10, holdSeconds = 5)
    val HINGE = Move("hinge", Kind.REHAB, reps = 10)

    // Posture, hips and feet: nothing loaded, nothing bent forward.
    val CHIN_TUCK = Move("chin_tuck", Kind.POSTURE, reps = 10, holdSeconds = 5)
    val SCAP_SQUEEZE = Move("scap_squeeze", Kind.POSTURE, reps = 10, holdSeconds = 3)
    val PEC_STRETCH = Move("pec_stretch", Kind.POSTURE, reps = 2, holdSeconds = 30)
    val WALL_ANGEL = Move("wall_angel", Kind.POSTURE, reps = 8)
    val HIP_FLEXOR = Move("hip_flexor", Kind.POSTURE, reps = 2, holdSeconds = 30, sides = true)
    val HIP_ABDUCTION = Move("hip_abduction", Kind.POSTURE, reps = 10, sides = true)
    val SHORT_FOOT = Move("short_foot", Kind.POSTURE, reps = 5, holdSeconds = 5, sides = true)

    // Desk breaks: standing, a minute or two.
    val STANDING_EXTENSION = Move("standing_extension", Kind.DESK, reps = 10)
    val WALK_BREAK = Move("walk_break", Kind.DESK, reps = 1, holdSeconds = 120)
    val CALF_RAISE = Move("calf_raise", Kind.DESK, reps = 15)
    val STANDING_HIP_FLEXOR = Move("standing_hip_flexor", Kind.DESK, reps = 1, holdSeconds = 30, sides = true)

    // Rebuild: strength.
    val SIDE_PLANK = Move("side_plank", Kind.STRENGTH, reps = 3, holdSeconds = 15, sides = true)
    val PUSH_UP = Move("push_up", Kind.STRENGTH, reps = 10, sets = 3)
    val BOX_SQUAT = Move("box_squat", Kind.STRENGTH, reps = 10, sets = 3)
    val SPLIT_SQUAT = Move("split_squat", Kind.STRENGTH, reps = 8, sides = true, sets = 3)
    val ROW = Move("row", Kind.STRENGTH, reps = 10, sets = 3)
    val SINGLE_LEG_BRIDGE = Move("single_leg_bridge", Kind.STRENGTH, reps = 8, sides = true, sets = 2)
    val LOADED_HINGE = Move("loaded_hinge", Kind.STRENGTH, reps = 8, sets = 3)
    val CARRY = Move("carry", Kind.STRENGTH, reps = 3, sides = true)

    val ALL = listOf(
        PRESS_UP, BIRD_DOG, SIDE_PLANK_KNEES, CURL_UP, BRIDGE, HINGE,
        CHIN_TUCK, SCAP_SQUEEZE, PEC_STRETCH, WALL_ANGEL, HIP_FLEXOR, HIP_ABDUCTION, SHORT_FOOT,
        STANDING_EXTENSION, WALK_BREAK, CALF_RAISE, STANDING_HIP_FLEXOR,
        SIDE_PLANK, PUSH_UP, BOX_SQUAT, SPLIT_SQUAT, ROW, SINGLE_LEG_BRIDGE, LOADED_HINGE, CARRY,
    )

    fun byId(id: String): Move? = ALL.firstOrNull { it.id == id }

    /** A part of a routine: a session can stop after any part. */
    data class Part(val id: String, val moves: List<Move>)

    /** What the evening session is today. */
    enum class Session { RECOVERY, STRENGTH }

    /** After waking: press-ups (and the check-in). */
    val MORNING = listOf(PRESS_UP)

    val REHAB_PART = Part("rehab", listOf(PRESS_UP, BIRD_DOG, SIDE_PLANK_KNEES, CURL_UP, BRIDGE, HINGE))
    val POSTURE_PART = Part("posture", listOf(CHIN_TUCK, HIP_ABDUCTION, SHORT_FOOT, HIP_FLEXOR))

    /** Standing, at the desk: one per break, in turn. */
    val DESK = listOf(STANDING_EXTENSION, WALK_BREAK, CHIN_TUCK, PEC_STRETCH, HINGE, CALF_RAISE, STANDING_HIP_FLEXOR, WALL_ANGEL, SCAP_SQUEEZE)

    /** Rebuild, weeks 1 and 2: bodyweight only. From week 3, a light loaded hinge and carries. */
    const val LOADED_FROM_WEEK = 3

    /** A strength session at most every other day, three a week at most. */
    const val STRENGTH_GAP_DAYS = 2L

    /** The session for [today]: strength in Rebuild unless one was done less than two days ago. */
    fun session(phase: Phase, lastStrength: LocalDate?, today: LocalDate): Session =
        if (phase == Phase.REBUILD && (lastStrength == null || ChronoUnit.DAYS.between(lastStrength, today) >= STRENGTH_GAP_DAYS)) {
            Session.STRENGTH
        } else {
            Session.RECOVERY
        }

    /** The parts of [session]; [rebuildWeek]: 1 in the first week of Rebuild. */
    fun parts(session: Session, rebuildWeek: Int = 1): List<Part> = when (session) {
        Session.RECOVERY -> listOf(REHAB_PART, POSTURE_PART)
        Session.STRENGTH -> listOf(
            Part("warmup", listOf(PRESS_UP, BIRD_DOG, SIDE_PLANK, CURL_UP)),
            Part(
                "strength",
                listOf(PUSH_UP, BOX_SQUAT, ROW, SPLIT_SQUAT, SINGLE_LEG_BRIDGE) +
                    if (rebuildWeek >= LOADED_FROM_WEEK) listOf(LOADED_HINGE, CARRY) else emptyList(),
            ),
            POSTURE_PART,
        )
    }

    /** The week of Rebuild [today] is in, from 1. */
    fun rebuildWeek(rebuildStart: LocalDate?, today: LocalDate): Int =
        if (rebuildStart == null) 1 else (ChronoUnit.DAYS.between(rebuildStart, today).coerceAtLeast(0) / 7 + 1).toInt()

    /** The desk break after [count] breaks. */
    fun deskMove(count: Int): Move = DESK[Math.floorMod(count, DESK.size)]

    // The day's log.

    /** Where the leg pain reaches this morning, from none to the lowest. */
    enum class PainSpot { NONE, BACK, BUTTOCK, THIGH, KNEE, LOWER_LEG }

    enum class Trend { BETTER, SAME, WORSE }

    /** What was done on [date]. [evening]: the session done ("rehab": its first part only), null if none. */
    data class Day(
        val date: LocalDate,
        val morning: Boolean = false,
        val evening: String? = null,
        val walkMinutes: Int = 0,
        val pain: PainSpot? = null,
        val trend: Trend? = null,
        val strength: Boolean = false,
    ) {
        val checkedIn: Boolean get() = pain != null
    }

    /** The last [DAYS_KEPT] days are kept. */
    const val DAYS_KEPT = 60

    fun encode(days: Collection<Day>): String = days.sortedBy { it.date }.takeLast(DAYS_KEPT).joinToString("\n") { day ->
        buildList {
            add(day.date.toString())
            if (day.morning) add("m")
            day.evening?.let { add("e=$it") }
            if (day.walkMinutes > 0) add("w=${day.walkMinutes}")
            day.pain?.let { add("p=${it.name}") }
            day.trend?.let { add("t=${it.name}") }
            if (day.strength) add("s")
        }.joinToString(";")
    }

    fun decode(text: String?): List<Day> = text.orEmpty().lines().mapNotNull { line ->
        val fields = line.split(';')
        val date = runCatching { LocalDate.parse(fields[0]) }.getOrNull() ?: return@mapNotNull null
        var day = Day(date)
        for (field in fields.drop(1)) {
            val value = field.substringAfter('=', "")
            day = when {
                field == "m" -> day.copy(morning = true)
                field == "s" -> day.copy(strength = true)
                field.startsWith("e=") -> day.copy(evening = value.ifEmpty { null })
                field.startsWith("w=") -> day.copy(walkMinutes = value.toIntOrNull() ?: 0)
                field.startsWith("p=") -> day.copy(pain = PainSpot.entries.firstOrNull { it.name == value })
                field.startsWith("t=") -> day.copy(trend = Trend.entries.firstOrNull { it.name == value })
                else -> day
            }
        }
        day
    }

    /** [days] with [date]'s entry changed by [change] (created if missing). */
    fun update(days: List<Day>, date: LocalDate, change: (Day) -> Day): List<Day> {
        val existing = days.firstOrNull { it.date == date } ?: Day(date)
        return (days.filter { it.date != date } + change(existing)).sortedBy { it.date }.takeLast(DAYS_KEPT)
    }

    // Warnings.

    enum class Warning {
        /** The leg pain is back at the knee or lower, three mornings running, after being higher: back off. */
        BACK_OFF,

        /** Worse this morning than yesterday, after yesterday's strength session: it was too much. */
        TOO_MUCH,
    }

    fun warning(days: List<Day>, today: LocalDate): Warning? {
        val byDate = days.associateBy { it.date }
        val lastThree = (0L..2L).map { byDate[today.minusDays(it)]?.pain }
        val low = lastThree.all { it != null && it >= PainSpot.KNEE }
        val wasHigher = (3L..16L).any { val pain = byDate[today.minusDays(it)]?.pain; pain != null && pain < PainSpot.KNEE }
        if (low && wasHigher) return Warning.BACK_OFF
        if (byDate[today]?.trend == Trend.WORSE && byDate[today.minusDays(1)]?.strength == true) return Warning.TOO_MUCH
        return null
    }

    /** Days in a row, ending today or yesterday, with an evening session done. */
    fun streak(days: List<Day>, today: LocalDate): Int {
        val done = days.filter { it.evening != null }.map { it.date }.toSet()
        var day = if (today in done) today else today.minusDays(1)
        var count = 0
        while (day in done) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    // Prompts.

    /** The morning prompt is armed by the first unlock between these hours after the screen was off this long. */
    const val MORNING_FROM_HOUR = 4
    const val MORNING_UNTIL_HOUR = 13
    const val NIGHT_MS = 3 * 3600_000L

    /** "Later" waits this long; the evening prompt comes at most this many times. */
    const val LATER_MS = 45 * 60_000L
    const val EVENING_PROMPTS_MAX = 3

    /** An unlock at [hour] after the screen was off [offMs]: waking up, the morning prompt is armed. */
    fun wakesUp(hour: Int, offMs: Long): Boolean = hour in MORNING_FROM_HOUR until MORNING_UNTIL_HOUR && offMs >= NIGHT_MS

    /** The morning prompt: armed today, not done or set aside today, not put off, still morning. */
    fun morningDue(today: Day?, armedToday: Boolean, setAside: Boolean, hour: Int, now: Long, laterUntil: Long): Boolean =
        armedToday && !setAside && today?.morning != true && today?.checkedIn != true &&
            hour < MORNING_UNTIL_HOUR && now >= laterUntil

    /** The evening prompt: from [eveningMinutes] (minutes after midnight), session not done or set aside today. */
    fun eveningDue(
        today: Day?,
        setAside: Boolean,
        minuteOfDay: Int,
        eveningMinutes: Int,
        promptsToday: Int,
        now: Long,
        laterUntil: Long,
    ): Boolean = minuteOfDay >= eveningMinutes && !setAside && today?.evening == null &&
        promptsToday < EVENING_PROMPTS_MAX && now >= laterUntil
}

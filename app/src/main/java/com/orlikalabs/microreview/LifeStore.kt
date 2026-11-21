package com.orlikalabs.microreview

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.LocalTime

/**
 * The lifestyle side's storage ([Habits], [Tasks], [Progress]): in `life`, backed up (the habits and their days, the
 * tasks, the XP, counters and seals: what the user made and earned); the reminders' state and the celebrations to
 * show in `life_state`, left out like the rest of the app's state. Thread-safe: the card answers award XP from a
 * background thread.
 */
object LifeStore {
    private const val PREFS = "com.orlikalabs.microreview.life"
    private const val STATE = "com.orlikalabs.microreview.life_state"

    private const val KEY_HABITS = "habits"
    private const val KEY_HABIT_DAYS = "habit_days"
    private const val KEY_TASKS = "tasks"
    private const val KEY_LEDGER = "ledger"
    private const val KEY_XP = "xp"
    private const val KEY_COUNTERS = "counters"
    private const val KEY_UNLOCKED = "unlocked"
    private const val KEY_BEST_WATER = "best_water"
    private const val KEY_BEST_EVENING = "best_evening"
    private const val KEY_BEST_PERFECT = "best_perfect"

    private const val KEY_REMINDED = "reminded"
    private const val KEY_LAST_PACE = "last_pace"
    private const val KEY_CELEBRATE = "celebrate"

    // Habits.

    fun habits(context: Context): List<Habits.Habit> =
        Habits.decodeHabits(prefs(context).getString(KEY_HABITS, null)) ?: listOf(Habits.WATER)

    fun setHabits(context: Context, habits: List<Habits.Habit>) = prefs(context).edit().putString(KEY_HABITS, Habits.encodeHabits(habits)).apply()

    fun habitDays(context: Context): List<Habits.Day> = Habits.decodeDays(prefs(context).getString(KEY_HABIT_DAYS, null))

    fun habitToday(context: Context): Habits.Day? = habitDays(context).firstOrNull { it.date == LocalDate.now() }

    /**
     * [habit] tapped ([delta] -1 to take one back). Awards: a cup or a tap, the habit done, all of them done (a
     * perfect day). Taking one back takes nothing back: the caps keep it fair enough.
     */
    @Synchronized
    fun tap(context: Context, habit: Habits.Habit, delta: Int = 1) {
        val today = LocalDate.now()
        val habits = habits(context)
        val before = habitDays(context)
        val wasDone = Habits.done(habit, before.firstOrNull { it.date == today })
        val wasAll = Habits.allDone(habits, before.firstOrNull { it.date == today })
        val after = Habits.tap(before, today, habit.id, delta)
        prefs(context).edit().putString(KEY_HABIT_DAYS, Habits.encodeDays(after)).apply()
        if (delta <= 0) return
        val day = after.firstOrNull { it.date == today }
        if (habit.id == Habits.WATER.id) {
            if (day != null && day.count(habit.id) <= habit.goal) award(context, Progress.Event.WATER_CUP, Progress.Counter.CUPS)
        } else {
            award(context, Progress.Event.HABIT_TAP)
        }
        if (!wasDone && Habits.done(habit, day)) {
            award(context, Progress.Event.HABIT_DONE)
            if (habit.id == Habits.WATER.id) best(context, KEY_BEST_WATER, Habits.streak(habit, after, today))
        }
        if (!wasAll && Habits.allDone(habits, day)) {
            best(context, KEY_BEST_PERFECT, perfectStreak(habits, after, today))
            award(context, Progress.Event.ALL_HABITS, Progress.Counter.PERFECT_DAYS)
        }
    }

    private fun perfectStreak(habits: List<Habits.Habit>, days: List<Habits.Day>, today: LocalDate): Int {
        val byDate = days.associateBy { it.date }
        var date = today
        var count = 0
        while (Habits.allDone(habits, byDate[date])) {
            count++
            date = date.minusDays(1)
        }
        return count
    }

    // Tasks.

    fun tasks(context: Context): List<Tasks.Task> = Tasks.decode(prefs(context).getString(KEY_TASKS, null))

    @Synchronized
    private fun setTasks(context: Context, tasks: List<Tasks.Task>) =
        prefs(context).edit().putString(KEY_TASKS, Tasks.encode(Tasks.tidy(tasks, System.currentTimeMillis()))).apply()

    @Synchronized
    fun addTask(context: Context, title: String, due: Long?, starred: Boolean = false) {
        val now = System.currentTimeMillis()
        val id = maxOf(now, (tasks(context).maxOfOrNull { it.id } ?: 0L) + 1)
        setTasks(context, tasks(context) + Tasks.Task(id, title.trim(), created = now, due = due, starred = starred))
    }

    @Synchronized
    fun updateTask(context: Context, id: Long, change: (Tasks.Task) -> Tasks.Task) = setTasks(context, Tasks.update(tasks(context), id, change))

    @Synchronized
    fun completeTask(context: Context, id: Long) {
        val now = System.currentTimeMillis()
        val task = tasks(context).firstOrNull { it.id == id && it.isOpen } ?: return
        updateTask(context, id) { it.copy(done = now) }
        award(context, Progress.Event.TASK_DONE, Progress.Counter.TASKS)
        if (task.due != null && now <= task.due) award(context, Progress.Event.TASK_ON_TIME)
    }

    @Synchronized
    fun deleteTask(context: Context, id: Long) = setTasks(context, tasks(context).filter { it.id != id })

    // Progress.

    fun xp(context: Context): Int = prefs(context).getInt(KEY_XP, 0)

    fun ledger(context: Context): Progress.Ledger = Progress.decodeLedger(prefs(context).getString(KEY_LEDGER, null))

    fun unlocked(context: Context): Set<String> = prefs(context).getStringSet(KEY_UNLOCKED, emptySet()).orEmpty()

    fun stats(context: Context): Progress.Stats {
        val prefs = prefs(context)
        return Progress.Stats(
            counters = Progress.decodeCounters(prefs.getString(KEY_COUNTERS, null)),
            bestWaterStreak = prefs.getInt(KEY_BEST_WATER, 0),
            bestEveningStreak = prefs.getInt(KEY_BEST_EVENING, 0),
            bestPerfectStreak = prefs.getInt(KEY_BEST_PERFECT, 0),
            xp = prefs.getInt(KEY_XP, 0),
        )
    }

    /** The evening routine's streak reached [streak] ([MovePlan.streak]): kept if it's the best. */
    fun eveningStreak(context: Context, streak: Int) {
        best(context, KEY_BEST_EVENING, streak)
        checkAchievements(context)
    }

    /**
     * [event] happened: its XP (capped per day), [counter] counted, the rank and the seals checked; a rank up or a
     * new seal is kept to be celebrated in the app ([celebrations]). Returns the XP earned.
     */
    @Synchronized
    fun award(context: Context, event: Progress.Event, counter: String? = null): Int {
        val prefs = prefs(context)
        val today = LocalDate.now()
        val ledger = ledger(context)
        val points = Progress.points(event, ledger.earned(today, event))
        val xpBefore = prefs.getInt(KEY_XP, 0)
        val edit = prefs.edit()
        if (counter != null) {
            val counters = Progress.decodeCounters(prefs.getString(KEY_COUNTERS, null))
            edit.putString(KEY_COUNTERS, Progress.encodeCounters(counters + (counter to (counters[counter] ?: 0) + 1)))
        }
        if (points > 0) {
            edit.putString(KEY_LEDGER, Progress.encodeLedger(ledger.add(today, event, points)))
            edit.putInt(KEY_XP, xpBefore + points)
        }
        edit.commit()
        if (Progress.rank(xpBefore + points).index > Progress.rank(xpBefore).index) {
            celebrate(context, "rank:${Progress.rank(xpBefore + points).index}")
        }
        checkAchievements(context)
        return points
    }

    @Synchronized
    private fun checkAchievements(context: Context) {
        val unlocked = unlocked(context)
        val fresh = Progress.newlyUnlocked(stats(context), unlocked)
        if (fresh.isEmpty()) return
        prefs(context).edit().putStringSet(KEY_UNLOCKED, unlocked + fresh.map { it.id }).apply()
        fresh.forEach { celebrate(context, "ach:${it.id}") }
    }

    private fun best(context: Context, key: String, value: Int) {
        if (value > prefs(context).getInt(key, 0)) prefs(context).edit().putInt(key, value).apply()
    }

    // Celebrations: "rank:<index>" or "ach:<id>", shown by the app when it's next in front.

    @Synchronized
    private fun celebrate(context: Context, what: String) {
        val list = state(context).getString(KEY_CELEBRATE, "").orEmpty().split('\n').filter { it.isNotEmpty() }
        state(context).edit().putString(KEY_CELEBRATE, (list + what).joinToString("\n")).apply()
    }

    @Synchronized
    fun takeCelebrations(context: Context): List<String> {
        val list = state(context).getString(KEY_CELEBRATE, "").orEmpty().split('\n').filter { it.isNotEmpty() }
        if (list.isNotEmpty()) state(context).edit().remove(KEY_CELEBRATE).apply()
        return list
    }

    // Habit reminders' state.

    /** The habit reminder due now ([Habits.due]). */
    fun dueHabitReminder(context: Context, now: Long = System.currentTimeMillis()): Habits.Reminder? {
        val time = LocalTime.now()
        return Habits.due(habits(context), habitToday(context), time.hour * 60 + time.minute, reminded(context), lastPace(context), now)
    }

    /** [reminder] was shown (or answered): not again today for a dose, not for a while for the pace. */
    fun reminded(context: Context, reminder: Habits.Reminder, now: Long = System.currentTimeMillis()) {
        val state = state(context)
        if (reminder.doseTime != null) {
            val today = LocalDate.now().toString()
            val keys = reminded(context) + "${reminder.habit.id}@${reminder.doseTime}"
            state.edit().putString(KEY_REMINDED, today + "\n" + keys.joinToString("\n")).apply()
        } else {
            val pace = lastPace(context) + (reminder.habit.id to now)
            state.edit().putString(KEY_LAST_PACE, pace.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
        }
    }

    fun remindedKeys(context: Context): Set<String> = reminded(context)

    fun lastPaceTimes(context: Context): Map<String, Long> = lastPace(context)

    private fun reminded(context: Context): Set<String> {
        val lines = state(context).getString(KEY_REMINDED, null)?.split('\n') ?: return emptySet()
        return if (lines.firstOrNull() == LocalDate.now().toString()) lines.drop(1).filter { it.isNotEmpty() }.toSet() else emptySet()
    }

    private fun lastPace(context: Context): Map<String, Long> = state(context).getString(KEY_LAST_PACE, null).orEmpty().split(';').mapNotNull { kv ->
        kv.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toLongOrNull()?.let { k to it } }
    }.toMap()

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun state(context: Context): SharedPreferences = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
}

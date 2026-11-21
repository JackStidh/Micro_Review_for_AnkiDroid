package com.orlikalabs.microreview

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.LocalTime

/**
 * Move's storage ([MovePlan]): the choices (program phase, prompts, evening time) in a backed-up file, the log of the
 * days and the prompts' state in another, left out of backups like the rest of the app's state.
 */
object MoveStore {
    private const val PREFS = "com.orlikalabs.microreview.move"
    private const val LOG_PREFS = "com.orlikalabs.microreview.move_log"

    private const val KEY_PHASE = "phase"
    private const val KEY_PROMPTS = "prompts"
    private const val KEY_EVENING_MINUTES = "evening_minutes"
    private const val KEY_REBUILD_START = "rebuild_start"

    private const val KEY_LOG = "log"
    private const val KEY_SCREEN_OFF_AT = "screen_off_at"
    private const val KEY_MORNING_ARMED = "morning_armed"
    private const val KEY_MORNING_ASIDE = "morning_aside"
    private const val KEY_EVENING_ASIDE = "evening_aside"
    private const val KEY_MORNING_LATER = "morning_later"
    private const val KEY_EVENING_LATER = "evening_later"
    private const val KEY_EVENING_PROMPTS = "evening_prompts"

    const val DEFAULT_EVENING_MINUTES = 19 * 60

    /** The evening times offered, minutes after midnight. */
    val EVENING_TIMES = (16..22).map { it * 60 }

    enum class Prompt { MORNING, EVENING }

    // Choices.

    /** The program's phase, null until one is picked (Move is off until then). */
    fun phase(context: Context): MovePlan.Phase? =
        prefs(context).getString(KEY_PHASE, null)?.let { name -> MovePlan.Phase.entries.firstOrNull { it.name == name } }

    fun setPhase(context: Context, phase: MovePlan.Phase) {
        val edit = prefs(context).edit().putString(KEY_PHASE, phase.name)
        if (phase == MovePlan.Phase.REBUILD) {
            edit.putString(KEY_REBUILD_START, LocalDate.now().toString())
        } else {
            edit.remove(KEY_REBUILD_START)
        }
        edit.apply()
        UsageMonitorService.sync(context.applicationContext)
    }

    fun rebuildWeek(context: Context): Int =
        MovePlan.rebuildWeek(prefs(context).getString(KEY_REBUILD_START, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, LocalDate.now())

    /** Prompts on: picked a program and didn't turn them off. */
    fun promptsOn(context: Context): Boolean = phase(context) != null && prefs(context).getBoolean(KEY_PROMPTS, true)

    fun setPromptsOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROMPTS, on).apply()
        UsageMonitorService.sync(context.applicationContext)
    }

    fun eveningMinutes(context: Context): Int = prefs(context).getInt(KEY_EVENING_MINUTES, DEFAULT_EVENING_MINUTES)

    fun setEveningMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_EVENING_MINUTES, minutes).apply()

    // The log.

    @Volatile
    private var cachedText: String? = null

    @Volatile
    private var cachedDays: List<MovePlan.Day> = emptyList()

    fun days(context: Context): List<MovePlan.Day> {
        val text = log(context).getString(KEY_LOG, null)
        if (text != cachedText) {
            cachedDays = MovePlan.decode(text)
            cachedText = text
        }
        return cachedDays
    }

    fun today(context: Context): MovePlan.Day? = days(context).firstOrNull { it.date == LocalDate.now() }

    @Synchronized
    fun updateToday(context: Context, change: (MovePlan.Day) -> MovePlan.Day) {
        val days = MovePlan.update(days(context), LocalDate.now(), change)
        log(context).edit().putString(KEY_LOG, MovePlan.encode(days)).apply()
    }

    fun lastStrength(context: Context): LocalDate? = days(context).filter { it.strength }.maxOfOrNull { it.date }

    /** Today's evening session. */
    fun session(context: Context): MovePlan.Session =
        MovePlan.session(phase(context) ?: MovePlan.Phase.RECOVERY, lastStrength(context), LocalDate.now())

    fun warning(context: Context): MovePlan.Warning? = MovePlan.warning(days(context), LocalDate.now())

    // Prompts.

    /** The screen went off: when, to tell a night from a moment. */
    fun onScreenOff(context: Context, now: Long = System.currentTimeMillis()) =
        log(context).edit().putLong(KEY_SCREEN_OFF_AT, now).apply()

    /** The screen came on: after a night, in the morning, the morning prompt is armed for today. */
    fun onScreenOn(context: Context, now: Long = System.currentTimeMillis()) {
        val offAt = log(context).getLong(KEY_SCREEN_OFF_AT, 0L)
        if (offAt != 0L && MovePlan.wakesUp(LocalTime.now().hour, now - offAt)) {
            log(context).edit().putString(KEY_MORNING_ARMED, LocalDate.now().toString()).apply()
        }
    }

    /** The prompt due now, if any. */
    fun duePrompt(context: Context, now: Long = System.currentTimeMillis()): Prompt? {
        if (!promptsOn(context)) return null
        val log = log(context)
        val today = LocalDate.now().toString()
        val time = LocalTime.now()
        val day = today(context)
        if (MovePlan.morningDue(
                day,
                armedToday = log.getString(KEY_MORNING_ARMED, null) == today,
                setAside = log.getString(KEY_MORNING_ASIDE, null) == today,
                hour = time.hour,
                now = now,
                laterUntil = log.getLong(KEY_MORNING_LATER, 0L),
            )
        ) {
            return Prompt.MORNING
        }
        val prompts = log.getString(KEY_EVENING_PROMPTS, null)?.takeIf { it.startsWith("$today:") }?.substringAfter(':')?.toIntOrNull() ?: 0
        if (MovePlan.eveningDue(
                day,
                setAside = log.getString(KEY_EVENING_ASIDE, null) == today,
                minuteOfDay = time.hour * 60 + time.minute,
                eveningMinutes = eveningMinutes(context),
                promptsToday = prompts,
                now = now,
                laterUntil = log.getLong(KEY_EVENING_LATER, 0L),
            )
        ) {
            return Prompt.EVENING
        }
        return null
    }

    /** [prompt] was shown: the evening one is counted. */
    fun prompted(context: Context, prompt: Prompt) {
        if (prompt != Prompt.EVENING) return
        val today = LocalDate.now().toString()
        val log = log(context)
        val count = log.getString(KEY_EVENING_PROMPTS, null)?.takeIf { it.startsWith("$today:") }?.substringAfter(':')?.toIntOrNull() ?: 0
        log.edit().putString(KEY_EVENING_PROMPTS, "$today:${count + 1}").apply()
    }

    /** "Later": the prompt comes back in [MovePlan.LATER_MS]. */
    fun later(context: Context, prompt: Prompt, now: Long = System.currentTimeMillis()) =
        log(context).edit().putLong(if (prompt == Prompt.MORNING) KEY_MORNING_LATER else KEY_EVENING_LATER, now + MovePlan.LATER_MS).apply()

    /** "Not today". */
    fun setAside(context: Context, prompt: Prompt) =
        log(context).edit().putString(if (prompt == Prompt.MORNING) KEY_MORNING_ASIDE else KEY_EVENING_ASIDE, LocalDate.now().toString()).apply()

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun log(context: Context): SharedPreferences = context.getSharedPreferences(LOG_PREFS, Context.MODE_PRIVATE)
}

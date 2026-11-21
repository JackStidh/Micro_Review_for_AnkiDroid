package com.orlikalabs.microreview

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.time.LocalTime

/**
 * The guard's choices ([Guard]): the spell breaker's apps and minutes, the long-session minutes, the inactivity nudge,
 * the bedtime; in `life` (backed up). Its state in `life_state`; the stretch in the watched apps lives in memory, in
 * the monitor's process, and starts over if it restarts.
 */
object GuardStore {
    private const val PREFS = "com.orlikalabs.microreview.life"
    private const val STATE = "com.orlikalabs.microreview.life_state"

    private const val KEY_SPELL_MINUTES = "spell_minutes"
    private const val KEY_WATCHED = "watched_apps"
    private const val KEY_SESSION_MINUTES = "session_minutes"
    private const val KEY_INACTIVITY = "inactivity"
    private const val KEY_BEDTIME = "bedtime"

    private const val KEY_SAMPLES = "step_samples"
    private const val KEY_LAST_INACTIVE = "last_inactive"
    private const val KEY_SESSION_LATER = "session_later"
    private const val KEY_SOFT_LATER = "soft_later"
    private const val KEY_LAST_SOFT = "last_soft"
    private const val KEY_BEDTIME_ANSWERED = "bedtime_answered"
    private const val KEY_BEDTIME_LATER = "bedtime_later"
    private const val KEY_BEDTIME_STRIKES = "bedtime_strikes"
    private const val KEY_SLEEP = "sleep"
    private const val KEY_LAST_USE_END = "last_use_end"

    const val DEFAULT_SPELL_MINUTES = 20
    const val DEFAULT_SESSION_MINUTES = 60
    val SPELL_CHOICES = listOf(0, 10, 15, 20, 30, 45, 60)
    val SESSION_CHOICES = listOf(0, 30, 45, 60, 90, 120)

    /** Bedtimes offered, minutes after midnight (-1: off). */
    val BEDTIMES = listOf(-1) + (21 * 60..23 * 60 + 30 step 30).toList() + listOf(0, 30, 60)

    // Choices.

    fun spellMinutes(context: Context): Int = prefs(context).getInt(KEY_SPELL_MINUTES, DEFAULT_SPELL_MINUTES)

    fun setSpellMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_SPELL_MINUTES, minutes).apply()

    /** The scrolling apps watched: chosen, or the gated apps until some are. */
    fun watched(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_WATCHED, null) ?: FocusSettings.gatedApps(context)

    fun setWatched(context: Context, apps: Set<String>) = prefs(context).edit().putStringSet(KEY_WATCHED, apps).apply()

    fun sessionMinutes(context: Context): Int = prefs(context).getInt(KEY_SESSION_MINUTES, DEFAULT_SESSION_MINUTES)

    fun setSessionMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_SESSION_MINUTES, minutes).apply()

    fun inactivityOn(context: Context): Boolean = prefs(context).getBoolean(KEY_INACTIVITY, true) && canCountSteps(context)

    fun setInactivityOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_INACTIVITY, on).apply()

    /** Minutes after midnight, -1 when off (the default: it's a personal time). */
    fun bedtime(context: Context): Int = prefs(context).getInt(KEY_BEDTIME, -1)

    fun setBedtime(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_BEDTIME, minutes).apply()

    // The stretch in the watched apps and the phone session, in memory.

    @Volatile
    var stretch = Guard.Stretch()

    /** When the phone's current session of use started (0: screen off). */
    @Volatile
    var useStartedAt = 0L

    /** The app in front at [now] ([UsageMonitorService], every second). */
    fun track(context: Context, app: String?, now: Long) {
        stretch = Guard.track(stretch, app, watched(context), now)
    }

    /** The screen came on: a new session after a real break, and last night's sleep after a night. */
    fun onScreenOn(context: Context, now: Long = System.currentTimeMillis()) {
        val lastEnd = state(context).getLong(KEY_LAST_USE_END, 0L)
        if (useStartedAt == 0L || now - lastEnd >= Guard.SESSION_BREAK_MS) useStartedAt = now
        if (LocalTime.now().hour in MovePlan.MORNING_FROM_HOUR until MovePlan.MORNING_UNTIL_HOUR) {
            val today = LocalDate.now().toString()
            val stored = state(context).getString(KEY_SLEEP, null)
            if (stored?.startsWith("$today:") != true) {
                Guard.sleepMs(lastEnd, now)?.let { state(context).edit().putString(KEY_SLEEP, "$today:$it").apply() }
            }
        }
        readSteps(context)
    }

    /** The screen went off after being used. */
    fun onScreenOff(context: Context, used: Boolean, now: Long = System.currentTimeMillis()) {
        if (used) state(context).edit().putLong(KEY_LAST_USE_END, now).apply()
    }

    /** Last night's sleep, if measured this morning. */
    fun sleepMs(context: Context): Long? {
        val stored = state(context).getString(KEY_SLEEP, null) ?: return null
        return stored.takeIf { it.startsWith("${LocalDate.now()}:") }?.substringAfter(':')?.toLongOrNull()
    }

    // The spell breaker's answers.

    fun more(now: Long = System.currentTimeMillis()) {
        stretch = Guard.more(stretch, now)
    }

    fun exempt() {
        stretch = stretch.copy(exempt = true)
    }

    fun broken(context: Context) {
        stretch = Guard.Stretch()
        LifeStore.award(context, Progress.Event.SPELL_BROKEN, Progress.Counter.SPELLS)
    }

    fun sessionLater(context: Context, now: Long = System.currentTimeMillis()) =
        state(context).edit().putLong(KEY_SESSION_LATER, now + Guard.SESSION_LATER_MS).apply()

    /** A break taken from a long session: the session starts over. */
    fun sessionBreak(context: Context) {
        useStartedAt = System.currentTimeMillis()
        LifeStore.award(context, Progress.Event.STAND_UP, Progress.Counter.STAND_UPS)
    }

    fun sessionLaterUntil(context: Context): Long = state(context).getLong(KEY_SESSION_LATER, 0L)

    // Steps.

    fun canCountSteps(context: Context): Boolean {
        val sensors = context.getSystemService(SensorManager::class.java) ?: return false
        if (sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) == null) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    fun hasStepSensor(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** Asks the step counter for its count once, kept as a reading ([Guard.Sample]). */
    fun readSteps(context: Context) {
        if (!canCountSteps(context)) return
        val app = context.applicationContext
        val sensors = app.getSystemService(SensorManager::class.java) ?: return
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val main = Handler(Looper.getMainLooper())
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sensors.unregisterListener(this)
                addSample(app, Guard.Sample(System.currentTimeMillis(), event.values[0].toLong()))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        try {
            sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, main)
            main.postDelayed({ sensors.unregisterListener(listener) }, 10_000L)
        } catch (e: Exception) {
            FocusLog.i(app, "step counter: $e")
        }
    }

    @Synchronized
    private fun addSample(context: Context, sample: Guard.Sample) {
        val samples = Guard.withSample(samples(context), sample)
        state(context).edit().putString(KEY_SAMPLES, Guard.encodeSamples(samples)).apply()
    }

    fun samples(context: Context): List<Guard.Sample> = Guard.decodeSamples(state(context).getString(KEY_SAMPLES, null))

    /** Steps since midnight, from the readings (null without the step counter). */
    fun stepsToday(context: Context): Long? {
        if (!canCountSteps(context)) return null
        val midnight = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Guard.stepsSince(samples(context), midnight)
    }

    fun inactive(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        inactivityOn(context) && Guard.inactive(samples(context), now, LocalTime.now().hour, state(context).getLong(KEY_LAST_INACTIVE, 0L))

    fun inactiveNudged(context: Context, now: Long = System.currentTimeMillis()) = state(context).edit().putLong(KEY_LAST_INACTIVE, now).apply()

    // Bedtime.

    fun bedtimeDue(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val bedtime = bedtime(context)
        val time = LocalTime.now()
        if (!Guard.pastBedtime(time.hour * 60 + time.minute, bedtime)) return false
        return state(context).getString(KEY_BEDTIME_ANSWERED, null) != nightOf() && now >= state(context).getLong(KEY_BEDTIME_LATER, 0L)
    }

    fun bedtimeStrikes(context: Context): Int =
        state(context).getString(KEY_BEDTIME_STRIKES, null)?.takeIf { it.startsWith("${nightOf()}:") }?.substringAfter(':')?.toIntOrNull() ?: 0

    /** "Going to bed": on time, a point for the seal; either way, not again tonight. */
    fun goingToBed(context: Context, award: Boolean = true) {
        val time = LocalTime.now()
        state(context).edit().putString(KEY_BEDTIME_ANSWERED, nightOf()).apply()
        if (award && Guard.onTime(time.hour * 60 + time.minute, bedtime(context))) {
            LifeStore.award(context, Progress.Event.BEDTIME, Progress.Counter.BEDTIMES)
        }
    }

    fun bedtimeLater(context: Context, now: Long = System.currentTimeMillis()) {
        state(context).edit()
            .putLong(KEY_BEDTIME_LATER, now + Guard.BEDTIME_LATER_MS)
            .putString(KEY_BEDTIME_STRIKES, "${nightOf()}:${bedtimeStrikes(context) + 1}")
            .apply()
    }

    /** The night a time belongs to: before 4:00 it's still the evening before's. */
    private fun nightOf(): String {
        val now = java.time.LocalDateTime.now()
        return (if (now.hour < 4) now.toLocalDate().minusDays(1) else now.toLocalDate()).toString()
    }

    // The soft nudges' spacing.

    /** At most one soft nudge (habits, tasks, inactivity) in this long. */
    const val SOFT_GAP_MS = 20 * 60_000L
    const val SOFT_LATER_MS = 30 * 60_000L

    fun softAllowed(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        now - state(context).getLong(KEY_LAST_SOFT, 0L) >= SOFT_GAP_MS && now >= state(context).getLong(KEY_SOFT_LATER, 0L)

    fun softShown(context: Context, now: Long = System.currentTimeMillis()) = state(context).edit().putLong(KEY_LAST_SOFT, now).apply()

    fun softLater(context: Context, now: Long = System.currentTimeMillis()) = state(context).edit().putLong(KEY_SOFT_LATER, now + SOFT_LATER_MS).apply()

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun state(context: Context): SharedPreferences = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
}

package com.orlikalabs.microreview

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject

/**
 * User choices about cards while the phone is in use: nudges (a card every so often, with an interval per app),
 * the app gate (cards before a chosen app opens), "Pass / Fail only", and when cards must stay away (snooze,
 * quiet hours, driving, Do Not Disturb, calls). The gate's pass and the day's emergency skips are kept here too.
 */
object FocusSettings {
    private const val PREFS = "com.orlikalabs.microreview.focus"

    private const val KEY_NUDGES = "nudges_enabled"
    private const val KEY_NUDGE_STYLE = "nudge_style"
    private const val KEY_DEFAULT_INTERVAL = "default_interval_minutes"
    private const val KEY_APP_INTERVALS = "app_intervals"
    private const val KEY_NUDGE_LIFETIME = "nudge_lifetime_seconds"
    private const val KEY_PASS_FAIL = "pass_fail_only"

    private const val KEY_SNOOZE_UNTIL = "snooze_until"
    private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    private const val KEY_MORNING = "morning_minute"
    private const val KEY_QUIET_HOURS = "quiet_hours"
    private const val KEY_PAUSE_DRIVING = "pause_when_driving"
    private const val KEY_DETECT_VEHICLE = "detect_vehicle"
    private const val KEY_NOT_DRIVING_UNTIL = "not_driving_until"
    private const val KEY_PAUSE_DND = "pause_on_dnd"
    private const val KEY_PAUSE_CALL = "pause_in_call"

    private const val KEY_GATE = "gate_enabled"
    private const val KEY_GATED_APPS = "gated_apps"
    private const val KEY_GATE_CARDS = "gate_cards"
    private const val KEY_SKIPS_PER_DAY = "skips_per_day"
    private const val KEY_SKIP_DAY = "skip_day"
    private const val KEY_SKIPS_USED = "skips_used"
    private const val KEY_GATE_PASS = "gate_pass"
    private const val KEY_SNOOZE_LIFTS_GATE = "snooze_lifts_gate"

    const val DEFAULT_INTERVAL_MINUTES = 20
    const val DEFAULT_NUDGE_LIFETIME_SECONDS = 30
    const val DEFAULT_SNOOZE_MINUTES = 30
    const val DEFAULT_MORNING_MINUTE = 7 * 60

    /** How a nudge shows its card. */
    enum class NudgeStyle {
        /** A pop-up notification: Show answer, then Pass or Fail. */
        NOTIFICATION,

        /** The card takes the whole screen (needs "Display over other apps"): it can't be overlooked, and the time is exact. */
        FULL_SCREEN,
    }

    // Nudges

    fun nudgesEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_NUDGES, false)

    fun setNudgesEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_NUDGES, enabled).apply()

    fun nudgeStyle(context: Context): NudgeStyle =
        prefs(context).getString(KEY_NUDGE_STYLE, null)
            ?.let { name -> NudgeStyle.entries.firstOrNull { it.name == name } }
            ?: NudgeStyle.NOTIFICATION

    fun setNudgeStyle(context: Context, style: NudgeStyle) = prefs(context).edit().putString(KEY_NUDGE_STYLE, style.name).apply()

    /** Minutes of phone use between two cards, for apps without their own interval; [NudgeClock.NEVER] for none. */
    fun defaultIntervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_DEFAULT_INTERVAL, DEFAULT_INTERVAL_MINUTES).coerceAtLeast(NudgeClock.NEVER)

    fun setDefaultIntervalMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_DEFAULT_INTERVAL, minutes).apply()

    /** The apps (package names) with their own interval in minutes; [NudgeClock.NEVER] for "never on this app". */
    fun appIntervals(context: Context): Map<String, Int> {
        val stored = prefs(context).getString(KEY_APP_INTERVALS, null) ?: return emptyMap()
        return try {
            val json = JSONObject(stored)
            json.keys().asSequence().associateWith { json.getInt(it).coerceAtLeast(NudgeClock.NEVER) }
        } catch (e: JSONException) {
            emptyMap()
        }
    }

    /** Gives [packageName] its own interval, or removes it when [minutes] is null (back to the default). */
    fun setAppInterval(context: Context, packageName: String, minutes: Int?) {
        val intervals = appIntervals(context).toMutableMap()
        if (minutes == null) intervals.remove(packageName) else intervals[packageName] = minutes
        val json = JSONObject()
        intervals.forEach { (app, value) -> json.put(app, value) }
        prefs(context).edit().putString(KEY_APP_INTERVALS, json.toString()).apply()
    }

    /** How long an untouched card notification stays before it is withdrawn, so its time doesn't count. */
    fun nudgeLifetimeSeconds(context: Context): Int =
        prefs(context).getInt(KEY_NUDGE_LIFETIME, DEFAULT_NUDGE_LIFETIME_SECONDS).coerceIn(10, 300)

    fun setNudgeLifetimeSeconds(context: Context, seconds: Int) = prefs(context).edit().putInt(KEY_NUDGE_LIFETIME, seconds).apply()

    /** "Pass / Fail only": Again and Good, relabeled; Hard and Easy are never offered. */
    fun passFailOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_PASS_FAIL, true)

    fun setPassFailOnly(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_PASS_FAIL, enabled).apply()

    // Snooze and pauses

    /** The end of the current snooze (epoch milliseconds), 0 for none. */
    fun snoozeUntil(context: Context): Long = prefs(context).getLong(KEY_SNOOZE_UNTIL, 0)

    fun setSnoozeUntil(context: Context, until: Long) = prefs(context).edit().putLong(KEY_SNOOZE_UNTIL, until).apply()

    /** The length of the snooze offered on a card. */
    fun snoozeMinutes(context: Context): Int = prefs(context).getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES).coerceIn(5, 24 * 60)

    fun setSnoozeMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_SNOOZE_MINUTES, minutes).apply()

    /** When "until tomorrow" ends, in minutes since midnight. */
    fun morningMinute(context: Context): Int = prefs(context).getInt(KEY_MORNING, DEFAULT_MORNING_MINUTE)

    fun quietHours(context: Context): List<QuietTime.Window> =
        prefs(context).getString(KEY_QUIET_HOURS, null)?.let(QuietTime::parse) ?: emptyList()

    fun setQuietHours(context: Context, windows: List<QuietTime.Window>) =
        prefs(context).edit().putString(KEY_QUIET_HOURS, QuietTime.format(windows)).apply()

    fun pauseWhenDriving(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSE_DRIVING, true)

    fun setPauseWhenDriving(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_PAUSE_DRIVING, enabled).apply()

    /** Also use the phone's motion ("in a vehicle") to tell driving; needs the physical activity permission. */
    fun detectVehicle(context: Context): Boolean = prefs(context).getBoolean(KEY_DETECT_VEHICLE, false)

    fun setDetectVehicle(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_DETECT_VEHICLE, enabled).apply()

    /** "I'm not driving" (a passenger): until then, motion doesn't count as driving. */
    fun notDrivingUntil(context: Context): Long = prefs(context).getLong(KEY_NOT_DRIVING_UNTIL, 0)

    fun setNotDrivingUntil(context: Context, until: Long) = prefs(context).edit().putLong(KEY_NOT_DRIVING_UNTIL, until).apply()

    fun pauseOnDoNotDisturb(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSE_DND, true)

    fun setPauseOnDoNotDisturb(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_PAUSE_DND, enabled).apply()

    fun pauseInCall(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSE_CALL, true)

    fun setPauseInCall(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_PAUSE_CALL, enabled).apply()

    // App gate

    fun gateEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_GATE, false)

    fun setGateEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_GATE, enabled).apply()

    fun gatedApps(context: Context): Set<String> = prefs(context).getStringSet(KEY_GATED_APPS, null)?.toSet() ?: emptySet()

    fun setGatedApps(context: Context, apps: Set<String>) = prefs(context).edit().putStringSet(KEY_GATED_APPS, apps.toSet()).apply()

    fun gateCards(context: Context): Int = GateRules.cardsRequired(prefs(context).getInt(KEY_GATE_CARDS, GateRules.DEFAULT_CARDS))

    fun setGateCards(context: Context, cards: Int) = prefs(context).edit().putInt(KEY_GATE_CARDS, cards).apply()

    fun skipsPerDay(context: Context): Int = prefs(context).getInt(KEY_SKIPS_PER_DAY, GateRules.DEFAULT_SKIPS_PER_DAY).coerceIn(0, 10)

    fun setSkipsPerDay(context: Context, skips: Int) = prefs(context).edit().putInt(KEY_SKIPS_PER_DAY, skips).apply()

    /** Emergency skips left on [today] (epoch day). */
    fun skipsLeft(context: Context, today: Long): Int {
        val prefs = prefs(context)
        return GateRules.skipsLeft(skipsPerDay(context), prefs.getLong(KEY_SKIP_DAY, -1), prefs.getInt(KEY_SKIPS_USED, 0), today)
    }

    /** Uses one of [today]'s emergency skips. Returns whether one was left. */
    fun useSkip(context: Context, today: Long): Boolean {
        if (skipsLeft(context, today) <= 0) return false
        val prefs = prefs(context)
        val used = if (prefs.getLong(KEY_SKIP_DAY, -1) == today) prefs.getInt(KEY_SKIPS_USED, 0) else 0
        prefs.edit().putLong(KEY_SKIP_DAY, today).putInt(KEY_SKIPS_USED, used + 1).apply()
        return true
    }

    /** The app that may be used without the gate until another app is opened ([GateRules.passAfter]). */
    fun gatePass(context: Context): String? = prefs(context).getString(KEY_GATE_PASS, null)

    /** Written with commit(): the monitor reads it right after the gate closes. */
    fun setGatePass(context: Context, packageName: String?) {
        prefs(context).edit().putString(KEY_GATE_PASS, packageName).commit()
    }

    /** Whether a snooze also opens the gate. Off by default: snoozing must not be the easy way in. */
    fun snoozeLiftsGate(context: Context): Boolean = prefs(context).getBoolean(KEY_SNOOZE_LIFTS_GATE, false)

    fun setSnoozeLiftsGate(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_SNOOZE_LIFTS_GATE, enabled).apply()

    /** Whether anything needs the phone's use to be watched ([UsageMonitorService]). */
    fun monitorWanted(context: Context): Boolean = nudgesEnabled(context) || gateEnabled(context)

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

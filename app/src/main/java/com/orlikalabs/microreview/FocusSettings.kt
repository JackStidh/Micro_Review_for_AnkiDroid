package com.orlikalabs.microreview

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject

/**
 * User choices about cards while the phone is in use: nudges (a card every so often, with an interval per app),
 * the app gate (cards before a chosen app opens), "Pass / Fail only", and when cards must stay away (snooze,
 * quiet hours, Bluetooth, Do Not Disturb, calls). The gate's pass and the day's emergency skips are kept here too.
 */
object FocusSettings {
    private const val PREFS = "com.orlikalabs.microreview.focus"

    private const val KEY_NUDGES = "nudges_enabled"
    private const val KEY_DEFAULT_INTERVAL = "default_interval_minutes"
    private const val KEY_APP_INTERVALS = "app_intervals"
    private const val KEY_PASS_FAIL = "pass_fail_only"
    private const val KEY_NOTE_FIELDS = "show_note_fields"
    private const val KEY_PAUSE_BLUETOOTH = "pause_on_bluetooth"
    private const val KEY_PC_CODE = "pc_pairing_code"
    private const val KEY_PAUSE_PC = "pause_when_pc_in_use"

    private const val KEY_SNOOZE_UNTIL = "snooze_until"
    private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    private const val KEY_MORNING = "morning_minute"
    private const val KEY_QUIET_HOURS = "quiet_hours"
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

    /** The look of the app ([com.orlikalabs.microreview.ui.aero.AeroThemes]): the theme shown (or locked), its name. */
    private const val KEY_THEME = "theme"

    /** Locked: always [KEY_THEME]. Otherwise each screen shuffles among the themes not in [KEY_THEMES_OFF]. */
    private const val KEY_THEME_LOCKED = "theme_locked"
    private const val KEY_THEMES_OFF = "themes_off"

    const val DEFAULT_INTERVAL_MINUTES = 20
    /** How long an untouched card notification stays before it is withdrawn, so its time doesn't count. */
    const val NUDGE_LIFETIME_SECONDS = 30
    const val DEFAULT_SNOOZE_MINUTES = 30
    const val DEFAULT_MORNING_MINUTE = 7 * 60

    // Nudges

    fun nudgesEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_NUDGES, false)

    fun setNudgesEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_NUDGES, enabled).apply()

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


    /** "Pass / Fail only": Again and Good, relabeled; Hard and Easy are never offered. */
    fun passFailOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_PASS_FAIL, true)

    fun setPassFailOnly(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_PASS_FAIL, enabled).apply()

    /** Under the answer of the full-screen card, the note's fields the card didn't show ([NoteFields]). */
    fun showNoteFields(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTE_FIELDS, true)

    fun setShowNoteFields(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_NOTE_FIELDS, enabled).apply()

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

    fun pauseOnDoNotDisturb(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSE_DND, true)

    fun setPauseOnDoNotDisturb(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_PAUSE_DND, enabled).apply()

    /** Everything paused (cards, gate, scheduled reviews) while a Bluetooth device is connected ([BluetoothState]). */
    fun pauseOnBluetooth(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSE_BLUETOOTH, true)

    fun setPauseOnBluetooth(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_PAUSE_BLUETOOTH, enabled).apply()

    /** The pairing code of Micro Review Desktop ([PcPairing.normalize]d), or null if not paired. */
    fun pcPairingCode(context: Context): String? = prefs(context).getString(KEY_PC_CODE, null)?.let(PcPairing::normalize)

    fun setPcPairingCode(context: Context, code: String?) = prefs(context).edit().putString(KEY_PC_CODE, code).apply()

    /** Everything paused for 5 minutes after the paired PC's last use ([PcActivity]). */
    fun pauseWhenPcInUse(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSE_PC, true)

    fun setPauseWhenPcInUse(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_PAUSE_PC, enabled).apply()

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
    /** The monitor runs for the nudges, the gate, Move's prompts ([MoveStore.promptsOn]) and the lifestyle ones ([LifeSettings]). */
    fun monitorWanted(context: Context): Boolean =
        nudgesEnabled(context) || gateEnabled(context) || MoveStore.promptsOn(context) || LifeSettings.promptsOn(context)

    // Look

    fun themeName(context: Context): String? = prefs(context).getString(KEY_THEME, null)

    fun setThemeName(context: Context, name: String) = prefs(context).edit().putString(KEY_THEME, name).apply()

    fun themeLocked(context: Context): Boolean = prefs(context).getBoolean(KEY_THEME_LOCKED, false)

    fun setThemeLocked(context: Context, locked: Boolean) = prefs(context).edit().putBoolean(KEY_THEME_LOCKED, locked).apply()

    /** The themes left out of the shuffle. */
    fun themesOff(context: Context): Set<String> = ThemeShuffle.decode(prefs(context).getString(KEY_THEMES_OFF, null))

    fun setThemesOff(context: Context, off: Set<String>) =
        prefs(context).edit().putString(KEY_THEMES_OFF, ThemeShuffle.encode(off)).apply()

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

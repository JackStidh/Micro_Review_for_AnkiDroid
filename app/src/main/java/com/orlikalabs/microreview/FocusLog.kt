package com.orlikalabs.microreview

import android.content.Context
import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * What the focus features did, in a short log kept on the phone, shown and copied from the diagnostics of the focus
 * settings: what the monitor saw, why a card or the gate came or didn't. Also sent to logcat (tag "Focus").
 */
object FocusLog {
    private const val TAG = "Focus"
    private const val PREFS = "com.orlikalabs.microreview.focus_log"
    private const val KEY_LINES = "lines"
    private const val MAX_LINES = 150
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val lines = ArrayDeque<String>()
    private var loaded = false

    fun i(context: Context, message: String) {
        Log.i(TAG, message)
        val line = "${LocalTime.now().format(TIME)} $message"
        synchronized(this) {
            load(context)
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            prefs(context).edit().putString(KEY_LINES, lines.joinToString("\n")).apply()
        }
    }

    /** The log, oldest first. */
    fun lines(context: Context): List<String> = synchronized(this) {
        load(context)
        lines.toList()
    }

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).getString(KEY_LINES, null)?.split('\n')?.filter { it.isNotEmpty() }?.forEach(lines::addLast)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** What the monitor ([UsageMonitorService]) is doing now, for the diagnostics. Same process. */
object MonitorStatus {
    @Volatile
    var running = false

    /** Whether it watches: the screen is on. */
    @Volatile
    var polling = false

    @Volatile
    var foreground: String? = null

    /** Time until the next card in [foreground], null for none. */
    @Volatile
    var nextCardMs: Long? = null

    @Volatile
    var pauseReason: Interruptions.Reason? = null

    /** When it last looked (System.currentTimeMillis()). */
    @Volatile
    var lastStepAt = 0L
}

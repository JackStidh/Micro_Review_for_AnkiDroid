package com.orlikalabs.microreview

import android.content.Context

/** Whether the lifestyle prompts run ([LifePrompts]): off until turned on (a new user gets no nudges unasked), in `life` (backed up). */
object LifeSettings {
    private const val PREFS = "com.orlikalabs.microreview.life"
    private const val KEY_PROMPTS = "prompts"

    fun promptsOn(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PROMPTS, false)

    fun setPromptsOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PROMPTS, on).apply()
        UsageMonitorService.sync(context.applicationContext)
    }
}

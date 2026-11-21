package com.orlikalabs.microreview

import kotlin.random.Random

/**
 * Pure part of the theme shuffle ([com.orlikalabs.microreview.ui.aero.AeroThemes]): unless the theme is locked, each
 * time the app comes into view or a card screen opens, another theme, at random among the ones ticked.
 */
object ThemeShuffle {
    /** Back in the app sooner than this (a rotation, a moment away) isn't a new visit: the theme stays. */
    const val VISIT_GAP_MS = 2_000L

    /** The next theme: one of the ticked [ids] (all if none are), not [last] when another one is ticked. */
    fun next(ids: List<String>, off: Set<String>, last: String?, random: Random = Random.Default): String {
        val ticked = ids.filter { it !in off }.ifEmpty { ids }
        val fresh = ticked.filter { it != last }.ifEmpty { ticked }
        return fresh[random.nextInt(fresh.size)]
    }

    /** [off] with [id] ticked again, or unticked unless it's the last one ticked. */
    fun toggled(ids: List<String>, off: Set<String>, id: String): Set<String> = when {
        id in off -> off - id
        ids.count { it !in off && it != id } == 0 -> off
        else -> off + id
    }

    /** Whether the app coming into view at [now] is a new visit: first time, or hidden since [hiddenAt] long enough. */
    fun isNewVisit(hiddenAt: Long, now: Long): Boolean = hiddenAt < 0 || now - hiddenAt >= VISIT_GAP_MS || now < hiddenAt

    /** "yoru,torii" and back. Unknown names are kept: harmless, and a theme may come back. */
    fun encode(off: Set<String>): String = off.sorted().joinToString(",")

    fun decode(text: String?): Set<String> = text.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

package com.orlikalabs.microreview

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The to-do list: tasks with an optional due time, a star for the day's few that matter, and reminders when one is
 * due or when unfinished ones have waited. Pure part; storage in [LifeStore].
 */
object Tasks {
    /** Times in epoch ms; [due] null: whenever. [starred]: one of today's few. */
    data class Task(
        val id: Long,
        val title: String,
        val created: Long,
        val due: Long? = null,
        val done: Long? = null,
        val starred: Boolean = false,
        val remindedAt: Long? = null,
        val snoozedUntil: Long? = null,
    ) {
        val isOpen: Boolean get() = done == null
    }

    /** A due task is reminded at most this often, and an undated one waits this long after it was added or reminded. */
    const val DUE_GAP_MS = 2 * 3600_000L
    const val NUDGE_GAP_MS = 4 * 3600_000L

    /** Unfinished tasks are nudged between these hours only. */
    const val NUDGE_FROM_HOUR = 10
    const val NUDGE_UNTIL_HOUR = 21

    /** Done tasks are kept this long, for the record. */
    const val DONE_KEPT_MS = 14 * 24 * 3600_000L

    fun open(tasks: List<Task>): List<Task> =
        tasks.filter { it.isOpen }.sortedWith(compareByDescending<Task> { it.starred }.thenBy { it.due ?: Long.MAX_VALUE }.thenBy { it.created })

    fun overdue(tasks: List<Task>, now: Long): List<Task> = tasks.filter { it.isOpen && it.due != null && it.due <= now }

    /**
     * The task to remind now: a due one (soonest first) not reminded for [DUE_GAP_MS]; otherwise, during the day, the
     * first open one (starred first) that has waited [NUDGE_GAP_MS] since it was added or last reminded. Snoozed ones
     * wait.
     */
    fun due(tasks: List<Task>, now: Long, hour: Int): Task? {
        val ready = tasks.filter { it.isOpen && (it.snoozedUntil ?: 0L) <= now }
        ready.filter { it.due != null && it.due <= now && now - (it.remindedAt ?: 0L) >= DUE_GAP_MS }
            .minByOrNull { it.due!! }?.let { return it }
        if (hour !in NUDGE_FROM_HOUR until NUDGE_UNTIL_HOUR) return null
        return open(ready).firstOrNull { it.due == null && now - (it.remindedAt ?: it.created) >= NUDGE_GAP_MS }
    }

    fun update(tasks: List<Task>, id: Long, change: (Task) -> Task): List<Task> = tasks.map { if (it.id == id) change(it) else it }

    /** Done ones older than [DONE_KEPT_MS] dropped. */
    fun tidy(tasks: List<Task>, now: Long): List<Task> = tasks.filter { it.done == null || now - it.done < DONE_KEPT_MS }

    // Storage: one task per line, fields separated by "|", the title URL-encoded.

    fun encode(tasks: List<Task>): String = tasks.joinToString("\n") { t ->
        listOf(
            t.id, t.created, t.due ?: "", t.done ?: "", if (t.starred) 1 else 0, t.remindedAt ?: "", t.snoozedUntil ?: "",
            URLEncoder.encode(t.title, "UTF-8"),
        ).joinToString("|")
    }

    fun decode(text: String?): List<Task> = text.orEmpty().lines().mapNotNull { line ->
        val f = line.split('|')
        if (f.size < 8) return@mapNotNull null
        val id = f[0].toLongOrNull() ?: return@mapNotNull null
        Task(
            id = id,
            title = runCatching { URLDecoder.decode(f[7], "UTF-8") }.getOrDefault(f[7]),
            created = f[1].toLongOrNull() ?: 0L,
            due = f[2].toLongOrNull(),
            done = f[3].toLongOrNull(),
            starred = f[4] == "1",
            remindedAt = f[5].toLongOrNull(),
            snoozedUntil = f[6].toLongOrNull(),
        )
    }
}

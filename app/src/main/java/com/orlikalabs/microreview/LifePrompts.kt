package com.orlikalabs.microreview

import java.time.LocalTime

/** A full-screen prompt of the lifestyle side, shown in the card's window ([CardOverlay.requestLife]). */
sealed class LifePrompt {
    /** Move's morning check-in or evening routine. */
    data class Move(val prompt: MoveStore.Prompt) : LifePrompt()

    /** Breaking the spell: [minutes] in the watched apps (or, [session], on the phone at all). */
    data class Spell(val app: String?, val minutes: Int, val strikes: Int, val session: Boolean) : LifePrompt()

    /** Past bedtime, still on the phone. */
    data class Bedtime(val strikes: Int) : LifePrompt()

    /** The soft nudges together, so they come as one: doses and water, tasks, getting up. */
    data class Nudge(val habits: List<Habits.Reminder>, val tasks: List<Tasks.Task>, val inactive: Boolean) : LifePrompt()
}

/**
 * Which prompt the monitor shows now ([UsageMonitorService.lifeStep]), first that applies:
 * 1. the spell breaker and the long session (in the moment; held back only by a snooze, a call or a Bluetooth pause);
 * 2. bedtime (the same);
 * 3. Move's morning check-in;
 * 4. the soft nudges, bundled, at most every 20 minutes ([GuardStore.SOFT_GAP_MS]; also held back by Do Not Disturb);
 * 5. Move's evening routine.
 */
object LifePrompts {
    private val HARD_HOLD = setOf(Interruptions.Reason.SNOOZED, Interruptions.Reason.IN_CALL, Interruptions.Reason.BLUETOOTH)
    private val SOFT_HOLD = HARD_HOLD + Interruptions.Reason.DO_NOT_DISTURB

    fun next(context: android.content.Context, app: String?, pause: Interruptions.Reason?, now: Long = System.currentTimeMillis()): LifePrompt? {
        if (pause in HARD_HOLD) return null
        if (!LifeSettings.promptsOn(context)) {
            // Only Move's own prompts, which have their own switch.
            if (pause in SOFT_HOLD) return null
            return MoveStore.duePrompt(context, now)?.let { LifePrompt.Move(it) }
        }
        val stretch = GuardStore.stretch
        val inWatched = app != null && app in GuardStore.watched(context)
        if (Guard.spellDue(stretch, inWatched, now, GuardStore.spellMinutes(context))) {
            return LifePrompt.Spell(app, Guard.minutes(stretch, now), stretch.strikes, session = false)
        }
        if (Guard.sessionDue(GuardStore.useStartedAt, now, GuardStore.sessionMinutes(context), GuardStore.sessionLaterUntil(context))) {
            return LifePrompt.Spell(app, ((now - GuardStore.useStartedAt) / 60_000L).toInt(), 0, session = true)
        }
        if (GuardStore.bedtimeDue(context, now)) return LifePrompt.Bedtime(GuardStore.bedtimeStrikes(context))
        if (pause in SOFT_HOLD) return null
        val move = MoveStore.duePrompt(context, now)
        if (move == MoveStore.Prompt.MORNING) return LifePrompt.Move(move)
        if (GuardStore.softAllowed(context, now)) nudge(context, now)?.let { return it }
        if (move == MoveStore.Prompt.EVENING) return LifePrompt.Move(move)
        return null
    }

    /** Everything soft that's due: every habit reminder, the task due (and one more), the inactivity nudge. */
    fun nudge(context: android.content.Context, now: Long = System.currentTimeMillis()): LifePrompt.Nudge? {
        val time = LocalTime.now()
        val habits = LifeStore.habits(context)
        val day = LifeStore.habitToday(context)
        val reminders = mutableListOf<Habits.Reminder>()
        val seen = mutableSetOf<String>()
        while (true) {
            val next = Habits.due(habits.filter { it.id !in seen }, day, time.hour * 60 + time.minute, LifeStore.remindedKeys(context), LifeStore.lastPaceTimes(context), now)
                ?: break
            reminders += next
            seen += next.habit.id
        }
        val tasks = LifeStore.tasks(context)
        val due = Tasks.due(tasks, now, time.hour)?.let { first -> listOf(first) + Tasks.overdue(tasks, now).filter { it.id != first.id }.take(1) }.orEmpty()
        val inactive = GuardStore.inactive(context, now)
        if (reminders.isEmpty() && due.isEmpty() && !inactive) return null
        return LifePrompt.Nudge(reminders, due, inactive)
    }

    /** [nudge] was shown: each part reminded, so it isn't shown again right away. */
    fun shown(context: android.content.Context, prompt: LifePrompt, now: Long = System.currentTimeMillis()) {
        when (prompt) {
            is LifePrompt.Nudge -> {
                GuardStore.softShown(context, now)
                prompt.habits.forEach { LifeStore.reminded(context, it, now) }
                prompt.tasks.forEach { task -> LifeStore.updateTask(context, task.id) { it.copy(remindedAt = now) } }
                if (prompt.inactive) GuardStore.inactiveNudged(context, now)
            }
            is LifePrompt.Move -> MoveStore.prompted(context, prompt.prompt)
            else -> Unit
        }
    }
}

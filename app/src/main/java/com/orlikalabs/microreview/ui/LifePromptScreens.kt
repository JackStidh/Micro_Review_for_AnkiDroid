package com.orlikalabs.microreview.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orlikalabs.microreview.Habits
import com.orlikalabs.microreview.LifePrompt
import com.orlikalabs.microreview.MovePlan
import com.orlikalabs.microreview.MoveText
import com.orlikalabs.microreview.R
import com.orlikalabs.microreview.Tasks
import com.orlikalabs.microreview.ui.aero.AeroBackground
import com.orlikalabs.microreview.ui.aero.AeroText
import com.orlikalabs.microreview.ui.aero.GelButton
import com.orlikalabs.microreview.ui.aero.GelTone
import com.orlikalabs.microreview.ui.aero.GlassPanel
import com.orlikalabs.microreview.ui.aero.LocalAero
import com.orlikalabs.microreview.ui.aero.LocalAeroMotion
import com.orlikalabs.microreview.ui.aero.Orb
import com.orlikalabs.microreview.ui.aero.Pill
import com.orlikalabs.microreview.ui.aero.Seal
import com.orlikalabs.microreview.ui.aero.orbInk
import com.orlikalabs.microreview.ui.aero.titleShadow
import kotlinx.coroutines.launch

interface SpellActions {
    fun close()
    fun more()
    fun onPurpose()
    fun water()
}

/**
 * Breaking the spell: how long it's been, a breathing orb to come back to the room, a desk exercise instead, and the
 * way out (one tap) or back in ("5 more minutes", held longer each time: [holdMs]).
 */
@Composable
fun SpellScreen(prompt: LifePrompt.Spell, appLabel: String?, deskMove: MovePlan.Move, holdMs: Long, actions: SpellActions) {
    PromptFrame(
        kanji = stringResource(R.string.kanji_wake),
        title = if (prompt.session) stringResource(R.string.spell_session_title, prompt.minutes) else stringResource(R.string.spell_title, prompt.minutes, appLabel ?: stringResource(R.string.spell_these_apps)),
        subtitle = stringResource(if (prompt.session) R.string.spell_session_detail else R.string.spell_detail),
        buttons = {
            GelButton(
                stringResource(if (prompt.session) R.string.spell_break else R.string.spell_close),
                actions::close,
                Modifier.fillMaxWidth(),
                tone = GelTone.PASS,
                glyph = stringResource(R.string.kanji_wake),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HoldGel(
                    stringResource(if (prompt.session) R.string.spell_more_session else R.string.spell_more),
                    holdMs,
                    actions::more,
                    Modifier.weight(1f),
                    tone = GelTone.GOLD,
                )
                if (!prompt.session) {
                    HoldGel(stringResource(R.string.spell_on_purpose), 3_000L, actions::onPurpose, Modifier.weight(1f), tone = GelTone.NEUTRAL)
                }
            }
            if (holdMs > 0) {
                Text(stringResource(R.string.spell_hold, (holdMs / 1000).toInt()), style = AeroText.soft(12), modifier = Modifier.padding(top = 6.dp))
            }
        },
    ) {
        Breathing()
        GlassPanel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.spell_instead), style = AeroText.title(16))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoveFigure(deskMove, Modifier.size(width = 88.dp, height = 64.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(MoveText.name(deskMove)), style = AeroText.body(15).copy(fontWeight = FontWeight.SemiBold))
                    Text(stringResource(MoveText.dose(deskMove)), style = AeroText.soft(13))
                }
            }
            Spacer(Modifier.height(10.dp))
            GelButton(stringResource(R.string.spell_water), actions::water, Modifier.fillMaxWidth(), tone = GelTone.ACCENT, compact = true, glyph = "水")
        }
    }
}

/** An orb that swells for 4 seconds and settles for 6: breathe with it. */
@Composable
private fun Breathing() {
    val p = LocalAero.current
    val animate = LocalAeroMotion.current
    val transition = rememberInfiniteTransition(label = "breath")
    val size by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath size",
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Orb(null, Modifier.scale(if (animate) size else 0.86f), size = 150.dp, tone = GelTone.ACCENT) {
                Text("息", style = TextStyle(color = orbInk(GelTone.ACCENT), fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = p.headings))
            }
        }
        Text(stringResource(R.string.spell_breathe), style = AeroText.soft(14), textAlign = TextAlign.Center)
    }
}

interface BedtimeActions {
    fun goingToBed()
    fun later()
    fun notTonight()
}

@Composable
fun BedtimeScreen(prompt: LifePrompt.Bedtime, bedtime: String, holdMs: Long, actions: BedtimeActions) {
    PromptFrame(
        kanji = stringResource(R.string.kanji_sleep),
        title = stringResource(R.string.bedtime_title),
        subtitle = stringResource(R.string.bedtime_detail, bedtime),
        buttons = {
            GelButton(stringResource(R.string.bedtime_going), actions::goingToBed, Modifier.fillMaxWidth(), tone = GelTone.PASS, glyph = stringResource(R.string.kanji_sleep))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HoldGel(stringResource(R.string.bedtime_later), holdMs, actions::later, Modifier.weight(1f), tone = GelTone.GOLD)
                GelButton(stringResource(R.string.bedtime_not_tonight), actions::notTonight, Modifier.weight(1f), tone = GelTone.NEUTRAL, compact = true)
            }
        },
    ) {
        Breathing()
        GlassPanel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bedtime_tips), style = AeroText.body(15))
        }
    }
}

interface NudgeActions {
    fun habit(reminder: Habits.Reminder)
    fun taskDone(task: Tasks.Task)
    fun taskTomorrow(task: Tasks.Task)
    fun walked()
    fun allGood()
    fun later()
}

/** The soft nudges as one card: each with its own quick tap, then "All good" or "Later". */
@Composable
fun NudgeScreen(prompt: LifePrompt.Nudge, habitName: (Habits.Habit) -> String, habitCount: (Habits.Habit) -> Int, stepsToday: Long?, actions: NudgeActions) {
    val handled = remember { mutableStateOf(setOf<String>()) }
    fun handle(key: String) {
        handled.value = handled.value + key
    }
    PromptFrame(
        kanji = stringResource(R.string.kanji_bell),
        title = stringResource(R.string.nudge_title),
        subtitle = stringResource(R.string.nudge_detail),
        buttons = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GelButton(stringResource(R.string.nudge_all_good), actions::allGood, Modifier.weight(1f), tone = GelTone.PASS)
                GelButton(stringResource(R.string.nudge_later), actions::later, Modifier.weight(1f), tone = GelTone.GOLD, compact = true)
            }
        },
    ) {
        GlassPanel(Modifier.fillMaxWidth()) {
            prompt.habits.forEach { reminder ->
                val key = "h:${reminder.habit.id}"
                val water = reminder.doseTime == null
                NudgeRow(
                    glyph = reminder.habit.glyph,
                    title = habitName(reminder.habit),
                    detail = if (water) {
                        stringResource(R.string.nudge_behind, habitCount(reminder.habit), reminder.habit.goal)
                    } else {
                        stringResource(R.string.nudge_dose_at, "%d:%02d".format(reminder.doseTime!! / 60, reminder.doseTime % 60))
                    },
                    done = key in handled.value,
                ) {
                    GelButton(stringResource(if (water) R.string.nudge_cup else R.string.nudge_taken), {
                        actions.habit(reminder)
                        if (!water) handle(key)
                    }, tone = GelTone.ACCENT, compact = true)
                }
            }
            prompt.tasks.forEach { task ->
                val key = "t:${task.id}"
                NudgeRow(glyph = stringResource(R.string.kanji_tasks), title = task.title, detail = stringResource(R.string.nudge_task), done = key in handled.value) {
                    Column(horizontalAlignment = Alignment.End) {
                        GelButton(stringResource(R.string.life_task_done), {
                            actions.taskDone(task)
                            handle(key)
                        }, tone = GelTone.PASS, compact = true)
                        Spacer(Modifier.height(4.dp))
                        GelButton(stringResource(R.string.nudge_tomorrow), {
                            actions.taskTomorrow(task)
                            handle(key)
                        }, tone = GelTone.NEUTRAL, compact = true)
                    }
                }
            }
            if (prompt.inactive) {
                NudgeRow(
                    glyph = stringResource(R.string.kanji_walk),
                    title = stringResource(R.string.nudge_inactive),
                    detail = stepsToday?.let { stringResource(R.string.nudge_steps, it.toInt()) } ?: "",
                    done = "walk" in handled.value,
                ) {
                    GelButton(stringResource(R.string.nudge_walked), {
                        actions.walked()
                        handle("walk")
                    }, tone = GelTone.ACCENT, compact = true)
                }
            }
        }
    }
}

@Composable
private fun NudgeRow(glyph: String, title: String, detail: String, done: Boolean, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Seal(glyph, 40.dp, tone = if (done) GelTone.PASS else GelTone.ACCENT)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AeroText.title(16))
            if (detail.isNotEmpty()) Text(detail, style = AeroText.soft(13))
        }
        Spacer(Modifier.width(8.dp))
        if (done) Pill(stringResource(R.string.move_done), color = LocalAero.current.pass) else action()
    }
}

/** The common frame of these prompts: a seal and a title, what to read scrolling, the buttons fixed at the bottom. */
@Composable
private fun PromptFrame(
    kanji: String,
    title: String,
    subtitle: String,
    buttons: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    AeroBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = insets.calculateTopPadding() + 18.dp, bottom = insets.calculateBottomPadding() + 14.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Seal(kanji, 58.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(title, style = AeroText.title(24).copy(fontWeight = FontWeight.ExtraBold, shadow = titleShadow(LocalAero.current)))
                        Text(subtitle, style = AeroText.soft(14))
                    }
                }
                content()
            }
            Spacer(Modifier.height(12.dp))
            buttons()
        }
    }
}

/**
 * A gel that must be held for [holdMs] to act: it fills while held, empties if let go early. Instant when [holdMs] is
 * 0. Friction for what shouldn't be too easy.
 */
@Composable
fun HoldGel(text: String, holdMs: Long, onDone: () -> Unit, modifier: Modifier = Modifier, tone: GelTone = GelTone.GOLD) {
    if (holdMs <= 0L) {
        GelButton(text, onDone, modifier, tone = tone, compact = true)
        return
    }
    val charge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var fired by remember { mutableStateOf(false) }
    Box(modifier) {
        GelButton(text, {}, Modifier.fillMaxWidth(), tone = tone, compact = true, charge = charge.value.coerceAtMost(0.999f))
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(holdMs) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val job = scope.launch {
                            charge.animateTo(1f, tween(holdMs.toInt(), easing = LinearEasing))
                            if (!fired) {
                                fired = true
                                onDone()
                            }
                        }
                        waitForUpOrCancellation()
                        if (charge.value < 1f) {
                            job.cancel()
                            scope.launch { charge.animateTo(0f, tween(250)) }
                        }
                    }
                },
        )
    }
}

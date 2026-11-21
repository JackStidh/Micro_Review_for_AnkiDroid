package com.orlikalabs.microreview.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.orlikalabs.microreview.Habits
import com.orlikalabs.microreview.LifeText
import com.orlikalabs.microreview.Progress
import com.orlikalabs.microreview.R
import com.orlikalabs.microreview.Tasks
import com.orlikalabs.microreview.ui.aero.AeroCheck
import com.orlikalabs.microreview.ui.aero.AeroText
import com.orlikalabs.microreview.ui.aero.Beads
import com.orlikalabs.microreview.ui.aero.GelButton
import com.orlikalabs.microreview.ui.aero.GelTone
import com.orlikalabs.microreview.ui.aero.GlassPanel
import com.orlikalabs.microreview.ui.aero.LocalAero
import com.orlikalabs.microreview.ui.aero.Orb
import com.orlikalabs.microreview.ui.aero.Pill
import com.orlikalabs.microreview.ui.aero.Seal
import com.orlikalabs.microreview.ui.aero.orbInk
import com.orlikalabs.microreview.ui.aero.titleShadow
import kotlin.math.cos
import kotlin.math.sin

/** The day at a glance on the home screen: the rank, the habits to tap, the next task, Move. */
data class TodaySummary(
    val xp: Int = 0,
    val xpToday: Int = 0,
    val habits: List<Pair<Habits.Habit, Int>> = emptyList(),
    val habitNames: Map<String, String> = emptyMap(),
    val nextTask: Tasks.Task? = null,
    val tasksLeft: Int = 0,
    val move: MoveSummary = MoveSummary(),
)

interface TodayActions {
    fun openLife(tab: Int)
    fun tapHabit(habit: Habits.Habit, delta: Int)
    fun completeTask(id: Long)
    fun openMove()
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TodayBoard(today: TodaySummary, actions: TodayActions) {
    val context = LocalContext.current
    val p = LocalAero.current
    val rank = Progress.rank(today.xp)
    GlassPanel(Modifier.fillMaxWidth(), corner = 30.dp, padding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { actions.openLife(2) }, verticalAlignment = Alignment.CenterVertically) {
            Seal(rank.kanji, 50.dp, tone = GelTone.GOLD)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_today), style = AeroText.title(19), modifier = Modifier.weight(1f))
                    Pill(stringResource(R.string.life_xp_today, today.xpToday), color = p.gold)
                }
                Text(stringResource(R.string.life_rank_xp, LifeText.rankName(context, rank), today.xp), style = AeroText.soft(12))
                Spacer(Modifier.height(6.dp))
                XpBar(Progress.toNext(today.xp))
            }
        }
        if (today.habits.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                today.habits.forEach { (habit, count) ->
                    val done = count >= habit.goal
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                        Box(
                            Modifier.size(50.dp).clip(CircleShape).combinedClickable(onClick = { actions.tapHabit(habit, 1) }, onLongClick = { actions.tapHabit(habit, -1) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Orb(null, size = 50.dp, tone = if (done) GelTone.PASS else GelTone.ACCENT) {
                                Text(habit.glyph, style = TextStyle(color = orbInk(if (done) GelTone.PASS else GelTone.ACCENT), fontSize = 20.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                        Text(
                            if (done) "✓" else "$count/${habit.goal}",
                            style = AeroText.title(13).copy(color = if (done) p.pass else p.ink),
                        )
                        Text(today.habitNames[habit.id].orEmpty(), style = AeroText.soft(10), maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { actions.openLife(1) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val task = today.nextTask
            if (task != null) {
                AeroCheck(false, onClick = { actions.completeTask(task.id) })
                Spacer(Modifier.width(10.dp))
                Text(task.title, style = AeroText.body(15), maxLines = 1, modifier = Modifier.weight(1f))
            } else {
                Seal(stringResource(R.string.kanji_tasks), 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.home_tasks_none), style = AeroText.soft(14), modifier = Modifier.weight(1f))
            }
            Pill(if (today.tasksLeft > 0) stringResource(R.string.home_tasks_left, today.tasksLeft) else stringResource(R.string.life_add_task), color = p.accent)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = actions::openMove).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Seal(stringResource(R.string.kanji_move), 30.dp, tone = if (today.move.setUp && today.move.doneToday >= 3) GelTone.PASS else GelTone.ACCENT)
            Spacer(Modifier.width(10.dp))
            Text(
                if (today.move.setUp) stringResource(R.string.move_home_detail, today.move.doneToday) else stringResource(R.string.move_home_off),
                style = AeroText.body(14),
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            if (today.move.setUp) Beads(today.move.doneToday, 3)
        }
    }
}

/**
 * A rank reached or a seal unlocked: the seal big and gold, sparks flying out, then "Nice". [what] is
 * "rank:<index>" or "ach:<id>" ([com.orlikalabs.microreview.LifeStore.takeCelebrations]).
 */
@Composable
fun Celebration(what: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val p = LocalAero.current
    val (kanji, title, detail) = when {
        what.startsWith("rank:") -> {
            val rank = Progress.RANKS[what.substringAfter(':').toIntOrNull()?.coerceIn(0, Progress.RANKS.lastIndex) ?: 0]
            Triple(rank.kanji, stringResource(R.string.life_rank_up), stringResource(R.string.life_rank_up_detail, LifeText.rankName(context, rank)))
        }
        else -> {
            val id = what.substringAfter(':')
            val achievement = Progress.ACHIEVEMENTS.firstOrNull { it.id == id }
            Triple(achievement?.kanji ?: "印", stringResource(R.string.life_unlocked), stringResource(LifeText.achievement(id)) + ": " + stringResource(LifeText.achievementDetail(id)))
        }
    }
    val burst = remember(what) { Animatable(0f) }
    LaunchedEffect(what) { burst.animateTo(1f, tween(1_600)) }
    Dialog(onDismissRequest = onDone) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(320.dp)) {
                val t = burst.value
                val center = Offset(size.width / 2, size.height / 2)
                for (i in 0 until 28) {
                    val angle = i * (2 * Math.PI / 28) + i * 0.37
                    val distance = size.minDimension * 0.5f * t * (0.55f + (i % 5) * 0.1f)
                    val c = Offset(center.x + (cos(angle) * distance).toFloat(), center.y + (sin(angle) * distance).toFloat())
                    val color = if (i % 3 == 0) p.gold else if (i % 3 == 1) p.accent else Color.White
                    drawCircle(color.copy(alpha = (1f - t) * 0.9f + 0.1f), radius = (6f - 4f * t) * density, center = c)
                }
            }
            GlassPanel(Modifier.padding(24.dp), corner = 32.dp, solid = true, padding = PaddingValues(24.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Seal(kanji, 96.dp, tone = GelTone.GOLD)
                    Spacer(Modifier.height(14.dp))
                    Text(title, style = TextStyle(color = p.ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = p.headings, shadow = titleShadow(p)))
                    Spacer(Modifier.height(6.dp))
                    Text(detail, style = AeroText.body(16), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    GelButton(stringResource(R.string.life_nice), onDone, Modifier.fillMaxWidth(), tone = GelTone.PASS)
                }
            }
        }
    }
}

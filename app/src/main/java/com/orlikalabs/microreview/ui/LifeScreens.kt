package com.orlikalabs.microreview.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orlikalabs.microreview.GuardStore
import com.orlikalabs.microreview.Habits
import com.orlikalabs.microreview.LifeText
import com.orlikalabs.microreview.Progress
import com.orlikalabs.microreview.R
import com.orlikalabs.microreview.Tasks
import com.orlikalabs.microreview.ui.aero.AeroBackground
import com.orlikalabs.microreview.ui.aero.AeroCheck
import com.orlikalabs.microreview.ui.aero.AeroDialog
import com.orlikalabs.microreview.ui.aero.AeroSwitch
import com.orlikalabs.microreview.ui.aero.AeroText
import com.orlikalabs.microreview.ui.aero.Beads
import com.orlikalabs.microreview.ui.aero.ChoiceDialog
import com.orlikalabs.microreview.ui.aero.GelButton
import com.orlikalabs.microreview.ui.aero.GelTone
import com.orlikalabs.microreview.ui.aero.GlassPanel
import com.orlikalabs.microreview.ui.aero.LocalAero
import com.orlikalabs.microreview.ui.aero.Orb
import com.orlikalabs.microreview.ui.aero.Pill
import com.orlikalabs.microreview.ui.aero.Seal
import com.orlikalabs.microreview.ui.aero.SectionTitle
import com.orlikalabs.microreview.ui.aero.halo
import com.orlikalabs.microreview.ui.aero.orbInk
import com.orlikalabs.microreview.ui.aero.titleShadow
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** What the Life screen shows. */
data class LifeState(
    val promptsOn: Boolean = false,
    val habits: List<Habits.Habit> = emptyList(),
    val habitNames: Map<String, String> = emptyMap(),
    val habitDays: List<Habits.Day> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val tasks: List<Tasks.Task> = emptyList(),
    val xp: Int = 0,
    val ledger: Progress.Ledger = Progress.Ledger(),
    val unlocked: Set<String> = emptySet(),
    val spellMinutes: Int = GuardStore.DEFAULT_SPELL_MINUTES,
    val watched: Set<String> = emptySet(),
    val sessionMinutes: Int = GuardStore.DEFAULT_SESSION_MINUTES,
    val inactivityOn: Boolean = true,
    val canCountSteps: Boolean = false,
    val hasStepSensor: Boolean = false,
    val stepsToday: Long? = null,
    val bedtime: Int = -1,
    val sleepMs: Long? = null,
    val now: Long = System.currentTimeMillis(),
) {
    val day: Habits.Day? get() = habitDays.firstOrNull { it.date == today }
}

interface LifeActions {
    fun back()
    fun setPromptsOn(on: Boolean)
    fun tapHabit(habit: Habits.Habit, delta: Int)
    fun saveHabit(original: Habits.Habit?, habit: Habits.Habit)
    fun deleteHabit(habit: Habits.Habit)
    fun addTask(title: String, due: Long?, starred: Boolean)
    fun completeTask(id: Long)
    fun reopenTask(id: Long)
    fun deleteTask(id: Long)
    fun starTask(id: Long)
    fun setSpellMinutes(minutes: Int)
    fun setWatched(apps: Set<String>)
    fun apps(): List<LauncherApp>?
    fun setSessionMinutes(minutes: Int)
    fun setInactivityOn(on: Boolean)
    fun allowSteps()
    fun setBedtime(minutes: Int)
}

private fun clock(minutes: Int) = "%d:%02d".format(minutes / 60, minutes % 60)

@Composable
fun LifeScreen(state: LifeState, actions: LifeActions, initialTab: Int = 0, appsLoaded: Int = 0) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var editing by remember { mutableStateOf<Habits.Habit?>(null) }
    var adding by remember { mutableStateOf(false) }
    AeroBackground(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { LifeHeader(actions::back) }
            item(key = "tabs") { Tabs(tab) { tab = it } }
            when (tab) {
                0 -> {
                    item(key = "nudges") { NudgesPanel(state.promptsOn, actions::setPromptsOn) }
                    item(key = "rank") { RankPanel(state) }
                    item(key = "habits") {
                        HabitsPanel(state, actions, onEdit = { editing = it }, onAdd = { adding = true })
                    }
                    item(key = "body") { BodyPanel(state) }
                }
                1 -> {
                    item(key = "add") { AddTask(actions::addTask) }
                    item(key = "tasks") { TaskList(state, actions) }
                }
                2 -> {
                    item(key = "rank") { RankPanel(state, big = true) }
                    item(key = "xp") { XpDays(state) }
                    item(key = "seals") { Seals(state.unlocked) }
                    item(key = "how") { HowToEarn() }
                }
                else -> {
                    item(key = "nudges") { NudgesPanel(state.promptsOn, actions::setPromptsOn) }
                    item(key = "guard") { GuardPanel(state, actions, appsLoaded) }
                }
            }
        }
    }
    if (adding || editing != null) {
        HabitDialog(
            original = editing,
            name = editing?.let { state.habitNames[it.id] }.orEmpty(),
            taken = state.habits.map { it.id }.toSet(),
            onSave = { original, habit ->
                actions.saveHabit(original, habit)
                adding = false
                editing = null
            },
            onDelete = { habit ->
                actions.deleteHabit(habit)
                editing = null
            },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun LifeHeader(onBack: () -> Unit) {
    val p = LocalAero.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Orb(onBack, size = 48.dp, contentDescription = stringResource(R.string.move_back)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.move_back), tint = orbInk(), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.life_title), style = AeroText.title(28).copy(fontWeight = FontWeight.ExtraBold, shadow = titleShadow(p)))
            Text(stringResource(R.string.life_subtitle), style = AeroText.soft(13))
        }
        Seal(stringResource(R.string.kanji_life), 54.dp)
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        R.string.life_tab_today to R.string.kanji_today,
        R.string.life_tab_tasks to R.string.kanji_tasks,
        R.string.life_tab_progress to R.string.kanji_rank,
        R.string.life_tab_guard to R.string.kanji_guard,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEachIndexed { index, (label, _) ->
            GelButton(stringResource(label), { onSelect(index) }, Modifier.weight(1f), tone = if (index == selected) GelTone.ACCENT else GelTone.NEUTRAL, compact = true)
        }
    }
}

@Composable
private fun NudgesPanel(on: Boolean, onChange: (Boolean) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), tint = if (on) null else LocalAero.current.gold.copy(alpha = 0.2f)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
            Seal(stringResource(R.string.kanji_bell), 38.dp, tone = if (on) GelTone.PASS else GelTone.GOLD)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.life_prompts_on), style = AeroText.title(17), modifier = Modifier.weight(1f))
            AeroSwitch(on, onChange)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(if (on) R.string.life_prompts_on_detail else R.string.life_prompts_off_detail), style = AeroText.soft(13))
    }
}

/** The rank: its kanji on a gold seal, the bar to the next, today's points. */
@Composable
fun RankPanel(state: LifeState, big: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rank = Progress.rank(state.xp)
    val next = Progress.next(state.xp)
    GlassPanel(modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seal(rank.kanji, if (big) 76.dp else 56.dp, tone = GelTone.GOLD)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(LifeText.rankName(context, rank), style = AeroText.title(if (big) 24 else 19))
                Text(stringResource(R.string.life_xp_total, state.xp), style = AeroText.soft(13))
                Spacer(Modifier.height(8.dp))
                XpBar(Progress.toNext(state.xp))
                Spacer(Modifier.height(4.dp))
                Text(
                    next?.let { stringResource(R.string.life_rank_next, it.xp - state.xp, LifeText.rankName(context, it)) } ?: stringResource(R.string.life_rank_top),
                    style = AeroText.soft(12),
                )
            }
            Spacer(Modifier.width(8.dp))
            Pill(stringResource(R.string.life_xp_today, state.ledger.total(state.today)), color = LocalAero.current.gold)
        }
    }
}

/** A glossy gel tube, filled to [fraction]. */
@Composable
fun XpBar(fraction: Float, modifier: Modifier = Modifier) {
    val p = LocalAero.current
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(Color.Black.copy(alpha = if (p.isDark) 0.35f else 0.1f), cornerRadius = r)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(Brush.verticalGradient(listOf(p.gold, p.goldDeep)), size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
            drawRoundRect(Color.White.copy(alpha = 0.45f), topLeft = Offset(size.height / 3, 1.5f), size = Size(maxOf(w - size.height * 0.66f, 0f), size.height * 0.32f), cornerRadius = r)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitsPanel(state: LifeState, actions: LifeActions, onEdit: (Habits.Habit) -> Unit, onAdd: () -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.kanji_habit), stringResource(R.string.life_habits), stringResource(R.string.life_habits_detail))
        GlassPanel(Modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
            state.habits.forEach { habit ->
                val count = state.day?.count(habit.id) ?: 0
                val done = Habits.done(habit, state.day)
                val streak = Habits.streak(habit, state.habitDays, state.today)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .combinedClickable(onClick = { actions.tapHabit(habit, 1) }, onLongClick = { actions.tapHabit(habit, -1) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Orb(null, size = 52.dp, tone = if (done) GelTone.PASS else GelTone.ACCENT) {
                            Text(habit.glyph, style = TextStyle(color = orbInk(if (done) GelTone.PASS else GelTone.ACCENT), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.habitNames[habit.id].orEmpty(), style = AeroText.title(16))
                        val detail = buildString {
                            append(stringResource(R.string.life_count, count, habit.goal))
                            if (streak > 1) append(" · ").append(stringResource(R.string.life_streak, streak))
                            if (habit.times.isNotEmpty()) append(" · ").append(habit.times.sorted().joinToString(", ") { clock(it) })
                        }
                        Text(detail, style = AeroText.soft(12))
                        if (habit.goal in 2..12) {
                            Spacer(Modifier.height(4.dp))
                            Beads(minOf(count, habit.goal), habit.goal)
                        }
                    }
                    Text("›", modifier = Modifier.clip(CircleShape).clickable { onEdit(habit) }.padding(10.dp), style = AeroText.title(22).copy(color = LocalAero.current.inkSoft))
                }
            }
            Spacer(Modifier.height(6.dp))
            GelButton(stringResource(R.string.life_add_habit), onAdd, Modifier.fillMaxWidth(), tone = GelTone.NEUTRAL, compact = true, glyph = "+")
        }
    }
}

@Composable
private fun BodyPanel(state: LifeState) {
    if (state.stepsToday == null && state.sleepMs == null) return
    GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        state.stepsToday?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal(stringResource(R.string.kanji_walk), 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.guard_steps_today, it.toInt()), style = AeroText.body(16))
            }
        }
        state.sleepMs?.let {
            if (state.stepsToday != null) Spacer(Modifier.height(10.dp))
            val minutes = (it / 60_000L).toInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal(stringResource(R.string.kanji_sleep), 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.guard_sleep, minutes / 60, minutes % 60), style = AeroText.body(16))
            }
        }
    }
}

/** The due times offered for a task: in an hour, this evening, tomorrow morning or evening, in a week, whenever. */
private fun dueChoices(now: Long): List<Pair<Int, Long?>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    return listOfNotNull(
        R.string.life_due_hour to now + 3600_000L,
        (R.string.life_due_evening to at(today, 19)).takeIf { at(today, 19) > now },
        R.string.life_due_tomorrow_morning to at(today.plusDays(1), 9),
        R.string.life_due_tomorrow_evening to at(today.plusDays(1), 19),
        R.string.life_due_week to at(today.plusDays(7), 9),
        R.string.life_task_no_due to null,
    )
}

@Composable
private fun AddTask(onAdd: (String, Long?, Boolean) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf<Long?>(null) }
    var dueLabel by rememberSaveable { mutableStateOf(R.string.life_task_no_due) }
    var starred by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    fun add() {
        if (title.isBlank()) return
        onAdd(title, due, starred)
        title = ""
        due = null
        dueLabel = R.string.life_task_no_due
        starred = false
    }
    Column {
        SectionTitle(stringResource(R.string.kanji_tasks), stringResource(R.string.life_tasks), stringResource(R.string.life_tasks_detail))
        GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
            AeroField(title, { title = it }, stringResource(R.string.life_task_title), onDone = ::add)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GelButton(stringResource(dueLabel), { picking = true }, tone = if (due != null) GelTone.ACCENT else GelTone.NEUTRAL, compact = true, glyph = stringResource(R.string.kanji_task_due))
                GelButton(stringResource(R.string.life_task_star), { starred = !starred }, tone = if (starred) GelTone.GOLD else GelTone.NEUTRAL, compact = true, glyph = if (starred) "★" else "☆")
                Spacer(Modifier.weight(1f))
                GelButton(stringResource(R.string.life_task_add), ::add, tone = GelTone.PASS, compact = true, enabled = title.isNotBlank())
            }
        }
    }
    if (picking) {
        val choices = remember { dueChoices(System.currentTimeMillis()) }
        ChoiceDialog(
            title = stringResource(R.string.life_task_due),
            options = choices.map { stringResource(it.first) },
            selected = choices.indexOfFirst { it.first == dueLabel },
            onPick = { index ->
                picking = false
                due = choices[index].second
                dueLabel = choices[index].first
            },
            onDismiss = { picking = false },
            kanji = stringResource(R.string.kanji_task_due),
            cancel = stringResource(R.string.life_habit_cancel),
        )
    }
}

/** A text field on glass. */
@Composable
fun AeroField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    val p = LocalAero.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (p.isDark) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.75f))
            .border(1.dp, p.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(hint, style = AeroText.soft(16))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
            cursorBrush = SolidColor(p.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TaskList(state: LifeState, actions: LifeActions) {
    val open = Tasks.open(state.tasks)
    val done = state.tasks.filter { !it.isOpen }.sortedByDescending { it.done }
    GlassPanel(Modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        if (open.isEmpty()) Text(stringResource(R.string.life_tasks_empty), style = AeroText.soft(14), modifier = Modifier.padding(vertical = 8.dp))
        open.forEach { TaskRow(it, state.now, actions) }
        if (done.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.life_tasks_done_recent), style = AeroText.title(15))
            done.take(10).forEach { TaskRow(it, state.now, actions) }
        }
    }
}

@Composable
private fun TaskRow(task: Tasks.Task, now: Long, actions: LifeActions) {
    val p = LocalAero.current
    val overdue = task.isOpen && task.due != null && task.due <= now
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AeroCheck(!task.isOpen, onClick = { if (task.isOpen) actions.completeTask(task.id) else actions.reopenTask(task.id) })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = AeroText.body(16).copy(
                    textDecoration = if (task.isOpen) null else TextDecoration.LineThrough,
                    color = if (task.isOpen) p.ink else p.inkSoft,
                ),
            )
            task.due?.let { due ->
                Text(
                    (if (overdue) stringResource(R.string.life_task_overdue) + " · " else "") +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(due)),
                    style = AeroText.soft(12).copy(color = if (overdue) p.fail else p.inkSoft),
                )
            }
        }
        if (task.isOpen) {
            Text(
                if (task.starred) "★" else "☆",
                modifier = Modifier.clip(CircleShape).clickable { actions.starTask(task.id) }.padding(8.dp),
                style = TextStyle(color = if (task.starred) p.gold else p.inkSoft, fontSize = 22.sp, shadow = halo(p)),
            )
        }
        Text(
            "×",
            modifier = Modifier.clip(CircleShape).clickable { actions.deleteTask(task.id) }.padding(8.dp),
            style = TextStyle(color = p.inkSoft, fontSize = 22.sp, shadow = halo(p)),
        )
    }
}

/** The last two weeks' points, as glossy bars. */
@Composable
private fun XpDays(state: LifeState) {
    val p = LocalAero.current
    val days = (13 downTo 0).map { state.today.minusDays(it.toLong()) }
    val totals = days.map { state.ledger.total(it) }
    val top = (totals.maxOrNull() ?: 0).coerceAtLeast(1)
    GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Text(stringResource(R.string.life_xp_days), style = AeroText.title(16))
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val slot = size.width / days.size
            totals.forEachIndexed { i, total ->
                val h = size.height * 0.9f * total / top
                val x = i * slot + slot * 0.18f
                val w = slot * 0.64f
                drawRoundRect(Color.Black.copy(alpha = if (p.isDark) 0.25f else 0.06f), Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                if (h > 0f) {
                    drawRoundRect(Brush.verticalGradient(listOf(p.accent, p.accentDeep), size.height - h, size.height), Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
                    drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(x + w * 0.2f, size.height - h + 2f), Size(w * 0.25f, h * 0.6f), CornerRadius(w / 4))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Seals(unlocked: Set<String>) {
    Column {
        SectionTitle(stringResource(R.string.kanji_seal), stringResource(R.string.life_achievements), stringResource(R.string.life_achievements_detail, unlocked.size, Progress.ACHIEVEMENTS.size))
        GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 4) {
                Progress.ACHIEVEMENTS.forEach { a ->
                    val got = a.id in unlocked
                    Column(Modifier.width(76.dp).graphicsLayer { alpha = if (got) 1f else 0.38f }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Seal(a.kanji, 52.dp, tone = if (got) GelTone.GOLD else GelTone.NEUTRAL)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(LifeText.achievement(a.id)), style = AeroText.body(12).copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center, maxLines = 2)
                        Text(stringResource(LifeText.achievementDetail(a.id)), style = AeroText.soft(10), textAlign = TextAlign.Center, maxLines = 3)
                    }
                }
            }
        }
    }
}

@Composable
private fun HowToEarn() {
    GlassPanel(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.life_how_xp), style = AeroText.title(16))
        Spacer(Modifier.height(6.dp))
        stringResource(R.string.life_how_xp_list).split('\n').forEach { Text("· $it", style = AeroText.body(14), modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

/** [appsLoaded] changes when the app list arrives, so the picker shows it. */
@Suppress("UNUSED_PARAMETER")
@Composable
private fun GuardPanel(state: LifeState, actions: LifeActions, appsLoaded: Int) {
    var pick by remember { mutableStateOf<String?>(null) }
    Column {
        SectionTitle(stringResource(R.string.kanji_wake), stringResource(R.string.guard_spell), stringResource(R.string.guard_spell_detail))
        GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            ChoiceRow(stringResource(R.string.guard_spell), minutesLabel(state.spellMinutes)) { pick = "spell" }
            ChoiceRow(stringResource(R.string.guard_watched), stringResource(R.string.guard_watched_detail, state.watched.size)) { pick = "apps" }
        }
        SectionTitle(stringResource(R.string.kanji_stand), stringResource(R.string.guard_session), stringResource(R.string.guard_session_detail))
        GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            ChoiceRow(stringResource(R.string.guard_session), minutesLabel(state.sessionMinutes)) { pick = "session" }
        }
        SectionTitle(stringResource(R.string.kanji_walk), stringResource(R.string.guard_inactivity), stringResource(R.string.guard_inactivity_detail))
        GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            when {
                !state.hasStepSensor -> Text(stringResource(R.string.guard_no_sensor), style = AeroText.soft(14))
                !state.canCountSteps -> GelButton(stringResource(R.string.guard_steps_allow), actions::allowSteps, Modifier.fillMaxWidth(), tone = GelTone.ACCENT, compact = true)
                else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.guard_inactivity), style = AeroText.body(16), modifier = Modifier.weight(1f))
                    AeroSwitch(state.inactivityOn, actions::setInactivityOn)
                }
            }
        }
        SectionTitle(stringResource(R.string.kanji_sleep), stringResource(R.string.guard_bedtime), stringResource(R.string.guard_bedtime_detail))
        GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            ChoiceRow(stringResource(R.string.guard_bedtime), if (state.bedtime < 0) stringResource(R.string.guard_off) else clock(state.bedtime)) { pick = "bedtime" }
        }
    }
    when (pick) {
        "spell", "session" -> {
            val spell = pick == "spell"
            val choices = if (spell) GuardStore.SPELL_CHOICES else GuardStore.SESSION_CHOICES
            ChoiceDialog(
                title = stringResource(if (spell) R.string.guard_spell else R.string.guard_session),
                options = choices.map { minutesLabel(it) },
                selected = choices.indexOf(if (spell) state.spellMinutes else state.sessionMinutes),
                onPick = { i ->
                    pick = null
                    if (spell) actions.setSpellMinutes(choices[i]) else actions.setSessionMinutes(choices[i])
                },
                onDismiss = { pick = null },
                kanji = stringResource(R.string.kanji_wake),
                cancel = stringResource(R.string.life_habit_cancel),
            )
        }
        "bedtime" -> ChoiceDialog(
            title = stringResource(R.string.guard_bedtime),
            options = GuardStore.BEDTIMES.map { if (it < 0) stringResource(R.string.guard_off) else clock(it) },
            selected = GuardStore.BEDTIMES.indexOf(state.bedtime),
            onPick = { i ->
                pick = null
                actions.setBedtime(GuardStore.BEDTIMES[i])
            },
            onDismiss = { pick = null },
            kanji = stringResource(R.string.kanji_sleep),
            cancel = stringResource(R.string.life_habit_cancel),
        )
        "apps" -> AppsDialog(
            title = stringResource(R.string.guard_watched),
            apps = actions.apps(),
            initial = state.watched,
            multiple = true,
            onDone = { chosen ->
                pick = null
                actions.setWatched(chosen)
            },
            onDismiss = { pick = null },
        )
    }
}

@Composable
private fun minutesLabel(minutes: Int) = if (minutes <= 0) stringResource(R.string.guard_off) else stringResource(R.string.guard_minutes, minutes)

@Composable
private fun ChoiceRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AeroText.body(16), modifier = Modifier.weight(1f))
        Pill(value, color = LocalAero.current.accent)
    }
}

/** Adding or changing a habit: its name, how many a day, its reminder times; ideas to start from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitDialog(
    original: Habits.Habit?,
    name: String,
    taken: Set<String>,
    onSave: (Habits.Habit?, Habits.Habit) -> Unit,
    onDelete: (Habits.Habit) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(name) }
    var glyph by remember { mutableStateOf(original?.glyph ?: "習") }
    var target by remember { mutableStateOf(original?.target ?: 1) }
    var times by remember { mutableStateOf(original?.times.orEmpty()) }
    var paced by remember { mutableStateOf(original?.pace ?: false) }
    var builtIn by remember { mutableStateOf(original?.takeIf { it.name.isEmpty() }?.id) }
    var pickTime by remember { mutableStateOf(false) }
    AeroDialog(
        onDismiss = onDismiss,
        title = stringResource(if (original == null) R.string.life_add_habit else R.string.life_edit_habit),
        kanji = glyph,
        buttons = {
            if (original != null && original.id != Habits.WATER.id) {
                GelButton(stringResource(R.string.life_habit_delete), { onDelete(original) }, tone = GelTone.FAIL, compact = true)
            }
            GelButton(stringResource(R.string.life_habit_cancel), onDismiss, tone = GelTone.NEUTRAL, compact = true)
            GelButton(stringResource(R.string.life_habit_save), {
                val id = original?.id ?: builtIn?.takeIf { it !in taken } ?: Habits.newId(title, taken)
                // A built-in habit keeps its translated name unless renamed.
                val builtInName = builtIn?.let { LifeText.habitName(context, Habits.Habit(it, "")) }
                val stored = if (builtIn != null && title == builtInName) "" else title.trim()
                onSave(original, Habits.Habit(id, stored, target.coerceAtLeast(1), times.sorted(), glyph.ifBlank { "習" }.take(2), paced))
            }, tone = GelTone.PASS, compact = true, enabled = title.isNotBlank())
        },
    ) {
        if (original == null) {
            Text(stringResource(R.string.life_suggestions), style = AeroText.title(15))
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Habits.SUGGESTIONS.filter { it.id !in taken }.forEach { idea ->
                    GelButton(LifeText.habitName(context, idea), {
                        title = LifeText.habitName(context, idea)
                        glyph = idea.glyph
                        target = idea.target
                        times = idea.times
                        paced = idea.pace
                        builtIn = idea.id
                    }, tone = if (builtIn == idea.id) GelTone.ACCENT else GelTone.NEUTRAL, compact = true, glyph = idea.glyph)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(stringResource(R.string.life_habit_name), style = AeroText.title(15))
        Spacer(Modifier.height(6.dp))
        AeroField(title, { title = it }, stringResource(R.string.life_habit_name))
        Spacer(Modifier.height(14.dp))
        if (times.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.life_habit_target), style = AeroText.title(15), modifier = Modifier.weight(1f))
                GelButton("−", { target = (target - 1).coerceAtLeast(1) }, tone = GelTone.NEUTRAL, compact = true)
                Text("$target", style = AeroText.title(18), modifier = Modifier.padding(horizontal = 12.dp))
                GelButton("+", { target = (target + 1).coerceAtMost(30) }, tone = GelTone.NEUTRAL, compact = true)
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(stringResource(R.string.life_habit_times), style = AeroText.title(15))
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (times.isEmpty()) Text(stringResource(R.string.life_habit_no_times), style = AeroText.soft(13), modifier = Modifier.padding(vertical = 8.dp))
            times.sorted().forEach { t ->
                GelButton("${clock(t)}  ×", { times = times - t }, tone = GelTone.ACCENT, compact = true)
            }
            GelButton(stringResource(R.string.life_habit_add_time), { pickTime = true }, tone = GelTone.NEUTRAL, compact = true, glyph = "+")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { paced = !paced }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.life_habit_paced), style = AeroText.body(15), modifier = Modifier.weight(1f))
            AeroSwitch(paced, onCheckedChange = { paced = it })
        }
    }
    if (pickTime) {
        val slots = (5 * 2..23 * 2 + 1).map { it * 30 }
        ChoiceDialog(
            title = stringResource(R.string.life_pick_time),
            options = slots.map { clock(it) },
            selected = -1,
            onPick = { i ->
                pickTime = false
                if (slots[i] !in times) times = times + slots[i]
            },
            onDismiss = { pickTime = false },
            kanji = glyph,
            cancel = stringResource(R.string.life_habit_cancel),
        )
    }
}

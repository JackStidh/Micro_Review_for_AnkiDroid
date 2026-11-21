package com.orlikalabs.microreview.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orlikalabs.microreview.MovePlan
import com.orlikalabs.microreview.MoveStore
import com.orlikalabs.microreview.MoveText
import com.orlikalabs.microreview.R
import com.orlikalabs.microreview.ui.aero.AeroBackground
import com.orlikalabs.microreview.ui.aero.AeroDialog
import com.orlikalabs.microreview.ui.aero.AeroSwitch
import com.orlikalabs.microreview.ui.aero.AeroText
import com.orlikalabs.microreview.ui.aero.Beads
import com.orlikalabs.microreview.ui.aero.ChoiceDialog
import com.orlikalabs.microreview.ui.aero.GelButton
import com.orlikalabs.microreview.ui.aero.GelTone
import com.orlikalabs.microreview.ui.aero.GlassPanel
import com.orlikalabs.microreview.ui.aero.LocalAero
import com.orlikalabs.microreview.ui.aero.LocalAeroMotion
import com.orlikalabs.microreview.ui.aero.Orb
import com.orlikalabs.microreview.ui.aero.Pill
import com.orlikalabs.microreview.ui.aero.Seal
import com.orlikalabs.microreview.ui.aero.SectionTitle
import com.orlikalabs.microreview.ui.aero.orbInk
import com.orlikalabs.microreview.ui.aero.titleShadow
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

// The prompt, in the card's window.

/** What the morning or evening prompt shows. */
data class MovePromptData(
    val prompt: MoveStore.Prompt,
    val pain: MovePlan.PainSpot? = null,
    val trend: MovePlan.Trend? = null,
    val session: MovePlan.Session = MovePlan.Session.RECOVERY,
    val streak: Int = 0,
    val warning: MovePlan.Warning? = null,
)

interface MovePromptActions {
    fun pain(spot: MovePlan.PainSpot)
    fun trend(trend: MovePlan.Trend)
    fun pressUpsDone()
    fun start()
    fun later()
    fun notToday()
}

/** Move's prompt over the app in use: the morning check-in and press-ups, or the evening routine. */
@Composable
fun MovePromptScreen(data: MovePromptData, actions: MovePromptActions) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val morning = data.prompt == MoveStore.Prompt.MORNING
    AeroBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = insets.calculateTopPadding() + 18.dp, bottom = insets.calculateBottomPadding() + 14.dp),
        ) {
            // What to read scrolls; what to do stays at the bottom, always in view.
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Seal(stringResource(if (morning) R.string.kanji_morning else R.string.kanji_evening), 58.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            stringResource(if (morning) R.string.move_good_morning else R.string.move_evening_prompt),
                            style = AeroText.title(28).copy(fontWeight = FontWeight.ExtraBold, shadow = titleShadow(LocalAero.current)),
                        )
                        Text(stringResource(R.string.move_subtitle), style = AeroText.soft(13))
                    }
                }
                data.warning?.let { WarningPanel(it) }
                if (morning) {
                    GlassPanel(Modifier.fillMaxWidth(), tint = LocalAero.current.gold.copy(alpha = 0.18f)) {
                        Text(stringResource(R.string.move_first_hour), style = AeroText.body(16))
                    }
                    GlassPanel(Modifier.fillMaxWidth()) {
                        CheckIn(data.pain, data.trend, actions::pain, actions::trend)
                    }
                } else {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.move_evening_prompt_detail, stringResource(sessionText(data.session))),
                            style = AeroText.body(16),
                        )
                        if (data.streak > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.move_streak, data.streak), style = AeroText.soft(14))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (morning) {
                GelButton(
                    stringResource(R.string.move_press_ups_done),
                    actions::pressUpsDone,
                    Modifier.fillMaxWidth(),
                    tone = GelTone.PASS,
                    glyph = stringResource(R.string.kanji_morning),
                    detail = stringResource(R.string.move_press_up_name),
                )
            } else {
                GelButton(stringResource(R.string.move_start), actions::start, Modifier.fillMaxWidth(), tone = GelTone.PASS, glyph = stringResource(R.string.kanji_move))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GelButton(stringResource(R.string.move_later), actions::later, Modifier.weight(1f), tone = GelTone.GOLD, compact = true)
                GelButton(stringResource(R.string.move_not_today), actions::notToday, Modifier.weight(1f), tone = GelTone.NEUTRAL, compact = true)
            }
        }
    }
}

/** Where the leg pain reaches, and better or worse than yesterday: one tap each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckIn(
    pain: MovePlan.PainSpot?,
    trend: MovePlan.Trend?,
    onPain: (MovePlan.PainSpot) -> Unit,
    onTrend: (MovePlan.Trend) -> Unit,
) {
    var shownPain by remember(pain) { mutableStateOf(pain) }
    var shownTrend by remember(trend) { mutableStateOf(trend) }
    Text(stringResource(R.string.move_pain_question), style = AeroText.title(17))
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MovePlan.PainSpot.entries.forEach { spot ->
            Chip(stringResource(MoveText.pain(spot)), spot == shownPain) {
                shownPain = spot
                onPain(spot)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.move_trend_question), style = AeroText.title(17))
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MovePlan.Trend.entries.forEach { value ->
            Chip(stringResource(MoveText.trend(value)), value == shownTrend) {
                shownTrend = value
                onTrend(value)
            }
        }
    }
}

/** A small gel to pick: lit when chosen. */
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    GelButton(
        text,
        onClick,
        tone = if (selected) GelTone.ACCENT else GelTone.NEUTRAL,
        compact = true,
        glyph = if (selected) "✓" else null,
    )
}

@Composable
private fun WarningPanel(warning: MovePlan.Warning) {
    GlassPanel(Modifier.fillMaxWidth(), tint = LocalAero.current.fail.copy(alpha = 0.22f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seal(stringResource(R.string.kanji_danger), 36.dp, tone = GelTone.FAIL)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(MoveText.warning(warning)), style = AeroText.body(15), modifier = Modifier.weight(1f))
        }
    }
}

private fun sessionText(session: MovePlan.Session): Int =
    if (session == MovePlan.Session.STRENGTH) R.string.move_evening_strength else R.string.move_evening_recovery

// The Move screen.

/** What the Move screen shows. */
data class MoveState(
    val phase: MovePlan.Phase? = null,
    val days: List<MovePlan.Day> = emptyList(),
    val todayDate: LocalDate = LocalDate.now(),
    val session: MovePlan.Session = MovePlan.Session.RECOVERY,
    val rebuildWeek: Int = 1,
    val streak: Int = 0,
    val warning: MovePlan.Warning? = null,
    val promptsOn: Boolean = true,
    val eveningMinutes: Int = MoveStore.DEFAULT_EVENING_MINUTES,
) {
    val today: MovePlan.Day? get() = days.firstOrNull { it.date == todayDate }

    /** Morning, evening, walk: how many are done today. */
    val doneToday: Int
        get() = listOf(today?.morning == true, today?.evening != null, (today?.walkMinutes ?: 0) >= WALK_GOAL_MINUTES).count { it }

    companion object {
        const val WALK_GOAL_MINUTES = 30
    }
}

interface MoveActions {
    fun back()
    fun pickPhase(phase: MovePlan.Phase)
    fun pain(spot: MovePlan.PainSpot)
    fun trend(trend: MovePlan.Trend)
    fun morningDone()
    fun startSession()
    fun addWalk(minutes: Int)
    fun setPromptsOn(on: Boolean)
    fun setEveningMinutes(minutes: Int)
}

@Composable
fun MoveScreen(state: MoveState, actions: MoveActions) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var confirmPhase by remember { mutableStateOf<MovePlan.Phase?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    AeroBackground(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { MoveHeader(actions::back) }
            val phase = state.phase
            if (phase == null) {
                item(key = "setup") { Setup(actions::pickPhase) }
            } else {
                state.warning?.let { warning -> item(key = "warning") { WarningPanel(warning) } }
                item(key = "today") { Today(state, actions) }
                item(key = "days") { LastDays(state) }
                item(key = "phase") { PhasePanel(phase, state.rebuildWeek) { next -> if (next == MovePlan.Phase.REBUILD) confirmPhase = next else actions.pickPhase(next) } }
                item(key = "routine") { Routine(MovePlan.parts(state.session, state.rebuildWeek)) }
                item(key = "desk") { Desk() }
                item(key = "rules") { Rules() }
                item(key = "prompts") { Prompts(state, actions::setPromptsOn) { pickTime = true } }
            }
            item(key = "advice") {
                Text(stringResource(R.string.move_not_advice), Modifier.padding(horizontal = 6.dp, vertical = 8.dp), style = AeroText.soft(13))
            }
        }
    }
    confirmPhase?.let { next ->
        AeroDialog(
            onDismiss = { confirmPhase = null },
            title = stringResource(R.string.move_to_rebuild),
            kanji = stringResource(R.string.kanji_rebuild),
            buttons = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GelButton(stringResource(R.string.move_back), { confirmPhase = null }, tone = GelTone.NEUTRAL, compact = true)
                    GelButton(stringResource(R.string.move_to_rebuild), {
                        confirmPhase = null
                        actions.pickPhase(next)
                    }, tone = GelTone.PASS, compact = true)
                }
            },
        ) {
            Text(stringResource(R.string.move_to_rebuild_confirm), style = AeroText.body())
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.move_rebuild_ready), style = AeroText.soft(14))
        }
    }
    if (pickTime) {
        val times = MoveStore.EVENING_TIMES
        ChoiceDialog(
            title = stringResource(R.string.move_evening_time),
            options = times.map { minutes -> "%d:%02d".format(minutes / 60, minutes % 60) },
            selected = times.indexOf(state.eveningMinutes),
            onPick = { index ->
                pickTime = false
                actions.setEveningMinutes(times[index])
            },
            onDismiss = { pickTime = false },
            kanji = stringResource(R.string.kanji_evening),
            cancel = stringResource(R.string.move_back),
        )
    }
}

@Composable
private fun MoveHeader(onBack: () -> Unit) {
    val p = LocalAero.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Orb(onBack, size = 48.dp, contentDescription = stringResource(R.string.move_back)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.move_back), tint = orbInk(), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.move_title), style = AeroText.title(28).copy(fontWeight = FontWeight.ExtraBold, shadow = titleShadow(p)))
            Text(stringResource(R.string.move_subtitle), style = AeroText.soft(13))
        }
        Seal(stringResource(R.string.kanji_move), 54.dp)
    }
}

@Composable
private fun Setup(onPick: (MovePlan.Phase) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), corner = 32.dp, padding = PaddingValues(20.dp)) {
        Text(stringResource(R.string.move_setup_title), style = AeroText.title(22).copy(shadow = titleShadow(LocalAero.current)))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.move_setup_detail), style = AeroText.soft(14))
        Spacer(Modifier.height(16.dp))
        GelButton(
            stringResource(R.string.move_setup_recovery),
            { onPick(MovePlan.Phase.RECOVERY) },
            Modifier.fillMaxWidth(),
            tone = GelTone.PASS,
            glyph = stringResource(R.string.kanji_recovery),
            detail = stringResource(R.string.move_setup_recovery_detail),
        )
        Spacer(Modifier.height(10.dp))
        GelButton(
            stringResource(R.string.move_setup_rebuild),
            { onPick(MovePlan.Phase.REBUILD) },
            Modifier.fillMaxWidth(),
            tone = GelTone.ACCENT,
            glyph = stringResource(R.string.kanji_rebuild),
            detail = stringResource(R.string.move_setup_rebuild_detail),
        )
    }
}

@Composable
private fun Today(state: MoveState, actions: MoveActions) {
    val today = state.today
    var checkingIn by rememberSaveable { mutableStateOf(false) }
    Column {
        SectionTitle(stringResource(R.string.kanji_body), stringResource(R.string.move_today), stringResource(R.string.move_streak, state.streak))
        GlassPanel(Modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            TodayRow(
                kanji = stringResource(R.string.kanji_morning),
                title = stringResource(R.string.move_morning),
                detail = today?.pain?.let { "${stringResource(R.string.move_morning_detail)} · ${stringResource(MoveText.pain(it))}" }
                    ?: stringResource(R.string.move_morning_detail),
                done = today?.morning == true,
                action = stringResource(R.string.move_check_in),
                onAction = { checkingIn = !checkingIn },
            )
            if (checkingIn || (today?.morning != true && today?.pain != null)) {
                Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp)) {
                    CheckIn(today?.pain, today?.trend, actions::pain, actions::trend)
                    Spacer(Modifier.height(12.dp))
                    GelButton(stringResource(R.string.move_press_ups_done), {
                        checkingIn = false
                        actions.morningDone()
                    }, Modifier.fillMaxWidth(), tone = GelTone.PASS, compact = true)
                }
            }
            TodayRow(
                kanji = stringResource(R.string.kanji_evening),
                title = stringResource(R.string.move_evening),
                detail = stringResource(sessionText(state.session)),
                done = today?.evening != null,
                action = stringResource(R.string.move_start),
                onAction = actions::startSession,
            )
            TodayRow(
                kanji = stringResource(R.string.kanji_walk),
                title = stringResource(R.string.move_walk),
                detail = stringResource(R.string.move_walk_detail, today?.walkMinutes ?: 0),
                done = (today?.walkMinutes ?: 0) >= MoveState.WALK_GOAL_MINUTES,
                action = stringResource(R.string.move_walk_add),
                onAction = { actions.addWalk(10) },
                actionWhenDone = true,
            )
        }
    }
}

@Composable
private fun TodayRow(
    kanji: String,
    title: String,
    detail: String,
    done: Boolean,
    action: String,
    onAction: () -> Unit,
    actionWhenDone: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Seal(kanji, 38.dp, tone = if (done) GelTone.PASS else GelTone.ACCENT)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AeroText.title(17))
            Text(detail, style = AeroText.soft(13))
        }
        Spacer(Modifier.width(8.dp))
        if (done && !actionWhenDone) {
            Pill(stringResource(R.string.move_done), color = LocalAero.current.pass)
        } else {
            GelButton(action, onAction, tone = if (done) GelTone.PASS else GelTone.ACCENT, compact = true)
        }
    }
}

/** Two weeks: a bead per evening done, a dot for where the leg pain reached that morning. */
@Composable
private fun LastDays(state: MoveState) {
    val p = LocalAero.current
    val byDate = state.days.associateBy { it.date }
    GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Text(stringResource(R.string.move_last_days), style = AeroText.title(16))
        Text(stringResource(R.string.move_last_days_detail), style = AeroText.soft(12))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (back in 13 downTo 0) {
                val date = state.todayDate.minusDays(back.toLong())
                val day = byDate[date]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Beads(if (day?.evening != null) 1 else 0, 1)
                    Spacer(Modifier.height(6.dp))
                    val pain = day?.pain
                    Canvas(Modifier.size(10.dp)) {
                        if (pain != null) {
                            val depth = pain.ordinal / (MovePlan.PainSpot.entries.size - 1f)
                            drawCircle(lerp(p.pass, p.fail, depth))
                        } else {
                            drawCircle(p.inkSoft.copy(alpha = 0.25f), style = Stroke(1.5f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()),
                        style = TextStyle(color = if (back == 0) p.ink else p.inkSoft, fontSize = 11.sp, fontWeight = if (back == 0) FontWeight.Bold else FontWeight.Normal),
                    )
                }
            }
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

@Composable
private fun PhasePanel(phase: MovePlan.Phase, rebuildWeek: Int, onSwitch: (MovePlan.Phase) -> Unit) {
    val recovery = phase == MovePlan.Phase.RECOVERY
    Column {
        SectionTitle(
            stringResource(if (recovery) R.string.kanji_recovery else R.string.kanji_rebuild),
            "${stringResource(R.string.move_phase)}: ${stringResource(if (recovery) R.string.move_phase_recovery else R.string.move_phase_rebuild)}",
        )
        GlassPanel(Modifier.fillMaxWidth()) {
            Text(
                if (recovery) stringResource(R.string.move_phase_recovery_detail) else stringResource(R.string.move_phase_rebuild_detail, rebuildWeek),
                style = AeroText.body(15),
            )
            Spacer(Modifier.height(12.dp))
            if (recovery) {
                Text(stringResource(R.string.move_restrictions), style = AeroText.title(15))
                Spacer(Modifier.height(4.dp))
                Lines(stringResource(R.string.move_restrictions_list))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.move_rebuild_ready), style = AeroText.soft(13))
                Spacer(Modifier.height(12.dp))
                GelButton(stringResource(R.string.move_to_rebuild), { onSwitch(MovePlan.Phase.REBUILD) }, Modifier.fillMaxWidth(), tone = GelTone.ACCENT, compact = true, glyph = stringResource(R.string.kanji_rebuild))
            } else {
                Text(stringResource(R.string.move_rebuild_food), style = AeroText.soft(13))
                Spacer(Modifier.height(12.dp))
                GelButton(stringResource(R.string.move_to_recovery), { onSwitch(MovePlan.Phase.RECOVERY) }, Modifier.fillMaxWidth(), tone = GelTone.NEUTRAL, compact = true, glyph = stringResource(R.string.kanji_recovery))
            }
        }
    }
}

/** One line per "\n", each with a little bead. */
@Composable
private fun Lines(text: String) {
    val p = LocalAero.current
    text.split('\n').forEach { line ->
        Row(Modifier.padding(vertical = 3.dp)) {
            Canvas(Modifier.padding(top = 7.dp).size(7.dp)) { drawCircle(p.accent) }
            Spacer(Modifier.width(10.dp))
            Text(line, style = AeroText.body(14))
        }
    }
}

@Composable
private fun Routine(parts: List<MovePlan.Part>) {
    Column {
        SectionTitle(stringResource(R.string.kanji_move), stringResource(R.string.move_routine))
        GlassPanel(Modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
            parts.forEachIndexed { index, part ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Text(stringResource(MoveText.part(part)), style = AeroText.title(16), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                part.moves.forEach { MoveRow(it) }
            }
        }
    }
}

@Composable
private fun Desk() {
    Column {
        SectionTitle(stringResource(R.string.kanji_stand), stringResource(R.string.move_desk), stringResource(R.string.move_desk_detail))
        GlassPanel(Modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
            MovePlan.DESK.forEach { MoveRow(it) }
        }
    }
}

/** An exercise: its name and dose; tapped, how it's done. */
@Composable
private fun MoveRow(move: MovePlan.Move) {
    var open by rememberSaveable(move.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { open = !open }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(MoveText.name(move)), style = AeroText.body(15).copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text(stringResource(MoveText.dose(move)), style = AeroText.soft(13), textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 160.dp))
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(MoveText.how(move)), style = AeroText.soft(14))
        }
    }
}

@Composable
private fun Rules() {
    Column {
        SectionTitle(stringResource(R.string.kanji_rules), stringResource(R.string.move_rules))
        GlassPanel(Modifier.fillMaxWidth()) { Lines(stringResource(R.string.move_rules_list)) }
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), tint = LocalAero.current.fail.copy(alpha = 0.22f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal(stringResource(R.string.kanji_danger), 36.dp, tone = GelTone.FAIL)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.move_emergency), style = AeroText.title(17))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.move_emergency_list), style = AeroText.body(15))
        }
    }
}

@Composable
private fun Prompts(state: MoveState, onPromptsOn: (Boolean) -> Unit, onPickTime: () -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.kanji_bell), stringResource(R.string.move_reminders), stringResource(R.string.move_reminders_detail))
        GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPromptsOn(!state.promptsOn) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.move_reminders_on), style = AeroText.body(16), modifier = Modifier.weight(1f))
                AeroSwitch(state.promptsOn, onPromptsOn)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onPickTime).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.move_evening_time), style = AeroText.body(16), modifier = Modifier.weight(1f))
                Pill("%d:%02d".format(state.eveningMinutes / 60, state.eveningMinutes % 60), color = LocalAero.current.accent)
            }
        }
    }
}

// The guided session.

/**
 * One exercise at a time: how it's done and, for held ones, a timer orb (tap it in position; a tick and a buzz when
 * the hold is over) counting the reps, right side then left. Can stop after each part; [onFinish] gets the parts done.
 */
@Composable
fun MoveSessionScreen(parts: List<MovePlan.Part>, onFinish: (partsDone: Int) -> Unit, onClose: () -> Unit) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var partIndex by rememberSaveable { mutableIntStateOf(0) }
    var moveIndex by rememberSaveable { mutableIntStateOf(0) }
    var stage by rememberSaveable { mutableStateOf(SessionStage.MOVE) }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    AeroBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = insets.calculateTopPadding() + 10.dp, bottom = insets.calculateBottomPadding() + 18.dp),
        ) {
            val part = parts[partIndex]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Orb(onClose, size = 44.dp, contentDescription = stringResource(R.string.move_close)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.move_close), tint = orbInk(), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(MoveText.part(part)), style = AeroText.title(18))
                    Text(stringResource(R.string.move_session_of, moveIndex + 1, part.moves.size), style = AeroText.soft(13))
                }
                Beads(if (stage == SessionStage.MOVE) moveIndex else part.moves.size, part.moves.size)
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = Triple(stage, partIndex, moveIndex),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "session",
            ) { (shownStage, shownPart, shownMove) ->
                when (shownStage) {
                    SessionStage.MOVE -> MoveStep(
                        move = parts[shownPart].moves[shownMove],
                        isFirst = shownPart == 0 && shownMove == 0,
                        onBack = {
                            if (moveIndex > 0) moveIndex-- else if (partIndex > 0) {
                                partIndex--
                                moveIndex = parts[partIndex].moves.lastIndex
                            }
                        },
                        onNext = {
                            if (moveIndex < parts[partIndex].moves.lastIndex) moveIndex++ else stage = SessionStage.PART_DONE
                        },
                    )
                    SessionStage.PART_DONE -> PartDone(
                        part = parts[shownPart],
                        next = parts.getOrNull(shownPart + 1),
                        onKeepGoing = {
                            partIndex++
                            moveIndex = 0
                            stage = SessionStage.MOVE
                        },
                        onDone = { onFinish(partIndex + 1) },
                    )
                }
            }
        }
    }
}

private enum class SessionStage { MOVE, PART_DONE }

@Composable
private fun MoveStep(move: MovePlan.Move, isFirst: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    var done by rememberSaveable(move.id) { mutableIntStateOf(0) }
    val timed = move.holdSeconds > 0
    val complete = !timed || done >= move.holds
    Column(Modifier.fillMaxSize()) {
        GlassPanel(Modifier.fillMaxWidth(), corner = 30.dp, padding = PaddingValues(20.dp)) {
            Text(stringResource(MoveText.name(move)), style = AeroText.title(26).copy(shadow = titleShadow(LocalAero.current)))
            Spacer(Modifier.height(6.dp))
            Pill(stringResource(MoveText.dose(move)), color = LocalAero.current.accent)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(MoveText.how(move)), style = AeroText.body(16))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (timed) HoldTimer(move, done) { done++ }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GelButton(stringResource(R.string.move_back), onBack, Modifier.weight(1f), tone = GelTone.NEUTRAL, enabled = !isFirst, compact = true)
            GelButton(
                stringResource(if (complete) R.string.move_next else R.string.move_skip),
                onNext,
                Modifier.weight(2f),
                tone = if (complete) GelTone.PASS else GelTone.GOLD,
            )
        }
    }
}

/** The hold timer: an orb to tap in position; it counts down, then ticks and buzzes. */
@Composable
private fun HoldTimer(move: MovePlan.Move, done: Int, onHoldDone: () -> Unit) {
    val p = LocalAero.current
    val haptics = LocalHapticFeedback.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    var endsAt by remember(move.id, done) { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val holding = endsAt != 0L
    LaunchedEffect(endsAt) {
        if (endsAt == 0L) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now >= endsAt) break
            delay(100)
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        endsAt = 0L
        onHoldDone()
    }
    val perSide = move.reps * move.sets
    val allDone = done >= move.holds
    val side = if (!move.sides) null else if (done < perSide) R.string.move_side_right else R.string.move_side_left
    val repOnSide = if (move.sides) done % perSide else done
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val total = move.holdSeconds * 1000f
        val left = if (holding) ((endsAt - now).coerceAtLeast(0) / 1000f) else move.holdSeconds.toFloat()
        Orb(
            onClick = if (holding || allDone) null else ({ endsAt = System.currentTimeMillis() + move.holdSeconds * 1000L }),
            size = 170.dp,
            tone = when {
                allDone -> GelTone.PASS
                holding -> GelTone.GOLD
                else -> GelTone.ACCENT
            },
            contentDescription = stringResource(R.string.move_hold),
        ) {
            if (holding && LocalAeroMotion.current) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    drawArc(
                        Color.White.copy(alpha = 0.85f),
                        startAngle = -90f,
                        sweepAngle = 360f * (left * 1000f / total),
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset.Zero,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        allDone -> "✓"
                        holding -> "%.0f".format(kotlin.math.ceil(left))
                        else -> stringResource(R.string.move_hold)
                    },
                    style = TextStyle(color = orbInk(if (allDone) GelTone.PASS else if (holding) GelTone.GOLD else GelTone.ACCENT), fontSize = if (holding) 54.sp else 30.sp, fontWeight = FontWeight.ExtraBold),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (!allDone) {
            side?.let { Text(stringResource(it), style = AeroText.title(18)) }
            Text(stringResource(R.string.move_rep_of, repOnSide + 1, perSide), style = AeroText.soft(14))
            if (!holding && done == 0) Text(stringResource(R.string.move_tap_to_hold), style = AeroText.soft(13).copy(color = p.inkSoft))
        }
        Spacer(Modifier.height(8.dp))
        Beads(if (allDone) perSide else repOnSide, perSide)
    }
}

@Composable
private fun PartDone(part: MovePlan.Part, next: MovePlan.Part?, onKeepGoing: () -> Unit, onDone: () -> Unit) {
    val p = LocalAero.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            if (next == null) stringResource(R.string.otsukaresama) else stringResource(R.string.move_part_done, stringResource(MoveText.part(part))),
            style = TextStyle(color = p.ink, fontSize = if (next == null) 40.sp else 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = p.headings, shadow = titleShadow(p), textAlign = TextAlign.Center),
        )
        if (next == null) Text(stringResource(R.string.move_session_done), style = AeroText.soft(15))
        Spacer(Modifier.height(28.dp))
        if (next != null) {
            GelButton(stringResource(R.string.move_keep_going, stringResource(MoveText.part(next))), onKeepGoing, Modifier.fillMaxWidth(), tone = GelTone.PASS, glyph = stringResource(R.string.kanji_move))
            Spacer(Modifier.height(10.dp))
            GelButton(stringResource(R.string.move_done_for_today), onDone, Modifier.fillMaxWidth(), tone = GelTone.NEUTRAL, compact = true)
        } else {
            GelButton(stringResource(R.string.move_finish), onDone, Modifier.fillMaxWidth(), tone = GelTone.PASS)
        }
    }
}

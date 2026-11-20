package com.orlikalabs.microreview.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orlikalabs.microreview.AnswerButtons
import com.orlikalabs.microreview.FocusCardController
import com.orlikalabs.microreview.FocusCardController.Mode
import com.orlikalabs.microreview.FocusCardController.Stage
import com.orlikalabs.microreview.R
import com.orlikalabs.microreview.ui.aero.AeroBackground
import com.orlikalabs.microreview.ui.aero.AeroPalette
import com.orlikalabs.microreview.ui.aero.AeroText
import com.orlikalabs.microreview.ui.aero.Beads
import com.orlikalabs.microreview.ui.aero.GelButton
import com.orlikalabs.microreview.ui.aero.GelTone
import com.orlikalabs.microreview.ui.aero.GlassPanel
import com.orlikalabs.microreview.ui.aero.LocalAero
import com.orlikalabs.microreview.ui.aero.Seal
import com.orlikalabs.microreview.ui.aero.titleShadow

/**
 * The card, full screen: over the app in use ([com.orlikalabs.microreview.CardOverlay]) or as a screen of the app
 * ([com.orlikalabs.microreview.FocusReviewActivity]). Shows [ui] and calls [actions]. [syncing]: the card screen is
 * done and stays a moment over AnkiDroid's sync, under a veil.
 */
@Composable
fun CardScreen(ui: FocusCardController.Ui, actions: FocusCardController, syncing: Boolean = false) {
    val charge = remember(ui.lockSerial) { Animatable(0f) }
    LaunchedEffect(ui.lockSerial) {
        charge.animateTo(1f, tween(FocusCardController.APPEAR_LOCK_MS.toInt(), easing = LinearEasing))
    }
    val buttonCharge = if (ui.locked) charge.value.coerceAtMost(0.99f) else 1f
    AeroBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            TopBar(ui, actions)
            if (ui.mode == Mode.GATE && ui.required > 1) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Beads(ui.answered.coerceAtMost(ui.required), ui.required)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.gate_progress, ui.answered.coerceAtMost(ui.required), ui.required), style = AeroText.soft(13))
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = ui.stage,
                    transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = 0.94f)) togetherWith fadeOut(tween(180)) },
                    label = "stage",
                ) { stage ->
                    when (stage) {
                        Stage.CARD -> CardFace(ui)
                        Stage.RECORDED -> Recorded()
                        Stage.ALL_DONE -> AllDone()
                        Stage.LOADING -> Loading()
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            BottomActions(ui, actions, buttonCharge)
        }
        AnimatedVisibility(syncing, enter = fadeIn(tween(200)), exit = fadeOut()) { SyncVeil() }
    }
}

@Composable
private fun TopBar(ui: FocusCardController.Ui, actions: FocusCardController) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val kanji = when (ui.mode) {
            Mode.GATE -> stringResource(R.string.kanji_gate)
            Mode.NUDGE -> stringResource(R.string.kanji_nudge)
            Mode.SESSION -> stringResource(R.string.kanji_session)
        }
        Seal(kanji, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = when (ui.mode) {
                Mode.GATE -> stringResource(R.string.gate_title, ui.gatedApp)
                Mode.NUDGE -> stringResource(R.string.card_nudge_title)
                Mode.SESSION -> pluralStringResource(R.plurals.session_progress, ui.answered, ui.answered)
            }
            Text(title, style = AeroText.title(18).copy(shadow = titleShadow(LocalAero.current)), maxLines = 2)
            if (ui.deck.isNotEmpty()) Text(ui.deck, style = AeroText.soft(13), maxLines = 1)
        }
        if (ui.stage == Stage.CARD) {
            Spacer(Modifier.width(8.dp))
            GelButton(stringResource(R.string.bury), actions::bury, tone = GelTone.NEUTRAL, compact = true, enabled = ui.usable, glyph = stringResource(R.string.kanji_bury))
        }
    }
}

/** The card: its question, big; once revealed, a glossy rule, the answer and the note's other fields. */
@Composable
private fun CardFace(ui: FocusCardController.Ui) {
    val p = LocalAero.current
    val appear = remember(ui.cardSerial) { Animatable(0f) }
    LaunchedEffect(ui.cardSerial) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 260f)) }
    val lift = with(LocalDensity.current) { 36.dp.toPx() }
    GlassPanel(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * lift
                val s = 0.94f + 0.06f * appear.value
                scaleX = s
                scaleY = s
            },
        corner = 34.dp,
        padding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val minHeight = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = minHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        ui.question,
                        style = TextStyle(
                            color = p.ink,
                            fontSize = questionSize(ui.question).sp,
                            lineHeight = (questionSize(ui.question) * 1.25f).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            shadow = titleShadow(p),
                        ),
                    )
                    AnimatedVisibility(ui.revealed, enter = fadeIn(tween(320)) + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlossRule(Modifier.padding(vertical = 18.dp))
                            Text(
                                ui.answer,
                                style = TextStyle(
                                    color = p.ink,
                                    fontSize = answerSize(ui.answer).sp,
                                    lineHeight = (answerSize(ui.answer) * 1.35f).sp,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                            ui.fields.forEach { (name, value) ->
                                Spacer(Modifier.height(12.dp))
                                GlassPanel(Modifier.fillMaxWidth(), corner = 18.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), tint = p.accent) {
                                    Text(name, style = TextStyle(color = if (p.isDark) p.accent else p.accentDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                    Text(value, style = AeroText.body(15))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun questionSize(text: String): Float = when {
    text.length <= 4 -> 64f
    text.length <= 12 -> 44f
    text.length <= 40 -> 30f
    text.length <= 120 -> 23f
    else -> 19f
}

private fun answerSize(text: String): Float = when {
    text.length <= 16 -> 28f
    text.length <= 60 -> 21f
    else -> 17f
}

/** A rule of light with a pearl in the middle. */
@Composable
private fun GlossRule(modifier: Modifier = Modifier) {
    val p = LocalAero.current
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2
        drawLine(
            Brush.horizontalGradient(0f to Color.Transparent, 0.5f to p.accent, 1f to Color.Transparent),
            Offset(0f, y),
            Offset(size.width, y),
            2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val c = Offset(size.width / 2, y)
        drawCircle(Brush.radialGradient(listOf(Color.White, p.accent, p.accentDeep), c - Offset(2.dp.toPx(), 2.dp.toPx()), 9.dp.toPx()), 6.dp.toPx(), c)
    }
}

@Composable
private fun BottomActions(ui: FocusCardController.Ui, actions: FocusCardController, charge: Float) {
    val enabled = !ui.busy
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ui.stage == Stage.CARD) {
            if (!ui.revealed) {
                GelButton(
                    stringResource(R.string.show_answer),
                    actions::reveal,
                    Modifier.fillMaxWidth(),
                    tone = GelTone.ACCENT,
                    enabled = enabled,
                    charge = charge,
                    glyph = stringResource(R.string.kanji_answer),
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ui.choices.forEach { choice ->
                        val (tone, glyph) = look(choice.button)
                        GelButton(
                            choice.label,
                            { actions.answer(choice.button) },
                            Modifier.weight(1f),
                            tone = tone,
                            enabled = enabled,
                            charge = charge,
                            glyph = glyph,
                            detail = choice.interval,
                        )
                    }
                }
            }
            AnimatedVisibility(ui.snoozeOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.snooze_15_minutes to FocusCardController.Snooze.MINUTES_15,
                        R.string.snooze_1_hour to FocusCardController.Snooze.HOUR_1,
                        R.string.snooze_3_hours to FocusCardController.Snooze.HOURS_3,
                        R.string.snooze_until_tomorrow to FocusCardController.Snooze.UNTIL_TOMORROW,
                    ).forEach { (label, length) ->
                        GelButton(stringResource(label), { actions.snooze(length) }, Modifier.weight(1f), tone = GelTone.GOLD, compact = true, charge = charge)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                ui.mode == Mode.GATE && ui.stage != Stage.RECORDED -> {
                    if (ui.skipsLeft > 0) {
                        GelButton(
                            pluralStringResource(R.plurals.gate_skip, ui.skipsLeft, ui.skipsLeft),
                            actions::skip,
                            Modifier.weight(1f),
                            tone = GelTone.NEUTRAL,
                            compact = true,
                            charge = charge,
                        )
                    }
                    GelButton(stringResource(R.string.gate_leave), actions::leave, Modifier.weight(1f), tone = GelTone.NEUTRAL, compact = true, charge = charge)
                }
                ui.stage == Stage.RECORDED -> {
                    GelButton(stringResource(R.string.nudge_keep_going), actions::keepGoing, Modifier.weight(1f), tone = GelTone.ACCENT, glyph = stringResource(R.string.kanji_session))
                    GelButton(stringResource(R.string.done), actions::done, Modifier.weight(1f), tone = GelTone.NEUTRAL)
                }
                ui.mode == Mode.NUDGE && ui.stage == Stage.CARD -> GelButton(
                    stringResource(R.string.snooze),
                    actions::toggleSnooze,
                    Modifier.fillMaxWidth(),
                    tone = GelTone.NEUTRAL,
                    compact = true,
                    glyph = stringResource(R.string.kanji_snooze),
                )
                else -> GelButton(stringResource(R.string.done), actions::done, Modifier.fillMaxWidth(), tone = GelTone.NEUTRAL, compact = true)
            }
        }
    }
}

/** Each answer's gel and mark: ✕ fail, △ hard, ◯ pass, ◎ easy, as on a Japanese answer sheet. */
private fun look(button: Int): Pair<GelTone, String> = when (button) {
    AnswerButtons.AGAIN -> GelTone.FAIL to "✕"
    AnswerButtons.HARD -> GelTone.GOLD to "△"
    AnswerButtons.GOOD -> GelTone.PASS to "◯"
    else -> GelTone.ACCENT to "◎"
}

/** A nudge's card was answered: a glossy ◯ rings out. */
@Composable
private fun Recorded() {
    val p = LocalAero.current
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) { ring.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 180f)) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(170.dp).graphicsLayer { scaleX = ring.value; scaleY = ring.value }) {
            val stroke = 22.dp.toPx()
            val r = size.minDimension / 2 - stroke
            drawCircle(Brush.radialGradient(listOf(p.glow.copy(alpha = 0.5f), Color.Transparent), center, size.minDimension / 2), size.minDimension / 2)
            drawCircle(Brush.sweepGradient(listOf(p.pass, lerp(p.pass, Color.White, 0.5f), p.passDeep, p.pass), center), r, center, style = Stroke(stroke))
            rotate(-40f) {
                drawArc(Color.White.copy(alpha = 0.75f), 200f, 70f, false, center - Offset(r, r), Size(r * 2, r * 2), style = Stroke(stroke * 0.3f, cap = StrokeCap.Round))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.kanji_recorded), style = TextStyle(color = p.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = p.headings, shadow = titleShadow(p)))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.nudge_recorded), style = AeroText.body(17), textAlign = TextAlign.Center)
    }
}

@Composable
private fun AllDone() {
    val p = LocalAero.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Seal(stringResource(R.string.kanji_done), 96.dp, tone = GelTone.PASS)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.otsukaresama), style = TextStyle(color = p.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = p.headings, shadow = titleShadow(p)))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.all_cards_done), style = AeroText.body(16), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AeroSpinner(72.dp) }
}

/** A glossy ring, turning. */
@Composable
fun AeroSpinner(size: androidx.compose.ui.unit.Dp, palette: AeroPalette = LocalAero.current) {
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.14f
        val r = this.size.minDimension / 2 - stroke
        drawCircle(Color.White.copy(alpha = if (palette.isDark) 0.12f else 0.5f), r, style = Stroke(stroke))
        rotate(turn) {
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, palette.accent, palette.accentDeep), center),
                0f,
                270f,
                false,
                center - Offset(r, r),
                Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Over the card for the moment AnkiDroid syncs. */
@Composable
private fun SyncVeil() {
    val p = LocalAero.current
    Box(
        Modifier.fillMaxSize().drawBehind { drawRect(if (p.isDark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.6f)) },
        contentAlignment = Alignment.Center,
    ) { SyncBadge() }
}

/** "Syncing with AnkiWeb", on glass. */
@Composable
private fun SyncBadge() {
    GlassPanel(padding = PaddingValues(horizontal = 26.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AeroSpinner(36.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.kanji_sync), style = AeroText.title(20))
                Text(stringResource(R.string.syncing_cover), style = AeroText.body(15))
            }
        }
    }
}

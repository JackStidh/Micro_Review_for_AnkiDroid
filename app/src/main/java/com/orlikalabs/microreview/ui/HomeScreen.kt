package com.orlikalabs.microreview.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orlikalabs.microreview.Interruptions
import com.orlikalabs.microreview.R
import com.orlikalabs.microreview.ui.aero.AeroBackground
import com.orlikalabs.microreview.ui.aero.AeroCheck
import com.orlikalabs.microreview.ui.aero.AeroPalette
import com.orlikalabs.microreview.ui.aero.AeroText
import com.orlikalabs.microreview.ui.aero.Beads
import com.orlikalabs.microreview.ui.aero.GelButton
import com.orlikalabs.microreview.ui.aero.GelTone
import com.orlikalabs.microreview.ui.aero.GlassPanel
import com.orlikalabs.microreview.ui.aero.LocalAero
import com.orlikalabs.microreview.ui.aero.Orb
import com.orlikalabs.microreview.ui.aero.Pill
import com.orlikalabs.microreview.ui.aero.Seal
import com.orlikalabs.microreview.ui.aero.SectionTitle
import com.orlikalabs.microreview.ui.aero.ThemeChoice
import com.orlikalabs.microreview.ui.aero.ThemePicker
import com.orlikalabs.microreview.ui.aero.orbInk
import com.orlikalabs.microreview.ui.aero.titleShadow

sealed interface ScreenState {
    data object Loading : ScreenState
    data object ApiUnavailable : ScreenState
    data object PermissionMissing : ScreenState
    data object LoadFailed : ScreenState
    data object Ready : ScreenState
}

sealed interface CurrentCard {
    data object NotStarted : CurrentCard
    data object NoneDue : CurrentCard
    data class Showing(val question: String, val deckName: String) : CurrentCard
}

/** A deck as listed by AnkiDroid. Counts are null when AnkiDroid could not provide them. */
data class DeckItem(
    val id: Long,
    val name: String,
    val newCount: Int?,
    val learnCount: Int?,
    val reviewCount: Int?,
) {
    /** "Languages::Spanish::Verbs" is shown as "Verbs", under "Languages › Spanish". */
    val shortName: String get() = name.substringAfterLast("::")
    val parentPath: String? get() = name.substringBeforeLast("::", "").takeIf { it.isNotEmpty() }?.replace("::", " › ")
    val due: Int get() = (newCount ?: 0) + (learnCount ?: 0) + (reviewCount ?: 0)
}

/** Where things stand: what the hero panel says. */
data class HomeStatus(
    val nudgesOn: Boolean = false,
    val gateOn: Boolean = false,
    val gatedApps: Int = 0,
    /** Minutes of use until the next card, null if unknown or none. */
    val nextCardMinutes: Int? = null,
    val pause: Interruptions.Reason? = null,
    /** "15:30", while snoozed. */
    val snoozedUntil: String? = null,
    val pcPaired: Boolean = false,
    val answersWaiting: Boolean = false,
)

/** Something to allow before the cards can come. */
enum class SetupNeed { USAGE_ACCESS, OVERLAY, NOTIFICATIONS, BATTERY }

class HomeActions(
    val onToggleDeck: (Long) -> Unit,
    val onMoveDeck: (deckId: Long, offset: Int) -> Unit,
    val onGrantPermission: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onCardNow: () -> Unit,
    val onSnooze: () -> Unit,
    val onResume: () -> Unit,
    val onSync: () -> Unit,
    val onTheme: (AeroPalette) -> Unit,
    val onThemeLock: () -> Unit,
    val onOpenMove: () -> Unit,
    val onFix: (SetupNeed) -> Unit,
)

@Composable
fun HomeScreen(
    state: ScreenState,
    decks: List<DeckItem>,
    selectedDeckIds: List<Long>,
    currentCard: CurrentCard,
    status: HomeStatus,
    needs: List<SetupNeed>,
    snoozeMinutes: Int,
    actions: HomeActions,
    themeChoice: ThemeChoice = ThemeChoice(),
    move: MoveSummary = MoveSummary(),
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    AeroBackground(Modifier.fillMaxSize()) {
        val decksById = decks.associateBy { it.id }
        val selectedDecks = selectedDeckIds.mapNotNull { decksById[it] }
        val tree = remember(decks) { buildDeckTree(decks) }
        // Full names of the unfolded decks. Everything starts folded, like AnkiDroid's deck list.
        var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }
        val selectedNames = selectedDecks.map { it.name }.toSet()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { Header(actions.onOpenSettings) }
            when (state) {
                ScreenState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { AeroSpinner(64.dp) }
                }
                ScreenState.ApiUnavailable -> item(key = "error") {
                    Problem(R.string.error_api_unavailable_title, R.string.error_api_unavailable, R.string.retry, actions.onRetry)
                }
                ScreenState.PermissionMissing -> item(key = "error") {
                    Problem(R.string.error_permission_title, R.string.error_permission, R.string.grant_permission, actions.onGrantPermission)
                }
                ScreenState.LoadFailed -> item(key = "error") {
                    Problem(R.string.error_load_failed_title, R.string.error_load_failed, R.string.retry, actions.onRetry)
                }
                ScreenState.Ready -> {
                    item(key = "hero") { Hero(status, dueTotal(selectedDecks), snoozeMinutes, actions) }
                    item(key = "move") { MoveEntry(move, actions.onOpenMove) }
                    if (needs.isNotEmpty()) item(key = "needs") { Needs(needs, actions.onFix) }
                    item(key = "next") { NextUp(currentCard) }
                    item(key = "order-title") {
                        SectionTitle(stringResource(R.string.kanji_order), stringResource(R.string.review_order), stringResource(R.string.review_order_explanation))
                    }
                    if (selectedDecks.isEmpty()) {
                        item(key = "order-empty") { Text(stringResource(R.string.review_order_empty), Modifier.padding(horizontal = 6.dp), style = AeroText.soft(14)) }
                    }
                    itemsIndexed(selectedDecks, key = { _, deck -> "order-${deck.id}" }) { index, deck ->
                        SelectedDeckRow(
                            deck,
                            index,
                            isLast = index == selectedDecks.lastIndex,
                            onMove = { actions.onMoveDeck(deck.id, it) },
                            onRemove = { actions.onToggleDeck(deck.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "tree-title") { SectionTitle(stringResource(R.string.kanji_decks), stringResource(R.string.all_decks)) }
                    item(key = "tree") {
                        GlassPanel(Modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                            if (decks.isEmpty()) {
                                Text(stringResource(R.string.no_decks), Modifier.padding(14.dp), style = AeroText.body())
                            }
                            flattenVisible(tree, expanded).forEach { node ->
                                val deckId = node.deck?.id
                                DeckTreeRow(
                                    node = node,
                                    isExpanded = node.fullName in expanded,
                                    position = if (deckId == null) -1 else selectedDeckIds.indexOf(deckId),
                                    includedIn = selectedAncestor(node.fullName, selectedNames),
                                    onToggleExpanded = { expanded = if (node.fullName in expanded) expanded - node.fullName else expanded + node.fullName },
                                    onToggleSelected = { if (deckId != null) actions.onToggleDeck(deckId) },
                                )
                            }
                        }
                    }
                    item(key = "theme-title") {
                        SectionTitle(stringResource(R.string.kanji_theme), stringResource(R.string.theme_title), stringResource(R.string.theme_detail))
                    }
                    item(key = "themes") { ThemePicker(LocalAero.current, themeChoice, actions.onTheme, actions.onThemeLock) }
                }
            }
            item(key = "version") { AppVersion() }
        }
    }
}

/** Cards due in the selected decks, a selected subdeck of a selected deck counted once. */
private fun dueTotal(selected: List<DeckItem>): Int {
    val names = selected.map { it.name }.toSet()
    return selected.filter { deck -> names.none { deck.name.startsWith("$it::") } }.sumOf { it.due }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    val p = LocalAero.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Seal(stringResource(R.string.kanji_logo), 54.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_title),
                style = TextStyle(color = p.ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = p.headings, shadow = titleShadow(p)),
            )
            Text(stringResource(R.string.home_subtitle, p.kanji, p.name), style = AeroText.soft(13))
        }
        Orb({ onOpenSettings() }, size = 48.dp, contentDescription = stringResource(R.string.settings_title)) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title), tint = orbInk(), modifier = Modifier.size(26.dp))
        }
    }
}

/** Move on the home screen: today's three habits, or what it offers before a program is picked. */
data class MoveSummary(val setUp: Boolean = false, val doneToday: Int = 0)

@Composable
private fun MoveEntry(move: MoveSummary, onOpen: () -> Unit) {
    GlassPanel(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(onClick = onOpen),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seal(stringResource(R.string.kanji_move), 44.dp, tone = if (move.setUp && move.doneToday >= 3) GelTone.PASS else GelTone.ACCENT)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.move_title), style = AeroText.title(19))
                Text(
                    if (move.setUp) stringResource(R.string.move_home_detail, move.doneToday) else stringResource(R.string.move_home_off),
                    style = AeroText.soft(13),
                )
            }
            if (move.setUp) {
                Spacer(Modifier.width(8.dp))
                Beads(move.doneToday, 3)
            }
        }
    }
}

/** Where things stand, big: the orb, what it means, and what can be done about it. */
@Composable
private fun Hero(status: HomeStatus, due: Int, snoozeMinutes: Int, actions: HomeActions) {
    val p = LocalAero.current
    val snoozed = status.snoozedUntil != null
    val (title, detail) = statusTexts(status)
    GlassPanel(Modifier.fillMaxWidth(), corner = 32.dp, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusOrb(status)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = AeroText.title(20).copy(shadow = titleShadow(p)))
                Spacer(Modifier.height(4.dp))
                Text(detail, style = AeroText.soft(14))
            }
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(pluralStringResource(R.plurals.home_due, due, due), color = p.accent)
            if (status.gateOn) Pill(pluralStringResource(R.plurals.home_gated_apps, status.gatedApps, status.gatedApps), color = p.fail)
            if (status.nudgesOn) Pill(stringResource(R.string.home_nudges_on), color = p.pass)
            if (status.pcPaired) Pill(stringResource(R.string.home_pc_paired), color = p.gold)
            if (status.answersWaiting) Pill(stringResource(R.string.home_answers_waiting), color = p.neutralDeep)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GelButton(stringResource(R.string.home_card_now), actions.onCardNow, Modifier.weight(1f), tone = GelTone.ACCENT, glyph = stringResource(R.string.kanji_now))
            if (snoozed) {
                GelButton(stringResource(R.string.monitor_resume), actions.onResume, Modifier.weight(1f), tone = GelTone.PASS, glyph = stringResource(R.string.kanji_resume))
            } else {
                GelButton(stringResource(R.string.home_snooze), actions.onSnooze, Modifier.weight(1f), tone = GelTone.GOLD, glyph = stringResource(R.string.kanji_snooze), detail = stringResource(R.string.home_snooze_for, snoozeMinutes))
            }
        }
        Spacer(Modifier.height(10.dp))
        GelButton(stringResource(R.string.focus_ankiweb_sync_now), actions.onSync, Modifier.fillMaxWidth(), tone = GelTone.NEUTRAL, compact = true, glyph = stringResource(R.string.kanji_sync))
    }
}

@Composable
private fun statusTexts(status: HomeStatus): Pair<String, String> = when {
    status.snoozedUntil != null -> stringResource(R.string.home_snoozed, status.snoozedUntil) to stringResource(R.string.home_snoozed_detail)
    status.pause == Interruptions.Reason.PC_IN_USE -> stringResource(R.string.home_paused) to stringResource(R.string.monitor_pc_in_use)
    status.pause == Interruptions.Reason.BLUETOOTH -> stringResource(R.string.home_paused) to stringResource(R.string.monitor_bluetooth)
    status.pause == Interruptions.Reason.QUIET_HOURS -> stringResource(R.string.home_paused) to stringResource(R.string.monitor_quiet_hours)
    status.pause == Interruptions.Reason.DO_NOT_DISTURB -> stringResource(R.string.home_paused) to stringResource(R.string.monitor_do_not_disturb)
    status.pause == Interruptions.Reason.IN_CALL -> stringResource(R.string.home_paused) to stringResource(R.string.monitor_in_call)
    !status.nudgesOn && !status.gateOn -> stringResource(R.string.home_off) to stringResource(R.string.home_off_detail)
    status.nudgesOn && status.nextCardMinutes != null -> stringResource(R.string.home_next_card) to stringResource(R.string.monitor_next_card, status.nextCardMinutes)
    status.nudgesOn && status.gateOn -> stringResource(R.string.home_on) to stringResource(R.string.monitor_on_both)
    status.nudgesOn -> stringResource(R.string.home_on) to stringResource(R.string.monitor_on_nudges)
    else -> stringResource(R.string.home_on) to stringResource(R.string.monitor_on_gate)
}

/** The big orb: the minutes until the next card, or the kanji of why there is none. */
@Composable
private fun StatusOrb(status: HomeStatus) {
    val p = LocalAero.current
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "glow")
    val (tone, kanji) = when {
        status.snoozedUntil != null -> GelTone.GOLD to stringResource(R.string.kanji_snooze)
        status.pause != null -> GelTone.NEUTRAL to stringResource(R.string.kanji_pause)
        !status.nudgesOn && !status.gateOn -> GelTone.NEUTRAL to stringResource(R.string.kanji_off)
        status.nudgesOn && status.nextCardMinutes != null -> GelTone.ACCENT to null
        else -> GelTone.PASS to stringResource(R.string.kanji_gate)
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp).drawBehind {
        drawCircle(Brush.radialGradient(listOf(p.glow.copy(alpha = 0.25f + 0.35f * pulse), Color.Transparent), center, size.minDimension / 2), size.minDimension / 2)
    }) {
        Orb(onClick = null, size = 80.dp, tone = tone) {
            if (kanji == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${status.nextCardMinutes}", style = TextStyle(color = orbInk(tone), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold))
                    Text(stringResource(R.string.home_minutes_short), style = TextStyle(color = orbInk(tone), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            } else {
                Text(kanji, style = TextStyle(color = orbInk(tone), fontSize = 34.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun Needs(needs: List<SetupNeed>, onFix: (SetupNeed) -> Unit) {
    val p = LocalAero.current
    GlassPanel(Modifier.fillMaxWidth(), tint = p.gold) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seal(stringResource(R.string.kanji_setup), 34.dp, tone = GelTone.GOLD)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.focus_setup), style = AeroText.title(18))
        }
        needs.forEach { need ->
            val (title, detail) = when (need) {
                SetupNeed.USAGE_ACCESS -> R.string.focus_setup_usage to R.string.focus_setup_usage_detail
                SetupNeed.OVERLAY -> R.string.focus_setup_overlay to R.string.focus_setup_overlay_detail
                SetupNeed.NOTIFICATIONS -> R.string.focus_setup_notifications to R.string.focus_setup_notifications_detail
                SetupNeed.BATTERY -> R.string.setup_battery to R.string.setup_battery_detail
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = AeroText.body(15).copy(fontWeight = FontWeight.SemiBold))
                    Text(stringResource(detail), style = AeroText.soft(13))
                }
                Spacer(Modifier.width(10.dp))
                GelButton(stringResource(R.string.focus_setup_allow), { onFix(need) }, tone = GelTone.GOLD, compact = true)
            }
        }
    }
}

/** The card that comes next, like a flashcard on the desk. */
@Composable
private fun NextUp(card: CurrentCard) {
    val p = LocalAero.current
    GlassPanel(Modifier.fillMaxWidth().animateContentSize(), corner = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seal(stringResource(R.string.kanji_next), 30.dp, tone = GelTone.NEUTRAL)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.home_next_up), style = AeroText.soft(14).copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(10.dp))
        when (card) {
            is CurrentCard.Showing -> {
                Text(
                    card.question,
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(color = p.ink, fontSize = if (card.question.length <= 12) 36.sp else 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, shadow = titleShadow(p)),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.deckName.isNotEmpty()) {
                    Text(card.deckName.replace("::", " › "), Modifier.fillMaxWidth().padding(top = 8.dp), style = AeroText.soft(13).copy(textAlign = TextAlign.Center))
                }
            }
            CurrentCard.NoneDue -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.otsukaresama), style = AeroText.title(24))
                Text(stringResource(R.string.all_cards_done_next), style = AeroText.soft(14).copy(textAlign = TextAlign.Center))
            }
            CurrentCard.NotStarted -> Text(stringResource(R.string.current_card_none), style = AeroText.body(15))
        }
    }
}

@Composable
private fun SelectedDeckRow(deck: DeckItem, position: Int, isLast: Boolean, onMove: (Int) -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Orb(onClick = null, size = 34.dp, tone = GelTone.ACCENT) {
                Text("${position + 1}", style = TextStyle(color = orbInk(GelTone.ACCENT), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                deck.parentPath?.let { Text(it, style = AeroText.soft(11), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(deck.shortName, style = AeroText.title(16), maxLines = 2, overflow = TextOverflow.Ellipsis)
                DeckCounts(deck)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Orb({ onMove(-1) }, size = 34.dp, enabled = position > 0, contentDescription = stringResource(R.string.move_up)) {
                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up), tint = orbInk(), modifier = Modifier.size(22.dp))
                }
                Orb({ onMove(1) }, size = 34.dp, enabled = !isLast, contentDescription = stringResource(R.string.move_down)) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down), tint = orbInk(), modifier = Modifier.size(22.dp))
                }
                Orb(onRemove, size = 34.dp, tone = GelTone.FAIL, contentDescription = stringResource(R.string.remove_deck)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.remove_deck), tint = orbInk(GelTone.FAIL), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DeckTreeRow(
    node: DeckNode,
    isExpanded: Boolean,
    position: Int,
    includedIn: String?,
    onToggleExpanded: () -> Unit,
    onToggleSelected: () -> Unit,
) {
    val deck = node.deck
    val hasChildren = node.children.isNotEmpty()
    // Already reviewed through a selected parent. A deck selected on its own stays active so it can be removed.
    val covered = includedIn != null && position < 0
    val selectable = deck != null && !covered
    val turn by animateFloatAsState(if (isExpanded) 90f else 0f, label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = selectable || hasChildren) { if (selectable) onToggleSelected() else onToggleExpanded() }
            .padding(start = (8 + node.depth.coerceAtMost(MAX_INDENT_LEVELS) * 18).dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasChildren) {
            Orb(onToggleExpanded, size = 30.dp, contentDescription = stringResource(if (isExpanded) R.string.collapse else R.string.expand)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = orbInk(), modifier = Modifier.size(20.dp).rotate(turn))
            }
        } else {
            Spacer(Modifier.width(30.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp).alpha(if (covered) 0.5f else 1f)) {
            Text(node.shortName, style = AeroText.body(16).copy(fontWeight = if (node.depth == 0) FontWeight.SemiBold else FontWeight.Normal), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (covered) {
                Text(stringResource(R.string.included_with_parent, includedIn!!.substringAfterLast("::")), style = AeroText.soft(12))
            } else if (deck != null) {
                DeckCounts(deck)
            }
        }
        if (position >= 0) {
            Orb(onClick = null, size = 26.dp, tone = GelTone.ACCENT) {
                Text("${position + 1}", style = TextStyle(color = orbInk(GelTone.ACCENT), fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(8.dp))
        }
        if (deck != null) AeroCheck(position >= 0 || covered, onToggleSelected, enabled = !covered)
    }
}

/** Deeper levels aren't indented further, so names keep enough room on a phone screen. */
private const val MAX_INDENT_LEVELS = 4

/** New, learning, review, as little glossy counters, colored as in AnkiDroid's deck list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckCounts(deck: DeckItem) {
    if (deck.newCount == null || deck.learnCount == null || deck.reviewCount == null) return
    val p = LocalAero.current
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Pill(stringResource(R.string.count_new, deck.newCount), color = if (deck.newCount > 0) Color(0xFF2196F3) else p.inkSoft)
        Pill(stringResource(R.string.count_learn, deck.learnCount), color = if (deck.learnCount > 0) Color(0xFFE53935) else p.inkSoft)
        Pill(stringResource(R.string.count_review, deck.reviewCount), color = if (deck.reviewCount > 0) Color(0xFF43A047) else p.inkSoft)
    }
}

@Composable
private fun Problem(title: Int, message: Int, button: Int, onClick: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), corner = 30.dp, padding = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Seal(stringResource(R.string.kanji_problem), 64.dp, tone = GelTone.FAIL)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(title), style = AeroText.title(20).copy(textAlign = TextAlign.Center))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(message), style = AeroText.body(15).copy(textAlign = TextAlign.Center))
            Spacer(Modifier.height(20.dp))
            GelButton(stringResource(button), onClick, tone = GelTone.ACCENT)
        }
    }
}

/** The installed version, from `versionName` in app/build.gradle.kts. */
@Composable
private fun AppVersion() {
    val context = LocalContext.current
    val versionName = remember {
        @Suppress("DEPRECATION") // The flags overload needs API 33.
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
    Text(
        stringResource(R.string.app_version, stringResource(R.string.app_name), versionName ?: "?"),
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        style = AeroText.soft(12).copy(textAlign = TextAlign.Center),
    )
}

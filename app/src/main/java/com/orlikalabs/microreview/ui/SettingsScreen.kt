package com.orlikalabs.microreview.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orlikalabs.microreview.NudgeClock
import com.orlikalabs.microreview.R
import com.orlikalabs.microreview.ui.aero.AeroBackground
import com.orlikalabs.microreview.ui.aero.AeroCheck
import com.orlikalabs.microreview.ui.aero.AeroDialog
import com.orlikalabs.microreview.ui.aero.AeroPalette
import com.orlikalabs.microreview.ui.aero.AeroSwitch
import com.orlikalabs.microreview.ui.aero.AeroText
import com.orlikalabs.microreview.ui.aero.ChoiceDialog
import com.orlikalabs.microreview.ui.aero.GelButton
import com.orlikalabs.microreview.ui.aero.GelTone
import com.orlikalabs.microreview.ui.aero.GlassPanel
import com.orlikalabs.microreview.ui.aero.LocalAero
import com.orlikalabs.microreview.ui.aero.Orb
import com.orlikalabs.microreview.ui.aero.Pill
import com.orlikalabs.microreview.ui.aero.SectionTitle
import com.orlikalabs.microreview.ui.aero.AeroThemes
import com.orlikalabs.microreview.ui.aero.ThemeChoice
import com.orlikalabs.microreview.ui.aero.ThemePicker
import com.orlikalabs.microreview.ui.aero.orbInk
import com.orlikalabs.microreview.ui.aero.titleShadow

/** An app of the launcher: its name and package. */
data class LauncherApp(val name: String, val packageName: String)

/** What the settings show, read from the stored settings each time something changes. */
data class SettingsState(
    val themeChoice: ThemeChoice = ThemeChoice(),
    val missingUsage: Boolean = false,
    val missingOverlay: Boolean = false,
    val missingNotifications: Boolean = false,
    val snoozedUntil: String? = null,
    val snoozeMinutes: Int = 30,
    val snoozeLiftsGate: Boolean = false,
    val nudges: Boolean = false,
    val defaultInterval: Int = 20,
    /** (app name, minutes), sorted by name. */
    val appIntervals: List<Pair<LauncherApp, Int>> = emptyList(),
    val passFail: Boolean = true,
    val noteFields: Boolean = true,
    val gate: Boolean = false,
    val gatedApps: List<LauncherApp> = emptyList(),
    val gateCards: Int = 5,
    val skipsPerDay: Int = 1,
    val bluetooth: Boolean = true,
    val bluetoothPermission: Boolean = true,
    val doNotDisturb: Boolean = true,
    val calls: Boolean = true,
    /** "22:00 – 07:00". */
    val quietHours: List<String> = emptyList(),
    /** "k3p9-x2mq-7rtv-w8de", null if not paired. */
    val pcCode: String? = null,
    val pausePc: Boolean = true,
    val ankiPending: Boolean = false,
    val ankiAsked: String = "",
    val canReport: Boolean = false,
    val version: String = "",
)

/** What the settings can do. */
interface SettingsActions {
    fun back()
    fun fixUsage()
    fun fixOverlay()
    fun fixNotifications()
    fun snooze(minutes: Int?)
    fun resume()
    fun setSnoozeMinutes(minutes: Int)
    fun setSnoozeLiftsGate(on: Boolean)
    fun setNudges(on: Boolean)
    fun setDefaultInterval(minutes: Int)
    fun setAppInterval(app: String, minutes: Int?)
    fun showCardNow()
    fun setPassFail(on: Boolean)
    fun setNoteFields(on: Boolean)
    fun setGate(on: Boolean)
    fun setGatedApps(apps: Set<String>)
    fun setGateCards(cards: Int)
    fun setSkips(skips: Int)
    fun setBluetooth(on: Boolean)
    fun askBluetooth()
    fun setDoNotDisturb(on: Boolean)
    fun setCalls(on: Boolean)
    fun addQuietHours()
    fun clearQuietHours()
    fun pair(code: String): Boolean
    fun unpair()
    fun setPausePc(on: Boolean)
    fun syncNow()
    /** A theme tapped: chosen when locked, ticked in or out of the shuffle otherwise ([AeroThemes.tap]). */
    fun chooseTheme(palette: AeroPalette)
    fun toggleThemeLock()
    fun diagnostics(): String
    fun copyDiagnostics()
    fun reportIssue()
    /** The launcher's apps, loaded in the background; null until they are. */
    fun apps(): List<LauncherApp>?
}

val DEFAULT_INTERVALS = listOf(NudgeClock.NEVER, 5, 10, 15, 20, 30, 45, 60, 90, 120)
val APP_INTERVALS = listOf(NudgeClock.NEVER, 2, 3, 5, 10, 15, 20, 30, 60)
val SNOOZE_LENGTHS = listOf(15, 30, 60, 120, 240)
val GATE_CARDS = listOf(1, 2, 3, 5, 7, 10, 15, 20)
val SKIPS = listOf(0, 1, 2, 3)

/** The dialog open over the settings. */
private sealed interface Open {
    data class Choice(val title: String, val options: List<String>, val selected: Int, val onPick: (Int) -> Unit) : Open
    data object GatedApps : Open
    data object PickApp : Open
    data class AppInterval(val app: LauncherApp, val current: Int?) : Open
    data object Pairing : Open
    data object Licenses : Open
}

@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions, appsLoaded: Int) {
    var open by remember { mutableStateOf<Open?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val cancel = stringResource(android.R.string.cancel)
    val every: @Composable (Int) -> String = { minutes ->
        if (minutes == NudgeClock.NEVER) stringResource(R.string.focus_interval_never) else stringResource(R.string.focus_interval_every, minutes)
    }
    val intervalLabels = DEFAULT_INTERVALS.map { every(it) }
    val appIntervalLabels = listOf(stringResource(R.string.focus_interval_default)) + APP_INTERVALS.map { every(it) }
    val snoozeLabels = SNOOZE_LENGTHS.map { stringResource(R.string.minutes, it) }
    val gateCardLabels = GATE_CARDS.map { pluralStringResource(R.plurals.focus_cards, it, it) }

    AeroBackground(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "top") { TopRow(actions::back) }

            if (state.missingUsage || state.missingOverlay || state.missingNotifications) {
                item(key = "setup") {
                    Section(stringResource(R.string.kanji_setup), stringResource(R.string.focus_setup), tint = LocalAero.current.gold) {
                        Text(stringResource(R.string.focus_setup_restricted), style = AeroText.soft(13))
                        if (state.missingUsage) Action(stringResource(R.string.focus_setup_usage), stringResource(R.string.focus_setup_usage_detail), stringResource(R.string.focus_setup_allow), actions::fixUsage, GelTone.GOLD)
                        if (state.missingOverlay) Action(stringResource(R.string.focus_setup_overlay), stringResource(R.string.focus_setup_overlay_detail), stringResource(R.string.focus_setup_allow), actions::fixOverlay, GelTone.GOLD)
                        if (state.missingNotifications) Action(stringResource(R.string.focus_setup_notifications), stringResource(R.string.focus_setup_notifications_detail), stringResource(R.string.focus_setup_allow), actions::fixNotifications, GelTone.GOLD)
                    }
                }
            }

            item(key = "snooze") {
                Section(stringResource(R.string.kanji_snooze), stringResource(R.string.focus_snooze), stringResource(R.string.focus_snooze_detail)) {
                    if (state.snoozedUntil != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.monitor_snoozed_until, state.snoozedUntil), Modifier.weight(1f), style = AeroText.body(15))
                            GelButton(stringResource(R.string.monitor_resume), actions::resume, tone = GelTone.PASS, compact = true)
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GelButton(stringResource(R.string.snooze_30_minutes), { actions.snooze(30) }, tone = GelTone.GOLD, compact = true)
                            GelButton(stringResource(R.string.snooze_1_hour), { actions.snooze(60) }, tone = GelTone.GOLD, compact = true)
                            GelButton(stringResource(R.string.snooze_2_hours), { actions.snooze(120) }, tone = GelTone.GOLD, compact = true)
                            GelButton(stringResource(R.string.snooze_until_tomorrow), { actions.snooze(null) }, tone = GelTone.GOLD, compact = true)
                        }
                    }
                    Value(stringResource(R.string.focus_snooze_length), stringResource(R.string.minutes, state.snoozeMinutes)) {
                        val title = it
                        open = Open.Choice(title, snoozeLabels, SNOOZE_LENGTHS.indexOf(state.snoozeMinutes)) { i -> actions.setSnoozeMinutes(SNOOZE_LENGTHS[i]) }
                    }
                    Switch(stringResource(R.string.focus_snooze_lifts_gate), stringResource(R.string.focus_snooze_lifts_gate_detail), state.snoozeLiftsGate, actions::setSnoozeLiftsGate)
                }
            }

            item(key = "nudges") {
                Section(stringResource(R.string.kanji_nudge), stringResource(R.string.focus_nudges)) {
                    Switch(stringResource(R.string.focus_nudges_enable), stringResource(R.string.focus_nudges_detail), state.nudges, actions::setNudges)
                    AnimatedVisibility(state.nudges) {
                        Column {
                            Value(stringResource(R.string.focus_default_interval), every(state.defaultInterval)) { title ->
                                open = Open.Choice(title, intervalLabels, DEFAULT_INTERVALS.indexOf(state.defaultInterval)) { i -> actions.setDefaultInterval(DEFAULT_INTERVALS[i]) }
                            }
                            Text(stringResource(R.string.focus_app_intervals), Modifier.padding(top = 12.dp), style = AeroText.body(16).copy(fontWeight = FontWeight.SemiBold))
                            if (state.appIntervals.isEmpty()) {
                                Text(stringResource(R.string.focus_app_intervals_none), style = AeroText.soft(13))
                            }
                            state.appIntervals.forEach { (app, minutes) ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = Open.AppInterval(app, minutes) }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(app.name, Modifier.weight(1f), style = AeroText.body(15))
                                    Pill(every(minutes), color = LocalAero.current.accent)
                                }
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GelButton(stringResource(R.string.settings_add_app), { open = Open.PickApp }, tone = GelTone.NEUTRAL, compact = true)
                                GelButton(stringResource(R.string.focus_show_now), actions::showCardNow, tone = GelTone.ACCENT, compact = true, glyph = stringResource(R.string.kanji_now))
                            }
                        }
                    }
                }
            }

            item(key = "answers") {
                Section(stringResource(R.string.kanji_answer), stringResource(R.string.focus_answers)) {
                    Switch(stringResource(R.string.focus_pass_fail), stringResource(R.string.focus_pass_fail_detail), state.passFail, actions::setPassFail)
                    Switch(stringResource(R.string.focus_note_fields), stringResource(R.string.focus_note_fields_detail), state.noteFields, actions::setNoteFields)
                }
            }

            item(key = "gate") {
                Section(stringResource(R.string.kanji_gate), stringResource(R.string.focus_gate)) {
                    Switch(stringResource(R.string.focus_gate_enable), stringResource(R.string.focus_gate_detail), state.gate, actions::setGate)
                    AnimatedVisibility(state.gate) {
                        Column {
                            Action(
                                stringResource(R.string.focus_gate_apps),
                                if (state.gatedApps.isEmpty()) stringResource(R.string.focus_gate_apps_none) else state.gatedApps.joinToString(", ") { it.name },
                                stringResource(R.string.focus_choose),
                                { open = Open.GatedApps },
                            )
                            Value(stringResource(R.string.focus_gate_cards), pluralStringResource(R.plurals.focus_cards, state.gateCards, state.gateCards)) { title ->
                                open = Open.Choice(title, gateCardLabels, GATE_CARDS.indexOf(state.gateCards)) { i -> actions.setGateCards(GATE_CARDS[i]) }
                            }
                            Value(stringResource(R.string.focus_gate_skips), stringResource(R.string.focus_gate_skips_detail, state.skipsPerDay)) { title ->
                                open = Open.Choice(title, SKIPS.map { "$it" }, SKIPS.indexOf(state.skipsPerDay)) { i -> actions.setSkips(SKIPS[i]) }
                            }
                        }
                    }
                }
            }

            item(key = "pauses") {
                Section(stringResource(R.string.kanji_pause), stringResource(R.string.focus_pauses)) {
                    Switch(stringResource(R.string.focus_bluetooth), stringResource(R.string.focus_bluetooth_detail), state.bluetooth, actions::setBluetooth)
                    if (state.bluetooth && !state.bluetoothPermission) {
                        Action(stringResource(R.string.focus_bluetooth_permission), stringResource(R.string.focus_bluetooth_permission_detail), stringResource(R.string.focus_setup_allow), actions::askBluetooth, GelTone.GOLD)
                    }
                    Switch(stringResource(R.string.focus_dnd), stringResource(R.string.focus_dnd_detail), state.doNotDisturb, actions::setDoNotDisturb)
                    Switch(stringResource(R.string.focus_call), stringResource(R.string.focus_call_detail), state.calls, actions::setCalls)
                    Text(stringResource(R.string.focus_quiet), Modifier.padding(top = 12.dp), style = AeroText.body(16).copy(fontWeight = FontWeight.SemiBold))
                    if (state.quietHours.isEmpty()) Text(stringResource(R.string.focus_quiet_none), style = AeroText.soft(13))
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.quietHours.forEach { Pill(it, color = LocalAero.current.accent) }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GelButton(stringResource(R.string.focus_quiet_add), actions::addQuietHours, tone = GelTone.NEUTRAL, compact = true)
                        if (state.quietHours.isNotEmpty()) GelButton(stringResource(R.string.focus_quiet_clear), actions::clearQuietHours, tone = GelTone.FAIL, compact = true)
                    }
                }
            }

            item(key = "pc") {
                Section(stringResource(R.string.kanji_pc), stringResource(R.string.focus_pc)) {
                    if (state.pcCode == null) {
                        Action(stringResource(R.string.focus_pc_pair), stringResource(R.string.focus_pc_pair_detail), stringResource(R.string.focus_pc_enter_code), { open = Open.Pairing }, GelTone.ACCENT)
                    } else {
                        Switch(stringResource(R.string.focus_pc_pause), stringResource(R.string.focus_pc_pause_detail), state.pausePc, actions::setPausePc)
                        Action(stringResource(R.string.focus_pc_paired), state.pcCode, stringResource(R.string.focus_pc_unpair), actions::unpair, GelTone.FAIL)
                    }
                }
            }

            item(key = "ankiweb") {
                Section(stringResource(R.string.kanji_sync), stringResource(R.string.focus_ankiweb)) {
                    Action(
                        stringResource(R.string.focus_ankiweb_sync),
                        stringResource(if (state.ankiPending) R.string.focus_ankiweb_pending else R.string.focus_ankiweb_synced, state.ankiAsked),
                        stringResource(R.string.focus_ankiweb_sync_now),
                        actions::syncNow,
                        GelTone.ACCENT,
                    )
                }
            }

            item(key = "theme") {
                Section(stringResource(R.string.kanji_theme), stringResource(R.string.theme_title), stringResource(R.string.theme_detail)) {
                    ThemePicker(LocalAero.current, state.themeChoice, actions::chooseTheme, actions::toggleThemeLock)
                }
            }

            item(key = "about") {
                Section(stringResource(R.string.kanji_about), stringResource(R.string.settings_help), state.version) {
                    if (state.canReport) Action(stringResource(R.string.report_issue), stringResource(R.string.report_issue_summary), stringResource(R.string.settings_open), actions::reportIssue)
                    Action(stringResource(R.string.licenses_title), stringResource(R.string.licenses_summary), stringResource(R.string.settings_open), { open = Open.Licenses })
                    Action(stringResource(R.string.focus_diagnostics), stringResource(R.string.settings_diagnostics_detail), stringResource(if (showDiagnostics) R.string.settings_hide else R.string.settings_show), { showDiagnostics = !showDiagnostics })
                    AnimatedVisibility(showDiagnostics) {
                        Column {
                            GlassPanel(Modifier.fillMaxWidth().padding(top = 8.dp), corner = 16.dp, padding = PaddingValues(12.dp), solid = true) {
                                Text(remember(appsLoaded, state) { actions.diagnostics() }, style = AeroText.soft(11).copy(fontFamily = FontFamily.Monospace))
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GelButton(stringResource(R.string.focus_diagnostics_copy), actions::copyDiagnostics, tone = GelTone.NEUTRAL, compact = true)
                            }
                        }
                    }
                }
            }
        }
    }

    when (val dialog = open) {
        null -> Unit
        is Open.Choice -> ChoiceDialog(dialog.title, dialog.options, dialog.selected, { dialog.onPick(it); open = null }, { open = null }, cancel = cancel)
        Open.GatedApps -> AppsDialog(
            title = stringResource(R.string.focus_gate_apps),
            apps = actions.apps(),
            initial = state.gatedApps.map { it.packageName }.toSet(),
            multiple = true,
            onDone = { actions.setGatedApps(it); open = null },
            onDismiss = { open = null },
        )
        Open.PickApp -> AppsDialog(
            title = stringResource(R.string.focus_app_intervals),
            apps = actions.apps(),
            initial = emptySet(),
            multiple = false,
            onDone = { picked ->
                val app = actions.apps()?.firstOrNull { it.packageName in picked }
                open = app?.let { Open.AppInterval(it, null) }
            },
            onDismiss = { open = null },
        )
        is Open.AppInterval -> ChoiceDialog(
            dialog.app.name,
            appIntervalLabels,
            if (dialog.current == null) 0 else APP_INTERVALS.indexOf(dialog.current) + 1,
            { i ->
                actions.setAppInterval(dialog.app.packageName, if (i == 0) null else APP_INTERVALS[i - 1])
                open = null
            },
            { open = null },
            cancel = cancel,
        )
        Open.Pairing -> PairingDialog(actions::pair) { open = null }
        Open.Licenses -> AeroLicensesDialog { open = null }
    }
}

@Composable
private fun TopRow(onBack: () -> Unit) {
    val p = LocalAero.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Orb(onBack, size = 46.dp, contentDescription = stringResource(R.string.settings_back)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back), tint = orbInk(), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.settings_title), style = AeroText.title(28).copy(fontWeight = FontWeight.ExtraBold, shadow = titleShadow(p)))
            Text(stringResource(R.string.settings_subtitle), style = AeroText.soft(13))
        }
    }
}

/** A section: its seal and title, on a glass panel. */
@Composable
private fun Section(kanji: String, title: String, subtitle: String? = null, tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionTitle(kanji, title, subtitle)
        GlassPanel(Modifier.fillMaxWidth(), tint = tint, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), content = content)
    }
}

/** A setting that is on or off. */
@Composable
private fun Switch(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AeroText.body(16).copy(fontWeight = FontWeight.SemiBold))
            Text(summary, style = AeroText.soft(13))
        }
        Spacer(Modifier.width(12.dp))
        AeroSwitch(checked, onChange)
    }
}

/** A setting with something to do about it. */
@Composable
private fun Action(title: String, summary: String, button: String, onClick: () -> Unit, tone: GelTone = GelTone.NEUTRAL) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AeroText.body(16).copy(fontWeight = FontWeight.SemiBold))
            Text(summary, style = AeroText.soft(13))
        }
        Spacer(Modifier.width(12.dp))
        GelButton(button, onClick, tone = tone, compact = true)
    }
}

/** A setting with one value among several: tapped, the choices open. */
@Composable
private fun Value(title: String, value: String, onClick: (title: String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onClick(title) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = AeroText.body(16).copy(fontWeight = FontWeight.SemiBold))
        Pill(value, color = LocalAero.current.accent)
    }
}

/** The launcher's apps, to tick ([multiple]) or to pick one, with a search field. */
@Composable
internal fun AppsDialog(
    title: String,
    apps: List<LauncherApp>?,
    initial: Set<String>,
    multiple: Boolean,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalAero.current
    var chosen by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(Modifier.padding(20.dp).widthIn(max = 480.dp).fillMaxWidth(), solid = true, padding = PaddingValues(18.dp)) {
            Text(title, style = AeroText.title(20))
            Spacer(Modifier.height(12.dp))
            SearchField(query, { query = it })
            Spacer(Modifier.height(8.dp))
            if (apps == null) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { AeroSpinner(48.dp) }
            } else {
                val shown = apps.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        val ticked = app.packageName in chosen
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (multiple) {
                                        chosen = if (ticked) chosen - app.packageName else chosen + app.packageName
                                    } else {
                                        onDone(setOf(app.packageName))
                                    }
                                }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (multiple) {
                                AeroCheck(ticked, null)
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(app.name, style = AeroText.body(16).copy(fontWeight = if (ticked) FontWeight.Bold else FontWeight.Normal, color = p.ink))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                GelButton(stringResource(android.R.string.cancel), onDismiss, tone = GelTone.NEUTRAL, compact = true)
                if (multiple) GelButton(stringResource(android.R.string.ok), { onDone(chosen) }, tone = GelTone.ACCENT, compact = true)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val p = LocalAero.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .drawBehind { drawRect(if (p.isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.8f)) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(stringResource(R.string.settings_search), style = AeroText.soft(15))
            BasicTextField(value, onChange, singleLine = true, textStyle = AeroText.body(15), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Filled.Close, null, tint = p.inkSoft, modifier = Modifier.size(18.dp).clickable { onChange("") })
        }
    }
}

/** The PC's pairing code, typed. */
@Composable
private fun PairingDialog(onPair: (String) -> Boolean, onDismiss: () -> Unit) {
    val p = LocalAero.current
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AeroDialog(
        onDismiss,
        stringResource(R.string.focus_pc_enter_code),
        stringResource(R.string.kanji_pc),
        buttons = {
            GelButton(stringResource(android.R.string.cancel), onDismiss, tone = GelTone.NEUTRAL, compact = true)
            GelButton(stringResource(android.R.string.ok), {
                if (onPair(code)) onDismiss() else wrong = true
            }, tone = GelTone.ACCENT, compact = true)
        },
    ) {
        Text(stringResource(R.string.focus_pc_enter_code_detail), style = AeroText.body(15))
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .drawBehind { drawRect(if (p.isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.85f)) }
                .padding(14.dp),
        ) {
            if (code.isEmpty()) Text("xxxx-xxxx-xxxx-xxxx", style = AeroText.soft(17).copy(fontFamily = FontFamily.Monospace))
            BasicTextField(
                code,
                { code = it; wrong = false },
                singleLine = true,
                textStyle = AeroText.body(17).copy(fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (wrong) Text(stringResource(R.string.focus_pc_bad_code), Modifier.padding(top = 6.dp), style = AeroText.soft(13).copy(color = p.fail))
    }
}

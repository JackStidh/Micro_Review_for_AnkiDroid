package com.orlikalabs.microreview

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

/**
 * Settings of the cards that come while the phone is in use: what still has to be allowed, snooze, nudges and
 * their timing per app, the answer buttons, the app gate, and when cards stay away (Bluetooth, quiet hours, Do Not
 * Disturb, calls). Opened from the main screen and from the monitor's notification.
 *
 * Built from plain views in code: every row is a title, a summary and a switch or a button, rebuilt after each change.
 */
class FocusSettingsActivity : Activity() {
    private lateinit var content: LinearLayout

    /** The apps the launcher shows, as (name, package), loaded once in the background. */
    private var apps: List<Pair<String, String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(16)
            setPadding(padding, padding, padding, padding)
        }
        val scroll = ScrollView(this).apply {
            fitsSystemWindows = true
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        // Back from the system settings: a permission may have been allowed.
        rebuild()
        UsageMonitorService.sync(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Bluetooth without "Nearby devices" still works for audio devices: the setting stays.
        changed()
    }

    /** A setting changed: the monitor follows, and the rows show the new state. */
    private fun changed() {
        UsageMonitorService.sync(this)
        UsageMonitorService.refreshNotification(this)
        rebuild()
    }

    private fun rebuild() {
        content.removeAllViews()
        title(getString(R.string.focus_title))
        setupSection()
        snoozeSection()
        nudgeSection()
        answerSection()
        gateSection()
        pauseSection()
        pcSection()
        ankiWebSection()
        diagnosticsSection()
    }

    // What still has to be allowed

    private fun setupSection() {
        val monitorWanted = FocusSettings.monitorWanted(this)
        val needsOverlay = FocusSettings.gateEnabled(this) ||
            (FocusSettings.nudgesEnabled(this) && FocusSettings.nudgeStyle(this) == FocusSettings.NudgeStyle.FULL_SCREEN)
        val missingUsage = monitorWanted && !FocusState.hasUsageAccess(this)
        val missingOverlay = needsOverlay && !FocusState.canOpenOverApps(this)
        val missingNotifications = FocusSettings.nudgesEnabled(this) && !notificationsAllowed()
        if (!missingUsage && !missingOverlay && !missingNotifications) return
        header(getString(R.string.focus_setup))
        note(getString(R.string.focus_setup_restricted))
        if (missingUsage) {
            action(getString(R.string.focus_setup_usage), getString(R.string.focus_setup_usage_detail), getString(R.string.focus_setup_allow)) {
                open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        if (missingOverlay) {
            action(getString(R.string.focus_setup_overlay), getString(R.string.focus_setup_overlay_detail), getString(R.string.focus_setup_allow)) {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }
        if (missingNotifications) {
            action(getString(R.string.focus_setup_notifications), getString(R.string.focus_setup_notifications_detail), getString(R.string.focus_setup_allow)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                } else {
                    open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
            }
        }
    }

    private fun notificationsAllowed(): Boolean =
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).areNotificationsEnabled()

    // Snooze

    private fun snoozeSection() {
        header(getString(R.string.focus_snooze))
        val until = FocusSettings.snoozeUntil(this)
        if (QuietTime.isSnoozed(until, System.currentTimeMillis())) {
            note(getString(R.string.monitor_snoozed_until, UsageMonitorService.formatTime(this, until)))
            button(getString(R.string.monitor_resume)) {
                FocusState.resume(this)
                changed()
            }
        } else {
            note(getString(R.string.focus_snooze_detail))
            val row = row()
            listOf(30 to R.string.snooze_30_minutes, 60 to R.string.snooze_1_hour, 120 to R.string.snooze_2_hours).forEach { (minutes, label) ->
                rowButton(row, getString(label)) { snoozed { FocusState.snooze(this, minutes) } }
            }
            button(getString(R.string.snooze_until_tomorrow)) { snoozed { FocusState.snoozeUntilMorning(this) } }
        }
        choice(
            getString(R.string.focus_snooze_length),
            getString(R.string.minutes, FocusSettings.snoozeMinutes(this)),
            SNOOZE_LENGTHS.map { getString(R.string.minutes, it) },
            SNOOZE_LENGTHS.indexOf(FocusSettings.snoozeMinutes(this)),
        ) { FocusSettings.setSnoozeMinutes(this, SNOOZE_LENGTHS[it]) }
        toggle(getString(R.string.focus_snooze_lifts_gate), getString(R.string.focus_snooze_lifts_gate_detail), FocusSettings.snoozeLiftsGate(this)) {
            FocusSettings.setSnoozeLiftsGate(this, it)
        }
    }

    private fun snoozed(snooze: () -> Unit) {
        snooze()
        val context = applicationContext
        thread { NudgeSession.withdraw(context) }
        changed()
    }

    // Nudges

    private fun nudgeSection() {
        header(getString(R.string.focus_nudges))
        val enabled = FocusSettings.nudgesEnabled(this)
        toggle(getString(R.string.focus_nudges_enable), getString(R.string.focus_nudges_detail), enabled) {
            FocusSettings.setNudgesEnabled(this, it)
            val context = applicationContext
            thread {
                NudgeSession.onModeChanged(context)
                // Turned off: the usual card notification comes back.
                try {
                    CardStateController.syncWithAnkiDroid(context)
                } catch (e: Exception) {
                    android.util.Log.e("Focus", "sync after the mode change failed", e)
                }
            }
        }
        if (!enabled) return
        val interval = FocusSettings.defaultIntervalMinutes(this)
        choice(
            getString(R.string.focus_default_interval),
            intervalLabel(interval),
            DEFAULT_INTERVALS.map(::intervalLabel),
            DEFAULT_INTERVALS.indexOf(interval),
        ) { FocusSettings.setDefaultIntervalMinutes(this, DEFAULT_INTERVALS[it]) }

        val perApp = FocusSettings.appIntervals(this)
        val summary = if (perApp.isEmpty()) {
            getString(R.string.focus_app_intervals_none)
        } else {
            perApp.entries.sortedBy { appLabel(it.key).lowercase() }.joinToString("\n") { "${appLabel(it.key)}: ${intervalLabel(it.value)}" }
        }
        action(getString(R.string.focus_app_intervals), summary, getString(R.string.focus_choose)) {
            withApps { apps -> pickApp(apps) { app -> pickAppInterval(app) } }
        }

        button(getString(R.string.focus_show_now)) {
            val context = applicationContext
            thread {
                val shown = NudgeSession.fire(context)
                if (!shown) runOnUiThread { Toast.makeText(context, R.string.all_cards_done, Toast.LENGTH_SHORT).show() }
            }
        }

        val fullScreen = FocusSettings.nudgeStyle(this) == FocusSettings.NudgeStyle.FULL_SCREEN
        toggle(getString(R.string.focus_full_screen), getString(R.string.focus_full_screen_detail), fullScreen) {
            FocusSettings.setNudgeStyle(this, if (it) FocusSettings.NudgeStyle.FULL_SCREEN else FocusSettings.NudgeStyle.NOTIFICATION)
        }
        if (!fullScreen) {
            val lifetime = FocusSettings.nudgeLifetimeSeconds(this)
            choice(
                getString(R.string.focus_lifetime),
                getString(R.string.focus_lifetime_detail, lifetime),
                LIFETIMES.map { getString(R.string.seconds, it) },
                LIFETIMES.indexOf(lifetime),
            ) { FocusSettings.setNudgeLifetimeSeconds(this, LIFETIMES[it]) }
        }
    }

    private fun pickAppInterval(app: Pair<String, String>) {
        val current = FocusSettings.appIntervals(this)[app.second]
        val labels = listOf(getString(R.string.focus_interval_default)) + APP_INTERVALS.map(::intervalLabel)
        val checked = if (current == null) 0 else APP_INTERVALS.indexOf(current) + 1
        AlertDialog.Builder(this)
            .setTitle(app.first)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                FocusSettings.setAppInterval(this, app.second, if (which == 0) null else APP_INTERVALS[which - 1])
                dialog.dismiss()
                changed()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun intervalLabel(minutes: Int): String =
        if (minutes == NudgeClock.NEVER) getString(R.string.focus_interval_never) else getString(R.string.focus_interval_every, minutes)

    // Answer buttons

    private fun answerSection() {
        header(getString(R.string.focus_answers))
        toggle(getString(R.string.focus_pass_fail), getString(R.string.focus_pass_fail_detail), FocusSettings.passFailOnly(this)) {
            FocusSettings.setPassFailOnly(this, it)
            val context = applicationContext
            thread { StandardCardWidget.updateAll(context) }
        }
        toggle(getString(R.string.focus_note_fields), getString(R.string.focus_note_fields_detail), FocusSettings.showNoteFields(this)) {
            FocusSettings.setShowNoteFields(this, it)
        }
    }

    // App gate

    private fun gateSection() {
        header(getString(R.string.focus_gate))
        val enabled = FocusSettings.gateEnabled(this)
        toggle(getString(R.string.focus_gate_enable), getString(R.string.focus_gate_detail), enabled) {
            FocusSettings.setGateEnabled(this, it)
            if (!it) FocusSettings.setGatePass(this, null)
        }
        if (!enabled) return
        val gated = FocusSettings.gatedApps(this)
        val summary = if (gated.isEmpty()) {
            getString(R.string.focus_gate_apps_none)
        } else {
            gated.map(::appLabel).sortedBy { it.lowercase() }.joinToString(", ")
        }
        action(getString(R.string.focus_gate_apps), summary, getString(R.string.focus_choose)) {
            withApps { apps -> pickGatedApps(apps.filter { it.second !in GateRules.NEVER_GATED }) }
        }
        val cards = FocusSettings.gateCards(this)
        choice(
            getString(R.string.focus_gate_cards),
            resources.getQuantityString(R.plurals.focus_cards, cards, cards),
            GATE_CARDS.map { resources.getQuantityString(R.plurals.focus_cards, it, it) },
            GATE_CARDS.indexOf(cards),
        ) { FocusSettings.setGateCards(this, GATE_CARDS[it]) }
        val skips = FocusSettings.skipsPerDay(this)
        choice(
            getString(R.string.focus_gate_skips),
            getString(R.string.focus_gate_skips_detail, skips),
            SKIPS.map { it.toString() },
            SKIPS.indexOf(skips),
        ) { FocusSettings.setSkipsPerDay(this, SKIPS[it]) }
    }

    private fun pickGatedApps(apps: List<Pair<String, String>>) {
        val selected = FocusSettings.gatedApps(this).toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].second in selected }
        AlertDialog.Builder(this)
            .setTitle(R.string.focus_gate_apps)
            .setMultiChoiceItems(apps.map { it.first }.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) selected.add(apps[which].second) else selected.remove(apps[which].second)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                FocusSettings.setGatedApps(this, selected)
                changed()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // When cards stay away

    private fun pauseSection() {
        header(getString(R.string.focus_pauses))
        val bluetooth = FocusSettings.pauseOnBluetooth(this)
        toggle(getString(R.string.focus_bluetooth), getString(R.string.focus_bluetooth_detail), bluetooth) {
            FocusSettings.setPauseOnBluetooth(this, it)
            if (it) askBluetoothPermission()
        }
        if (bluetooth && !BluetoothState.hasPermission(this)) {
            action(getString(R.string.focus_bluetooth_permission), getString(R.string.focus_bluetooth_permission_detail), getString(R.string.focus_setup_allow)) {
                askBluetoothPermission()
            }
        }
        toggle(getString(R.string.focus_dnd), getString(R.string.focus_dnd_detail), FocusSettings.pauseOnDoNotDisturb(this)) {
            FocusSettings.setPauseOnDoNotDisturb(this, it)
        }
        toggle(getString(R.string.focus_call), getString(R.string.focus_call_detail), FocusSettings.pauseInCall(this)) {
            FocusSettings.setPauseInCall(this, it)
        }

        val windows = FocusSettings.quietHours(this)
        val summary = if (windows.isEmpty()) {
            getString(R.string.focus_quiet_none)
        } else {
            windows.joinToString("\n") { "${formatMinute(it.start)} – ${formatMinute(it.end)}" }
        }
        action(getString(R.string.focus_quiet), summary, getString(R.string.focus_quiet_add)) {
            pickTime(R.string.focus_quiet_from, 22 * 60) { start ->
                pickTime(R.string.focus_quiet_to, 7 * 60) { end ->
                    FocusSettings.setQuietHours(this, FocusSettings.quietHours(this) + QuietTime.Window(start, end))
                    changed()
                }
            }
        }
        if (windows.isNotEmpty()) {
            button(getString(R.string.focus_quiet_clear)) {
                FocusSettings.setQuietHours(this, emptyList())
                changed()
            }
        }
    }

    // AnkiWeb

    /** The answers given here reach AnkiWeb when AnkiDroid syncs: asked after the cards, or now ([AnkiSync]). */
    private fun ankiWebSection() {
        header(getString(R.string.focus_ankiweb))
        val asked = AnkiSync.requestedAt(this)
        val summary = getString(
            if (AnkiSync.isPending(this)) R.string.focus_ankiweb_pending else R.string.focus_ankiweb_synced,
            if (asked == 0L) getString(R.string.focus_ankiweb_never) else UsageMonitorService.formatTime(this, asked),
        )
        action(getString(R.string.focus_ankiweb_sync), summary, getString(R.string.focus_ankiweb_sync_now)) {
            AnkiSync.request(this, "from the settings")
            content.postDelayed({ if (!isFinishing) rebuild() }, 1_000)
        }
    }

    // The PC

    /** Pairing with Micro Review Desktop, the Anki add-on: the phone pauses while the PC is in use ([PcActivity]). */
    private fun pcSection() {
        header(getString(R.string.focus_pc))
        val code = FocusSettings.pcPairingCode(this)
        if (code == null) {
            action(getString(R.string.focus_pc_pair), getString(R.string.focus_pc_pair_detail), getString(R.string.focus_pc_enter_code)) {
                enterPairingCode()
            }
            return
        }
        toggle(getString(R.string.focus_pc_pause), getString(R.string.focus_pc_pause_detail), FocusSettings.pauseWhenPcInUse(this)) {
            FocusSettings.setPauseWhenPcInUse(this, it)
        }
        action(getString(R.string.focus_pc_paired), PcPairing.display(code), getString(R.string.focus_pc_unpair)) {
            FocusSettings.setPcPairingCode(this, null)
            PcActivity.forget()
            changed()
        }
    }

    private fun enterPairingCode() {
        val input = EditText(this).apply {
            hint = "xxxx-xxxx-xxxx-xxxx"
            isSingleLine = true
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.focus_pc_enter_code)
            .setMessage(R.string.focus_pc_enter_code_detail)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = PcPairing.normalize(input.text.toString())
                if (code == null) {
                    input.error = getString(R.string.focus_pc_bad_code)
                } else {
                    FocusSettings.setPcPairingCode(this, code)
                    PcActivity.forget()
                    dialog.dismiss()
                    changed()
                }
            }
        }
        dialog.show()
    }

    private fun askBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !BluetoothState.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH)
        }
    }

    // Diagnostics

    /** What the monitor sees and did: to tell why a card or the gate didn't come. */
    private fun diagnosticsSection() {
        header(getString(R.string.focus_diagnostics))
        val report = diagnostics()
        val view = text(report, android.R.style.TextAppearance_Material_Caption)
        view.typeface = android.graphics.Typeface.MONOSPACE
        view.setTextIsSelectable(true)
        content.addView(view)
        val row = row()
        rowButton(row, getString(R.string.focus_diagnostics_copy)) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Micro Review diagnostics", diagnostics()))
            Toast.makeText(this, R.string.focus_diagnostics_copied, Toast.LENGTH_SHORT).show()
        }
        rowButton(row, getString(R.string.focus_diagnostics_refresh)) { rebuild() }
    }

    private fun diagnostics(): String {
        fun yes(value: Boolean) = if (value) "yes" else "NO"
        val status = MonitorStatus
        val next = status.nextCardMs?.let { "${it / 60_000} min ${it / 1000 % 60} s" } ?: "none"
        val lastLook = if (status.lastStepAt == 0L) "never" else "${(System.currentTimeMillis() - status.lastStepAt) / 1000} s ago"
        val lines = mutableListOf(
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Usage access: ${yes(FocusState.hasUsageAccess(this))}",
            "Display over other apps: ${yes(FocusState.canOpenOverApps(this))}",
            "Notifications allowed: ${yes(notificationsAllowed())}",
            "Monitor running: ${yes(status.running)}, watching: ${yes(status.polling)}, last look: $lastLook",
            "App in front: ${status.foreground ?: "unknown"}",
            "Next card: $next",
            "Paused: ${status.pauseReason?.name?.lowercase() ?: "no"}",
            "Bluetooth connected: ${yes(BluetoothState.isConnected(this))} (nearby devices allowed: ${yes(BluetoothState.hasPermission(this))})",
            "PC: " + (FocusSettings.pcPairingCode(this)?.let {
                val until = PcActivity.pausedUntil
                val inUse = if (until > System.currentTimeMillis()) "in use until ${UsageMonitorService.formatTime(this, until)}" else "not in use"
                "paired, $inUse" + (PcActivity.lastError?.let { error -> ", last read failed: $error" } ?: "")
            } ?: "not paired"),
            "Card style: ${FocusSettings.nudgeStyle(this).name.lowercase()}, default every ${FocusSettings.defaultIntervalMinutes(this)} min, per app: ${FocusSettings.appIntervals(this)}",
            "Gated: ${FocusSettings.gatedApps(this)}, pass: ${FocusSettings.gatePass(this) ?: "none"}",
            "AnkiDroid sync: answers waiting ${yes(AnkiSync.isPending(this))}, last asked " +
                (AnkiSync.requestedAt(this).takeIf { it != 0L }?.let { UsageMonitorService.formatTime(this, it) } ?: "never"),
            "",
            "Recent events:",
        )
        lines += FocusLog.lines(this).takeLast(DIAGNOSTIC_LINES).reversed()
        return lines.joinToString("\n")
    }

    private fun pickTime(titleRes: Int, initial: Int, onPicked: (Int) -> Unit) {
        val dialog = TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initial / 60,
            initial % 60,
            DateFormat.is24HourFormat(this),
        )
        dialog.setTitle(titleRes)
        dialog.show()
    }

    private fun formatMinute(minute: Int): String {
        val pattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
        return LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(pattern))
    }

    // Apps

    /** Runs [then] with the apps of the launcher, read in the background the first time. */
    private fun withApps(then: (List<Pair<String, String>>) -> Unit) {
        apps?.let { return then(it) }
        thread {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val found = packageManager.queryIntentActivities(launcher, 0)
                .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
                .filter { it.second != packageName }
                .distinctBy { it.second }
                .sortedBy { it.first.lowercase() }
            runOnUiThread {
                apps = found
                if (!isFinishing && !isDestroyed) then(found)
            }
        }
    }

    private fun pickApp(apps: List<Pair<String, String>>, onPicked: (Pair<String, String>) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.focus_app_intervals)
            .setItems(apps.map { it.first }.toTypedArray()) { _, which -> onPicked(apps[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun appLabel(app: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(app, 0)).toString()
        } catch (e: Exception) {
            app
        }

    private fun open(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) startActivity(fallback)
        }
    }

    // Rows

    private fun title(text: String) {
        content.addView(text(text, android.R.style.TextAppearance_Material_Headline))
    }

    private fun header(text: String) {
        val view = text(text, android.R.style.TextAppearance_Material_Title)
        view.setPadding(0, dp(28), 0, dp(4))
        content.addView(view)
    }

    private fun note(text: String) {
        val view = text(text, android.R.style.TextAppearance_Material_Body1)
        view.setPadding(0, dp(4), 0, dp(4))
        content.addView(view)
    }

    private fun text(text: String, appearance: Int): TextView =
        TextView(this).apply {
            setTextAppearance(appearance)
            this.text = text
        }

    /** A title and a summary. */
    private fun label(title: String, summary: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, android.R.style.TextAppearance_Material_Subhead))
            addView(text(summary, android.R.style.TextAppearance_Material_Caption).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) })
        }

    private fun row(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    /** A setting that is on or off. */
    private fun toggle(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = row()
        row.addView(label(title, summary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val switch = Switch(this)
        switch.isChecked = checked
        switch.setOnCheckedChangeListener { _, isChecked ->
            onChange(isChecked)
            // Not during the switch's own callback: the rows are rebuilt.
            content.post { changed() }
        }
        row.addView(switch)
        row.setOnClickListener { switch.toggle() }
    }

    /** A setting with something to do about it. */
    private fun action(title: String, summary: String, buttonText: String, onClick: () -> Unit) {
        val row = row()
        row.addView(label(title, summary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowButton(row, buttonText, onClick)
    }

    /** A setting with one value among [labels]. */
    private fun choice(title: String, summary: String, labels: List<String>, checked: Int, onPicked: (Int) -> Unit) {
        action(title, summary, getString(R.string.focus_change)) {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                    onPicked(which)
                    dialog.dismiss()
                    changed()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun button(text: String, onClick: () -> Unit) {
        val button = Button(this)
        button.text = text
        button.isAllCaps = false
        button.setOnClickListener { onClick() }
        content.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun rowButton(row: LinearLayout, text: String, onClick: () -> Unit) {
        val button = Button(this)
        button.text = text
        button.isAllCaps = false
        button.setOnClickListener { onClick() }
        row.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 2
        private const val REQUEST_BLUETOOTH = 3
        private const val DIAGNOSTIC_LINES = 60
        private val DEFAULT_INTERVALS = listOf(NudgeClock.NEVER, 5, 10, 15, 20, 30, 45, 60, 90, 120)
        private val APP_INTERVALS = listOf(NudgeClock.NEVER, 2, 3, 5, 10, 15, 20, 30, 60)
        private val SNOOZE_LENGTHS = listOf(15, 30, 60, 120, 240)
        private val LIFETIMES = listOf(15, 30, 45, 60, 120)
        private val GATE_CARDS = listOf(1, 2, 3, 5, 7, 10, 15, 20)
        private val SKIPS = listOf(0, 1, 2, 3)
    }
}

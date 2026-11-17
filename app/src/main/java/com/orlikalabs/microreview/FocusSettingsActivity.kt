package com.orlikalabs.microreview

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

/**
 * Settings of the cards that come while the phone is in use: what still has to be allowed, snooze, nudges and
 * their timing per app, the answer buttons, the app gate, and when cards stay away (driving, quiet hours, Do Not
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
        if (requestCode == REQUEST_ACTIVITY && !DrivingDetector.hasActivityPermission(this)) {
            FocusSettings.setDetectVehicle(this, false)
        }
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
        val driving = FocusSettings.pauseWhenDriving(this)
        toggle(getString(R.string.focus_driving), getString(R.string.focus_driving_detail), driving) {
            FocusSettings.setPauseWhenDriving(this, it)
        }
        if (driving) {
            val motion = FocusSettings.detectVehicle(this) && DrivingDetector.hasActivityPermission(this)
            toggle(getString(R.string.focus_vehicle), getString(R.string.focus_vehicle_detail), motion) {
                FocusSettings.setDetectVehicle(this, it)
                if (it && !DrivingDetector.hasActivityPermission(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQUEST_ACTIVITY)
                }
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
        private const val REQUEST_ACTIVITY = 1
        private const val REQUEST_NOTIFICATIONS = 2
        private val DEFAULT_INTERVALS = listOf(NudgeClock.NEVER, 5, 10, 15, 20, 30, 45, 60, 90, 120)
        private val APP_INTERVALS = listOf(NudgeClock.NEVER, 2, 3, 5, 10, 15, 20, 30, 60)
        private val SNOOZE_LENGTHS = listOf(15, 30, 60, 120, 240)
        private val LIFETIMES = listOf(15, 30, 45, 60, 120)
        private val GATE_CARDS = listOf(1, 2, 3, 5, 7, 10, 15, 20)
        private val SKIPS = listOf(0, 1, 2, 3)
    }
}

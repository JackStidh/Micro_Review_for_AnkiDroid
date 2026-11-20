package com.orlikalabs.microreview

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orlikalabs.microreview.ui.LauncherApp
import com.orlikalabs.microreview.ui.SettingsActions
import com.orlikalabs.microreview.ui.SettingsScreen
import com.orlikalabs.microreview.ui.SettingsState
import com.orlikalabs.microreview.ui.aero.AeroPalette
import com.orlikalabs.microreview.ui.aero.AeroTheme
import com.orlikalabs.microreview.ui.aero.AeroThemes
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

/**
 * The settings ([SettingsScreen]): what still has to be allowed, snooze, nudges and their timing per app, the
 * answer buttons, the app gate, when cards stay away (Bluetooth, Do Not Disturb, calls, quiet hours), the PC, the
 * AnkiWeb sync, the theme, and help. Opened from the home screen and from the monitor's notification.
 *
 * Every change is stored at once; the screen then shows the settings read again ([read]).
 */
class FocusSettingsActivity : ComponentActivity(), SettingsActions {
    private var state by mutableStateOf(SettingsState())
    private var palette by mutableStateOf(AeroThemes.SAKURA)

    /** Changes when the launcher's apps arrive. */
    private var appsLoaded by mutableIntStateOf(0)

    /** The apps the launcher shows, loaded once in the background. */
    @Volatile
    private var apps: List<LauncherApp>? = null
    private var loadingApps = false

    override fun onCreate(savedInstanceState: Bundle?) {
        palette = AeroThemes.current(this)
        applyBars(palette)
        super.onCreate(savedInstanceState)
        state = read()
        setContent {
            Crossfade(palette, animationSpec = tween(450), label = "theme") { shown ->
                AeroTheme(shown) { SettingsScreen(state, this, appsLoaded) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the system settings: a permission may have been allowed.
        state = read()
        UsageMonitorService.sync(this)
    }

    /** Notifications or "Nearby devices": allowed or not, the screen shows it. Bluetooth without "Nearby devices"
     * still works for audio devices: the setting stays. */
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { changed() }

    /** A setting changed: the monitor follows, and the screen shows the new state. */
    private fun changed() {
        UsageMonitorService.sync(this)
        UsageMonitorService.refreshNotification(this)
        state = read()
    }

    private fun applyBars(palette: AeroPalette) {
        val bars = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
    }

    private fun read(): SettingsState {
        val monitorWanted = FocusSettings.monitorWanted(this)
        val needsOverlay = FocusSettings.gateEnabled(this) ||
            (FocusSettings.nudgesEnabled(this) && FocusSettings.nudgeStyle(this) == FocusSettings.NudgeStyle.FULL_SCREEN)
        val now = System.currentTimeMillis()
        val until = FocusSettings.snoozeUntil(this)
        val asked = AnkiSync.requestedAt(this)
        return SettingsState(
            missingUsage = monitorWanted && !FocusState.hasUsageAccess(this),
            missingOverlay = needsOverlay && !FocusState.canOpenOverApps(this),
            missingNotifications = FocusSettings.nudgesEnabled(this) && !notificationsAllowed(),
            snoozedUntil = if (QuietTime.isSnoozed(until, now)) UsageMonitorService.formatTime(this, until) else null,
            snoozeMinutes = FocusSettings.snoozeMinutes(this),
            snoozeLiftsGate = FocusSettings.snoozeLiftsGate(this),
            nudges = FocusSettings.nudgesEnabled(this),
            defaultInterval = FocusSettings.defaultIntervalMinutes(this),
            appIntervals = FocusSettings.appIntervals(this).map { (app, minutes) -> LauncherApp(appLabel(app), app) to minutes }
                .sortedBy { it.first.name.lowercase() },
            passFail = FocusSettings.passFailOnly(this),
            noteFields = FocusSettings.showNoteFields(this),
            gate = FocusSettings.gateEnabled(this),
            gatedApps = FocusSettings.gatedApps(this).map { LauncherApp(appLabel(it), it) }.sortedBy { it.name.lowercase() },
            gateCards = FocusSettings.gateCards(this),
            skipsPerDay = FocusSettings.skipsPerDay(this),
            bluetooth = FocusSettings.pauseOnBluetooth(this),
            bluetoothPermission = BluetoothState.hasPermission(this),
            doNotDisturb = FocusSettings.pauseOnDoNotDisturb(this),
            calls = FocusSettings.pauseInCall(this),
            quietHours = FocusSettings.quietHours(this).map { "${formatMinute(it.start)} – ${formatMinute(it.end)}" },
            pcCode = FocusSettings.pcPairingCode(this)?.let(PcPairing::display),
            pausePc = FocusSettings.pauseWhenPcInUse(this),
            ankiPending = AnkiSync.isPending(this),
            ankiAsked = if (asked == 0L) getString(R.string.focus_ankiweb_never) else UsageMonitorService.formatTime(this, asked),
            canReport = ReportIssue.isAvailable(this),
            version = getString(R.string.app_version, getString(R.string.app_name), versionName()),
        )
    }

    @Suppress("DEPRECATION") // The flags overload needs API 33.
    private fun versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"

    private fun notificationsAllowed(): Boolean =
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).areNotificationsEnabled()

    // The actions of the screen

    override fun back() = finish()

    override fun fixUsage() = open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    override fun fixOverlay() =
        open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))

    override fun fixNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun snooze(minutes: Int?) {
        if (minutes == null) FocusState.snoozeUntilMorning(this) else FocusState.snooze(this, minutes)
        val context = applicationContext
        thread { NudgeSession.withdraw(context) }
        changed()
    }

    override fun resume() {
        FocusState.resume(this)
        changed()
    }

    override fun setSnoozeMinutes(minutes: Int) = update { FocusSettings.setSnoozeMinutes(this, minutes) }

    override fun setSnoozeLiftsGate(on: Boolean) = update { FocusSettings.setSnoozeLiftsGate(this, on) }

    override fun setNudges(on: Boolean) = update {
        FocusSettings.setNudgesEnabled(this, on)
        val context = applicationContext
        thread { NudgeSession.onModeChanged(context) }
    }

    override fun setDefaultInterval(minutes: Int) = update { FocusSettings.setDefaultIntervalMinutes(this, minutes) }

    override fun setAppInterval(app: String, minutes: Int?) = update { FocusSettings.setAppInterval(this, app, minutes) }

    override fun showCardNow() {
        val context = applicationContext
        thread {
            val shown = NudgeSession.fire(context)
            if (!shown) runOnUiThread { Toast.makeText(context, R.string.all_cards_done, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun setPassFail(on: Boolean) = update { FocusSettings.setPassFailOnly(this, on) }

    override fun setNoteFields(on: Boolean) = update { FocusSettings.setShowNoteFields(this, on) }

    override fun setGate(on: Boolean) = update {
        FocusSettings.setGateEnabled(this, on)
        if (!on) FocusSettings.setGatePass(this, null)
    }

    override fun setGatedApps(apps: Set<String>) = update {
        FocusSettings.setGatedApps(this, apps.filter { it !in GateRules.NEVER_GATED }.toSet())
    }

    override fun setGateCards(cards: Int) = update { FocusSettings.setGateCards(this, cards) }

    override fun setSkips(skips: Int) = update { FocusSettings.setSkipsPerDay(this, skips) }

    override fun setBluetooth(on: Boolean) = update {
        FocusSettings.setPauseOnBluetooth(this, on)
        if (on) askBluetooth()
    }

    override fun askBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !BluetoothState.hasPermission(this)) {
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    override fun setDoNotDisturb(on: Boolean) = update { FocusSettings.setPauseOnDoNotDisturb(this, on) }

    override fun setCalls(on: Boolean) = update { FocusSettings.setPauseInCall(this, on) }

    override fun addQuietHours() {
        pickTime(R.string.focus_quiet_from, 22 * 60) { start ->
            pickTime(R.string.focus_quiet_to, 7 * 60) { end ->
                update { FocusSettings.setQuietHours(this, FocusSettings.quietHours(this) + QuietTime.Window(start, end)) }
            }
        }
    }

    override fun clearQuietHours() = update { FocusSettings.setQuietHours(this, emptyList()) }

    override fun pair(code: String): Boolean {
        val normalized = PcPairing.normalize(code) ?: return false
        update {
            FocusSettings.setPcPairingCode(this, normalized)
            PcActivity.forget()
        }
        return true
    }

    override fun unpair() = update {
        FocusSettings.setPcPairingCode(this, null)
        PcActivity.forget()
    }

    override fun setPausePc(on: Boolean) = update { FocusSettings.setPauseWhenPcInUse(this, on) }

    override fun syncNow() {
        // In front: allowed to start AnkiDroid's sync.
        AnkiSync.request(this, "from the settings")
        state = read()
    }

    override fun chooseTheme(palette: AeroPalette) {
        AeroThemes.choose(this, palette)
        this.palette = palette
        applyBars(palette)
    }

    override fun copyDiagnostics() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Micro Review diagnostics", diagnostics()))
        Toast.makeText(this, R.string.focus_diagnostics_copied, Toast.LENGTH_SHORT).show()
    }

    override fun reportIssue() = ReportIssue.open(this)

    override fun apps(): List<LauncherApp>? {
        apps?.let { return it }
        if (!loadingApps) {
            loadingApps = true
            thread {
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val found = packageManager.queryIntentActivities(launcher, 0)
                    .map { LauncherApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
                    .filter { it.packageName != packageName }
                    .distinctBy { it.packageName }
                    .sortedBy { it.name.lowercase() }
                runOnUiThread {
                    apps = found
                    appsLoaded++
                }
            }
        }
        return null
    }

    /** What the monitor sees and did: to tell why a card or the gate didn't come. */
    override fun diagnostics(): String {
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
                (AnkiSync.requestedAt(this).takeIf { it != 0L }?.let { UsageMonitorService.formatTime(this, it) } ?: "never") +
                ", PC synced " + (AnkiSync.pcSyncedAt(this).takeIf { it != 0L }?.let { UsageMonitorService.formatTime(this, it) } ?: "never"),
            "Theme: ${AeroThemes.current(this).id}",
            "",
            "Recent events:",
        )
        lines += FocusLog.lines(this).takeLast(DIAGNOSTIC_LINES).reversed()
        return lines.joinToString("\n")
    }

    private fun update(change: () -> Unit) {
        change()
        changed()
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

    companion object {
        private const val DIAGNOSTIC_LINES = 60
    }
}

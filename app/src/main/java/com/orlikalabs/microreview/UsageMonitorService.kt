package com.orlikalabs.microreview

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Date

/**
 * Watches the phone's use while the screen is on and unlocked, for the two features that depend on it:
 * - nudges: counts the time spent on the phone, per app where an app has its own interval ([NudgeClock]), and
 *   offers a card when one is due ([NudgeSession]), unless something says not now ([FocusState.pauseReason]);
 * - the app gate: when one of the chosen apps comes to the front without a pass, opens the gate over it
 *   ([GateRules], [FocusReviewActivity]).
 *
 * Which app is in front comes from the usage events ("Usage access"), read once a second while the phone is in
 * use; nothing runs while the screen is off. A foreground service, because Android only delivers the screen
 * events to a running component and stops background work otherwise. Its notification doubles as the snooze
 * control. Opening the gate from here needs "Display over other apps".
 */
class UsageMonitorService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private val clock = NudgeClock()

    /** The app in front, as far as the usage events told. Used on the monitor thread only, like what follows. */
    private var foreground: String? = null
    private var lastEventTime = 0L
    private var lastTick = 0L
    private var polling = false
    private var lastSyncAt = 0L
    private var shownState: String? = null

    private val pollTask = Runnable { poll() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handler.post { stopPolling() }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> handler.post { startPollingIfInUse() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("usage-monitor").apply { start() }
        handler = Handler(thread.looper)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = buildNotification(this, pauseReason = null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Android refused the foreground start (started from the background without an exemption).
            Log.e(TAG, "the monitor can't run in the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!FocusSettings.monitorWanted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.post {
            shownState = null
            startPollingIfInUse()
            updateNotification()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        NudgeSession.withdraw(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPollingIfInUse() {
        if (polling || !FocusState.isInUse(this)) return
        Log.i(TAG, "phone in use: watching")
        polling = true
        lastTick = SystemClock.elapsedRealtime()
        handler.removeCallbacks(pollTask)
        handler.post(pollTask)
    }

    /** The screen is off or locked: nothing is looked at any more. */
    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(pollTask)
        NudgeSession.withdraw(this)
    }

    private fun poll() {
        if (!polling) return
        try {
            step()
        } catch (e: Exception) {
            Log.e(TAG, "monitor step failed", e)
        }
        if (polling) handler.postDelayed(pollTask, POLL_INTERVAL_MS)
    }

    private fun step() {
        if (!FocusSettings.monitorWanted(this)) {
            polling = false
            stopSelf()
            return
        }
        // A missed screen event must not leave a card counting time on a dark screen.
        if (!FocusState.isInUse(this)) {
            stopPolling()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastTick
        lastTick = now

        val current = readForeground()
        if (current != null && current != foreground) {
            foreground = current
            onForegroundChanged(current)
        }
        NudgeSession.reconcile(this)
        updateNotification()
        if (FocusSettings.nudgesEnabled(this)) nudgeStep(elapsed)
    }

    private fun nudgeStep(elapsed: Long) {
        val app = foreground
        val perApp = FocusSettings.appIntervals(this)
        // Already reviewing, here or in AnkiDroid: the time until the next card starts over.
        if (app == packageName || app == ANKIDROID_PACKAGE) {
            clock.reset(app, perApp)
            return
        }
        val due = clock.tick(app, elapsed, perApp, FocusSettings.defaultIntervalMinutes(this))
        if (!due || NudgeSession.isBusy(this)) return
        // Not now: the card stays due and comes as soon as nothing is in the way.
        if (FocusState.pauseReason(this) != null) return
        NudgeSession.fire(this)
        // Also with nothing to review: checked again after the next interval, not every second.
        clock.reset(app, perApp)
    }

    /** The app in front according to the usage events since the last read, or the one known so far. */
    private fun readForeground(): String? {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val begin = if (lastEventTime == 0L || lastEventTime > now) now - FIRST_LOOKBACK_MS else lastEventTime
        val events = usage.queryEvents(begin, now + 1_000) ?: return foreground
        val event = UsageEvents.Event()
        var latest: String? = null
        var latestTime = begin
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // An activity of the app was resumed (ACTIVITY_RESUMED has the same value on Android 10+).
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) latest = event.packageName
            if (event.timeStamp >= latestTime) latestTime = event.timeStamp + 1
        }
        // Without "Usage access" there are no events: still don't read the same half hour every second.
        lastEventTime = maxOf(latestTime, now - FIRST_LOOKBACK_MS)
        return latest ?: foreground
    }

    private fun onForegroundChanged(app: String) {
        if (!FocusSettings.gateEnabled(this)) return
        val before = FocusSettings.gatePass(this)
        val pass = GateRules.passAfter(before, app, packageName)
        if (pass != before) FocusSettings.setGatePass(this, pass)
        val gated = FocusSettings.gatedApps(this)
        // Most apps aren't gated: no need to look for a due card.
        if (app !in gated || app == pass) return
        val mustGate = GateRules.mustGate(
            foreground = app,
            ownPackage = packageName,
            enabled = true,
            gated = gated,
            pass = pass,
            hasDueCard = hasDueCard(),
            paused = FocusState.gatePaused(this),
        )
        if (!mustGate) return
        if (!FocusState.canOpenOverApps(this)) {
            Log.w(TAG, "$app is gated, but \"Display over other apps\" isn't allowed")
            return
        }
        Log.i(TAG, "gating $app")
        startActivity(FocusReviewActivity.gateIntent(this, app))
    }

    /**
     * Whether a card can be asked. "Nothing due" may be old news (new cards come due during the day): it is
     * checked against AnkiDroid, at most every few minutes.
     */
    private fun hasDueCard(): Boolean {
        val anki = AnkiDroidHelper(this)
        val stored = anki.storedState ?: return false
        if (stored.hasCard()) return true
        val now = SystemClock.elapsedRealtime()
        if (now - lastSyncAt < DUE_CHECK_INTERVAL_MS && lastSyncAt != 0L) return false
        lastSyncAt = now
        return try {
            CardStateController.syncWithAnkiDroid(this)
            anki.storedState?.hasCard() == true
        } catch (e: Exception) {
            Log.e(TAG, "could not check for due cards", e)
            false
        }
    }

    /** Posts the monitor's notification again when what it says changed (snoozed, paused, back on). */
    private fun updateNotification() {
        val reason = FocusState.pauseReason(this)
        val state = "$reason:${FocusSettings.snoozeUntil(this)}:${reason == Interruptions.Reason.DRIVING && DrivingDetector.isOnlyMotion(this)}"
        if (state == shownState) return
        shownState = state
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(this, reason))
    }

    companion object {
        private const val TAG = "Focus"
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "focus_monitor"
        private const val ANKIDROID_PACKAGE = "com.ichi2.anki"
        private const val POLL_INTERVAL_MS = 1_000L
        private const val FIRST_LOOKBACK_MS = 30 * 60_000L
        private const val DUE_CHECK_INTERVAL_MS = 5 * 60_000L

        /**
         * Starts or stops the monitor according to the settings. From the background, Android may refuse the start
         * (it allows it with "Display over other apps", after boot, and from the app's screens): it is then
         * started the next time the app is opened.
         */
        fun sync(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, UsageMonitorService::class.java)
            try {
                if (FocusSettings.monitorWanted(appContext)) {
                    ContextCompat.startForegroundService(appContext, intent)
                } else {
                    appContext.stopService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "could not start the monitor", e)
            }
            DrivingDetector.updateVehicleReports(appContext)
        }

        /** A snooze started or ended from a button: the running monitor's notification says so right away. */
        fun refreshNotification(context: Context) {
            if (!FocusSettings.monitorWanted(context)) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Only if the monitor runs: this must not post a stray notification.
            if (manager.activeNotifications.none { it.id == NOTIFICATION_ID }) return
            manager.notify(NOTIFICATION_ID, buildNotification(context, FocusState.pauseReason(context)))
        }

        private fun createChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.monitor_channel_name), NotificationManager.IMPORTANCE_LOW)
            channel.description = context.getString(R.string.monitor_channel_description)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }

        /** The monitor's notification: what is on, why cards are held back if they are, and the snooze buttons. */
        fun buildNotification(context: Context, pauseReason: Interruptions.Reason?): Notification {
            createChannel(context)
            val text = when (pauseReason) {
                Interruptions.Reason.SNOOZED -> context.getString(R.string.monitor_snoozed_until, formatTime(context, FocusSettings.snoozeUntil(context)))
                Interruptions.Reason.QUIET_HOURS -> context.getString(R.string.monitor_quiet_hours)
                Interruptions.Reason.DRIVING -> context.getString(R.string.monitor_driving)
                Interruptions.Reason.DO_NOT_DISTURB -> context.getString(R.string.monitor_do_not_disturb)
                Interruptions.Reason.IN_CALL -> context.getString(R.string.monitor_in_call)
                null -> context.getString(
                    when {
                        FocusSettings.nudgesEnabled(context) && FocusSettings.gateEnabled(context) -> R.string.monitor_on_both
                        FocusSettings.gateEnabled(context) -> R.string.monitor_on_gate
                        else -> R.string.monitor_on_nudges
                    }
                )
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, FocusSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.monitor_title))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
            when (pauseReason) {
                Interruptions.Reason.SNOOZED ->
                    builder.addAction(0, context.getString(R.string.monitor_resume), FocusActions.intent(context, FocusActions.ACTION_RESUME))
                Interruptions.Reason.DRIVING ->
                    // Only worth offering when motion alone says so: a passenger. A connected car screen is driving.
                    if (DrivingDetector.isOnlyMotion(context)) {
                        builder.addAction(0, context.getString(R.string.monitor_not_driving), FocusActions.intent(context, FocusActions.ACTION_NOT_DRIVING))
                    }
                else -> {
                    builder.addAction(0, context.getString(R.string.snooze_30_minutes), FocusActions.intent(context, FocusActions.ACTION_SNOOZE_PREFIX + 30))
                    builder.addAction(0, context.getString(R.string.snooze_2_hours), FocusActions.intent(context, FocusActions.ACTION_SNOOZE_PREFIX + 120))
                    builder.addAction(0, context.getString(R.string.snooze_until_tomorrow), FocusActions.intent(context, FocusActions.ACTION_SNOOZE_UNTIL_MORNING))
                }
            }
            return builder.build()
        }

        /** The time of day of [epochMillis], in the phone's 12 or 24 hour format. */
        fun formatTime(context: Context, epochMillis: Long): String = DateFormat.getTimeFormat(context).format(Date(epochMillis))
    }
}

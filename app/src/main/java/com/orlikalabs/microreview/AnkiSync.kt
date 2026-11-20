package com.orlikalabs.microreview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.net.NetworkCapabilities

/**
 * Asks AnkiDroid to sync with AnkiWeb, so that the answers given here reach the user's other devices.
 *
 * AnkiDroid only syncs when it is opened or closed, and Micro Review answers through its API without opening it:
 * without this, the answers stayed on the phone until AnkiDroid was next opened. AnkiDroid takes a sync request from
 * other apps (the "DO_SYNC" action, the one Tasker uses): it shows its deck list for a moment, starts the sync in the
 * background and closes. It ignores requests less than 2 minutes apart, and syncs on a metered network only if its
 * own setting allows it.
 *
 * Only while the phone is plugged in, at most every 15 minutes: one sync when it charges (typically at night) rather
 * than syncs all day; "Sync now" in the settings any time.
 *
 * Starting AnkiDroid from the background is only allowed while one of this app's windows is visible: the request is
 * made as the card overlay closes ([CardOverlay]), or under a short "Syncing" cover ([CardOverlay.showSyncCover])
 * when the plugged-in phone is unlocked, both fading out over AnkiDroid's moment in front; or from this app's screens.
 */
object AnkiSync {
    private const val PREFS = "com.orlikalabs.microreview.anki_sync"
    private const val KEY_PENDING = "pending"
    private const val KEY_REQUESTED_AT = "requested_at"
    const val ACTION_DO_SYNC = "com.ichi2.anki.DO_SYNC"
    private const val ANKIDROID = "com.ichi2.anki"

    /** An answer (or a bury) was recorded: it waits to be synced. */
    fun onChanged(context: Context) {
        prefs(context).edit().putBoolean(KEY_PENDING, true).apply()
    }

    fun isPending(context: Context): Boolean = prefs(context).getBoolean(KEY_PENDING, false)

    fun requestedAt(context: Context): Long = prefs(context).getLong(KEY_REQUESTED_AT, 0)

    /** Whether to ask now ([AnkiSyncRules.due]: answers waiting, plugged in, not asked lately), the phone being online. */
    fun isDue(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        AnkiSyncRules.due(isPending(context), requestedAt(context), now, isPluggedIn(context)) && isOnline(context)

    /** Whether the phone is on a charger (or a computer's USB, or a wireless charger). */
    fun isPluggedIn(context: Context): Boolean {
        val battery = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    /** Whether AnkiDroid in front now is the moment that follows a request (not the user opening it). */
    fun isFlash(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        AnkiSyncRules.isFlash(requestedAt(context), now)

    /**
     * Sends the request. Call it while one of this app's windows is visible. Returns whether AnkiDroid took it (it may
     * still skip the sync: offline, metered network, not logged in, or another sync under way).
     */
    fun request(context: Context, reason: String): Boolean {
        val intent = Intent(ACTION_DO_SYNC)
            .setPackage(ANKIDROID)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return try {
            context.startActivity(intent)
            prefs(context).edit().putBoolean(KEY_PENDING, false).putLong(KEY_REQUESTED_AT, System.currentTimeMillis()).apply()
            FocusLog.i(context, "asked AnkiDroid to sync ($reason)")
            true
        } catch (e: ActivityNotFoundException) {
            FocusLog.i(context, "AnkiDroid doesn't take sync requests: $e")
            false
        } catch (e: SecurityException) {
            FocusLog.i(context, "couldn't ask AnkiDroid to sync: $e")
            false
        }
    }

    private fun isOnline(context: Context): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

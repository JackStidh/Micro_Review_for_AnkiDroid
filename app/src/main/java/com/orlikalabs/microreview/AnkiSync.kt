package com.orlikalabs.microreview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Asks AnkiDroid to sync with AnkiWeb, so that the answers given here reach the user's other devices.
 *
 * AnkiDroid only syncs when it is opened or closed, and Micro Review answers through its API without opening it:
 * without this, the answers stayed on the phone until AnkiDroid was next opened. AnkiDroid takes a sync request from
 * other apps (the "DO_SYNC" action, the one Tasker uses), but only through its screen: it shows its deck list for a
 * moment, starts the sync in the background and closes. There is no way to sync it unseen. It ignores requests less
 * than 2 minutes apart, and syncs on a metered network only if its own setting allows it.
 *
 * So the request is only made as a card screen closes, under it ([CardOverlay], [FocusReviewActivity]): Android lets
 * this app start AnkiDroid while one of its windows is visible, and the card screen stays a moment over AnkiDroid's
 * moment in front, then fades out. Then when answers wait here, or the PC (Micro Review Desktop) said on the relay
 * that it synced its answers; and "Sync now" any time. Until the phone's next sync, the cards the PC said it
 * reviewed are passed over ([skipped]), so they aren't reviewed here a second time.
 */
object AnkiSync {
    private const val PREFS = "com.orlikalabs.microreview.anki_sync"
    private const val KEY_PENDING = "pending"
    private const val KEY_REQUESTED_AT = "requested_at"
    private const val KEY_PC_SYNCED_AT = "pc_synced_at"
    private const val KEY_PC_REVIEWED = "pc_reviewed"
    const val ACTION_DO_SYNC = "com.ichi2.anki.DO_SYNC"
    private const val ANKIDROID = "com.ichi2.anki"

    /** An answer (or a bury) was recorded: it waits to be synced. */
    fun onChanged(context: Context) {
        prefs(context).edit().putBoolean(KEY_PENDING, true).apply()
    }

    fun isPending(context: Context): Boolean = prefs(context).getBoolean(KEY_PENDING, false)

    fun requestedAt(context: Context): Long = prefs(context).getLong(KEY_REQUESTED_AT, 0)

    /** When the PC last said it synced its answers (epoch milliseconds, relay time), 0 if never. */
    fun pcSyncedAt(context: Context): Long = prefs(context).getLong(KEY_PC_SYNCED_AT, 0)

    fun setPcSyncedAt(context: Context, at: Long) {
        if (at > pcSyncedAt(context)) prefs(context).edit().putLong(KEY_PC_SYNCED_AT, at).apply()
    }

    /** The PC said at [time] that it reviewed [cards]: passed over until the phone syncs ([skipped]). */
    @Synchronized
    fun addPcReviewed(context: Context, cards: Collection<CardSync.CardKey>, time: Long) {
        if (cards.isEmpty()) return
        val prefs = prefs(context)
        val all = AnkiSyncRules.withPcReviewed(
            AnkiSyncRules.decode(prefs.getString(KEY_PC_REVIEWED, null)), cards, time, requestedAt(context)
        )
        prefs.edit().putString(KEY_PC_REVIEWED, AnkiSyncRules.encode(all)).apply()
    }

    /** The cards reviewed on the PC and not synced here yet: not to be shown, nor answered. */
    @JvmStatic
    fun skipped(context: Context): Set<CardSync.CardKey> =
        AnkiSyncRules.skipped(AnkiSyncRules.decode(prefs(context).getString(KEY_PC_REVIEWED, null)), requestedAt(context))

    /** As a card screen closes ([AnkiSyncRules.dueAfterCards]), the phone being online. */
    fun isDueAfterCards(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        AnkiSyncRules.dueAfterCards(isPending(context), requestedAt(context), now, pcSyncedAt(context)) && isOnline(context)

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

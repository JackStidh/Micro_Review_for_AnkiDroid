package com.orlikalabs.microreview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

/**
 * After a reboot or an app update, starts the monitor of the nudges and the gate again; after a reboot, also checks
 * the stored card against AnkiDroid.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> Unit
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // An app update stops the monitor.
                UsageMonitorService.sync(context)
                return
            }
            else -> return
        }
        Log.i("Notifications", "boot completed")
        UsageMonitorService.sync(context)
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                CardStateController.syncWithAnkiDroid(appContext)
            } catch (e: Exception) {
                Log.e("Notifications", "could not check the card after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

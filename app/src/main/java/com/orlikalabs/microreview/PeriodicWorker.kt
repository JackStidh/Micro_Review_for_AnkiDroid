package com.orlikalabs.microreview

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Hourly check that the displayed card is still in sync with AnkiDroid (new cards after "Bravo", cards reviewed in
 * AnkiDroid), and that the monitor of the nudges and the gate still runs.
 */
class PeriodicWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Log.i("BackgroundService", "Periodic worker RAN")
        // The monitor can be stopped by the system: start it again if Android lets a background start through.
        UsageMonitorService.sync(applicationContext)
        if (DeckSelection.load(applicationContext).isEmpty() && AnkiDroidHelper(applicationContext).storedState == null) {
            Log.i("BackgroundService", "Periodic worker - no deck selected")
            return Result.success()
        }
        CardStateController.syncWithAnkiDroid(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "WORKER_ANKI"
        private const val INTERVAL_HOURS = 1L

        /** Schedules the hourly check, or updates an existing schedule (it used to be every 8 hours). */
        fun schedule(context: Context) {
            Log.i("BackgroundService", "scheduling the periodic worker")
            val request = PeriodicWorkRequest.Builder(PeriodicWorker::class.java, INTERVAL_HOURS, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

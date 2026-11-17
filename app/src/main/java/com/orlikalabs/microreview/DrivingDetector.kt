package com.orlikalabs.microreview

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Tells whether the user is probably driving, so that no card interrupts. Three signs, any of which is enough:
 * - the phone is connected to a car screen (Android Auto), asked from Android Auto's own provider;
 * - the phone is in car mode;
 * - optionally, the phone's motion says "in a vehicle" (Google Play services' activity recognition, which needs the
 *   physical activity permission). That one is also true for a passenger: "I'm not driving" turns it off for a while.
 */
object DrivingDetector {
    private const val TAG = "Focus"
    private const val PREFS = "com.orlikalabs.microreview.driving"
    private const val KEY_IN_VEHICLE = "in_vehicle"
    private const val KEY_REPORTED_AT = "reported_at"

    /** Android Auto's connection provider (what `androidx.car.app.connection.CarConnection` reads). */
    private val CAR_CONNECTION_URI: Uri = Uri.Builder().scheme("content").authority("androidx.car.app.connection").build()
    private const val CAR_CONNECTION_STATE = "CarConnectionState"
    private const val CAR_NOT_CONNECTED = 0

    /** The provider is another app's: don't ask it more often than this. */
    private const val CAR_CHECK_INTERVAL_MS = 15_000L

    @Volatile
    private var carCheckedAt = 0L

    @Volatile
    private var carConnected = false

    /** Whether the user is probably driving. Queries another app at most every 15 seconds: call it off the main thread. */
    fun isDriving(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (isCarConnected(context, now) || isCarMode(context)) return true
        if (!FocusSettings.detectVehicle(context) || now < FocusSettings.notDrivingUntil(context)) return false
        val prefs = prefs(context)
        return Interruptions.inVehicle(prefs.getBoolean(KEY_IN_VEHICLE, false), prefs.getLong(KEY_REPORTED_AT, 0), now)
    }

    /** Whether only the phone's motion says "driving" now: then "I'm not driving" is worth offering. */
    fun isOnlyMotion(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        !carConnected && !isCarMode(context) && isDriving(context, now)

    private fun isCarMode(context: Context): Boolean {
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }

    private fun isCarConnected(context: Context, now: Long): Boolean {
        if (now - carCheckedAt in 0 until CAR_CHECK_INTERVAL_MS) return carConnected
        carCheckedAt = now
        carConnected = try {
            context.contentResolver.query(CAR_CONNECTION_URI, arrayOf(CAR_CONNECTION_STATE), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(CAR_CONNECTION_STATE)
                column >= 0 && cursor.moveToFirst() && cursor.getInt(column) != CAR_NOT_CONNECTED
            } ?: false
        } catch (e: Exception) {
            // Android Auto isn't installed, or refuses: not connected as far as we know.
            false
        }
        return carConnected
    }

    /** Whether the physical activity permission is granted (not needed before Android 10). */
    fun hasActivityPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /**
     * Starts or stops the "in a vehicle" reports according to the settings. Safe to call repeatedly; does nothing
     * without the permission or without Google Play services.
     */
    @SuppressLint("MissingPermission") // checked by hasActivityPermission()
    fun updateVehicleReports(context: Context) {
        val appContext = context.applicationContext
        val wanted = FocusSettings.monitorWanted(appContext) && FocusSettings.pauseWhenDriving(appContext) &&
            FocusSettings.detectVehicle(appContext) && hasActivityPermission(appContext)
        try {
            val client = ActivityRecognition.getClient(appContext)
            if (wanted) {
                val transitions = listOf(
                    ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                    ActivityTransition.ACTIVITY_TRANSITION_EXIT,
                ).map { transition ->
                    ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(transition)
                        .build()
                }
                client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), reportIntent(appContext))
                    .addOnFailureListener { Log.w(TAG, "vehicle reports refused", it) }
            } else {
                client.removeActivityTransitionUpdates(reportIntent(appContext))
                prefs(appContext).edit().clear().apply()
            }
        } catch (e: Exception) {
            // No Google Play services, or the permission was just revoked.
            Log.w(TAG, "vehicle reports unavailable", e)
        }
    }

    /** Google Play services fills the intent in: it must be mutable (the flag is ignored before Android 12). */
    @SuppressLint("InlinedApi")
    private fun reportIntent(context: Context): PendingIntent {
        val intent = Intent(context, VehicleReceiver::class.java)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    internal fun onVehicle(context: Context, entered: Boolean, now: Long = System.currentTimeMillis()) {
        Log.i(TAG, if (entered) "in a vehicle" else "left the vehicle")
        prefs(context).edit().putBoolean(KEY_IN_VEHICLE, entered).putLong(KEY_REPORTED_AT, now).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Receives the "entered / left a vehicle" reports asked by [DrivingDetector.updateVehicleReports]. */
class VehicleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        // In order: the last event is the current state.
        for (event in result.transitionEvents) {
            if (event.activityType == DetectedActivity.IN_VEHICLE) {
                DrivingDetector.onVehicle(context, event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            }
        }
    }
}

package com.orlikalabs.microreview

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a Bluetooth device is connected, for "Pause while a Bluetooth device is connected".
 *
 * Two sources: Bluetooth audio devices (car audio, headphones, earbuds), which Android lists without any permission;
 * and, with the "Nearby devices" permission, every connection, followed by [BluetoothWatcher] while the monitor runs.
 */
object BluetoothState {
    @Volatile
    internal var watcher: BluetoothWatcher? = null

    @SuppressLint("InlinedApi") // constants of newer versions: unknown types simply never match
    private val AUDIO_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
    )

    fun isConnected(context: Context): Boolean = audioDeviceConnected(context) || watcher?.anyConnected() == true

    /** "Nearby devices" (Android 12+); before, the install-time Bluetooth permission. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun audioDeviceConnected(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return try {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS or AudioManager.GET_DEVICES_INPUTS).any { it.type in AUDIO_TYPES }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Follows the Bluetooth connections while the monitor runs (needs "Nearby devices"): every device from its
 * connection on (link events), and the audio and headset devices already connected when it starts (profile proxies).
 */
class BluetoothWatcher(context: Context) {
    private val context = context.applicationContext
    private val linked = ConcurrentHashMap.newKeySet<String>()
    private val proxies = ConcurrentHashMap<Int, BluetoothProfile>()

    @Volatile
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> address(intent)?.let { linked.add(it) }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> address(intent)?.let { linked.remove(it) }
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF) != BluetoothAdapter.STATE_ON) linked.clear()
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies[profile] = proxy
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
        }
    }

    /** Starts following, if allowed. Safe to call again, for example once the permission is granted. */
    @SuppressLint("MissingPermission", "InlinedApi") // checked by hasPermission(); LE audio is just not found before 13
    fun start() {
        BluetoothState.watcher = this
        if (registered || !BluetoothState.hasPermission(context)) return
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            val adapter = adapter() ?: return
            listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.LE_AUDIO).forEach { profile ->
                adapter.getProfileProxy(context, profileListener, profile)
            }
        } catch (e: Exception) {
            FocusLog.i(context, "bluetooth: can't follow connections ($e)")
        }
    }

    fun stop() {
        if (BluetoothState.watcher === this) BluetoothState.watcher = null
        if (registered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Already gone.
            }
            registered = false
        }
        val adapter = adapter()
        proxies.forEach { (profile, proxy) -> adapter?.closeProfileProxy(profile, proxy) }
        proxies.clear()
        linked.clear()
    }

    @SuppressLint("MissingPermission") // a revoked permission throws: caught
    fun anyConnected(): Boolean {
        if (linked.isNotEmpty()) return true
        if (!BluetoothState.hasPermission(context)) return false
        return try {
            proxies.values.any { it.connectedDevices.isNotEmpty() }
        } catch (e: Exception) {
            false
        }
    }

    private fun adapter(): BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    private fun address(intent: Intent): String? {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        return try {
            device?.address
        } catch (e: SecurityException) {
            null
        }
    }
}

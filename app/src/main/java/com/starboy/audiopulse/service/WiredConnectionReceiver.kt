package com.starboy.audiopulse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.starboy.audiopulse.data.ConnectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wired plug receiver registered by AudioPulseApplication, not an Activity.
 * The sticky state delivered during registration is recorded but never treated
 * as a physical disconnect/connect transition.
 */
class WiredConnectionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WiredConnectionReceiver"
    }

    private data class WiredDevice(
        val name: String,
        val address: String,
        val type: Int
    )

    @Volatile
    private var lastKnownConnected: Boolean? = null

    @Volatile
    private var lastKnownDevice: WiredDevice? = null

    fun initialize(context: Context, stickyState: Int) {
        synchronized(this) {
            val currentDevices = getConnectedWiredDevices(context)
            if (currentDevices.isNotEmpty()) {
                lastKnownConnected = true
                lastKnownDevice = currentDevices.first()
            } else if (stickyState == 0 || stickyState == 1) {
                lastKnownConnected = stickyState == 1
            } else {
                lastKnownConnected = false
            }

            DiagnosticLogger.log(
                context, "application_receiver", "WIRED_STATE_INITIALIZED",
                lastKnownDevice?.name ?: "No wired device",
                "WIRED",
                "connected=$lastKnownConnected; stickyState=$stickyState; deviceAddress=${lastKnownDevice?.address ?: "unknown"}"
            )
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_HEADSET_PLUG) return

        val rawState = intent.getIntExtra("state", -1)
        if (rawState != 0 && rawState != 1) {
            DiagnosticLogger.log(
                context, "application_receiver", "WIRED_CONNECTION_EVENT",
                "Unknown Wired Device", "WIRED",
                "rawState=$rawState; ignored=true", isReplay = true
            )
            return
        }

        val connected = rawState == 1
        val currentDevices = getConnectedWiredDevices(context)
        val currentDevice = currentDevices.firstOrNull()
        val device: WiredDevice?
        val isReplay: Boolean

        synchronized(this) {
            isReplay = lastKnownConnected == connected
            device = if (connected) {
                currentDevice ?: lastKnownDevice ?: createFallbackDevice(intent)
            } else {
                lastKnownDevice ?: currentDevice ?: createFallbackDevice(intent)
            }

            if (!isReplay) {
                lastKnownConnected = connected
                lastKnownDevice = if (connected) device else null
            }
        }

        val selectedDevice = device ?: createFallbackDevice(intent)
        val stateName = if (connected) "PLUGGED" else "UNPLUGGED"
        DiagnosticLogger.log(
            context, "application_receiver", "WIRED_CONNECTION_EVENT",
            selectedDevice.name, "WIRED",
            "state=$stateName; rawState=$rawState; deviceAddress=${selectedDevice.address}; realChange=${!isReplay}",
            isReplay = isReplay
        )
        Log.d(TAG, "Wired state=$stateName replay=$isReplay device=${selectedDevice.name} address=${selectedDevice.address}")

        if (isReplay) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (connected) {
                    DeviceSessionManager.connected(
                        context, selectedDevice.address, selectedDevice.name,
                        ConnectionType.WIRED, "application_receiver", "wired_plug_event"
                    )
                } else {
                    DeviceSessionManager.disconnected(
                        context, selectedDevice.address, selectedDevice.name,
                        ConnectionType.WIRED, "application_receiver", "wired_unplug_event"
                    )
                }
            } catch (e: Exception) {
                DiagnosticLogger.log(
                    context, "application_receiver", "SESSION_EVENT_ERROR",
                    selectedDevice.name, "WIRED",
                    "deviceAddress=${selectedDevice.address}; error=${e.javaClass.simpleName}:${e.message}"
                )
                Log.e(TAG, "Unable to process wired session event", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createFallbackDevice(intent: Intent): WiredDevice {
        val name = intent.getStringExtra("name")?.takeIf { it.isNotBlank() } ?: "Wired Headphones"
        val type = AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        return WiredDevice(name, "wired:headset", type)
    }

    private fun getConnectedWiredDevices(context: Context): List<WiredDevice> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }
            .map { info ->
                val name = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired Headphones"
                val platformAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.address
                } else {
                    ""
                }
                val address = platformAddress.takeIf { it.isNotBlank() }
                    ?: "wired:headset"
                WiredDevice(name, address, info.type)
            }
    }
}

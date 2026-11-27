package com.starboy.audiopulse.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.starboy.audiopulse.data.ConnectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manifest receiver for Bluetooth profile connection changes.
 * Wired events are handled by the application-lifetime WiredConnectionReceiver.
 */
class HeadphoneReceiver(private val eventSource: String = "manifest_receiver") : BroadcastReceiver() {
    companion object {
        private const val TAG = "HeadphoneReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
                handleBluetoothEvent(context, intent, state, "A2DP")
            }
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                handleBluetoothEvent(context, intent, state, "HEADSET")
            }
        }
    }

    private fun handleBluetoothEvent(
        context: Context,
        intent: Intent,
        state: Int,
        profile: String
    ) {
        val device = getBluetoothDevice(intent)
        val deviceName = getBluetoothDeviceName(device, "Bluetooth $profile Device")
        val deviceAddress = getBluetoothDeviceAddress(device)
        val stateName = bluetoothStateName(state)

        DiagnosticLogger.log(
            context, eventSource, "BLUETOOTH_CONNECTION_EVENT", deviceName,
            "BLUETOOTH_$profile",
            "profile=$profile; action=${intent.action}; state=$stateName; rawState=$state; deviceAddress=${deviceAddress ?: "unknown"}"
        )
        Log.d(TAG, "Bluetooth $profile state=$stateName device=$deviceName address=$deviceAddress")

        val reason = "bluetooth_${profile.lowercase()}_${stateName.lowercase()}"
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> launchSessionOperation(context) {
                DeviceSessionManager.connected(
                    context, deviceAddress.orEmpty(), deviceName,
                    ConnectionType.BLUETOOTH, eventSource, reason
                )
            }
            BluetoothProfile.STATE_DISCONNECTED -> launchSessionOperation(context) {
                DeviceSessionManager.disconnected(
                    context, deviceAddress.orEmpty(), deviceName,
                    ConnectionType.BLUETOOTH, eventSource, reason
                )
            }
            else -> Unit
        }
    }

    private fun launchSessionOperation(context: Context, operation: suspend () -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                operation()
            } catch (e: Exception) {
                DiagnosticLogger.log(
                    context, eventSource, "SESSION_EVENT_ERROR", "Unknown Bluetooth Device",
                    "BLUETOOTH", "error=${e.javaClass.simpleName}:${e.message}"
                )
                Log.e(TAG, "Unable to process Bluetooth session event", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun getBluetoothDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun getBluetoothDeviceName(device: BluetoothDevice?, fallback: String): String =
        try {
            device?.name ?: fallback
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for device name", e)
            fallback
        }

    private fun getBluetoothDeviceAddress(device: BluetoothDevice?): String? =
        try {
            device?.address
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for device address", e)
            null
        }

    private fun bluetoothStateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }
}

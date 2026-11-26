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
import com.starboy.audiopulse.data.AppDatabase
import com.starboy.audiopulse.data.ConnectionType
import com.starboy.audiopulse.data.DiagnosticLog
import com.starboy.audiopulse.data.HeadphoneSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that detects headphone connection/disconnection events.
 * Diagnostic events are saved locally so they can be viewed in AudioPulse.
 */
class HeadphoneReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeadphoneReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: "Unknown action"
        Log.d(TAG, "Action: $action")
        DiagnosticLog.record(context, "Broadcast received: $action")

        when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", -1)
                val name = intent.getStringExtra("name") ?: "Wired Headphones"
                DiagnosticLog.record(context, "Wired state: $state")

                when (state) {
                    0 -> {
                        Log.d(TAG, "Wired headphones unplugged: $name")
                        handleHeadphoneDisconnect(context)
                    }
                    1 -> {
                        Log.d(TAG, "Wired headphones plugged: $name")
                        handleHeadphoneConnect(context, name, ConnectionType.WIRED)
                    }
                    else -> DiagnosticLog.record(context, "Unknown wired state: $state")
                }
            }

            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
                DiagnosticLog.record(context, "Bluetooth A2DP state: ${stateName(state)} ($state)")

                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                val deviceName = try {
                    device?.name ?: "Bluetooth Device"
                } catch (e: SecurityException) {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for device name", e)
                    DiagnosticLog.record(context, "Permission error reading Bluetooth device name")
                    "Permission Denied Device"
                }

                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Bluetooth audio device connected: $deviceName")
                        handleHeadphoneConnect(context, deviceName, ConnectionType.BLUETOOTH)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Bluetooth audio device disconnected: $deviceName")
                        handleHeadphoneDisconnect(context)
                    }
                    else -> DiagnosticLog.record(context, "A2DP intermediate/unknown state: $state")
                }
            }

            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                DiagnosticLog.record(context, "Bluetooth headset state: ${stateName(state)} ($state)")

                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                val deviceName = try {
                    device?.name ?: "Bluetooth Headset"
                } catch (e: SecurityException) {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for device name", e)
                    DiagnosticLog.record(context, "Permission error reading Bluetooth headset name")
                    "Permission Denied Device"
                }

                when (state) {
                    BluetoothHeadset.STATE_CONNECTED -> {
                        Log.d(TAG, "Bluetooth headset connected: $deviceName")
                        handleHeadphoneConnect(context, deviceName, ConnectionType.BLUETOOTH)
                    }
                    BluetoothHeadset.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Bluetooth headset disconnected: $deviceName")
                        handleHeadphoneDisconnect(context)
                    }
                    else -> DiagnosticLog.record(context, "Headset intermediate/unknown state: $state")
                }
            }

            else -> DiagnosticLog.record(context, "Received unhandled action: $action")
        }
    }

    private fun handleHeadphoneConnect(
        context: Context,
        deviceName: String,
        connectionType: ConnectionType
    ) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                DiagnosticLog.record(context, "Starting session insert for $connectionType")
                val database = AppDatabase.getDatabase(context)
                val sessionDao = database.sessionDao()
                val activeSessions = sessionDao.getActiveSessions()

                if (activeSessions.isNotEmpty()) {
                    Log.d(TAG, "Active session already exists. Ignoring duplicate connection.")
                    DiagnosticLog.record(context, "Insert skipped: an active session already exists")
                    return@launch
                }

                val session = HeadphoneSession(
                    deviceName = deviceName,
                    connectionType = connectionType,
                    connectedTime = System.currentTimeMillis(),
                    disconnectedTime = null,
                    isActive = true
                )

                Log.d(TAG, "Session started: $deviceName ($connectionType)")
                DiagnosticLog.record(context, "Session insert successful (ID: $insertedId)")
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting session", e)
                DiagnosticLog.record(context, "Session insert FAILED: ${e.javaClass.simpleName}: ${e.message ?: "no message"}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleHeadphoneDisconnect(context: Context) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                DiagnosticLog.record(context, "Starting session end/update")
                val database = AppDatabase.getDatabase(context)
                val sessionDao = database.sessionDao()
                val activeSessions = sessionDao.getActiveSessions()

                if (activeSessions.isNotEmpty()) {
                    val lastSession = activeSessions.first()
                    val updatedSession = lastSession.copy(
                        disconnectedTime = System.currentTimeMillis(),
                        isActive = false
                    )

                    sessionDao.updateSession(updatedSession)
                    Log.d(TAG, "Session ended: ${lastSession.deviceName}")
                    DiagnosticLog.record(context, "Session update successful for ID: ${lastSession.id}")
                } else {
                    DiagnosticLog.record(context, "Disconnect received, but no active session exists")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating session", e)
                DiagnosticLog.record(context, "Session update FAILED: ${e.javaClass.simpleName}: ${e.message ?: "no message"}")
            } finally {
                pendingResult.finish()
            }
        }
    }

}

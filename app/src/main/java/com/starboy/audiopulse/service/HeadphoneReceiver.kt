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
import com.starboy.audiopulse.data.HeadphoneSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that detects headphone connection/disconnection events
 */
class HeadphoneReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeadphoneReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        Log.d(TAG, "Action: ${intent.action}")

        when (intent.action) {
            // Wired headphones
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", -1)
                val name = intent.getStringExtra("name") ?: "Wired Headphones"

                when (state) {
                    0 -> {
                        Log.d(TAG, "Wired headphones unplugged: $name")
                        handleHeadphoneDisconnect(context)
                    }
                    1 -> {
                        Log.d(TAG, "Wired headphones plugged: $name")
                        handleHeadphoneConnect(context, name, ConnectionType.WIRED)
                    }
                }
            }

            // Bluetooth/TWS Audio Connected
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)

                // Android 13+ Safe Parcelable Extraction
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                // Fallback in case of missing BLUETOOTH_CONNECT permission
                val deviceName = try {
                    device?.name ?: "Bluetooth Device"
                } catch (e: SecurityException) {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for device name")
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
                }
            }

            // Bluetooth Headset Connected
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)

                // Android 13+ Safe Parcelable Extraction
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                // Fallback in case of missing BLUETOOTH_CONNECT permission
                val deviceName = try {
                    device?.name ?: "Bluetooth Headset"
                } catch (e: SecurityException) {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for device name")
                    "Permission Denied Device"
                }

                when (state) {
                    BluetoothHeadset.STATE_CONNECTED  -> {
                        Log.d(TAG, "Bluetooth headset connected: $deviceName")
                        handleHeadphoneConnect(context, deviceName, ConnectionType.BLUETOOTH)
                    }
                    BluetoothHeadset.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Bluetooth headset disconnected: $deviceName")
                        handleHeadphoneDisconnect(context)
                    }
                }
            }
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
                val database = AppDatabase.getDatabase(context)
                val sessionDao = database.sessionDao()

                val activeSessions = sessionDao.getActiveSessions()

                if (activeSessions.isNotEmpty()) {
                    Log.d(TAG, "Active session already exists. Ignoring duplicate connection.")
                    return@launch
                }

                val session = HeadphoneSession(
                    deviceName = deviceName,
                    connectionType = connectionType,
                    connectedTime = System.currentTimeMillis(),
                    disconnectedTime = null,
                    isActive = true
                )

                sessionDao.insertSession(session)
                Log.d(TAG, "Session started: $deviceName ($connectionType)")
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting session", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleHeadphoneDisconnect(context: Context) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating session", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
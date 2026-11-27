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
 * The no-argument constructor is used by the manifest receiver. MainActivity
 * supplies "dynamic_receiver" for its dynamically registered wired receiver.
 */
class HeadphoneReceiver(private val eventSource: String = "manifest_receiver") : BroadcastReceiver() {
    companion object { private const val TAG = "HeadphoneReceiver" }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", -1)
                val name = intent.getStringExtra("name") ?: "Wired Headphones"
                val stateName = when (state) {
                    0 -> "UNPLUGGED"
                    1 -> "PLUGGED"
                    else -> "UNKNOWN($state)"
                }
                DiagnosticLogger.log(context, eventSource, "WIRED_CONNECTION_EVENT", name,
                    ConnectionType.WIRED.name, "action=${intent.action}; state=$stateName; rawState=$state")
                Log.d(TAG, "Wired state=$stateName device=$name source=$eventSource")
                when (state) {
                    0 -> handleHeadphoneDisconnect(context, name, ConnectionType.WIRED, "wired_unplug_event")
                    1 -> handleHeadphoneConnect(context, name, ConnectionType.WIRED, "wired_plug_event")
                }
            }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
                val name = getBluetoothDeviceName(getBluetoothDevice(intent), "Bluetooth A2DP Device")
                val stateName = bluetoothStateName(state)
                DiagnosticLogger.log(context, eventSource, "BLUETOOTH_CONNECTION_EVENT", name,
                    "BLUETOOTH_A2DP", "profile=A2DP; action=${intent.action}; state=$stateName; rawState=$state")
                Log.d(TAG, "Bluetooth A2DP state=$stateName device=$name source=$eventSource")
                when (state) {
                    BluetoothProfile.STATE_CONNECTED ->
                        handleHeadphoneConnect(context, name, ConnectionType.BLUETOOTH, "bluetooth_a2dp_connected")
                    BluetoothProfile.STATE_DISCONNECTED ->
                        handleHeadphoneDisconnect(context, name, ConnectionType.BLUETOOTH, "bluetooth_a2dp_disconnected")
                }
            }
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                val name = getBluetoothDeviceName(getBluetoothDevice(intent), "Bluetooth Headset")
                val stateName = bluetoothStateName(state)
                DiagnosticLogger.log(context, eventSource, "BLUETOOTH_CONNECTION_EVENT", name,
                    "BLUETOOTH_HEADSET", "profile=HEADSET; action=${intent.action}; state=$stateName; rawState=$state")
                Log.d(TAG, "Bluetooth Headset state=$stateName device=$name source=$eventSource")
                when (state) {
                    BluetoothHeadset.STATE_CONNECTED ->
                        handleHeadphoneConnect(context, name, ConnectionType.BLUETOOTH, "bluetooth_headset_connected")
                    BluetoothHeadset.STATE_DISCONNECTED ->
                        handleHeadphoneDisconnect(context, name, ConnectionType.BLUETOOTH, "bluetooth_headset_disconnected")
                }
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
        try { device?.name ?: fallback }
        catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for device name", e)
            "Permission Denied Device"
        }

    private fun bluetoothStateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }

    private fun handleHeadphoneConnect(context: Context, deviceName: String,
        connectionType: ConnectionType, reason: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessionDao = AppDatabase.getDatabase(context).sessionDao()
                val activeSessions = sessionDao.getActiveSessions()
                if (activeSessions.isNotEmpty()) {
                    DiagnosticLogger.log(context, eventSource, "SESSION_CREATE_SKIPPED", deviceName,
                        connectionType.name, "reason=active_session_already_exists; trigger=$reason")
                    Log.d(TAG, "Active session already exists. Ignoring duplicate connection.")
                    return@launch
                }
                val session = HeadphoneSession(deviceName = deviceName, connectionType = connectionType,
                    connectedTime = System.currentTimeMillis(), disconnectedTime = null, isActive = true)
                val sessionId = sessionDao.insertSession(session)
                DiagnosticLogger.log(context, eventSource, "SESSION_CREATE", deviceName, connectionType.name,
                    "sessionId=$sessionId; reason=$reason")
                Log.d(TAG, "Session started: $deviceName ($connectionType)")
            } catch (e: Exception) {
                DiagnosticLogger.log(context, eventSource, "SESSION_CREATE_ERROR", deviceName, connectionType.name,
                    "reason=$reason; error=${e.javaClass.simpleName}:${e.message}")
                Log.e(TAG, "Error inserting session", e)
            } finally { pendingResult.finish() }
        }
    }

    private fun handleHeadphoneDisconnect(context: Context, deviceName: String,
        connectionType: ConnectionType, reason: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessionDao = AppDatabase.getDatabase(context).sessionDao()
                val activeSessions = sessionDao.getActiveSessions()
                if (activeSessions.isNotEmpty()) {
                    val lastSession = activeSessions.first()
                    val updatedSession = lastSession.copy(disconnectedTime = System.currentTimeMillis(), isActive = false)
                    sessionDao.updateSession(updatedSession)
                    DiagnosticLogger.log(context, eventSource, "SESSION_UPDATE",
                        lastSession.deviceName, lastSession.connectionType.name,
                        "sessionId=${lastSession.id}; isActive=true->false; triggerDevice=$deviceName; triggerType=${connectionType.name}; reason=$reason")
                    DiagnosticLogger.log(context, eventSource, "SESSION_CLOSE",
                        lastSession.deviceName, lastSession.connectionType.name,
                        "sessionId=${lastSession.id}; reason=$reason; triggerDevice=$deviceName; triggerType=${connectionType.name}")
                    Log.d(TAG, "Session ended: ${lastSession.deviceName}")
                } else {
                    DiagnosticLogger.log(context, eventSource, "SESSION_CLOSE_SKIPPED", deviceName,
                        connectionType.name, "reason=no_active_session; trigger=$reason")
                }
            } catch (e: Exception) {
                DiagnosticLogger.log(context, eventSource, "SESSION_UPDATE_ERROR", deviceName, connectionType.name,
                    "reason=$reason; error=${e.javaClass.simpleName}:${e.message}")
                Log.e(TAG, "Error updating session", e)
            } finally { pendingResult.finish() }
        }
    }
}

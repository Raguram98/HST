package com.starboy.audiopulse.service

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.starboy.audiopulse.data.AppDatabase
import com.starboy.audiopulse.data.ConnectionType
import com.starboy.audiopulse.data.HeadphoneSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Serializes connection events from all receiver sources and matches sessions
 * by stable device identity rather than by whichever session happens to be first.
 */
object DeviceSessionManager {
    private const val TAG = "DeviceSessionManager"
    private val eventMutex = Mutex()

    private fun normalizeAddress(address: String): String = address.trim().uppercase(Locale.US)

    /**
     * Reads connected audio outputs and closes only addressed sessions whose
     * device is no longer present. If a Bluetooth output cannot expose its
     * address, reconciliation is skipped rather than risking a false close.
     */
    suspend fun reconcileFromAudioManager(context: Context, source: String) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val addresses = mutableSetOf<String>()

            outputs.forEach { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "Wired Headphones"
                        val platformAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            device.address
                        } else {
                            ""
                        }
                        val address = platformAddress.takeIf { it.isNotBlank() }
                            ?: "wired:headset"
                        addresses.add(normalizeAddress(address))
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            device.address
                        } else {
                            ""
                        }
                        if (address.isNullOrBlank()) {
                            DiagnosticLogger.log(
                                context, source, "SESSION_RECONCILE_SKIPPED",
                                device.productName?.toString() ?: "Bluetooth Audio Device",
                                "BLUETOOTH", "reason=connected_output_missing_device_address"
                            )
                            return
                        }
                        addresses.add(normalizeAddress(address))
                    }
                }
            }

            reconcileConnectedDevices(context, addresses, source)
        } catch (e: SecurityException) {
            DiagnosticLogger.log(
                context, source, "SESSION_RECONCILE_SKIPPED", "N/A", "AUDIO",
                "reason=audio_device_query_permission_denied; error=${e.message}"
            )
            Log.w(TAG, "Audio device reconciliation skipped due to permission", e)
        } catch (e: Exception) {
            DiagnosticLogger.log(
                context, source, "SESSION_RECONCILE_SKIPPED", "N/A", "AUDIO",
                "reason=audio_device_query_failed; error=${e.javaClass.simpleName}:${e.message}"
            )
            Log.e(TAG, "Audio device reconciliation failed", e)
        }
    }

    suspend fun connected(
        context: Context,
        deviceAddress: String,
        deviceName: String,
        connectionType: ConnectionType,
        source: String,
        reason: String
    ) {
        if (deviceAddress.isBlank()) {
            DiagnosticLogger.log(
                context, source, "SESSION_CREATE_SKIPPED", deviceName, connectionType.name,
                "reason=missing_device_address; trigger=$reason"
            )
            return
        }

        val stableAddress = normalizeAddress(deviceAddress)
        eventMutex.withLock {
            val dao = AppDatabase.getDatabase(context).sessionDao()
            val existing = dao.getActiveSessionsForDevice(stableAddress)
            if (existing.isNotEmpty()) {
                DiagnosticLogger.log(
                    context, source, "SESSION_CREATE_SKIPPED", deviceName, connectionType.name,
                    "reason=duplicate_open_session; deviceAddress=$stableAddress; trigger=$reason"
                )
                Log.d(TAG, "Skipping duplicate connection for $deviceAddress")
                return
            }

            val sessionId = dao.insertSession(
                HeadphoneSession(
                    deviceName = deviceName,
                    connectionType = connectionType,
                    deviceAddress = stableAddress,
                    connectedTime = System.currentTimeMillis(),
                    disconnectedTime = null,
                    isActive = true
                )
            )
            DiagnosticLogger.log(
                context, source, "SESSION_CREATE", deviceName, connectionType.name,
                "sessionId=$sessionId; deviceAddress=$stableAddress; reason=$reason"
            )
            Log.d(TAG, "Session started: $deviceName ($deviceAddress)")
        }
    }

    suspend fun disconnected(
        context: Context,
        deviceAddress: String,
        deviceName: String,
        connectionType: ConnectionType,
        source: String,
        reason: String
    ) {
        if (deviceAddress.isBlank()) {
            DiagnosticLogger.log(
                context, source, "SESSION_CLOSE_SKIPPED", deviceName, connectionType.name,
                "reason=missing_device_address; trigger=$reason"
            )
            return
        }

        val stableAddress = normalizeAddress(deviceAddress)
        eventMutex.withLock {
            val dao = AppDatabase.getDatabase(context).sessionDao()
            val matchingSessions = dao.getActiveSessionsForDevice(stableAddress)
            if (matchingSessions.isEmpty()) {
                DiagnosticLogger.log(
                    context, source, "SESSION_CLOSE_SKIPPED", deviceName, connectionType.name,
                    "reason=no_matching_active_session; deviceAddress=$stableAddress; trigger=$reason"
                )
                return
            }

            val now = System.currentTimeMillis()
            matchingSessions.forEach { session ->
                dao.updateSession(session.copy(disconnectedTime = now, isActive = false))
                DiagnosticLogger.log(
                    context, source, "SESSION_CLOSE", session.deviceName, session.connectionType.name,
                    "sessionId=${session.id}; deviceAddress=$stableAddress; reason=$reason"
                )
            }
            Log.d(TAG, "Closed matching session(s) for $deviceAddress")
        }
    }

    suspend fun reconcileConnectedDevices(
        context: Context,
        connectedDeviceAddresses: Set<String>,
        source: String = "reconciliation"
    ) {
        val stableAddresses = connectedDeviceAddresses.map { normalizeAddress(it) }.toSet()
        eventMutex.withLock {
            val dao = AppDatabase.getDatabase(context).sessionDao()
            val activeSessions = dao.getActiveSessions()
            val now = System.currentTimeMillis()

            activeSessions.forEach { session ->
                // Legacy sessions have no address. Never close them based on an
                // identity comparison that cannot be made safely.
                if (session.deviceAddress.isBlank()) {
                    DiagnosticLogger.log(
                        context, source, "SESSION_RECONCILE_SKIPPED", session.deviceName,
                        session.connectionType.name,
                        "sessionId=${session.id}; reason=legacy_session_without_device_address"
                    )
                } else if (normalizeAddress(session.deviceAddress) !in stableAddresses) {
                    dao.updateSession(session.copy(disconnectedTime = now, isActive = false))
                    DiagnosticLogger.log(
                        context, source, "SESSION_CLOSE", session.deviceName, session.connectionType.name,
                        "sessionId=${session.id}; deviceAddress=${session.deviceAddress}; reason=device_absent_during_reconciliation"
                    )
                } else {
                    DiagnosticLogger.log(
                        context, source, "SESSION_RECONCILE_MATCH", session.deviceName,
                        session.connectionType.name,
                        "sessionId=${session.id}; deviceAddress=${session.deviceAddress}; reason=device_still_connected"
                    )
                }
            }
        }
    }
}

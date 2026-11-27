package com.starboy.audiopulse.service

import android.content.Context
import android.util.Log
import com.starboy.audiopulse.data.AppDatabase
import com.starboy.audiopulse.data.ConnectionType
import com.starboy.audiopulse.data.HeadphoneSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes connection events from all receiver sources and matches sessions
 * by stable device identity rather than by whichever session happens to be first.
 */
object DeviceSessionManager {
    private const val TAG = "DeviceSessionManager"
    private val eventMutex = Mutex()

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

        eventMutex.withLock {
            val dao = AppDatabase.getDatabase(context).sessionDao()
            val existing = dao.getActiveSessionsForDevice(deviceAddress)
            if (existing.isNotEmpty()) {
                DiagnosticLogger.log(
                    context, source, "SESSION_CREATE_SKIPPED", deviceName, connectionType.name,
                    "reason=duplicate_open_session; deviceAddress=$deviceAddress; trigger=$reason"
                )
                Log.d(TAG, "Skipping duplicate connection for $deviceAddress")
                return
            }

            val sessionId = dao.insertSession(
                HeadphoneSession(
                    deviceName = deviceName,
                    connectionType = connectionType,
                    deviceAddress = deviceAddress,
                    connectedTime = System.currentTimeMillis(),
                    disconnectedTime = null,
                    isActive = true
                )
            )
            DiagnosticLogger.log(
                context, source, "SESSION_CREATE", deviceName, connectionType.name,
                "sessionId=$sessionId; deviceAddress=$deviceAddress; reason=$reason"
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

        eventMutex.withLock {
            val dao = AppDatabase.getDatabase(context).sessionDao()
            val matchingSessions = dao.getActiveSessionsForDevice(deviceAddress)
            if (matchingSessions.isEmpty()) {
                DiagnosticLogger.log(
                    context, source, "SESSION_CLOSE_SKIPPED", deviceName, connectionType.name,
                    "reason=no_matching_active_session; deviceAddress=$deviceAddress; trigger=$reason"
                )
                return
            }

            val now = System.currentTimeMillis()
            matchingSessions.forEach { session ->
                dao.updateSession(session.copy(disconnectedTime = now, isActive = false))
                DiagnosticLogger.log(
                    context, source, "SESSION_CLOSE", session.deviceName, session.connectionType.name,
                    "sessionId=${session.id}; deviceAddress=$deviceAddress; reason=$reason"
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
                } else if (session.deviceAddress !in connectedDeviceAddresses) {
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

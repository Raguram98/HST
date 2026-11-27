package com.starboy.audiopulse.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Data model representing a single headphone session.
 */
@Entity(tableName = "headphone_sessions")
data class HeadphoneSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val deviceName: String,
    val connectionType: ConnectionType,

    // Stable device key: Bluetooth MAC address or a synthetic wired-device key.
    // Empty means this is a legacy session created before device identity was stored.
    @ColumnInfo(defaultValue = "''")
    val deviceAddress: String = "",

    val connectedTime: Long,
    val disconnectedTime: Long? = null,
    val isActive: Boolean = true
) {
    fun getDurationMs(): Long {
        return if (disconnectedTime != null) {
            disconnectedTime - connectedTime
        } else {
            System.currentTimeMillis() - connectedTime
        }
    }

    fun getDurationString(): String {
        val durationMs = getDurationMs()
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = durationMs / (1000 * 60 * 60)

        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%02d:%02d", minutes, seconds)
            else -> String.format("%02d sec", seconds)
        }
    }

    fun getFormattedConnectTime(): String {
        val date = Date(connectedTime)
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(date)
    }
}

enum class ConnectionType {
    BLUETOOTH,
    WIRED
}

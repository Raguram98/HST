package com.starboy.audiopulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Data model representing a single headphone session
 */
@Entity(tableName = "headphone_sessions")
data class HeadphoneSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Device information
    val deviceName: String,
    val connectionType: ConnectionType, // BLUETOOTH or WIRED
    
    // Session timing
    val connectedTime: Long, // Timestamp in milliseconds
    val disconnectedTime: Long? = null, // Null if currently connected
    
    // Derived
    val isActive: Boolean = true
) {
    // Calculate session duration in milliseconds
    fun getDurationMs(): Long {
        return if (disconnectedTime != null) {
            disconnectedTime - connectedTime
        } else {
            System.currentTimeMillis() - connectedTime
        }
    }
    
    // Convert to user-friendly duration string
    fun getDurationString(): String {
        val durationMs = getDurationMs()
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        
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

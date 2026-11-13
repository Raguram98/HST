package com.starboy.audiopulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for accessing HeadphoneSession data from the database
 */
@Dao
interface SessionDao {

    /**
     * Insert a new session
     */
    @Insert
    suspend fun insertSession(session: HeadphoneSession): Long

    /**
     * Update an existing session (e.g., mark as disconnected)
     */
    @Update
    suspend fun updateSession(session: HeadphoneSession)

    /**
     * Get all sessions ordered by most recent first
     */
    @Query("SELECT * FROM headphone_sessions ORDER BY connectedTime DESC")
    fun getAllSessions(): Flow<List<HeadphoneSession>>

    /**
     * Get all sessions (one-time query)
     */
    @Query("SELECT * FROM headphone_sessions ORDER BY connectedTime DESC")
    suspend fun getAllSessionsOnce(): List<HeadphoneSession>

    /**
     * Get active sessions (currently connected)
     */
    @Query("SELECT * FROM headphone_sessions WHERE isActive = 1 ORDER BY connectedTime DESC")
    suspend fun getActiveSessions(): List<HeadphoneSession>

    /**
     * Get sessions within a date range
     */
    @Query("SELECT * FROM headphone_sessions WHERE connectedTime BETWEEN :startTime AND :endTime ORDER BY connectedTime DESC")
    suspend fun getSessionsInRange(startTime: Long, endTime: Long): List<HeadphoneSession>

    /**
     * Get sessions for a specific device
     */
    @Query("SELECT * FROM headphone_sessions WHERE deviceName = :deviceName ORDER BY connectedTime DESC")
    fun getSessionsByDevice(deviceName: String): Flow<List<HeadphoneSession>>

    /**
     * Get total duration for all sessions in milliseconds
     */
    @Query("SELECT SUM(CASE WHEN disconnectedTime IS NOT NULL THEN disconnectedTime - connectedTime ELSE 0 END) FROM headphone_sessions")
    suspend fun getTotalDuration(): Long?

    /**
     * Get count of sessions
     */
    @Query("SELECT COUNT(*) FROM headphone_sessions")
    suspend fun getSessionCount(): Int

    /**
     * Get most used device name
     */
    @Query("SELECT deviceName FROM headphone_sessions GROUP BY deviceName ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getMostUsedDevice(): String?
}
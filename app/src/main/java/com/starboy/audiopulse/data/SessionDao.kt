package com.starboy.audiopulse.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: HeadphoneSession): Long

    @Update
    suspend fun updateSession(session: HeadphoneSession)

    @Delete
    suspend fun deleteSession(session: HeadphoneSession)

    @Query("SELECT * FROM headphone_sessions ORDER BY connectedTime DESC")
    fun getAllSessions(): Flow<List<HeadphoneSession>>

    @Query("SELECT * FROM headphone_sessions ORDER BY connectedTime DESC")
    suspend fun getAllSessionsOnce(): List<HeadphoneSession>

    @Query("SELECT * FROM headphone_sessions WHERE isActive = 1 ORDER BY connectedTime DESC")
    suspend fun getActiveSessions(): List<HeadphoneSession>

    @Query("SELECT * FROM headphone_sessions WHERE isActive = 1 AND deviceAddress = :deviceAddress ORDER BY connectedTime DESC")
    suspend fun getActiveSessionsForDevice(deviceAddress: String): List<HeadphoneSession>

    @Query("SELECT * FROM headphone_sessions WHERE connectedTime BETWEEN :startTime AND :endTime ORDER BY connectedTime DESC")
    suspend fun getSessionsInRange(startTime: Long, endTime: Long): List<HeadphoneSession>

    @Query("SELECT * FROM headphone_sessions WHERE deviceName = :deviceName ORDER BY connectedTime DESC")
    fun getSessionsByDevice(deviceName: String): Flow<List<HeadphoneSession>>

    @Query("SELECT SUM(CASE WHEN disconnectedTime IS NOT NULL THEN disconnectedTime - connectedTime ELSE 0 END) FROM headphone_sessions")
    suspend fun getTotalDuration(): Long?

    @Query("SELECT COUNT(*) FROM headphone_sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT deviceName FROM headphone_sessions GROUP BY deviceName ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getMostUsedDevice(): String?
}

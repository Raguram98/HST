package com.starboy.audiopulse.repository

import com.starboy.audiopulse.data.HeadphoneSession
import kotlinx.coroutines.flow.Flow
import com.starboy.audiopulse.data.SessionDao

/**
 * Repository for managing HeadphoneSession data
 */
class SessionRepository(private val sessionDao: SessionDao) {

    val allSessions: Flow<List<HeadphoneSession>> = sessionDao.getAllSessions()

    suspend fun insertSession(session: HeadphoneSession): Long {
        return sessionDao.insertSession(session)
    }

    suspend fun updateSession(session: HeadphoneSession) {
        sessionDao.updateSession(session)
    }

    suspend fun deleteSession(session: HeadphoneSession) {
        sessionDao.deleteSession(session)
    }

    suspend fun getAllSessionsOnce(): List<HeadphoneSession> {
        return sessionDao.getAllSessionsOnce()
    }

    suspend fun getActiveSessions(): List<HeadphoneSession> {
        return sessionDao.getActiveSessions()
    }

    suspend fun getSessionsInRange(startTime: Long, endTime: Long): List<HeadphoneSession> {
        return sessionDao.getSessionsInRange(startTime, endTime)
    }

    fun getSessionsByDevice(deviceName: String): Flow<List<HeadphoneSession>> {
        return sessionDao.getSessionsByDevice(deviceName)
    }

    suspend fun getTotalDuration(): Long {
        return sessionDao.getTotalDuration() ?: 0L
    }

    suspend fun getSessionCount(): Int {
        return sessionDao.getSessionCount()
    }

    suspend fun getMostUsedDevice(): String? {
        return sessionDao.getMostUsedDevice()
    }
}
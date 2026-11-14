package com.starboy.audiopulse.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starboy.audiopulse.data.AppDatabase
import com.starboy.audiopulse.data.HeadphoneSession
import com.starboy.audiopulse.repository.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for managing HeadphoneSession data
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SessionRepository

    val allSessions: Flow<List<HeadphoneSession>>

    init {
        val database = AppDatabase.getDatabase(application)
        val sessionDao = database.sessionDao()
        repository = SessionRepository(sessionDao)
        allSessions = repository.allSessions

        // Emit updates every second for real-time timer
        startRealtimeUpdates()
    }

    private fun startRealtimeUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(1000) // Update every second
            }
        }
    }

    fun insertSession(session: HeadphoneSession) {
        viewModelScope.launch {
            repository.insertSession(session)
        }
    }

    fun updateSession(session: HeadphoneSession) {
        viewModelScope.launch {
            repository.updateSession(session)
        }
    }

    fun renameSession(session: HeadphoneSession, newName: String) {
        viewModelScope.launch {
            repository.updateSession(session.copy(deviceName = newName))
        }
    }

    fun deleteSession(session: HeadphoneSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun getAllSessions(onResult: (List<HeadphoneSession>) -> Unit) {
        viewModelScope.launch {
            val sessions = repository.getAllSessionsOnce()
            onResult(sessions)
        }
    }

    fun getTotalDuration(onResult: (Long) -> Unit) {
        viewModelScope.launch {
            val totalMs = repository.getTotalDuration()
            onResult(totalMs)
        }
    }

    fun getSessionCount(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.getSessionCount()
            onResult(count)
        }
    }

    fun getMostUsedDevice(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val device = repository.getMostUsedDevice()
            onResult(device)
        }
    }

    fun getTodaysSessions(onResult: (List<HeadphoneSession>) -> Unit) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val startOfDay = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            val endOfDay = calendar.timeInMillis

            val sessions = repository.getSessionsInRange(startOfDay, endOfDay)
            onResult(sessions)
        }
    }

    fun getWeeklyStats(onResult: (List<HeadphoneSession>) -> Unit) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            val startOfWeek = calendar.timeInMillis

            val sessions = repository.getSessionsInRange(startOfWeek, System.currentTimeMillis())
            onResult(sessions)
        }
    }

    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        val days = hours / 24

        return when {
            days > 0 -> String.format("%d day%s %02d:%02d:%02d", days, if (days > 1) "s" else "", hours % 24, minutes, seconds)
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%02d:%02d", minutes, seconds)
            else -> String.format("%02d sec", seconds)
        }
    }
}
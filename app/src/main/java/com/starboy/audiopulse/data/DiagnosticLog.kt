package com.starboy.audiopulse.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stores a small, persistent diagnostic history for Bluetooth tracking.
 * The log is kept locally on the device and is visible from the Insights screen.
 */
object DiagnosticLog {

    private const val PREFS_NAME = "audiopulse_diagnostics"
    private const val LOG_KEY = "events"
    private const val MAX_ENTRIES = 100
    private val lock = Any()

    fun record(context: Context, message: String) {
        synchronized(lock) {
            try {
                val preferences = context.applicationContext.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                val timestamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date())
                val oldEntries = preferences.getString(LOG_KEY, "").orEmpty()
                val entries = (oldEntries.lines().filter { it.isNotBlank() } +
                    "[$timestamp] $message").takeLast(MAX_ENTRIES)

                preferences.edit().putString(LOG_KEY, entries.joinToString("\n")).apply()
            } catch (_: Exception) {
                // Diagnostics must never interrupt headphone session tracking.
            }
        }
    }

    fun getLogs(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        return preferences.getString(LOG_KEY, "").orEmpty()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().remove(LOG_KEY).apply()
    }
}

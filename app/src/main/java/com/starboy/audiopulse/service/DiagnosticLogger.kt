package com.starboy.audiopulse.service

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a rolling diagnostic history and exports it as a readable text file.
 * Android 10+: Downloads/AudioPulse/AudioPulse-diagnostics.txt
 * Android 8/9: app-specific Documents directory.
 */
object DiagnosticLogger {
    private const val TAG = "AudioPulseDiagnostics"
    private const val PREFS_NAME = "audiopulse_diagnostics"
    private const val ENTRIES_KEY = "entries"
    private const val EXPORT_URI_KEY = "export_uri"
    private const val MAX_ENTRIES = 500
    private val lock = Any()

    fun log(context: Context, source: String, event: String, deviceName: String,
        deviceType: String, details: String = "") {
        synchronized(lock) {
            val appContext = context.applicationContext
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
            val entry = JSONObject().apply {
                put("timestamp", timestamp)
                put("event", event)
                put("deviceName", deviceName)
                put("deviceType", deviceType)
                put("appState", getProcessState())
                put("processAlive", true)
                put("pid", Process.myPid())
                put("source", source)
                put("details", details)
            }
            val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = preferences.getString(ENTRIES_KEY, "").orEmpty()
            val retained = (existing.split('\n').filter { it.isNotBlank() } + entry.toString())
                .takeLast(MAX_ENTRIES).joinToString("\n")
            // History is intentionally not cleared at app launch.
            preferences.edit().putString(ENTRIES_KEY, retained).apply()
            rewriteExportFile(appContext, retained)
            Log.d(TAG, entry.toString())
        }
    }

    private fun getProcessState(): String = try {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        if (info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
            "FOREGROUND" else "BACKGROUND"
    } catch (e: Exception) {
        Log.w(TAG, "Unable to determine app process importance", e)
        "UNKNOWN"
    }

    private fun rewriteExportFile(context: Context, retainedEntries: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                rewriteDownloadsFile(context, retainedEntries)
            } else {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir
                directory.mkdirs()
                File(directory, "AudioPulse-diagnostics.txt").writeText(retainedEntries, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to write exported diagnostic log", e)
        }
    }

    private fun rewriteDownloadsFile(context: Context, retainedEntries: String) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var uri: Uri? = preferences.getString(EXPORT_URI_KEY, null)?.let(Uri::parse)
        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "AudioPulse-diagnostics.txt")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AudioPulse")
            }
            uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) preferences.edit().putString(EXPORT_URI_KEY, uri.toString()).apply()
        }
        val exportUri = uri ?: return
        context.contentResolver.openOutputStream(exportUri, "rwt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(retainedEntries)
        }
    }
}

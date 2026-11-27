package com.starboy.audiopulse

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import com.starboy.audiopulse.service.WiredConnectionReceiver
import com.starboy.audiopulse.service.DeviceSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

/**
 * Owns the wired-headphone receiver for the lifetime of the app process,
 * independent of whether MainActivity is visible.
 */
class AudioPulseApplication : Application() {
    private lateinit var wiredReceiver: WiredConnectionReceiver

    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        val stickyIntent = registerReceiver(null, filter)
        val stickyState = stickyIntent?.getIntExtra("state", -1) ?: -1

        wiredReceiver = WiredConnectionReceiver()
        wiredReceiver.initialize(this, stickyState)

        ContextCompat.registerReceiver(
            this,
            wiredReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        CoroutineScope(Dispatchers.IO).launch {
            DeviceSessionManager.reconcileFromAudioManager(applicationContext, "application_onCreate")
        }
    }
}

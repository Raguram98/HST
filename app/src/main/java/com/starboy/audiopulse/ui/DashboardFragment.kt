package com.starboy.audiopulse.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.starboy.audiopulse.databinding.FragmentDashboardBinding
import com.starboy.audiopulse.ui.viewmodels.SessionViewModel
import com.starboy.audiopulse.data.ConnectionType

/**
 * Fragment that displays statistics and analytics dashboard
 * Shows TWS vs Wired usage breakdown and key metrics
 */
class DashboardFragment : Fragment() {

    private val viewModel: SessionViewModel by viewModels()
    private lateinit var binding: FragmentDashboardBinding
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            loadStatistics()
            // Update every second
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadStatistics()
        handler.post(updateRunnable)
    }

    private fun loadStatistics() {
        // Load total duration
        viewModel.getTotalDuration { totalMs ->
            binding.totalDurationText.text = viewModel.formatDuration(totalMs)
        }

        // Load session count
        viewModel.getSessionCount { count ->
            binding.sessionCountText.text = count.toString()
        }

        // Load today's duration
        viewModel.getTodaysSessions { todaysSessions ->
            val totalMs = todaysSessions.sumOf { it.getDurationMs() }
            binding.todayDurationText.text = viewModel.formatDuration(totalMs)
        }

        // Load device type breakdown
        viewModel.getAllSessions { sessions ->
            updateDeviceTypeStats(sessions)
        }

        // Load most used device
        viewModel.getMostUsedDevice { device ->
            if (device != null) {
                binding.mostUsedDeviceText.text = device

                // Find the device and get its connection type
                viewModel.getAllSessions { sessions ->
                    val deviceSession = sessions.find { it.deviceName == device }
                    if (deviceSession != null) {
                        val deviceType = if (deviceSession.connectionType == ConnectionType.BLUETOOTH) "🎧 TWS" else "🎙️ Wired"
                        val deviceTypeLabel = if (deviceSession.connectionType == ConnectionType.BLUETOOTH) "Wireless" else "Wired"
                        binding.favoriteDeviceIcon.text = if (deviceSession.connectionType == ConnectionType.BLUETOOTH) "🎧" else "🎙️"
                        binding.favoriteDeviceType.text = deviceTypeLabel
                    }
                }
            } else {
                binding.mostUsedDeviceText.text = "N/A"
                binding.favoriteDeviceType.text = ""
                binding.favoriteDeviceIcon.text = "❓"
            }
        }
    }

    private fun updateDeviceTypeStats(sessions: List<com.starboy.audiopulse.data.HeadphoneSession>) {
        // Split sessions by connection type
        val bluetoothSessions = sessions.filter { it.connectionType == ConnectionType.BLUETOOTH }
        val wiredSessions = sessions.filter { it.connectionType == ConnectionType.WIRED }

        // Calculate totals for Bluetooth/TWS
        val bluetoothTotalMs = bluetoothSessions.sumOf { it.getDurationMs() }
        binding.bluetoothDurationText.text = viewModel.formatDuration(bluetoothTotalMs)
        binding.bluetoothCountText.text = "${bluetoothSessions.size} session${if (bluetoothSessions.size != 1) "s" else ""}"

        // Calculate totals for Wired
        val wiredTotalMs = wiredSessions.sumOf { it.getDurationMs() }
        binding.wiredDurationText.text = viewModel.formatDuration(wiredTotalMs)
        binding.wiredCountText.text = "${wiredSessions.size} session${if (wiredSessions.size != 1) "s" else ""}"
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }
}
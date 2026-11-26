package com.starboy.audiopulse.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.starboy.audiopulse.data.ConnectionType
import com.starboy.audiopulse.databinding.FragmentDashboardBinding
import com.starboy.audiopulse.ui.viewmodels.SessionViewModel

/** Fragment that displays listening statistics and device insights. */
class DashboardFragment : Fragment() {

    private val viewModel: SessionViewModel by viewModels()
    private lateinit var binding: FragmentDashboardBinding
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            loadStatistics()
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
        viewModel.getTotalDuration { totalMs ->
            binding.totalDurationText.text = viewModel.formatDuration(totalMs)
        }

        viewModel.getSessionCount { count ->
            binding.sessionCountText.text = count.toString()
        }

        viewModel.getTodaysSessions { todaysSessions ->
            val totalMs = todaysSessions.sumOf { it.getDurationMs() }
            binding.todayDurationText.text = viewModel.formatDuration(totalMs)
        }

        viewModel.getAllSessions { sessions ->
            updateDeviceTypeStats(sessions)
        }

        viewModel.getMostUsedDevice { device ->
            if (device != null) {
                binding.mostUsedDeviceText.text = device
                viewModel.getAllSessions { sessions ->
                    val deviceSession = sessions.find { it.deviceName == device }
                    if (deviceSession != null) {
                        val deviceTypeLabel = if (deviceSession.connectionType == ConnectionType.BLUETOOTH) "Wireless" else "Wired"
                        binding.favoriteDeviceIcon.setImageResource(
                            if (deviceSession.connectionType == ConnectionType.BLUETOOTH) {
                                com.starboy.audiopulse.R.drawable.ic_headphones
                            } else {
                                com.starboy.audiopulse.R.drawable.ic_audio_jack
                            }
                        )
                        binding.favoriteDeviceType.text = deviceTypeLabel
                    }
                }
            } else {
                binding.mostUsedDeviceText.text = "N/A"
                binding.favoriteDeviceType.text = ""
                binding.favoriteDeviceIcon.setImageResource(com.starboy.audiopulse.R.drawable.ic_headphones)
            }
        }
    }

    private fun updateDeviceTypeStats(sessions: List<com.starboy.audiopulse.data.HeadphoneSession>) {
        val bluetoothSessions = sessions.filter { it.connectionType == ConnectionType.BLUETOOTH }
        val wiredSessions = sessions.filter { it.connectionType == ConnectionType.WIRED }

        val bluetoothTotalMs = bluetoothSessions.sumOf { it.getDurationMs() }
        binding.bluetoothDurationText.text = viewModel.formatDuration(bluetoothTotalMs)
        binding.bluetoothCountText.text = "${bluetoothSessions.size} session${if (bluetoothSessions.size != 1) "s" else ""}"

        val wiredTotalMs = wiredSessions.sumOf { it.getDurationMs() }
        binding.wiredDurationText.text = viewModel.formatDuration(wiredTotalMs)
        binding.wiredCountText.text = "${wiredSessions.size} session${if (wiredSessions.size != 1) "s" else ""}"
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }
}

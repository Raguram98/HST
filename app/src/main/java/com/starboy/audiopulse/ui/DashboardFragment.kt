package com.starboy.audiopulse.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.starboy.audiopulse.data.ConnectionType
import com.starboy.audiopulse.data.DiagnosticLog
import com.starboy.audiopulse.databinding.FragmentDashboardBinding
import com.starboy.audiopulse.ui.viewmodels.SessionViewModel

/**
 * Fragment that displays statistics, analytics and Bluetooth diagnostics.
 */
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
        refreshDiagnostics()
        binding.refreshDiagnosticsButton.setOnClickListener {
            refreshDiagnostics()
        }
        binding.clearDiagnosticsButton.setOnClickListener {
            DiagnosticLog.clear(requireContext())
            refreshDiagnostics()
        }
        handler.post(updateRunnable)
    }

    private fun refreshDiagnostics() {
        val hasBluetoothPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

        binding.bluetoothPermissionStatusText.text = if (hasBluetoothPermission) {
            "BLUETOOTH_CONNECT: Granted"
        } else {
            "BLUETOOTH_CONNECT: NOT GRANTED"
        }

        val logs = DiagnosticLog.getLogs(requireContext())
        binding.diagnosticLogText.text = if (logs.isBlank()) {
            "No diagnostic events recorded yet. Connect or disconnect your Bluetooth headphones, then refresh."
        } else {
            logs
        }
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

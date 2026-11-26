package com.starboy.audiopulse.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.starboy.audiopulse.R
import com.starboy.audiopulse.data.ConnectionType
import com.starboy.audiopulse.data.HeadphoneSession
import com.starboy.audiopulse.databinding.FragmentDashboardBinding
import com.starboy.audiopulse.ui.viewmodels.SessionViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

/** Fragment that displays listening statistics and device insights. */
class DashboardFragment : Fragment() {

    private val viewModel: SessionViewModel by viewModels()
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private var latestSessions: List<HeadphoneSession> = emptyList()

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (_binding != null) {
                updateDashboard(latestSessions)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allSessions.collect { sessions ->
                latestSessions = sessions
                updateDashboard(sessions)
            }
        }
        handler.post(updateRunnable)
    }

    private fun updateDashboard(sessions: List<HeadphoneSession>) {
        val totalDuration = sessions.sumOf { it.getDurationMs() }
        binding.totalDurationText.text = viewModel.formatDuration(totalDuration)
        binding.sessionCountText.text = sessions.size.toString()

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todaysSessions = sessions.filter { it.connectedTime >= startOfToday }
        val todayDuration = todaysSessions.sumOf { it.getDurationMs() }
        binding.todayDurationText.text = viewModel.formatDuration(todayDuration)

        updateConnectionStats(sessions)
        updateFavoriteDevice(sessions)
    }

    private fun updateConnectionStats(sessions: List<HeadphoneSession>) {
        val bluetoothSessions = sessions.filter { it.connectionType == ConnectionType.BLUETOOTH }
        val wiredSessions = sessions.filter { it.connectionType == ConnectionType.WIRED }

        binding.bluetoothDurationText.text = viewModel.formatDuration(
            bluetoothSessions.sumOf { it.getDurationMs() }
        )
        binding.bluetoothCountText.text = sessionCountLabel(bluetoothSessions.size)

        binding.wiredDurationText.text = viewModel.formatDuration(
            wiredSessions.sumOf { it.getDurationMs() }
        )
        binding.wiredCountText.text = sessionCountLabel(wiredSessions.size)
    }

    private fun updateFavoriteDevice(sessions: List<HeadphoneSession>) {
        val favorite = sessions
            .groupBy { it.deviceName }
            .maxByOrNull { (_, deviceSessions) ->
                deviceSessions.sumOf { it.getDurationMs() }
            }

        if (favorite == null) {
            binding.mostUsedDeviceText.text = "N/A"
            binding.favoriteDeviceType.text = "No sessions recorded yet"
            binding.favoriteDeviceIcon.setImageResource(R.drawable.ic_headphones)
            return
        }

        val (deviceName, deviceSessions) = favorite
        val isBluetooth = deviceSessions.first().connectionType == ConnectionType.BLUETOOTH

        binding.mostUsedDeviceText.text = deviceName
        binding.favoriteDeviceType.text =
            "${if (isBluetooth) "Wireless" else "Wired"} · ${sessionCountLabel(deviceSessions.size)}"
        binding.favoriteDeviceIcon.setImageResource(
            if (isBluetooth) R.drawable.ic_headphones else R.drawable.ic_audio_jack
        )
    }

    private fun sessionCountLabel(count: Int): String =
        "$count session${if (count == 1) "" else "s"}"

    override fun onDestroyView() {
        handler.removeCallbacks(updateRunnable)
        _binding = null
        super.onDestroyView()
    }
}

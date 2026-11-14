package com.starboy.audiopulse.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.starboy.audiopulse.R
import com.starboy.audiopulse.data.HeadphoneSession
import com.starboy.audiopulse.ui.viewmodels.SessionViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

/** Displays recorded headphone sessions grouped by local calendar date. */
class SessionListFragment : Fragment() {

    private val viewModel: SessionViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SessionAdapter
    private lateinit var emptyStateView: View
    private lateinit var summaryText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_session_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.sessions_recycler_view)
        emptyStateView = view.findViewById(R.id.empty_state_view)
        summaryText = view.findViewById(R.id.sessions_summary_text)

        adapter = SessionAdapter(
            onRename = { session, name -> viewModel.renameSession(session, name) },
            onDelete = { session -> viewModel.deleteSession(session) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SessionListFragment.adapter
            edgeEffectFactory = BouncyEdgeEffectFactory()
            setHasFixedSize(false)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allSessions.collect { sessions ->
                adapter.submitSessions(sessions)
                emptyStateView.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
                summaryText.text = buildTodaySummary(sessions)
                summaryText.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun buildTodaySummary(sessions: List<HeadphoneSession>): String {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todaySessions = sessions.filter { it.connectedTime >= startOfToday }
        val totalMs = todaySessions.sumOf { it.getDurationMs() }
        val totalMinutes = totalMs / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val duration = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        return "${sessions.size} sessions · ${duration} today"
    }
}

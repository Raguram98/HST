package com.starboy.audiopulse.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.starboy.audiopulse.R
import com.starboy.audiopulse.data.ConnectionType
import com.starboy.audiopulse.data.HeadphoneSession
import com.starboy.audiopulse.ui.viewmodels.SessionViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/** Displays recorded headphone sessions grouped by local calendar date. */
class SessionListFragment : Fragment() {

    private val viewModel: SessionViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SessionAdapter
    private lateinit var emptyStateView: View
    private lateinit var emptyStateTitle: TextView
    private lateinit var emptyStateMessage: TextView
    private lateinit var summaryText: TextView
    private lateinit var searchInput: EditText
    private lateinit var connectionFilter: Spinner
    private lateinit var dateFilter: Spinner

    private var currentSessions: List<HeadphoneSession> = emptyList()
    private var selectedConnectionType: ConnectionType? = null
    private var selectedDateFilter = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_session_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.sessions_recycler_view)
        emptyStateView = view.findViewById(R.id.empty_state_view)
        emptyStateTitle = view.findViewById(R.id.empty_state_title)
        emptyStateMessage = view.findViewById(R.id.empty_state_message)
        summaryText = view.findViewById(R.id.sessions_summary_text)
        searchInput = view.findViewById(R.id.session_search_input)
        connectionFilter = view.findViewById(R.id.connection_filter_spinner)
        dateFilter = view.findViewById(R.id.date_filter_spinner)

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

        setupFilters()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allSessions.collect { sessions ->
                currentSessions = sessions
                applyFilters()
            }
        }
    }

    private fun setupFilters() {
        val connectionOptions = resources.getStringArray(R.array.session_connection_filters)
        connectionFilter.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            connectionOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val dateOptions = resources.getStringArray(R.array.session_date_filters)
        dateFilter.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            dateOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        connectionFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedConnectionType = when (position) {
                    1 -> ConnectionType.BLUETOOTH
                    2 -> ConnectionType.WIRED
                    else -> null
                }
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dateFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDateFilter = position
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun applyFilters() {
        if (!isAdded) return

        val query = searchInput.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        val startTime = getDateFilterStartTime()

        val filteredSessions = currentSessions.filter { session ->
            val matchesName = query.isEmpty() ||
                session.deviceName.lowercase(Locale.getDefault()).contains(query)
            val matchesConnection = selectedConnectionType == null ||
                session.connectionType == selectedConnectionType
            val matchesDate = startTime == null || session.connectedTime >= startTime

            matchesName && matchesConnection && matchesDate
        }

        adapter.submitSessions(filteredSessions)

        val hasAnySessions = currentSessions.isNotEmpty()
        val hasMatches = filteredSessions.isNotEmpty()
        emptyStateView.visibility = if (hasMatches) View.GONE else View.VISIBLE
        recyclerView.visibility = if (hasMatches) View.VISIBLE else View.GONE

        if (!hasAnySessions) {
            emptyStateTitle.text = "Nothing playing yet"
            emptyStateMessage.text =
                "Connect your headphones and your listening sessions will show up here."
            summaryText.visibility = View.GONE
        } else if (!hasMatches) {
            emptyStateTitle.text = "No matching sessions"
            emptyStateMessage.text = "Try another device name or change the connection and date filters."
            summaryText.visibility = View.VISIBLE
        } else {
            summaryText.visibility = View.VISIBLE
        }

        if (hasAnySessions) {
            summaryText.text = buildTodaySummary(currentSessions, filteredSessions.size)
        }
    }

    private fun getDateFilterStartTime(): Long? {
        if (selectedDateFilter == 0) return null

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (selectedDateFilter) {
            1 -> Unit
            2 -> calendar.add(Calendar.DAY_OF_YEAR, -6)
            3 -> calendar.add(Calendar.DAY_OF_YEAR, -29)
        }
        return calendar.timeInMillis
    }

    private fun buildTodaySummary(
        sessions: List<HeadphoneSession>,
        visibleCount: Int
    ): String {
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
        val countLabel = if (todaySessions.size == 1) "session" else "sessions"
        return "Showing $visibleCount of ${sessions.size} · ${todaySessions.size} $countLabel today · $duration"
    }
}

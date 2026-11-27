package com.starboy.audiopulse.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
    private lateinit var emptyStateIcon: ImageView
    private lateinit var emptyStateTitle: TextView
    private lateinit var emptyStateMessage: TextView
    private lateinit var resultCountText: TextView
    private lateinit var emptyClearButton: View
    private lateinit var searchInput: EditText
    private lateinit var subtitleText: TextView
    private lateinit var connectionChipGroup: ChipGroup
    private lateinit var timeChipGroup: ChipGroup

    private var currentSessions: List<HeadphoneSession> = emptyList()
    private var selectedConnectionType: ConnectionType? = null
    // 0 = all time, 1 = today, 2 = last 24 hours, 3 = last 7 days, 4 = last 30 days
    private var selectedTimeFilter = 0
    private var isUpdatingControls = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_session_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.sessions_recycler_view)
        emptyStateView = view.findViewById(R.id.empty_state_view)
        emptyStateIcon = view.findViewById(R.id.empty_state_icon)
        emptyStateTitle = view.findViewById(R.id.empty_state_title)
        emptyStateMessage = view.findViewById(R.id.empty_state_message)
        resultCountText = view.findViewById(R.id.sessions_result_count)
        emptyClearButton = view.findViewById(R.id.empty_clear_filters)
        searchInput = view.findViewById(R.id.session_search_input)
        subtitleText = view.findViewById(R.id.library_subtitle)
        connectionChipGroup = view.findViewById(R.id.connection_chip_group)
        timeChipGroup = view.findViewById(R.id.time_chip_group)

        adapter = SessionAdapter(
            onRename = { session, name -> viewModel.renameSession(session, name) },
            onDelete = { session -> viewModel.deleteSession(session) },
            isRecyclerIdle = { recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SessionListFragment.adapter
            edgeEffectFactory = BouncyEdgeEffectFactory()
            setHasFixedSize(false)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private val collapseThreshold = dp(48)
                private val expandThreshold = dp(16)
                private var subtitleCollapsed = false

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    // Clamp spring overscroll offsets and use hysteresis to prevent
                    // small bounce movements from repeatedly toggling the subtitle.
                    val scrollOffset = recyclerView.computeVerticalScrollOffset().coerceAtLeast(0)
                    val shouldCollapse = when {
                        !subtitleCollapsed && scrollOffset >= collapseThreshold -> true
                        subtitleCollapsed && scrollOffset <= expandThreshold -> false
                        else -> subtitleCollapsed
                    }

                    if (shouldCollapse != subtitleCollapsed) {
                        subtitleCollapsed = shouldCollapse
                        // Alpha keeps the measured header height unchanged.
                        subtitleText.alpha = if (subtitleCollapsed) 0f else 1f
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                        this@SessionListFragment.adapter.dismissActivePopup()
                    }
                }
            })
        }

        setupFilterChips()
        emptyClearButton.setOnClickListener { clearFilters() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allSessions.collect { sessions ->
                currentSessions = sessions
                applyFilters()
            }
        }
    }

    private fun setupFilterChips() {
        addFilterChips(connectionChipGroup, listOf("All", "Bluetooth", "Wired")) { index ->
            selectedConnectionType = when (index) {
                1 -> ConnectionType.BLUETOOTH
                2 -> ConnectionType.WIRED
                else -> null
            }
            applyFilters()
        }

        addFilterChips(
            timeChipGroup,
            listOf("All time", "Today", "Last 24h", "Last 7 days", "Last 30 days")
        ) { index ->
            selectedTimeFilter = index
            applyFilters()
        }
    }

    private fun addFilterChips(
        group: ChipGroup,
        labels: List<String>,
        onSelected: (Int) -> Unit
    ) {
        group.removeAllViews()
        labels.forEach { label ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isClickable = true
                chipMinHeight = dp(40).toFloat()
                textSize = 13f
                setEnsureMinTouchTargetSize(true)
            }
            group.addView(chip)
        }

        group.setOnCheckedStateChangeListener { chipGroup, checkedIds ->
            val selectedId = checkedIds.firstOrNull()
            var selectedIndex = 0
            for (index in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(index) as Chip
                styleChip(chip, chip.id == selectedId)
                if (chip.id == selectedId) selectedIndex = index
            }
            if (!isUpdatingControls) onSelected(selectedIndex)
        }
        group.check(group.getChildAt(0).id)
    }

    private fun styleChip(chip: Chip, selected: Boolean) {
        val context = requireContext()
        chip.chipBackgroundColor = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (selected) R.color.md_theme_primary else R.color.md_theme_surface
            )
        )
        chip.setTextColor(
            ContextCompat.getColor(
                context,
                if (selected) R.color.md_theme_on_primary else R.color.md_theme_on_surface_variant
            )
        )
        chip.chipStrokeWidth = dp(if (selected) 0 else 1).toFloat()
        chip.chipStrokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.md_theme_outline)
        )
    }

    private fun clearFilters() {
        isUpdatingControls = true
        selectedConnectionType = null
        selectedTimeFilter = 0
        searchInput.setText("")
        connectionChipGroup.check(connectionChipGroup.getChildAt(0).id)
        timeChipGroup.check(timeChipGroup.getChildAt(0).id)
        isUpdatingControls = false
        applyFilters()
    }

    private fun applyFilters() {
        if (!isAdded || !::searchInput.isInitialized) return

        val query = searchInput.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val startTime = getTimeFilterStartTime()
        val filteredSessions = currentSessions.filter { session ->
            val matchesName = query.isEmpty() ||
                session.deviceName.lowercase(Locale.ROOT).contains(query)
            val matchesConnection = selectedConnectionType == null ||
                session.connectionType == selectedConnectionType
            val matchesTime = startTime == null || session.connectedTime >= startTime
            matchesName && matchesConnection && matchesTime
        }

        adapter.submitSessions(filteredSessions)

        val filtersActive = query.isNotEmpty() ||
            selectedConnectionType != null || selectedTimeFilter != 0
        resultCountText.text = "${filteredSessions.size} ${if (filteredSessions.size == 1) "session" else "sessions"}"

        val showEmpty = filteredSessions.isEmpty()
        emptyStateView.visibility = if (showEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (showEmpty) View.GONE else View.VISIBLE
        emptyClearButton.visibility = if (showEmpty && filtersActive) View.VISIBLE else View.INVISIBLE

        if (currentSessions.isEmpty() && !filtersActive) {
            emptyStateIcon.visibility = View.VISIBLE
            emptyStateIcon.setImageResource(R.drawable.ic_headphones)
            emptyStateIcon.contentDescription = "Headphones"
            emptyStateTitle.visibility = View.VISIBLE
            emptyStateTitle.text = "Nothing playing yet"
            emptyStateMessage.visibility = View.VISIBLE
            emptyStateMessage.text =
                "Connect your headphones and your listening sessions will show up here."
        } else if (showEmpty) {
            emptyStateIcon.visibility = View.VISIBLE
            emptyStateIcon.setImageResource(R.drawable.ic_not_found)
            emptyStateIcon.contentDescription = "Not Found"
            emptyStateTitle.visibility = View.VISIBLE
            emptyStateTitle.text = getString(R.string.empty_state_filtered_title)
            emptyStateMessage.visibility = View.GONE
        }
    }

    private fun getTimeFilterStartTime(): Long? {
        val now = System.currentTimeMillis()
        return when (selectedTimeFilter) {
            1 -> Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            2 -> now - 24L * 60 * 60 * 1000
            3 -> now - 7L * 24 * 60 * 60 * 1000
            4 -> now - 30L * 24 * 60 * 60 * 1000
            else -> null
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

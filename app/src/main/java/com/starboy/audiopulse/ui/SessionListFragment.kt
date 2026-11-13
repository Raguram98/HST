package com.starboy.audiopulse.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.starboy.audiopulse.R
import com.starboy.audiopulse.ui.viewmodels.SessionViewModel
import kotlinx.coroutines.launch

/**
 * Fragment that displays a list of all recorded headphone sessions
 */
class SessionListFragment : Fragment() {

    private val viewModel: SessionViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SessionAdapter
    private lateinit var emptyStateView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_session_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.sessions_recycler_view)
        emptyStateView = view.findViewById(R.id.empty_state_view)

        // Setup adapter without delete callback
        adapter = SessionAdapter()

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SessionListFragment.adapter
            edgeEffectFactory = BouncyEdgeEffectFactory()
        }

        // Observe sessions
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allSessions.collect { sessions ->
                adapter.submitList(sessions)

                // Show/hide empty state
                if (sessions.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyStateView.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyStateView.visibility = View.GONE
                }
            }
        }
    }
}
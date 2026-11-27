package com.starboy.audiopulse.ui

import android.os.Handler
import android.os.Looper
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.PopupWindow
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.starboy.audiopulse.R
import com.starboy.audiopulse.data.ConnectionType
import com.starboy.audiopulse.data.HeadphoneSession
import com.starboy.audiopulse.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for grouped session history.
 * Date headers and cards are separate rows so long histories remain efficiently recycled.
 */
class SessionAdapter(
    private val onRename: (HeadphoneSession, String) -> Unit,
    private val onDelete: (HeadphoneSession) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class DateHeader(val title: String) : Row()
        data class Session(val value: HeadphoneSession) : Row()
    }

    private val rows = mutableListOf<Row>()
    private val expandedSessionIds = mutableSetOf<Long>()

    companion object {
        private const val TYPE_DATE = 0
        private const val TYPE_SESSION = 1
    }

    fun submitSessions(sessions: List<HeadphoneSession>) {
        rows.clear()
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        var lastDateKey: String? = null

        sessions.sortedByDescending { it.connectedTime }.forEach { session ->
            val date = Calendar.getInstance().apply { timeInMillis = session.connectedTime }
            val dateKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(session.connectedTime))
            if (dateKey != lastDateKey) {
                val title = when {
                    isSameDay(date, today) -> "Today"
                    isSameDay(date, yesterday) -> "Yesterday"
                    else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(session.connectedTime))
                }
                rows.add(Row.DateHeader(title))
                lastDateKey = dateKey
            }
            rows.add(Row.Session(session))
            if (session.isActive) expandedSessionIds.add(session.id)
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.DateHeader) TYPE_DATE else TYPE_SESSION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_DATE) {
            val text = TextView(parent.context).apply {
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary))
                textSize = 12f
                letterSpacing = 0.06f
                setPadding(dp(4), dp(18), dp(4), dp(5))
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            DateHeaderHolder(text)
        } else {
            val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SessionViewHolder(binding)
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.DateHeader -> (holder as DateHeaderHolder).bind(row.title)
            is Row.Session -> (holder as SessionViewHolder).bind(
                row.value,
                expandedSessionIds.contains(row.value.id),
                onToggle = {
                    val nowExpanded = if (expandedSessionIds.add(row.value.id)) {
                        true
                    } else {
                        expandedSessionIds.remove(row.value.id)
                        false
                    }
                    (holder as SessionViewHolder).setExpanded(nowExpanded)
                },
                onActions = { anchor -> showActions(anchor, row.value) }
            )
        }
    }

    private fun showActions(anchor: View, session: HeadphoneSession) {
        val popupView = LayoutInflater.from(anchor.context)
            .inflate(R.layout.popup_session_actions, null, false)

        val popupWidth = (208 * anchor.resources.displayMetrics.density).toInt()
        val popupWindow = PopupWindow(
            popupView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            elevation = 8 * anchor.resources.displayMetrics.density
        }

        popupView.findViewById<View>(R.id.action_rename).setOnClickListener {
            popupWindow.dismiss()
            showRenameDialog(anchor, session)
        }
        popupView.findViewById<View>(R.id.action_delete).setOnClickListener {
            popupWindow.dismiss()
            MaterialAlertDialogBuilder(anchor.context)
                .setTitle("Delete session?")
                .setMessage("This removes this session record from AudioPulse. It cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> onDelete(session) }
                .show()
        }

        // Measure the fully inflated menu so font scaling and future menu rows
        // are included when deciding whether it fits below the actions button.
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = popupView.measuredHeight

        // Find this session's card and compare its bottom edge with the space
        // available below the button. If the menu would cross the card bottom,
        // place its bottom edge level with the top of the actions button.
        var card: View? = anchor
        while (card != null && card.id != R.id.session_card) {
            card = card.parent as? View
        }

        val cardBottom = card?.let {
            val location = IntArray(2)
            it.getLocationOnScreen(location)
            location[1] + it.height
        }
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val anchorBottom = anchorLocation[1] + anchor.height
        val availableBelow = cardBottom?.minus(anchorBottom)
        val openUpward = availableBelow != null && popupHeight > availableBelow

        val verticalOffset = if (openUpward) -popupHeight - anchor.height else 0
        popupWindow.showAsDropDown(anchor, 0, verticalOffset, Gravity.END)
    }

    private fun showRenameDialog(anchor: View, session: HeadphoneSession) {
        val input = TextInputEditText(anchor.context).apply {
            setText(session.deviceName)
            setSingleLine(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        MaterialAlertDialogBuilder(anchor.context)
            .setTitle("Rename device")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotBlank()) onRename(session, name)
            }
            .show()
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean =
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

    private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class DateHeaderHolder(private val text: TextView) : RecyclerView.ViewHolder(text) {
        fun bind(title: String) { text.text = title }
    }

    inner class SessionViewHolder(
        private val binding: ItemSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val timerHandler = Handler(Looper.getMainLooper())
        private var updateRunnable: Runnable? = null
        private var currentSession: HeadphoneSession? = null

        fun bind(
            session: HeadphoneSession,
            expanded: Boolean,
            onToggle: () -> Unit,
            onActions: (View) -> Unit
        ) {
            stopUpdates()
            currentSession = session
            val isBluetooth = session.connectionType == ConnectionType.BLUETOOTH

            binding.deviceNameText.text = session.deviceName
            binding.deviceIcon.setImageResource(
                if (isBluetooth) R.drawable.ic_bluetooth else R.drawable.ic_audio_jack
            )
            binding.liveBadge.visibility = if (session.isActive) View.VISIBLE else View.GONE
            binding.liveBadge.text = if (session.isActive) "● LIVE" else ""
            binding.startTimeText.text = formatTime(session.connectedTime)
            binding.endTimeText.text = session.disconnectedTime?.let(::formatTime) ?: ""
            binding.endTimeRow.visibility = if (session.isActive) View.GONE else View.VISIBLE
            binding.connectionTypeText.text = if (isBluetooth) "Bluetooth" else "Wired"
            binding.batteryRow.visibility = if (session.isActive && isBluetooth) View.VISIBLE else View.GONE
            binding.batteryText.text = "Unavailable"

            binding.root.setOnClickListener { onToggle() }
            binding.expandIcon.setOnClickListener { onToggle() }
            binding.actionsButton.setOnClickListener { onActions(it) }
            setExpanded(expanded, false)
            updateDuration(session)
            startDurationUpdates(session)
        }

        fun setExpanded(expanded: Boolean, animate: Boolean = true) {
            if (animate) {
                // Animate the RecyclerView as a whole so neighbouring cards
                // move smoothly while this card expands or collapses.
                val transitionRoot = binding.root.parent as? ViewGroup ?: binding.root
                TransitionManager.beginDelayedTransition(
                    transitionRoot,
                    AutoTransition().apply { duration = 220 }
                )
            }
            binding.detailsContainer.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.actionsButton.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.expandIcon.text = ""
            binding.expandIcon.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0,
                0,
                if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down,
                0
            )
            binding.expandIcon.contentDescription =
                if (expanded) "Collapse session details" else "Expand session details"
        }

        private fun updateDuration(session: HeadphoneSession) {
            val duration = session.getDurationString()
            val connectionLabel =
                if (session.connectionType == ConnectionType.BLUETOOTH) "Bluetooth" else "Wired"
            binding.durationText.text = "$duration · $connectionLabel"
        }

        private fun startDurationUpdates(session: HeadphoneSession) {
            stopUpdates()
            updateRunnable = object : Runnable {
                override fun run() {
                    updateDuration(session)
                    if (session.isActive) timerHandler.postDelayed(this, 1000)
                }
            }
            updateRunnable?.let(timerHandler::post)
        }

        fun stopUpdates() {
            updateRunnable?.let(timerHandler::removeCallbacks)
            updateRunnable = null
        }

        fun resumeUpdates() {
            currentSession?.takeIf { it.isActive }?.let(::startDurationUpdates)
        }

        private fun formatTime(timestamp: Long): String =
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is SessionViewHolder) holder.stopUpdates()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is SessionViewHolder) holder.resumeUpdates()
    }
}

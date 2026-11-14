package com.starboy.audiopulse.ui

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.PopupMenu
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
    private val handler = Handler(Looper.getMainLooper())

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
                    if (!expandedSessionIds.add(row.value.id)) expandedSessionIds.remove(row.value.id)
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) notifyItemChanged(currentPosition)
                },
                onActions = { anchor -> showActions(anchor, row.value) }
            )
        }
    }

    private fun showActions(anchor: View, session: HeadphoneSession) {
        PopupMenu(anchor.context, anchor).apply {
            menu.add("Rename device")
            menu.add("Delete session")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Rename device" -> showRenameDialog(anchor, session)
                    "Delete session" -> MaterialAlertDialogBuilder(anchor.context)
                        .setTitle("Delete session?")
                        .setMessage("This removes this session record from AudioPulse. It cannot be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ -> onDelete(session) }
                        .show()
                }
                true
            }
            show()
        }
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
            val context = itemView.context
            val isBluetooth = session.connectionType == ConnectionType.BLUETOOTH

            binding.deviceNameText.text = session.deviceName
            binding.deviceIcon.text = if (isBluetooth) "🎧" else "🎙️"
            binding.liveBadge.visibility = if (session.isActive) View.VISIBLE else View.GONE
            binding.liveBadge.text = if (session.isActive) "● LIVE" else ""
            binding.startTimeText.text = formatTime(session.connectedTime)
            binding.endTimeText.text = session.disconnectedTime?.let(::formatTime) ?: "Still connected"
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
            if (animate) TransitionManager.beginDelayedTransition(
                binding.root,
                AutoTransition().apply { duration = 220 }
            )
            binding.detailsContainer.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.expandIcon.text = if (expanded) "Collapse details  ⌃" else "Expand details  ⌄"
            binding.expandIcon.contentDescription = if (expanded) "Collapse session details" else "Expand session details"
        }

        private fun updateDuration(session: HeadphoneSession) {
            val duration = session.getDurationString()
            binding.durationText.text = duration
        }

        private fun startDurationUpdates(session: HeadphoneSession) {
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

        private fun formatDate(timestamp: Long): String =
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
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

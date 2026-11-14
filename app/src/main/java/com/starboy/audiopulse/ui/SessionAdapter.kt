package com.starboy.audiopulse.ui

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.starboy.audiopulse.R
import com.starboy.audiopulse.data.ConnectionType
import com.starboy.audiopulse.data.HeadphoneSession
import com.starboy.audiopulse.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Displays recorded headphone sessions with expandable details and live duration updates. */
class SessionAdapter : ListAdapter<HeadphoneSession, SessionAdapter.SessionViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SessionViewHolder(
        private val binding: ItemSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val handler = Handler(Looper.getMainLooper())
        private var updateRunnable: Runnable? = null
        private var boundSession: HeadphoneSession? = null
        private var isExpanded = false

        fun bind(session: HeadphoneSession) {
            boundSession = session
            val context = itemView.context
            val isBluetooth = session.connectionType == ConnectionType.BLUETOOTH

            binding.deviceNameText.text = session.deviceName
            binding.deviceIcon.text = if (isBluetooth) "🎧" else "🎙️"
            binding.deviceIcon.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isBluetooth) R.color.icon_circle_background else R.color.badge_wired_background
                )
            )

            binding.connectionTypeBadge.apply {
                text = if (isBluetooth) "BLUETOOTH" else "WIRED"
                chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        if (isBluetooth) R.color.badge_wireless_background else R.color.badge_wired_background
                    )
                )
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (isBluetooth) R.color.badge_wireless_text else R.color.badge_wired_text
                    )
                )
                isClickable = false
                isCheckable = false
            }

            binding.liveBadge.visibility = if (session.isActive) android.view.View.VISIBLE else android.view.View.GONE
            binding.timestampText.text = formatDate(session.connectedTime)
            binding.startTimeText.text = formatTime(session.connectedTime)
            binding.endTimeText.text = session.disconnectedTime?.let(::formatTime) ?: "Still connected"
            binding.sessionDateText.text = formatDate(session.connectedTime)
            binding.connectionNameText.text = session.deviceName
            binding.connectionTypeText.text = if (isBluetooth) "Bluetooth" else "Wired"
            binding.sessionStatusText.text = if (session.isActive) "Active" else "Completed"

            // Keep each row's expanded state when RecyclerView rebinds it.
            binding.detailsContainer.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
            binding.expandIcon.text = if (isExpanded) "⌃" else "⌄"
            updateDuration(session)

            binding.root.setOnClickListener {
                toggleExpanded()
            }

            startDurationUpdates(session)
        }

        private fun toggleExpanded() {
            isExpanded = !isExpanded
            TransitionManager.beginDelayedTransition(binding.root, AutoTransition().apply {
                duration = 220
            })
            binding.detailsContainer.visibility =
                if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
            binding.expandIcon.text = if (isExpanded) "⌃" else "⌄"
        }

        private fun updateDuration(session: HeadphoneSession) {
            val duration = session.getDurationString()
            binding.durationText.text = duration
            binding.detailDurationText.text = duration
        }

        private fun startDurationUpdates(session: HeadphoneSession) {
            stopUpdates()
            updateRunnable = object : Runnable {
                override fun run() {
                    updateDuration(session)
                    if (session.isActive) {
                        handler.postDelayed(this, 1000)
                    }
                }
            }
            updateRunnable?.let(handler::post)
        }

        fun resumeUpdates() {
            boundSession?.let(::startDurationUpdates)
        }

        fun stopUpdates() {
            updateRunnable?.let(handler::removeCallbacks)
            updateRunnable = null
        }

        private fun formatTime(timestamp: Long): String =
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))

        private fun formatDate(timestamp: Long): String =
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }

    override fun onViewAttachedToWindow(holder: SessionViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.resumeUpdates()
    }

    override fun onViewDetachedFromWindow(holder: SessionViewHolder) {
        holder.stopUpdates()
        super.onViewDetachedFromWindow(holder)
    }
}

class SessionDiffCallback : DiffUtil.ItemCallback<HeadphoneSession>() {
    override fun areItemsTheSame(oldItem: HeadphoneSession, newItem: HeadphoneSession): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: HeadphoneSession, newItem: HeadphoneSession): Boolean =
        oldItem == newItem
}

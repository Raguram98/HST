package com.starboy.audiopulse.ui

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
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

/** Displays recorded headphone sessions and refreshes the duration of active sessions. */
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
                text = if (isBluetooth) "WIRELESS" else "WIRED"
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

            binding.timestampText.text = session.getFormattedConnectTime()
            binding.statusText.apply {
                text = if (session.isActive) "ACTIVE" else "ENDED"
                chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        if (session.isActive) R.color.badge_active_background else R.color.badge_complete_background
                    )
                )
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (session.isActive) R.color.badge_active_text else R.color.badge_complete_text
                    )
                )
                isClickable = false
                isCheckable = false
            }

            startDurationUpdates(session)
        }

        fun resumeUpdates() {
            boundSession?.let(::startDurationUpdates)
        }

        private fun startDurationUpdates(session: HeadphoneSession) {
            stopUpdates()
            updateRunnable = object : Runnable {
                override fun run() {
                    binding.durationText.text = session.getDurationString()
                    if (session.isActive) {
                        handler.postDelayed(this, 1000)
                    }
                }
            }
            updateRunnable?.let(handler::post)
        }

        fun stopUpdates() {
            updateRunnable?.let { handler.removeCallbacks(it) }
            updateRunnable = null
        }
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

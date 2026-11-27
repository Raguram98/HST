package com.starboy.audiopulse.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.PopupWindow
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
        data class DateHeader(val title: String, val stableId: Long) : Row()
        data class Session(val value: HeadphoneSession) : Row()
    }

    private val rows = mutableListOf<Row>()
    private val expandedSessionIds = mutableSetOf<Long>()

    companion object {
        private const val TYPE_DATE = 0
        private const val TYPE_SESSION = 1
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = when (val row = rows[position]) {
        is Row.DateHeader -> row.stableId
        is Row.Session -> row.value.id
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
                rows.add(Row.DateHeader(title, -dateKey.toLong()))
                lastDateKey = dateKey
            }
            rows.add(Row.Session(session))
            if (isLiveSession(session)) expandedSessionIds.add(session.id)
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

    fun dismissActivePopup() {
        activePopupWindow?.dismiss()
    }

    private var activePopupWindow: PopupWindow? = null

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
        dismissActivePopup()
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
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 8 * anchor.resources.displayMetrics.density
        }

        popupWindow.setOnDismissListener {
            if (activePopupWindow === popupWindow) activePopupWindow = null
        }
        activePopupWindow = popupWindow

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

    private fun isLiveSession(session: HeadphoneSession): Boolean =
        session.isActive && session.disconnectedTime == null

    private class DateHeaderHolder(private val text: TextView) : RecyclerView.ViewHolder(text) {
        fun bind(title: String) { text.text = title }
    }

    inner class SessionViewHolder(
        private val binding: ItemSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val timerHandler = Handler(Looper.getMainLooper())
        private var updateRunnable: Runnable? = null
        private var currentSession: HeadphoneSession? = null
        private var pulseAnimators: List<ObjectAnimator> = emptyList()

        fun bind(
            session: HeadphoneSession,
            expanded: Boolean,
            onToggle: () -> Unit,
            onActions: (View) -> Unit
        ) {
            stopUpdates()
            stopLiveAnimations()
            currentSession = session
            val isBluetooth = session.connectionType == ConnectionType.BLUETOOTH

            binding.deviceNameText.text = session.deviceName
            binding.deviceIcon.setImageResource(
                if (isBluetooth) R.drawable.ic_bluetooth else R.drawable.ic_audio_jack
            )
            binding.startTimeText.text = formatTime(session.connectedTime)
            binding.endTimeText.text = session.disconnectedTime?.let(::formatTime) ?: ""
            binding.endTimeRow.visibility = if (isLiveSession(session)) View.GONE else View.VISIBLE
            binding.connectionTypeText.text = connectionLabel(session)

            binding.root.setOnClickListener { onToggle() }
            binding.expandIcon.setOnClickListener { onToggle() }
            binding.actionsButton.setOnClickListener { onActions(it) }
            setExpanded(expanded)
            updateStatus(session)
            if (isLiveSession(session)) {
                startDurationUpdates(session)
                startLiveAnimationsIfAllowed()
            }
        }

        fun setExpanded(expanded: Boolean) {
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

        private fun updateStatus(session: HeadphoneSession) {
            val live = isLiveSession(session)
            val connection = connectionLabel(session)
            val durationSeconds = durationSeconds(session)

            binding.statusConnectionText.text = connection
            updateDurationText(session)
            binding.deviceAvatarRing.visibility = if (live) View.VISIBLE else View.GONE
            binding.root.contentDescription =
                "${if (live) "Live" else "Ended"}, $connection, ${if (live) "connected" else "lasted"} ${spokenDuration(durationSeconds)}"
        }

        private fun updateDurationText(session: HeadphoneSession) {
            binding.statusDurationText.text = formatDuration(durationSeconds(session))
        }

        private fun startDurationUpdates(session: HeadphoneSession) {
            stopUpdates()
            if (!isLiveSession(session) || !binding.root.isAttachedToWindow) return

            updateRunnable = object : Runnable {
                override fun run() {
                    if (!isLiveSession(session)) {
                        stopUpdates()
                        return
                    }
                    updateDurationText(session)
                    startLiveAnimationsIfAllowed()
                    timerHandler.postDelayed(this, 1000)
                }
            }
            updateRunnable?.let(timerHandler::post)
        }

        private fun startLiveAnimationsIfAllowed() {
            if (!isLiveSession(currentSession ?: return) || !binding.root.isAttachedToWindow) return

            val context = binding.root.context
            val animatorScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            val animationsEnabled = animatorScale > 0f && ValueAnimator.areAnimatorsEnabled()
            if (!animationsEnabled) {
                stopLiveAnimations()
                return
            }
            if (pulseAnimators.isNotEmpty()) return

            stopLiveAnimations()
            binding.deviceAvatarRing.visibility = View.VISIBLE
            val ring = binding.deviceAvatarRing
            pulseAnimators = listOf(
                ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.045f, 1f),
                ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, 1.045f, 1f),
                ObjectAnimator.ofFloat(ring, View.ALPHA, 1f, 0.48f, 1f)
            ).onEach { animator ->
                animator.duration = 1500
                animator.repeatCount = ValueAnimator.INFINITE
                animator.interpolator = AccelerateDecelerateInterpolator()
            }
            pulseAnimators.forEach { it.start() }
        }

        fun stopLiveAnimations() {
            pulseAnimators.forEach { it.cancel() }
            pulseAnimators = emptyList()
            binding.deviceAvatarRing.alpha = 1f
            binding.deviceAvatarRing.scaleX = 1f
            binding.deviceAvatarRing.scaleY = 1f
        }

        fun stopUpdates() {
            updateRunnable?.let(timerHandler::removeCallbacks)
            updateRunnable = null
        }

        fun resumeUpdates() {
            val session = currentSession ?: return
            if (isLiveSession(session)) {
                startDurationUpdates(session)
                startLiveAnimationsIfAllowed()
            } else {
                stopUpdates()
                stopLiveAnimations()
            }
        }

        private fun connectionLabel(session: HeadphoneSession): String =
            if (session.connectionType == ConnectionType.BLUETOOTH) "Bluetooth" else "Wired"

        private fun durationSeconds(session: HeadphoneSession): Long {
            val endTime = if (isLiveSession(session)) System.currentTimeMillis()
            else session.disconnectedTime ?: session.connectedTime
            return ((endTime - session.connectedTime).coerceAtLeast(0L)) / 1000L
        }

        private fun formatDuration(totalSeconds: Long): String {
            val seconds = totalSeconds % 60
            val totalMinutes = totalSeconds / 60
            val minutes = totalMinutes % 60
            val hours = totalMinutes / 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%d:%02d", totalMinutes, seconds)
            }
        }

        private fun spokenDuration(totalSeconds: Long): String {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return buildList {
                if (hours > 0) add("$hours ${if (hours == 1L) "hour" else "hours"}")
                if (minutes > 0) add("$minutes ${if (minutes == 1L) "minute" else "minutes"}")
                if (seconds > 0 || isEmpty()) add("$seconds ${if (seconds == 1L) "second" else "seconds"}")
            }.joinToString(" ")
        }

        private fun formatTime(timestamp: Long): String =
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is SessionViewHolder) {
            holder.stopUpdates()
            holder.stopLiveAnimations()
        }
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is SessionViewHolder) holder.resumeUpdates()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is SessionViewHolder) {
            holder.stopUpdates()
            holder.stopLiveAnimations()
        }
        super.onViewRecycled(holder)
    }
}

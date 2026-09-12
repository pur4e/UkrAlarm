package com.ukralarm.app.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.ukralarm.app.R
import com.ukralarm.app.model.Alarm

class AlarmAdapter(
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onClick: (Alarm) -> Unit,
    private val onLongClick: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    var isSelectionMode: Boolean = false
        private set

    val selectedIds = mutableSetOf<Long>()
    var onSelectionChanged: ((count: Int) -> Unit)? = null

    fun enterSelectionMode(initialAlarm: Alarm) {
        isSelectionMode = true
        selectedIds.clear()
        selectedIds.add(initialAlarm.id)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun toggleSelection(alarmId: Long) {
        if (selectedIds.contains(alarmId)) {
            selectedIds.remove(alarmId)
        } else {
            selectedIds.add(alarmId)
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun selectAll() {
        selectedIds.clear()
        for (i in 0 until itemCount) {
            selectedIds.add(getItem(i).id)
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun deselectAll() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardAlarm: MaterialCardView = itemView.findViewById(R.id.cardAlarm)
        private val checkSelect: CheckBox = itemView.findViewById(R.id.checkSelect)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val textLabel: TextView = itemView.findViewById(R.id.textLabel)
        private val textDays: TextView = itemView.findViewById(R.id.textDays)
        private val switchEnabled: SwitchMaterial = itemView.findViewById(R.id.switchEnabled)

        fun bind(alarm: Alarm) {
            val context = itemView.context
            textTime.text = alarm.getTimeString()
            textTime.alpha = if (alarm.isEnabled) 1.0f else 0.38f

            if (alarm.label.isNotEmpty()) {
                textLabel.text = alarm.label
                textLabel.visibility = View.VISIBLE
            } else {
                textLabel.visibility = View.GONE
            }

            textDays.text = alarm.getDaysString(context)

            if (isSelectionMode) {
                checkSelect.visibility = View.VISIBLE
                val isSelected = selectedIds.contains(alarm.id)
                checkSelect.isChecked = isSelected

                // Subtle border glow when selected
                if (isSelected) {
                    cardAlarm.strokeWidth = dpToPx(2)
                    cardAlarm.strokeColor = ContextCompat.getColor(context, R.color.ios_purple_dark)
                } else {
                    cardAlarm.strokeWidth = dpToPx(1)
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
                    cardAlarm.strokeColor = typedValue.data
                }

                switchEnabled.visibility = View.GONE
            } else {
                checkSelect.visibility = View.GONE
                switchEnabled.visibility = View.VISIBLE

                cardAlarm.strokeWidth = dpToPx(1)
                val typedValue = TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
                cardAlarm.strokeColor = typedValue.data

                switchEnabled.setOnCheckedChangeListener(null)
                switchEnabled.isChecked = alarm.isEnabled
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onToggle(alarm, isChecked)
                }
            }

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(alarm.id)
                } else {
                    onClick(alarm)
                }
            }

            itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode(alarm)
                    onLongClick(alarm)
                } else {
                    toggleSelection(alarm.id)
                }
                true
            }
        }

        private fun dpToPx(dp: Int): Int {
            return (dp * itemView.context.resources.displayMetrics.density).toInt()
        }
    }

    class AlarmDiffCallback : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem == newItem
    }
}

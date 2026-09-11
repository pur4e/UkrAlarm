package com.ukralarm.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.ukralarm.app.R

class WheelTimePicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val recyclerHours: RecyclerView
    private val recyclerMinutes: RecyclerView
    private val textColon: TextView

    private val hoursSnapHelper = LinearSnapHelper()
    private val minutesSnapHelper = LinearSnapHelper()

    private val hoursLayoutManager: LinearLayoutManager
    private val minutesLayoutManager: LinearLayoutManager

    private var currentHour: Int = 0
    private var currentMinute: Int = 0

    var hour: Int
        get() = currentHour
        set(value) {
            setHourInternal(value.coerceIn(0, 23))
        }

    var minute: Int
        get() = currentMinute
        set(value) {
            setMinuteInternal(value.coerceIn(0, 59))
        }

    companion object {
        private const val VIRTUAL_MULTIPLIER = 1000
        private const val HOURS_COUNT = 24
        private const val MINUTES_COUNT = 60
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_wheel_time_picker, this, true)

        recyclerHours = findViewById(R.id.recyclerHours)
        recyclerMinutes = findViewById(R.id.recyclerMinutes)
        textColon = findViewById(R.id.textColon)

        hoursLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        minutesLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

        recyclerHours.layoutManager = hoursLayoutManager
        recyclerMinutes.layoutManager = minutesLayoutManager

        hoursSnapHelper.attachToRecyclerView(recyclerHours)
        minutesSnapHelper.attachToRecyclerView(recyclerMinutes)

        setupRecyclerView(recyclerHours, HOURS_COUNT) { pos ->
            val target = pos % HOURS_COUNT
            scrollToValue(recyclerHours, hoursLayoutManager, hoursSnapHelper, target, HOURS_COUNT)
        }

        setupRecyclerView(recyclerMinutes, MINUTES_COUNT) { pos ->
            val target = pos % MINUTES_COUNT
            scrollToValue(recyclerMinutes, minutesLayoutManager, minutesSnapHelper, target, MINUTES_COUNT)
        }

        // Set default to current hour & minute
        val now = java.util.Calendar.getInstance()
        setTime(now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE))
    }

    fun setTime(h: Int, m: Int) {
        currentHour = h.coerceIn(0, 23)
        currentMinute = m.coerceIn(0, 59)

        post {
            jumpToValue(recyclerHours, hoursLayoutManager, currentHour, HOURS_COUNT)
            jumpToValue(recyclerMinutes, minutesLayoutManager, currentMinute, MINUTES_COUNT)
        }
    }

    private fun setHourInternal(h: Int) {
        currentHour = h
        jumpToValue(recyclerHours, hoursLayoutManager, currentHour, HOURS_COUNT)
    }

    private fun setMinuteInternal(m: Int) {
        currentMinute = m
        jumpToValue(recyclerMinutes, minutesLayoutManager, currentMinute, MINUTES_COUNT)
    }

    private fun setupRecyclerView(
        rv: RecyclerView,
        maxValue: Int,
        onItemClick: (Int) -> Unit
    ) {
        val adapter = WheelAdapter(maxValue, onItemClick)
        rv.adapter = adapter

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var lastSnappedValue: Int = -1

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateVisuals(recyclerView)

                // Haptic feedback when crossing values
                val centerView = findCenterView(recyclerView)
                if (centerView != null) {
                    val pos = (recyclerView.layoutManager as LinearLayoutManager).getPosition(centerView)
                    val value = pos % maxValue
                    if (value != lastSnappedValue && lastSnappedValue != -1) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    lastSnappedValue = value
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val helper = if (maxValue == HOURS_COUNT) hoursSnapHelper else minutesSnapHelper
                    val centerView = helper.findSnapView(recyclerView.layoutManager)
                    if (centerView != null) {
                        val pos = (recyclerView.layoutManager as LinearLayoutManager).getPosition(centerView)
                        val value = pos % maxValue
                        if (maxValue == HOURS_COUNT) {
                            currentHour = value
                        } else {
                            currentMinute = value
                        }
                    }
                    updateVisuals(recyclerView)
                }
            }
        })
    }

    private fun updateVisuals(rv: RecyclerView) {
        val centerY = rv.height / 2f
        val itemHeight = (64 * resources.displayMetrics.density)

        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val childCenterY = (child.top + child.bottom) / 2f
            val distance = Math.abs(centerY - childCenterY)
            val fraction = (distance / itemHeight).coerceIn(0f, 1f)

            // Center is 1.0 alpha, edges fade to 0.28
            child.alpha = 1.0f - (0.72f * fraction)
            // Center is scaled 1.15, edges 0.88
            val scale = 1.12f - (0.24f * fraction)
            child.scaleX = scale
            child.scaleY = scale
        }
    }

    private fun findCenterView(rv: RecyclerView): View? {
        val centerY = rv.height / 2
        var closestView: View? = null
        var minDistance = Int.MAX_VALUE

        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val childCenterY = (child.top + child.bottom) / 2
            val distance = Math.abs(centerY - childCenterY)
            if (distance < minDistance) {
                minDistance = distance
                closestView = child
            }
        }
        return closestView
    }

    private fun jumpToValue(
        rv: RecyclerView,
        lm: LinearLayoutManager,
        value: Int,
        maxValue: Int
    ) {
        val itemHeightPx = (64 * resources.displayMetrics.density).toInt()
        val offsetPx = (rv.height - itemHeightPx) / 2
        val baseCycle = (VIRTUAL_MULTIPLIER / 2) * maxValue
        val targetPosition = baseCycle + value

        lm.scrollToPositionWithOffset(targetPosition, offsetPx)
        rv.post { updateVisuals(rv) }
    }

    private fun scrollToValue(
        rv: RecyclerView,
        lm: LinearLayoutManager,
        snapHelper: LinearSnapHelper,
        value: Int,
        maxValue: Int
    ) {
        val centerView = snapHelper.findSnapView(lm)
        val currentPos = if (centerView != null) lm.getPosition(centerView) else (VIRTUAL_MULTIPLIER / 2) * maxValue
        val currentMod = currentPos % maxValue
        var diff = value - currentMod

        // Shortest path around cycle
        if (diff > maxValue / 2) diff -= maxValue
        if (diff < -maxValue / 2) diff += maxValue

        val targetPosition = currentPos + diff
        rv.smoothScrollToPosition(targetPosition)
    }

    inner class WheelAdapter(
        private val maxValue: Int,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<WheelAdapter.WheelViewHolder>() {

        override fun getItemCount(): Int = maxValue * VIRTUAL_MULTIPLIER

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WheelViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wheel_number, parent, false)
            return WheelViewHolder(view)
        }

        override fun onBindViewHolder(holder: WheelViewHolder, position: Int) {
            val value = position % maxValue
            holder.textNumber.text = String.format("%02d", value)
            holder.itemView.setOnClickListener {
                onItemClick(position)
            }
        }

        inner class WheelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textNumber: TextView = itemView.findViewById(R.id.textNumber)
        }
    }
}

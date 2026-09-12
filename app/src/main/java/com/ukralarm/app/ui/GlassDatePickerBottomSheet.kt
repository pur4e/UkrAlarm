package com.ukralarm.app.ui

import android.content.Context
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.ukralarm.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object GlassDatePickerBottomSheet {

    fun show(
        context: Context,
        initialTimestamp: Long,
        onDateSelected: (Long) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_glass_calendar, null)
        dialog.setContentView(view)

        // Make background transparent for rounded glass corners
        (view.parent as? View)?.setBackgroundResource(android.R.color.transparent)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }

        val btnClose = view.findViewById<ImageButton>(R.id.btnCalendarClose)
        val textMonthYear = view.findViewById<TextView>(R.id.textMonthYear)
        val btnPrevMonth = view.findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNextMonth = view.findViewById<ImageButton>(R.id.btnNextMonth)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerCalendarDays)
        val btnClearDate = view.findViewById<MaterialButton>(R.id.btnClearDate)
        val btnConfirmDate = view.findViewById<MaterialButton>(R.id.btnConfirmDate)

        // Selected date (can be null if 0L)
        var selectedCal: Calendar? = if (initialTimestamp > 0L) {
            Calendar.getInstance().apply { timeInMillis = initialTimestamp }
        } else {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        }

        // Current displayed month
        val displayedMonthCal = Calendar.getInstance().apply {
            if (selectedCal != null) {
                timeInMillis = selectedCal!!.timeInMillis
            }
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val monthFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())

        data class DayItem(
            val dayNumber: Int,
            val calendar: Calendar?,
            val isCurrentMonth: Boolean,
            val isToday: Boolean,
            val isSelected: Boolean
        )

        fun generateDays(): List<DayItem> {
            val items = mutableListOf<DayItem>()
            val workingCal = displayedMonthCal.clone() as Calendar

            val firstDayOfWeek = workingCal.get(Calendar.DAY_OF_WEEK)
            val leadingBlanks = when (firstDayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }

            for (i in 0 until leadingBlanks) {
                items.add(DayItem(0, null, isCurrentMonth = false, isToday = false, isSelected = false))
            }

            val maxDaysInMonth = workingCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (day in 1..maxDaysInMonth) {
                val dayCal = workingCal.clone() as Calendar
                dayCal.set(Calendar.DAY_OF_MONTH, day)

                val isToday = dayCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                        dayCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                val isSelected = selectedCal != null &&
                        dayCal.get(Calendar.YEAR) == selectedCal!!.get(Calendar.YEAR) &&
                        dayCal.get(Calendar.DAY_OF_YEAR) == selectedCal!!.get(Calendar.DAY_OF_YEAR)

                items.add(DayItem(day, dayCal, isCurrentMonth = true, isToday = isToday, isSelected = isSelected))
            }

            return items
        }

        var dayItems = generateDays()

        fun updateMonthHeader() {
            val raw = monthFormat.format(displayedMonthCal.time)
            textMonthYear.text = raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        updateMonthHeader()

        lateinit var adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>

        adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount(): Int = dayItems.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
                return object : RecyclerView.ViewHolder(item) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = dayItems[position]
                val textDay = holder.itemView.findViewById<TextView>(R.id.textDayNumber)

                if (!item.isCurrentMonth || item.dayNumber == 0) {
                    textDay.text = ""
                    textDay.background = null
                    holder.itemView.setOnClickListener(null)
                    return
                }

                textDay.text = item.dayNumber.toString()

                when {
                    item.isSelected -> {
                        textDay.setBackgroundResource(R.drawable.bg_calendar_day_selected)
                        textDay.setTextColor(Color.WHITE)
                    }
                    item.isToday -> {
                        textDay.setBackgroundResource(R.drawable.bg_calendar_day_today)
                        textDay.setTextColor(textDay.context.getColor(R.color.ios_purple_dark))
                    }
                    else -> {
                        textDay.background = null
                        val dayOfWeek = item.calendar?.get(Calendar.DAY_OF_WEEK)
                        if (dayOfWeek == Calendar.SUNDAY) {
                            textDay.setTextColor(Color.parseColor("#FF453A"))
                        } else {
                            val typedArray = textDay.context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
                            val color = typedArray.getColor(0, Color.WHITE)
                            typedArray.recycle()
                            textDay.setTextColor(color)
                        }
                    }
                }

                holder.itemView.setOnClickListener {
                    item.calendar?.let { cal ->
                        it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        selectedCal = cal.clone() as Calendar
                        dayItems = generateDays()
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }

        recycler.layoutManager = GridLayoutManager(context, 7)
        recycler.adapter = adapter

        btnPrevMonth.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            displayedMonthCal.add(Calendar.MONTH, -1)
            updateMonthHeader()
            dayItems = generateDays()
            adapter.notifyDataSetChanged()
        }

        btnNextMonth.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            displayedMonthCal.add(Calendar.MONTH, 1)
            updateMonthHeader()
            dayItems = generateDays()
            adapter.notifyDataSetChanged()
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnClearDate.setOnClickListener {
            onDateSelected(0L)
            dialog.dismiss()
        }

        btnConfirmDate.setOnClickListener {
            selectedCal?.let { cal ->
                onDateSelected(cal.timeInMillis)
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}

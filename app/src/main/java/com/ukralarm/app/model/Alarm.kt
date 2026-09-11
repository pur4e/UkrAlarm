package com.ukralarm.app.model

data class Alarm(
    val id: Long = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val label: String = "",
    val isEnabled: Boolean = true,
    val daysOfWeek: Int = 0,
    val isRepeating: Boolean = false,
    val ringtoneUri: String = "", // NEW field for custom ringtone
    val isVibrationEnabled: Boolean = true,
    val isSnoozeEnabled: Boolean = true,
    val specificDate: Long = 0L, // Timestamp for specific date
    val vibrationPattern: String = "basic",
    val snoozeIntervalMin: Int = 5,
    val snoozeMaxCount: Int = 3
) {
    fun getTimeString(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    fun getDaysString(context: android.content.Context): String {
        if (specificDate > 0L) {
            val format = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
            return format.format(java.util.Date(specificDate))
        }
        
        if (daysOfWeek == 0) return context.getString(com.ukralarm.app.R.string.just_once)
        if (daysOfWeek == 0b1111111) return context.getString(com.ukralarm.app.R.string.every_day)
        if (daysOfWeek == 0b0011111) return context.getString(com.ukralarm.app.R.string.weekdays)
        if (daysOfWeek == 0b1100000) return context.getString(com.ukralarm.app.R.string.weekends)

        val days = arrayOf(
            context.getString(com.ukralarm.app.R.string.day_monday),
            context.getString(com.ukralarm.app.R.string.day_tuesday),
            context.getString(com.ukralarm.app.R.string.day_wednesday),
            context.getString(com.ukralarm.app.R.string.day_thursday),
            context.getString(com.ukralarm.app.R.string.day_friday),
            context.getString(com.ukralarm.app.R.string.day_saturday),
            context.getString(com.ukralarm.app.R.string.day_sunday)
        )
        val result = java.lang.StringBuilder()
        for (i in 0..6) {
            if (daysOfWeek and (1 shl i) != 0) {
                if (result.isNotEmpty()) result.append(", ")
                result.append(days[i])
            }
        }
        return result.toString()
    }

    fun isDayEnabled(dayIndex: Int): Boolean {
        return daysOfWeek and (1 shl dayIndex) != 0
    }
}

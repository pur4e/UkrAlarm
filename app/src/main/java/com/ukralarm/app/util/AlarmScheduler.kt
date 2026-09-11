package com.ukralarm.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ukralarm.app.model.Alarm
import com.ukralarm.app.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {

    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "CHECK_AND_RING"
            putExtra("ALARM_ID", alarm.id)
            putExtra("SNOOZE_COUNT", 0)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = calculateNextTriggerTime(alarm)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Cannot use exact alarms. Use set instead of setAlarmClock as a fallback if needed,
            // or just ignore if we lack permission. However, setAlarmClock works without permission
            // on some versions, but crashes on others. We'll use setExact if we can, or set as fallback.
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleNext(context: Context, alarm: Alarm) {
        if (alarm.daysOfWeek != 0) {
            schedule(context, alarm)
        }
    }

    fun scheduleSnooze(context: Context, alarm: Alarm, currentSnoozeCount: Int) {
        if (alarm.snoozeMaxCount > 0 && currentSnoozeCount >= alarm.snoozeMaxCount) {
            // Reached max snooze
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "CHECK_AND_RING"
            putExtra("ALARM_ID", alarm.id)
            putExtra("SNOOZE_COUNT", currentSnoozeCount + 1)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val triggerTime = System.currentTimeMillis() + alarm.snoozeIntervalMin * 60 * 1000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerTime, pendingIntent), pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerTime, pendingIntent), pendingIntent)
        }
    }

    fun calculateNextTriggerTime(alarm: Alarm): Long {
        val now = Calendar.getInstance()
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.specificDate > 0L) {
            val specificCal = Calendar.getInstance().apply { timeInMillis = alarm.specificDate }
            alarmTime.set(Calendar.YEAR, specificCal.get(Calendar.YEAR))
            alarmTime.set(Calendar.MONTH, specificCal.get(Calendar.MONTH))
            alarmTime.set(Calendar.DAY_OF_MONTH, specificCal.get(Calendar.DAY_OF_MONTH))
            
            // If the specific date is in the past, return that past time (it will immediately ring or be skipped depending on logic)
            return alarmTime.timeInMillis
        }

        if (alarm.daysOfWeek == 0) {
            if (alarmTime.timeInMillis <= now.timeInMillis) {
                alarmTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            return alarmTime.timeInMillis
        }

        for (daysAhead in 0..7) {
            val checkTime = (alarmTime.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, daysAhead)
            }

            if (checkTime.timeInMillis <= now.timeInMillis) continue

            val dayOfWeek = checkTime.get(Calendar.DAY_OF_WEEK)
            val bitIndex = when (dayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }

            if (alarm.daysOfWeek and (1 shl bitIndex) != 0) {
                return checkTime.timeInMillis
            }
        }

        alarmTime.add(Calendar.DAY_OF_YEAR, 1)
        return alarmTime.timeInMillis
    }
}

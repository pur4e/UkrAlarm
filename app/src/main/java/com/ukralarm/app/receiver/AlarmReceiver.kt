package com.ukralarm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ukralarm.app.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("ALARM_ID", -1)
        if (alarmId == -1L) {
            Log.w(TAG, "Received intent without valid ALARM_ID, ignoring")
            return
        }

        val snoozeCount = intent.getIntExtra("SNOOZE_COUNT", 0)
        Log.d(TAG, "Alarm triggered: id=$alarmId, snoozeCount=$snoozeCount")

        // Acquire a temporary wake lock to ensure the service starts
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PureClock:AlarmReceiverWakeLock"
        )
        wakeLock.acquire(10_000L) // 10 seconds should be enough to start the service

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = "CHECK_AND_RING"
            putExtra("ALARM_ID", alarmId)
            putExtra("SNOOZE_COUNT", snoozeCount)
        }
        
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AlarmService", e)
        } finally {
            try {
                if (wakeLock.isHeld) wakeLock.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}

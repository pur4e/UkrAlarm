package com.ukralarm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ukralarm.app.data.AlarmDatabase
import com.ukralarm.app.util.AlarmScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val db = AlarmDatabase(context)
            val enabledAlarms = db.getEnabledAlarms()
            for (alarm in enabledAlarms) {
                AlarmScheduler.schedule(context, alarm)
            }
        }
    }
}

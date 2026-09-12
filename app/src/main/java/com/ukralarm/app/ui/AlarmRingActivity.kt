package com.ukralarm.app.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.ukralarm.app.R
import com.ukralarm.app.service.AlarmService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmRingActivity : BaseActivity() {

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmService.ACTION_CLOSE_RING_ACTIVITY) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm_ring)

        // Show current time
        val textTime = findViewById<TextView>(R.id.textAlarmTime)
        textTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val label = intent.getStringExtra("ALARM_LABEL") ?: ""
        val textLabel = findViewById<TextView>(R.id.textAlarmLabel)
        if (label.isNotEmpty()) {
            textLabel.text = label
            textLabel.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            val stopIntent = Intent(this, AlarmService::class.java).apply {
                action = "DISMISS"
            }
            startService(stopIntent)
            finish()
        }

        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            val snoozeIntent = Intent(this, AlarmService::class.java).apply {
                action = "SNOOZE"
                putExtra("ALARM_ID", intent.getLongExtra("ALARM_ID", 0))
            }
            startService(snoozeIntent)
            finish()
        }

        val filter = IntentFilter(AlarmService.ACTION_CLOSE_RING_ACTIVITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }
}

package com.ukralarm.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ukralarm.app.R
import com.ukralarm.app.api.AlertApi
import com.ukralarm.app.data.AlarmDatabase
import com.ukralarm.app.ui.AlarmRingActivity
import com.ukralarm.app.ui.MainActivity
import com.ukralarm.app.util.AlarmScheduler
import com.ukralarm.app.util.LocaleHelper

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isLooping = false
    private var isAlertNotificationShown = false
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private var autoSnoozeRunnable: Runnable? = null
    private var alarmId: Long = 0
    private var alarmLabel: String = ""
    private var originalTriggerTime: Long = 0
    
    private val MAX_WAIT_TIME_MS = 3 * 60 * 60 * 1000L
    private val CHECK_INTERVAL_MS = 30 * 1000L
    private val AUTO_SNOOZE_MS = 60 * 1000L // 1 minute auto snooze

    companion object {
        const val ACTION_CLOSE_RING_ACTIVITY = "com.ukralarm.app.ACTION_CLOSE_RING"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private var currentSnoozeCount = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        alarmId = intent?.getLongExtra("ALARM_ID", 0) ?: alarmId
        alarmLabel = intent?.getStringExtra("ALARM_LABEL") ?: alarmLabel
        currentSnoozeCount = intent?.getIntExtra("SNOOZE_COUNT", 0) ?: 0

        when (action) {
            "CHECK_AND_RING" -> {
                originalTriggerTime = System.currentTimeMillis()
                startForegroundCompat(1, createMonitoringNotification(getString(R.string.app_name)))
                checkAlertStatus()
            }
            "DISMISS" -> stopAlarm()
            "SNOOZE" -> snoozeAlarm()
        }

        return START_NOT_STICKY
    }

    private fun checkAlertStatus() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val region = prefs.getString("region", "") ?: ""
        val district = prefs.getString("district", "") ?: ""
        val postponeEnabled = prefs.getBoolean("postpone_enabled", true)

        if (region.isEmpty() || !postponeEnabled) {
            ringAlarm()
            return
        }

        Thread {
            try {
                val isAlert = AlertApi.isAlertActive(region, district)
                handler.post {
                    if (isAlert) {
                        val timeWaiting = System.currentTimeMillis() - originalTriggerTime
                        if (timeWaiting > MAX_WAIT_TIME_MS) {
                            ringAlarm()
                        } else {
                            if (!isAlertNotificationShown) {
                                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                notificationManager.notify(1, createMonitoringNotification(getString(R.string.postponed_by_alert)))
                                isAlertNotificationShown = true
                            }
                            
                            checkRunnable = Runnable { checkAlertStatus() }
                            handler.postDelayed(checkRunnable!!, CHECK_INTERVAL_MS)
                        }
                    } else {
                        ringAlarm()
                    }
                }
            } catch (e: Exception) {
                handler.post { ringAlarm() }
            }
        }.start()
    }

    private fun ringAlarm() {
        checkRunnable?.let { handler.removeCallbacks(it) }

        try {
            var alarmUri: android.net.Uri? = null
            
            // Try to get custom ringtone from DB
            if (alarmId > 0) {
                val db = AlarmDatabase(this)
                val alarm = db.getAlarm(alarmId)
                if (alarm != null && alarm.ringtoneUri.isNotEmpty()) {
                    alarmUri = android.net.Uri.parse(alarm.ringtoneUri)
                }
            }

            // Fallback to default
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri!!)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            var isVibEnabled = true
            var vibPattern = "basic"
            if (alarmId > 0) {
                val db = AlarmDatabase(this)
                val alarm = db.getAlarm(alarmId)
                if (alarm != null) {
                    isVibEnabled = alarm.isVibrationEnabled
                    vibPattern = alarm.vibrationPattern
                }
            }

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (isVibEnabled && vibrator?.hasVibrator() == true) {
                val timings = when (vibPattern) {
                    "heartbeat" -> longArrayOf(0, 200, 100, 200, 500)
                    "ticktock" -> longArrayOf(0, 100, 500, 100, 500)
                    else -> longArrayOf(0, 1000, 1000)
                }
                val amplitudes = when (vibPattern) {
                    "heartbeat" -> intArrayOf(0, 255, 0, 255, 0)
                    "ticktock" -> intArrayOf(0, 150, 0, 255, 0)
                    else -> intArrayOf(0, 255, 0)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(timings, 0)
                }
            }

            val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_LABEL", alarmLabel)
            }
            startActivity(fullScreenIntent)

            // Update to ringing notification
            startForegroundCompat(1, createRingingNotification())

            // Start Auto-snooze timer (1 minute auto-snooze or similar, standard is 1 min ring before auto snooze, let's use 60000)
            autoSnoozeRunnable = Runnable { snoozeAlarm() }
            handler.postDelayed(autoSnoozeRunnable!!, 60000L)

        } catch (e: Exception) {
            Log.e("AlarmService", "Error ringing alarm", e)
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        vibrator?.cancel()
        
        checkRunnable?.let { handler.removeCallbacks(it) }
        autoSnoozeRunnable?.let { handler.removeCallbacks(it) }

        if (alarmId > 0) {
            val db = AlarmDatabase(this)
            val alarm = db.getAlarm(alarmId)
            if (alarm != null) {
                if (alarm.specificDate > 0L || (!alarm.isRepeating && alarm.daysOfWeek == 0)) {
                    // Turn off one-off alarms completely
                    db.updateAlarm(alarm.copy(isEnabled = false))
                } else {
                    AlarmScheduler.scheduleNext(this, alarm)
                }
            }
        }
        
        sendBroadcast(Intent(ACTION_CLOSE_RING_ACTIVITY))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }


    private fun snoozeAlarm() {
        // Stop current ringing
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        
        checkRunnable?.let { handler.removeCallbacks(it) }
        autoSnoozeRunnable?.let { handler.removeCallbacks(it) }
        
        sendBroadcast(Intent(ACTION_CLOSE_RING_ACTIVITY))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        if (alarmId > 0) {
            val db = AlarmDatabase(this)
            val alarm = db.getAlarm(alarmId)
            if (alarm != null) {
                if (alarm.isSnoozeEnabled) {
                    AlarmScheduler.scheduleSnooze(this, alarm, currentSnoozeCount)
                } else {
                    // Just act as stop if snooze is disabled
                    if (alarm.specificDate > 0L || (!alarm.isRepeating && alarm.daysOfWeek == 0)) {
                        db.updateAlarm(alarm.copy(isEnabled = false))
                    } else {
                        AlarmScheduler.scheduleNext(this, alarm)
                    }
                }
            }
        }
        stopSelf()
    }

    private fun createMonitoringNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "ALARM_CHANNEL")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun createRingingNotification(): Notification {
        val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", alarmLabel)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmService::class.java).apply { action = "DISMISS" }
        val dismissPendingIntent = PendingIntent.getService(this, 1, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val snoozeIntent = Intent(this, AlarmService::class.java).apply { 
            action = "SNOOZE" 
            putExtra("ALARM_ID", alarmId)
        }
        val snoozePendingIntent = PendingIntent.getService(this, 2, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = if (alarmLabel.isNotEmpty()) alarmLabel else getString(R.string.ring_notification_title)

        return NotificationCompat.Builder(this, "ALARM_CHANNEL")
            .setContentTitle(title)
            .setContentText(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_settings)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, getString(R.string.dismiss), dismissPendingIntent)
            .addAction(0, getString(R.string.snooze), snoozePendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundCompat(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ALARM_CHANNEL",
                "Pure Clock Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pure Clock Alarm Alerts"
                setBypassDnd(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        vibrator?.cancel()
        checkRunnable?.let { handler.removeCallbacks(it) }
        autoSnoozeRunnable?.let { handler.removeCallbacks(it) }
    }
}

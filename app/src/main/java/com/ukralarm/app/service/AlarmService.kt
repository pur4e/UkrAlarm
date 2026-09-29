package com.ukralarm.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null
    private var isAlertNotificationShown = false
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private var autoSnoozeRunnable: Runnable? = null
    private var alarmId: Long = 0
    private var alarmLabel: String = ""
    private var originalTriggerTime: Long = 0
    
    private val MAX_WAIT_TIME_MS = 3 * 60 * 60 * 1000L
    private val CHECK_INTERVAL_MS = 30 * 1000L

    companion object {
        const val ACTION_CLOSE_RING_ACTIVITY = "com.ukralarm.app.ACTION_CLOSE_RING"
        private const val TAG = "AlarmService"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    private var currentSnoozeCount = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val intentAlarmId = intent?.getLongExtra("ALARM_ID", -1) ?: -1
        if (intentAlarmId > 0) {
            alarmId = intentAlarmId
        }
        alarmLabel = intent?.getStringExtra("ALARM_LABEL") ?: alarmLabel
        currentSnoozeCount = intent?.getIntExtra("SNOOZE_COUNT", 0) ?: 0

        // Load label from DB if not provided in intent
        if (alarmLabel.isEmpty() && alarmId > 0) {
            try {
                val db = AlarmDatabase(this)
                val alarm = db.getAlarm(alarmId)
                alarmLabel = alarm?.label ?: ""
            } catch (e: Exception) {
                Log.w(TAG, "Could not load alarm label", e)
            }
        }

        Log.d(TAG, "onStartCommand: action=$action, alarmId=$alarmId, snoozeCount=$currentSnoozeCount")

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

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PureClock:AlarmServiceWakeLock"
            ).apply {
                acquire(MAX_WAIT_TIME_MS + 60_000L) // max postpone + 1 min buffer
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock", e)
        }
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
                            Log.d(TAG, "Max postpone time reached, ringing anyway")
                            ringAlarm()
                        } else {
                            Log.d(TAG, "Alert active, postponing alarm")
                            if (!isAlertNotificationShown) {
                                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                notificationManager.notify(1, createMonitoringNotification(getString(R.string.postponed_by_alert)))
                                isAlertNotificationShown = true
                            }
                            
                            checkRunnable = Runnable { checkAlertStatus() }
                            handler.postDelayed(checkRunnable!!, CHECK_INTERVAL_MS)
                        }
                    } else {
                        Log.d(TAG, "No alert active, ringing alarm")
                        ringAlarm()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking alert status, ringing anyway", e)
                handler.post { ringAlarm() }
            }
        }.start()
    }

    private fun ringAlarm() {
        checkRunnable?.let { handler.removeCallbacks(it) }
        Log.d(TAG, "ringAlarm() called for alarmId=$alarmId")

        try {
            var alarmUri: Uri? = null
            
            // Try to get custom ringtone from DB
            if (alarmId > 0) {
                val db = AlarmDatabase(this)
                val alarm = db.getAlarm(alarmId)
                if (alarm != null && alarm.ringtoneUri.isNotEmpty()) {
                    alarmUri = Uri.parse(alarm.ringtoneUri)
                }
            }

            // Fallback to default alarm sound
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            // Fallback to notification sound if no alarm sound available
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri!!)
                setAudioAttributes(audioAttributes)
                // IMPORTANT: Must use the MediaPlayer's setLooping method explicitly,
                // not the Kotlin property syntax inside apply{} which would shadow with outer class fields
                setLooping(true)
                prepare()
                start()
            }
            Log.d(TAG, "MediaPlayer started, looping=${mediaPlayer?.isLooping}")

            // Load vibration settings
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
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
            }

            // Launch full-screen alarm activity
            val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_LABEL", alarmLabel)
            }
            startActivity(fullScreenIntent)

            // Update to ringing notification with full-screen intent
            startForegroundCompat(1, createRingingNotification())

            // Auto-snooze after 60 seconds if user doesn't interact
            autoSnoozeRunnable = Runnable { 
                Log.d(TAG, "Auto-snooze triggered")
                snoozeAlarm() 
            }
            handler.postDelayed(autoSnoozeRunnable!!, 60_000L)

        } catch (e: Exception) {
            Log.e(TAG, "Error ringing alarm", e)
        }
    }

    private fun stopAlarm() {
        Log.d(TAG, "stopAlarm() called")
        
        try { mediaPlayer?.stop() } catch (e: Exception) { /* ignore */ }
        try { mediaPlayer?.release() } catch (e: Exception) { /* ignore */ }
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
        releaseWakeLock()
        stopSelf()
    }


    private fun snoozeAlarm() {
        Log.d(TAG, "snoozeAlarm() called, snoozeCount=$currentSnoozeCount")
        
        // Stop current ringing
        try { mediaPlayer?.stop() } catch (e: Exception) { /* ignore */ }
        try { mediaPlayer?.release() } catch (e: Exception) { /* ignore */ }
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
        releaseWakeLock()
        stopSelf()
    }

    private fun createMonitoringNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "ALARM_CHANNEL")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_alarm_notification)
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
            putExtra("SNOOZE_COUNT", currentSnoozeCount)
        }
        val snoozePendingIntent = PendingIntent.getService(this, 2, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = if (alarmLabel.isNotEmpty()) alarmLabel else getString(R.string.ring_notification_title)

        return NotificationCompat.Builder(this, "ALARM_CHANNEL")
            .setContentTitle(title)
            .setContentText(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, getString(R.string.dismiss), dismissPendingIntent)
            .addAction(0, getString(R.string.snooze), snoozePendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
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
        val channel = NotificationChannel(
            "ALARM_CHANNEL",
            "Pure Clock Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pure Clock Alarm Alerts"
            setBypassDnd(true)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null) // Sound is managed by MediaPlayer, not notification
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try { mediaPlayer?.release() } catch (e: Exception) { /* ignore */ }
        vibrator?.cancel()
        checkRunnable?.let { handler.removeCallbacks(it) }
        autoSnoozeRunnable?.let { handler.removeCallbacks(it) }
        releaseWakeLock()
    }
}

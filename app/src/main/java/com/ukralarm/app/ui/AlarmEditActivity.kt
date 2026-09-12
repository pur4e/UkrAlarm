package com.ukralarm.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ukralarm.app.R
import com.ukralarm.app.data.AlarmDatabase
import com.ukralarm.app.model.Alarm
import com.ukralarm.app.util.AlarmScheduler
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AlarmEditActivity : BaseActivity() {

    private lateinit var db: AlarmDatabase
    private var alarmId: Long = 0
    private var currentAlarm: Alarm? = null

    private lateinit var timePicker: WheelTimePicker
    private lateinit var textDaysSummary: TextView
    private lateinit var textRingtoneName: TextView
    private lateinit var textVibrationName: TextView
    private lateinit var textSnoozeName: TextView
    private lateinit var editLabel: EditText
    private lateinit var dayToggles: Array<CheckBox>

    private lateinit var switchSound: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchVibration: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchSnooze: androidx.appcompat.widget.SwitchCompat

    private var selectedRingtoneUri: String = ""
    private var selectedVibrationPattern: String = "basic"
    private var selectedSnoozeIntervalMin: Int = 5
    private var selectedSnoozeMaxCount: Int = 3
    private var selectedSpecificDate: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_edit)

        db = AlarmDatabase(this)
        alarmId = intent.getLongExtra("ALARM_ID", 0)

        val textEditTitle = findViewById<TextView>(R.id.textEditTitle)
        if (alarmId > 0) {
            textEditTitle.setText(R.string.edit_alarm)
        } else {
            textEditTitle.setText(R.string.new_alarm)
        }

        timePicker = findViewById(R.id.timePicker)
        textDaysSummary = findViewById(R.id.textDaysSummary)
        textRingtoneName = findViewById(R.id.textRingtoneName)
        textVibrationName = findViewById(R.id.textVibrationName)
        textSnoozeName = findViewById(R.id.textSnoozeName)
        editLabel = findViewById(R.id.editLabel)

        switchSound = findViewById(R.id.switchSound)
        switchVibration = findViewById(R.id.switchVibration)
        switchSnooze = findViewById(R.id.switchSnooze)

        dayToggles = arrayOf(
            findViewById(R.id.btnMon),
            findViewById(R.id.btnTue),
            findViewById(R.id.btnWed),
            findViewById(R.id.btnThu),
            findViewById(R.id.btnFri),
            findViewById(R.id.btnSat),
            findViewById(R.id.btnSun)
        )

        for (toggle in dayToggles) {
            toggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedSpecificDate = 0L
                }
                updateDaysSummary()
            }
        }

        if (alarmId > 0) {
            currentAlarm = db.getAlarm(alarmId)
            currentAlarm?.let {
                timePicker.setTime(it.hour, it.minute)
                selectedRingtoneUri = it.ringtoneUri
                selectedVibrationPattern = it.vibrationPattern
                selectedSnoozeIntervalMin = it.snoozeIntervalMin
                selectedSnoozeMaxCount = it.snoozeMaxCount
                selectedSpecificDate = it.specificDate
                
                switchVibration.isChecked = it.isVibrationEnabled
                switchSnooze.isChecked = it.isSnoozeEnabled
                switchSound.isChecked = it.ringtoneUri.isNotEmpty()
                
                editLabel.setText(it.label)
                for (i in 0..6) {
                    dayToggles[i].isChecked = it.isDayEnabled(i)
                }
            }
        }

        updateDaysSummary()
        updateRingtoneDisplay()
        updateVibrationDisplay()
        updateSnoozeDisplay()

        findViewById<View>(R.id.btnCalendar).setOnClickListener {
            GlassDatePickerBottomSheet.show(
                context = this,
                initialTimestamp = selectedSpecificDate
            ) { selectedTimestamp ->
                selectedSpecificDate = selectedTimestamp
                if (selectedSpecificDate > 0L) {
                    for (toggle in dayToggles) toggle.isChecked = false
                }
                updateDaysSummary()
            }
        }

        findViewById<View>(R.id.btnRingtone).setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            
            val currentUri = if (selectedRingtoneUri.isNotEmpty()) Uri.parse(selectedRingtoneUri) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 999)
        }

        findViewById<View>(R.id.btnVibrationRow).setOnClickListener {
            val basicStr = getString(R.string.vib_basic)
            val heartbeatStr = getString(R.string.vib_heartbeat)
            val ticktockStr = getString(R.string.vib_ticktock)

            val options = listOf(basicStr, heartbeatStr, ticktockStr)
            val currentName = when (selectedVibrationPattern) {
                "heartbeat" -> heartbeatStr
                "ticktock" -> ticktockStr
                else -> basicStr
            }

            GlassBottomSheetPicker.show(
                context = this,
                title = getString(R.string.vibration),
                items = options,
                selectedItem = currentName
            ) { selected ->
                selectedVibrationPattern = when (selected) {
                    heartbeatStr -> "heartbeat"
                    ticktockStr -> "ticktock"
                    else -> "basic"
                }
                updateVibrationDisplay()
                sampleVibration(selectedVibrationPattern)
            }
        }

        findViewById<View>(R.id.btnSnoozeRow).setOnClickListener {
            showSnoozeDialog()
        }

        findViewById<View>(R.id.btnSave).setOnClickListener { saveAlarm() }
        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        
        val cardDelete = findViewById<View>(R.id.cardDelete)
        val btnDelete = findViewById<View>(R.id.btnDelete)
        if (alarmId > 0) {
            cardDelete.visibility = View.VISIBLE
            btnDelete.setOnClickListener { deleteAlarm() }
        } else {
            cardDelete.visibility = View.GONE
        }
    }

    private fun sampleVibration(patternKey: String) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val timings = when (patternKey) {
                "heartbeat" -> longArrayOf(0, 100, 100, 200)
                "ticktock" -> longArrayOf(0, 50, 200, 50)
                else -> longArrayOf(0, 300)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSnoozeDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_glass_snooze, null)
        dialog.setContentView(view)
        (view.parent as? View)?.setBackgroundResource(android.R.color.transparent)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }

        val btnClose = view.findViewById<View>(R.id.btnSnoozeClose)
        val pill5 = view.findViewById<TextView>(R.id.pillInterval5)
        val pill10 = view.findViewById<TextView>(R.id.pillInterval10)
        val pill15 = view.findViewById<TextView>(R.id.pillInterval15)
        val pill30 = view.findViewById<TextView>(R.id.pillInterval30)
        val pillCustom = view.findViewById<TextView>(R.id.pillIntervalCustom)
        val layoutCustom = view.findViewById<View>(R.id.layoutCustomMinutesSheet)
        val editCustom = view.findViewById<EditText>(R.id.editCustomMinutesSheet)

        val pillRep3 = view.findViewById<TextView>(R.id.pillRepeat3)
        val pillRep5 = view.findViewById<TextView>(R.id.pillRepeat5)
        val pillRepInf = view.findViewById<TextView>(R.id.pillRepeatInf)
        val btnSave = view.findViewById<View>(R.id.btnSaveSnoozeSheet)

        pill5.text = "5 " + getString(R.string.minutes_short)
        pill10.text = "10 " + getString(R.string.minutes_short)
        pill15.text = "15 " + getString(R.string.minutes_short)
        pill30.text = "30 " + getString(R.string.minutes_short)

        var tempInterval = selectedSnoozeIntervalMin
        var tempRepeats = selectedSnoozeMaxCount

        fun updateIntervalPills(value: Int, isCustom: Boolean) {
            pill5.isSelected = !isCustom && value == 5
            pill10.isSelected = !isCustom && value == 10
            pill15.isSelected = !isCustom && value == 15
            pill30.isSelected = !isCustom && value == 30
            pillCustom.isSelected = isCustom
            layoutCustom.visibility = if (isCustom) View.VISIBLE else View.GONE
            if (isCustom && editCustom.text.isEmpty()) {
                editCustom.setText(value.toString())
            }
        }

        fun updateRepeatPills(rep: Int) {
            pillRep3.isSelected = (rep == 3)
            pillRep5.isSelected = (rep == 5)
            pillRepInf.isSelected = (rep == 0)
        }

        val isStandard = listOf(5, 10, 15, 30).contains(tempInterval)
        updateIntervalPills(tempInterval, !isStandard)
        if (!isStandard) {
            editCustom.setText(tempInterval.toString())
        }
        updateRepeatPills(tempRepeats)

        pill5.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            tempInterval = 5
            updateIntervalPills(5, false)
        }
        pill10.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            tempInterval = 10
            updateIntervalPills(10, false)
        }
        pill15.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            tempInterval = 15
            updateIntervalPills(15, false)
        }
        pill30.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            tempInterval = 30
            updateIntervalPills(30, false)
        }
        pillCustom.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            updateIntervalPills(tempInterval, true)
            editCustom.requestFocus()
        }

        pillRep3.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            tempRepeats = 3
            updateRepeatPills(3)
        }
        pillRep5.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            tempRepeats = 5
            updateRepeatPills(5)
        }
        pillRepInf.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            tempRepeats = 0
            updateRepeatPills(0)
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            if (pillCustom.isSelected) {
                val input = editCustom.text.toString().toIntOrNull()
                tempInterval = when {
                    input == null || input <= 0 -> 5
                    input > 180 -> 180
                    else -> input
                }
            }
            selectedSnoozeIntervalMin = tempInterval
            selectedSnoozeMaxCount = tempRepeats
            updateSnoozeDisplay()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateDaysSummary() {
        if (selectedSpecificDate > 0L) {
            val format = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
            textDaysSummary.text = format.format(java.util.Date(selectedSpecificDate))
            return
        }

        var daysOfWeek = 0
        for (i in 0..6) {
            if (dayToggles[i].isChecked) {
                daysOfWeek = daysOfWeek or (1 shl i)
            }
        }
        
        val tempAlarm = Alarm(daysOfWeek = daysOfWeek, specificDate = selectedSpecificDate)
        textDaysSummary.text = tempAlarm.getDaysString(this)
    }

    private fun updateVibrationDisplay() {
        textVibrationName.text = when (selectedVibrationPattern) {
            "heartbeat" -> getString(R.string.vib_heartbeat)
            "ticktock" -> getString(R.string.vib_ticktock)
            else -> getString(R.string.vib_basic)
        }
    }

    private fun updateSnoozeDisplay() {
        val countStr = when (selectedSnoozeMaxCount) {
            0 -> getString(R.string.repeat_inf)
            5 -> getString(R.string.repeat_5)
            else -> getString(R.string.repeat_3)
        }
        textSnoozeName.text = getString(R.string.snooze_format, selectedSnoozeIntervalMin, countStr)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 999 && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                selectedRingtoneUri = uri.toString()
                updateRingtoneDisplay()
            }
        }
    }

    private fun updateRingtoneDisplay() {
        if (selectedRingtoneUri.isEmpty()) {
            textRingtoneName.text = getString(R.string.sound_default)
        } else {
            try {
                val ringtone = RingtoneManager.getRingtone(this, Uri.parse(selectedRingtoneUri))
                textRingtoneName.text = ringtone.getTitle(this)
            } catch (e: Exception) {
                textRingtoneName.text = getString(R.string.custom_sound)
            }
        }
    }

    private fun saveAlarm() {
        var daysOfWeek = 0
        var isRepeating = false
        for (i in 0..6) {
            if (dayToggles[i].isChecked) {
                daysOfWeek = daysOfWeek or (1 shl i)
                isRepeating = true
            }
        }

        val hour = timePicker.hour
        val minute = timePicker.minute

        val soundUriToSave = if (switchSound.isChecked) {
            if (selectedRingtoneUri.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString() else selectedRingtoneUri
        } else ""

        val alarm = Alarm(
            id = alarmId,
            hour = hour,
            minute = minute,
            label = editLabel.text.toString(),
            isEnabled = true,
            daysOfWeek = daysOfWeek,
            isRepeating = isRepeating,
            ringtoneUri = soundUriToSave,
            isVibrationEnabled = switchVibration.isChecked,
            isSnoozeEnabled = switchSnooze.isChecked,
            specificDate = selectedSpecificDate,
            vibrationPattern = selectedVibrationPattern,
            snoozeIntervalMin = selectedSnoozeIntervalMin,
            snoozeMaxCount = selectedSnoozeMaxCount
        )

        val newId = if (alarmId > 0) {
            db.updateAlarm(alarm)
            alarmId
        } else {
            db.insertAlarm(alarm)
        }

        val savedAlarm = alarm.copy(id = newId)
        AlarmScheduler.schedule(this, savedAlarm)
        
        showTimeRemainingToast(savedAlarm)
        com.ukralarm.app.widget.ClockWidgetProvider.updateAllWidgets(this)
        finish()
    }

    private fun showTimeRemainingToast(alarm: Alarm) {
        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm)
        val diffMs = nextTrigger - System.currentTimeMillis()
        if (diffMs > 0) {
            val days = TimeUnit.MILLISECONDS.toDays(diffMs)
            val hours = TimeUnit.MILLISECONDS.toHours(diffMs) % 24
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60
            
            val msg = if (days > 0) {
                getString(R.string.alarm_set_days, days, hours, minutes)
            } else if (hours > 0) {
                getString(R.string.alarm_set_hours, hours, minutes)
            } else if (minutes > 0) {
                getString(R.string.alarm_set_minutes, minutes)
            } else {
                getString(R.string.alarm_set_now)
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteAlarm() {
        currentAlarm?.let {
            AlarmScheduler.cancel(this, it.id)
            db.deleteAlarm(it.id)
            com.ukralarm.app.widget.ClockWidgetProvider.updateAllWidgets(this)
        }
        finish()
    }
}

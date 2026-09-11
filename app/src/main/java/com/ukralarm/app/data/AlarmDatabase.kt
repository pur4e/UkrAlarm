package com.ukralarm.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ukralarm.app.model.Alarm

class AlarmDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "alarms.db"
        private const val DATABASE_VERSION = 4 // Updated for vib/snooze settings
        private const val TABLE_ALARMS = "alarms"
        private const val COL_ID = "id"
        private const val COL_HOUR = "hour"
        private const val COL_MINUTE = "minute"
        private const val COL_LABEL = "label"
        private const val COL_ENABLED = "is_enabled"
        private const val COL_DAYS = "days_of_week"
        private const val COL_REPEATING = "is_repeating"
        private const val COL_RINGTONE = "ringtone_uri"
        private const val COL_VIBRATION = "is_vibration_enabled"
        private const val COL_SNOOZE = "is_snooze_enabled"
        private const val COL_SPECIFIC_DATE = "specific_date"
        private const val COL_VIB_PATTERN = "vibration_pattern"
        private const val COL_SNOOZE_INT = "snooze_interval_min"
        private const val COL_SNOOZE_CNT = "snooze_max_count"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_ALARMS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_HOUR INTEGER NOT NULL,
                $COL_MINUTE INTEGER NOT NULL,
                $COL_LABEL TEXT DEFAULT '',
                $COL_ENABLED INTEGER DEFAULT 1,
                $COL_DAYS INTEGER DEFAULT 0,
                $COL_REPEATING INTEGER DEFAULT 0,
                $COL_RINGTONE TEXT DEFAULT '',
                $COL_VIBRATION INTEGER DEFAULT 1,
                $COL_SNOOZE INTEGER DEFAULT 1,
                $COL_SPECIFIC_DATE INTEGER DEFAULT 0,
                $COL_VIB_PATTERN TEXT DEFAULT 'basic',
                $COL_SNOOZE_INT INTEGER DEFAULT 5,
                $COL_SNOOZE_CNT INTEGER DEFAULT 3
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_ALARMS ADD COLUMN $COL_RINGTONE TEXT DEFAULT ''")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_ALARMS ADD COLUMN $COL_VIBRATION INTEGER DEFAULT 1")
            db.execSQL("ALTER TABLE $TABLE_ALARMS ADD COLUMN $COL_SNOOZE INTEGER DEFAULT 1")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $TABLE_ALARMS ADD COLUMN $COL_SPECIFIC_DATE INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_ALARMS ADD COLUMN $COL_VIB_PATTERN TEXT DEFAULT 'basic'")
            db.execSQL("ALTER TABLE $TABLE_ALARMS ADD COLUMN $COL_SNOOZE_INT INTEGER DEFAULT 5")
            db.execSQL("ALTER TABLE $TABLE_ALARMS ADD COLUMN $COL_SNOOZE_CNT INTEGER DEFAULT 3")
        }
    }

    fun insertAlarm(alarm: Alarm): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_HOUR, alarm.hour)
            put(COL_MINUTE, alarm.minute)
            put(COL_LABEL, alarm.label)
            put(COL_ENABLED, if (alarm.isEnabled) 1 else 0)
            put(COL_DAYS, alarm.daysOfWeek)
            put(COL_REPEATING, if (alarm.isRepeating) 1 else 0)
            put(COL_RINGTONE, alarm.ringtoneUri)
            put(COL_VIBRATION, if (alarm.isVibrationEnabled) 1 else 0)
            put(COL_SNOOZE, if (alarm.isSnoozeEnabled) 1 else 0)
            put(COL_SPECIFIC_DATE, alarm.specificDate)
            put(COL_VIB_PATTERN, alarm.vibrationPattern)
            put(COL_SNOOZE_INT, alarm.snoozeIntervalMin)
            put(COL_SNOOZE_CNT, alarm.snoozeMaxCount)
        }
        return db.insert(TABLE_ALARMS, null, values)
    }

    fun updateAlarm(alarm: Alarm) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_HOUR, alarm.hour)
            put(COL_MINUTE, alarm.minute)
            put(COL_LABEL, alarm.label)
            put(COL_ENABLED, if (alarm.isEnabled) 1 else 0)
            put(COL_DAYS, alarm.daysOfWeek)
            put(COL_REPEATING, if (alarm.isRepeating) 1 else 0)
            put(COL_RINGTONE, alarm.ringtoneUri)
            put(COL_VIBRATION, if (alarm.isVibrationEnabled) 1 else 0)
            put(COL_SNOOZE, if (alarm.isSnoozeEnabled) 1 else 0)
            put(COL_SPECIFIC_DATE, alarm.specificDate)
            put(COL_VIB_PATTERN, alarm.vibrationPattern)
            put(COL_SNOOZE_INT, alarm.snoozeIntervalMin)
            put(COL_SNOOZE_CNT, alarm.snoozeMaxCount)
        }
        db.update(TABLE_ALARMS, values, "$COL_ID = ?", arrayOf(alarm.id.toString()))
    }

    fun deleteAlarm(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_ALARMS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getAlarm(id: Long): Alarm? {
        val db = readableDatabase
        val cursor = db.query(TABLE_ALARMS, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null)
        return if (cursor.moveToFirst()) {
            val alarm = cursorToAlarm(cursor)
            cursor.close()
            alarm
        } else {
            cursor.close()
            null
        }
    }

    fun getAllAlarms(): List<Alarm> {
        val alarms = mutableListOf<Alarm>()
        val db = readableDatabase
        val cursor = db.query(TABLE_ALARMS, null, null, null, null, null, "$COL_HOUR ASC, $COL_MINUTE ASC")
        while (cursor.moveToNext()) {
            alarms.add(cursorToAlarm(cursor))
        }
        cursor.close()
        return alarms
    }

    fun getEnabledAlarms(): List<Alarm> {
        val alarms = mutableListOf<Alarm>()
        val db = readableDatabase
        val cursor = db.query(TABLE_ALARMS, null, "$COL_ENABLED = 1", null, null, null, null)
        while (cursor.moveToNext()) {
            alarms.add(cursorToAlarm(cursor))
        }
        cursor.close()
        return alarms
    }

    private fun cursorToAlarm(cursor: android.database.Cursor): Alarm {
        val ringtoneIndex = cursor.getColumnIndex(COL_RINGTONE)
        val ringtone = if (ringtoneIndex >= 0) cursor.getString(ringtoneIndex) ?: "" else ""
        
        val vibIndex = cursor.getColumnIndex(COL_VIBRATION)
        val isVib = if (vibIndex >= 0) cursor.getInt(vibIndex) == 1 else true

        val snoozeIndex = cursor.getColumnIndex(COL_SNOOZE)
        val isSnooze = if (snoozeIndex >= 0) cursor.getInt(snoozeIndex) == 1 else true
        
        val dateIndex = cursor.getColumnIndex(COL_SPECIFIC_DATE)
        val specificDate = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L

        val vibPatternIndex = cursor.getColumnIndex(COL_VIB_PATTERN)
        val vibPattern = if (vibPatternIndex >= 0) cursor.getString(vibPatternIndex) ?: "basic" else "basic"

        val snoozeIntIndex = cursor.getColumnIndex(COL_SNOOZE_INT)
        val snoozeInt = if (snoozeIntIndex >= 0) cursor.getInt(snoozeIntIndex) else 5

        val snoozeCntIndex = cursor.getColumnIndex(COL_SNOOZE_CNT)
        val snoozeCnt = if (snoozeCntIndex >= 0) cursor.getInt(snoozeCntIndex) else 3
        
        return Alarm(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            hour = cursor.getInt(cursor.getColumnIndexOrThrow(COL_HOUR)),
            minute = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MINUTE)),
            label = cursor.getString(cursor.getColumnIndexOrThrow(COL_LABEL)) ?: "",
            isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ENABLED)) == 1,
            daysOfWeek = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DAYS)),
            isRepeating = cursor.getInt(cursor.getColumnIndexOrThrow(COL_REPEATING)) == 1,
            ringtoneUri = ringtone,
            isVibrationEnabled = isVib,
            isSnoozeEnabled = isSnooze,
            specificDate = specificDate,
            vibrationPattern = vibPattern,
            snoozeIntervalMin = snoozeInt,
            snoozeMaxCount = snoozeCnt
        )
    }
}

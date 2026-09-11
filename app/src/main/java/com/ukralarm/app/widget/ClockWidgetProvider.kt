package com.ukralarm.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.ukralarm.app.R
import com.ukralarm.app.data.AlarmDatabase
import com.ukralarm.app.ui.MainActivity
import com.ukralarm.app.ui.SettingsActivity

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, ClockWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids != null && ids.isNotEmpty()) {
                    for (id in ids) {
                        updateWidget(context, appWidgetManager, id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_clock)

                // Click pending intent for Main Activity
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val mainPending = PendingIntent.getActivity(
                    context,
                    0,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widgetContainer, mainPending)

                // Click pending intent for Settings Activity (alert info)
                val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val settingsPending = PendingIntent.getActivity(
                    context,
                    1,
                    settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widgetInfoPill, settingsPending)

                // Find next enabled alarm
                val db = AlarmDatabase(context)
                val alarms = db.getEnabledAlarms().sortedWith(compareBy({ it.hour }, { it.minute }))
                if (alarms.isNotEmpty()) {
                    val nextAlarm = alarms.first()
                    views.setTextViewText(R.id.textWidgetAlarm, "⏰ ${nextAlarm.getTimeString()}")
                } else {
                    views.setTextViewText(R.id.textWidgetAlarm, "⏰ --:--")
                }

                // Read region & alert status
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val region = prefs.getString("region", "") ?: ""
                if (region.isEmpty()) {
                    views.setTextViewText(R.id.textWidgetAlert, "Регіон?")
                    views.setTextColor(R.id.textWidgetAlert, Color.parseColor("#9E9E9E"))
                } else {
                    val isAlert = prefs.getBoolean("last_alert_state", false)
                    if (isAlert) {
                        views.setTextViewText(R.id.textWidgetAlert, "🔴 Тривога")
                        views.setTextColor(R.id.textWidgetAlert, Color.parseColor("#F44336"))
                    } else {
                        views.setTextViewText(R.id.textWidgetAlert, "🟢 Відбій")
                        views.setTextColor(R.id.textWidgetAlert, Color.parseColor("#4CAF50"))
                    }
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

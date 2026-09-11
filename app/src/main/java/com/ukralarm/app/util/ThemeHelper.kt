package com.ukralarm.app.util

import android.app.Activity
import android.content.Context
import com.ukralarm.app.R

object ThemeHelper {
    fun applyTheme(activity: Activity) {
        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val theme = prefs.getString("theme", "black") ?: "black"
        
        val isWhite = (theme == "white")
        when (theme) {
            "white" -> activity.setTheme(R.style.Theme_UkrAlarm_White)
            else -> activity.setTheme(R.style.Theme_UkrAlarm_Black)
        }

        try {
            val insetsController = androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            insetsController.isAppearanceLightStatusBars = isWhite
            insetsController.isAppearanceLightNavigationBars = isWhite
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

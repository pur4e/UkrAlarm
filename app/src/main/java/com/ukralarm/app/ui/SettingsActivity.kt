package com.ukralarm.app.ui

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.ukralarm.app.R
import com.ukralarm.app.api.AlertApi
import com.ukralarm.app.data.RegionList
import com.ukralarm.app.util.ThemeTransitionHelper

class SettingsActivity : BaseActivity() {

    private lateinit var scrollSettings: ScrollView
    private lateinit var pillThemeBlack: TextView
    private lateinit var pillThemeWhite: TextView
    private lateinit var pillLangUa: TextView
    private lateinit var pillLangEn: TextView
    private lateinit var pillLangRu: TextView

    private lateinit var btnRowRegion: View
    private lateinit var textSelectedRegion: TextView
    private lateinit var btnRowDistrict: View
    private lateinit var textSelectedDistrict: TextView

    private lateinit var switchPostpone: SwitchMaterial
    private lateinit var textAlertStatus: TextView
    private lateinit var alertIndicator: View
    private lateinit var btnCheckAlert: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        scrollSettings = findViewById(R.id.scrollSettings)
        pillThemeBlack = findViewById(R.id.pillThemeBlack)
        pillThemeWhite = findViewById(R.id.pillThemeWhite)
        pillLangUa = findViewById(R.id.pillLangUa)
        pillLangEn = findViewById(R.id.pillLangEn)
        pillLangRu = findViewById(R.id.pillLangRu)

        val savedScrollY = intent.getIntExtra("EXTRA_SAVED_SCROLL_Y", 0)
        if (savedScrollY > 0) {
            scrollSettings.isSmoothScrollingEnabled = false
            scrollSettings.scrollTo(0, savedScrollY)
            scrollSettings.post {
                scrollSettings.scrollTo(0, savedScrollY)
            }
        }

        btnRowRegion = findViewById(R.id.btnRowRegion)
        textSelectedRegion = findViewById(R.id.textSelectedRegion)
        btnRowDistrict = findViewById(R.id.btnRowDistrict)
        textSelectedDistrict = findViewById(R.id.textSelectedDistrict)

        switchPostpone = findViewById(R.id.switchPostpone)
        textAlertStatus = findViewById(R.id.textAlertStatus)
        alertIndicator = findViewById(R.id.alertIndicator)
        btnCheckAlert = findViewById(R.id.btnCheckAlert)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme", "black") ?: "black"
        val savedLang = prefs.getString("language", "uk") ?: "uk"
        var savedRegion = prefs.getString("region", "") ?: ""
        var savedDistrict = prefs.getString("district", "") ?: ""
        val isPostponeEnabled = prefs.getBoolean("postpone_enabled", true)

        switchPostpone.isChecked = isPostponeEnabled
        switchPostpone.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("postpone_enabled", isChecked).apply()
        }

        // Setup Theme Pills
        updateThemePills(savedTheme)
        pillThemeBlack.setOnClickListener {
            if (savedTheme != "black") {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val currentScroll = scrollSettings.scrollY
                ThemeTransitionHelper.switchThemeWithTransition(this, SettingsActivity::class.java, currentScroll) {
                    prefs.edit().putString("theme", "black").apply()
                }
            }
        }
        pillThemeWhite.setOnClickListener {
            if (savedTheme != "white") {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val currentScroll = scrollSettings.scrollY
                ThemeTransitionHelper.switchThemeWithTransition(this, SettingsActivity::class.java, currentScroll) {
                    prefs.edit().putString("theme", "white").apply()
                }
            }
        }

        // Setup Language Pills
        updateLangPills(savedLang)
        pillLangUa.setOnClickListener {
            if (savedLang != "uk") {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val currentScroll = scrollSettings.scrollY
                ThemeTransitionHelper.switchThemeWithTransition(this, SettingsActivity::class.java, currentScroll) {
                    prefs.edit().putString("language", "uk").apply()
                }
            }
        }
        pillLangEn.setOnClickListener {
            if (savedLang != "en") {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val currentScroll = scrollSettings.scrollY
                ThemeTransitionHelper.switchThemeWithTransition(this, SettingsActivity::class.java, currentScroll) {
                    prefs.edit().putString("language", "en").apply()
                }
            }
        }
        pillLangRu.setOnClickListener {
            if (savedLang != "ru") {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val currentScroll = scrollSettings.scrollY
                ThemeTransitionHelper.switchThemeWithTransition(this, SettingsActivity::class.java, currentScroll) {
                    prefs.edit().putString("language", "ru").apply()
                }
            }
        }

        // Setup Region Row
        textSelectedRegion.text = if (savedRegion.isNotEmpty()) savedRegion else getString(R.string.select_region_hint)
        btnRowRegion.setOnClickListener {
            GlassBottomSheetPicker.show(
                context = this,
                title = getString(R.string.select_region),
                items = RegionList.regions,
                selectedItem = savedRegion,
                enableSearch = true
            ) { selected ->
                if (selected != savedRegion) {
                    savedRegion = selected
                    savedDistrict = ""
                    prefs.edit().putString("region", savedRegion).remove("district").apply()
                    textSelectedRegion.text = savedRegion
                    textSelectedDistrict.text = getString(R.string.select_district_hint)
                    checkAlertStatus()
                }
            }
        }

        // Setup District Row
        textSelectedDistrict.text = if (savedDistrict.isNotEmpty()) savedDistrict else getString(R.string.select_district_hint)
        btnRowDistrict.setOnClickListener {
            if (savedRegion.isEmpty()) {
                Toast.makeText(this, getString(R.string.select_region_hint), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val districts = RegionList.districtsByRegion[savedRegion] ?: emptyList()
            if (districts.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_district), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            GlassBottomSheetPicker.show(
                context = this,
                title = getString(R.string.select_district),
                items = districts,
                selectedItem = savedDistrict,
                enableSearch = true
            ) { selected ->
                savedDistrict = selected
                prefs.edit().putString("district", savedDistrict).apply()
                textSelectedDistrict.text = savedDistrict
                checkAlertStatus()
            }
        }

        btnCheckAlert.setOnClickListener { checkAlertStatus() }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        textAlertStatus.text = getString(if (savedRegion.isEmpty()) R.string.no_region else R.string.alert_inactive)
    }

    private fun updateThemePills(theme: String) {
        pillThemeBlack.isSelected = (theme == "black")
        pillThemeWhite.isSelected = (theme == "white")
    }

    private fun updateLangPills(lang: String) {
        pillLangUa.isSelected = (lang == "uk")
        pillLangEn.isSelected = (lang == "en")
        pillLangRu.isSelected = (lang == "ru")
    }

    private fun checkAlertStatus() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val region = prefs.getString("region", "") ?: ""
        val district = prefs.getString("district", "") ?: ""

        if (region.isEmpty()) {
            textAlertStatus.text = getString(R.string.no_region)
            alertIndicator.setBackgroundResource(R.drawable.circle_indicator)
            return
        }

        btnCheckAlert.isEnabled = false
        btnCheckAlert.text = "..."

        Thread {
            try {
                val isAlert = AlertApi.isAlertActive(region, district)
                prefs.edit().putBoolean("last_alert_state", isAlert).apply()
                runOnUiThread {
                    if (isAlert) {
                        textAlertStatus.text = getString(R.string.alert_active)
                        alertIndicator.setBackgroundResource(0)
                        alertIndicator.setBackgroundColor(resources.getColor(R.color.alert_active, theme))
                    } else {
                        textAlertStatus.text = getString(R.string.alert_inactive)
                        alertIndicator.setBackgroundResource(0)
                        alertIndicator.setBackgroundColor(resources.getColor(R.color.alert_inactive, theme))
                    }
                    btnCheckAlert.isEnabled = true
                    btnCheckAlert.text = getString(R.string.check_alert)
                    com.ukralarm.app.widget.ClockWidgetProvider.updateAllWidgets(this@SettingsActivity)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    textAlertStatus.text = getString(R.string.alert_check_error)
                    btnCheckAlert.isEnabled = true
                    btnCheckAlert.text = getString(R.string.check_alert)
                }
            }
        }.start()
    }
}

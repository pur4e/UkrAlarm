package com.ukralarm.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ukralarm.app.R
import com.ukralarm.app.data.AlarmDatabase
import com.ukralarm.app.model.Alarm
import com.ukralarm.app.util.AlarmScheduler

class MainActivity : BaseActivity() {

    private lateinit var db: AlarmDatabase
    private lateinit var adapter: AlarmAdapter
    private lateinit var emptyText: TextView
    private lateinit var layoutNormalHeader: View
    private lateinit var layoutSelectionHeader: View
    private lateinit var btnCancelSelection: ImageButton
    private lateinit var textSelectionCount: TextView
    private lateinit var btnHeaderSelectAll: TextView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var layoutSelectionDock: View

    private var currentTheme: String = ""
    private var currentLang: String = ""
    private var currentAlarms: List<Alarm> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        currentTheme = prefs.getString("theme", "black") ?: "black"
        currentLang = prefs.getString("language", "uk") ?: "uk"

        db = AlarmDatabase(this)
        emptyText = findViewById(R.id.emptyView)
        layoutNormalHeader = findViewById(R.id.layoutNormalHeader)
        layoutSelectionHeader = findViewById(R.id.layoutSelectionHeader)
        btnCancelSelection = findViewById(R.id.btnCancelSelection)
        textSelectionCount = findViewById(R.id.textSelectionCount)
        btnHeaderSelectAll = findViewById(R.id.btnHeaderSelectAll)
        fabAdd = findViewById(R.id.fabAdd)
        layoutSelectionDock = findViewById(R.id.layoutSelectionDock)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerAlarms)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AlarmAdapter(
            onToggle = { alarm, isChecked ->
                val updated = alarm.copy(isEnabled = isChecked)
                db.updateAlarm(updated)
                if (isChecked) {
                    AlarmScheduler.schedule(this, updated)
                } else {
                    AlarmScheduler.cancel(this, updated.id)
                }
                loadAlarms()
            },
            onClick = { alarm ->
                val intent = Intent(this, AlarmEditActivity::class.java)
                intent.putExtra("ALARM_ID", alarm.id)
                startActivity(intent)
            },
            onLongClick = { _ ->
                enterSelectionMode()
            }
        )
        recyclerView.adapter = adapter

        adapter.onSelectionChanged = { count ->
            updateSelectionUi(count)
        }

        btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }

        btnHeaderSelectAll.setOnClickListener {
            if (adapter.selectedIds.size == currentAlarms.size) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
        }

        findViewById<View>(R.id.btnBatchEnable).setOnClickListener {
            batchToggle(true)
        }

        findViewById<View>(R.id.btnBatchDisable).setOnClickListener {
            batchToggle(false)
        }

        findViewById<View>(R.id.btnBatchDelete).setOnClickListener {
            batchDelete()
        }

        fabAdd.setOnClickListener {
            startActivity(Intent(this, AlarmEditActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun enterSelectionMode() {
        fabAdd.hide()
        layoutNormalHeader.visibility = View.GONE
        layoutSelectionHeader.visibility = View.VISIBLE
        layoutSelectionDock.visibility = View.VISIBLE
        updateSelectionUi(adapter.selectedIds.size)
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        fabAdd.show()
        layoutSelectionHeader.visibility = View.GONE
        layoutNormalHeader.visibility = View.VISIBLE
        layoutSelectionDock.visibility = View.GONE
    }

    private fun updateSelectionUi(selectedCount: Int) {
        textSelectionCount.text = getString(R.string.selected_count, selectedCount)
        if (selectedCount == currentAlarms.size && currentAlarms.isNotEmpty()) {
            btnHeaderSelectAll.text = getString(R.string.deselect_all)
        } else {
            btnHeaderSelectAll.text = getString(R.string.select_all)
        }
    }

    private fun batchToggle(enabled: Boolean) {
        val targets = if (adapter.selectedIds.isEmpty()) currentAlarms else currentAlarms.filter { adapter.selectedIds.contains(it.id) }
        for (alarm in targets) {
            val updated = alarm.copy(isEnabled = enabled)
            db.updateAlarm(updated)
            if (enabled) {
                AlarmScheduler.schedule(this, updated)
            } else {
                AlarmScheduler.cancel(this, updated.id)
            }
        }
        loadAlarms()
        exitSelectionMode()
    }

    private fun batchDelete() {
        val targets = if (adapter.selectedIds.isEmpty()) currentAlarms else currentAlarms.filter { adapter.selectedIds.contains(it.id) }
        for (alarm in targets) {
            AlarmScheduler.cancel(this, alarm.id)
            db.deleteAlarm(alarm.id)
        }
        loadAlarms()
        exitSelectionMode()
    }

    override fun onResume() {
        super.onResume()
        
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val newTheme = prefs.getString("theme", "black") ?: "black"
        val newLang = prefs.getString("language", "uk") ?: "uk"

        if (newTheme != currentTheme || newLang != currentLang) {
            currentTheme = newTheme
            currentLang = newLang
            com.ukralarm.app.util.ThemeTransitionHelper.switchThemeWithTransition(this, MainActivity::class.java) {}
            return
        }

        loadAlarms()
    }

    private fun loadAlarms() {
        currentAlarms = db.getAllAlarms()
        adapter.submitList(currentAlarms)
        if (currentAlarms.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            if (adapter.isSelectionMode) exitSelectionMode()
        } else {
            emptyText.visibility = View.GONE
        }
        com.ukralarm.app.widget.ClockWidgetProvider.updateAllWidgets(this)
    }
}

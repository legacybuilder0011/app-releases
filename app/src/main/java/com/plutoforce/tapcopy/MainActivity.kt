package com.plutoforce.tapcopy

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var enableButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)

        enableButton.setOnClickListener {
            if (isServiceEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                showDisclosureThenOpenSettings()
            }
        }

        findViewById<Button>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun showDisclosureThenOpenSettings() {
        AlertDialog.Builder(this)
            .setTitle("Allow TapCopy to capture text")
            .setMessage(
                "TapCopy uses Android Accessibility only for its core feature: showing a floating button and taking a screenshot after you tap that button. " +
                    "The screenshot is processed locally to recognize visible text. TapCopy does not inspect passwords, automate taps, type messages, or upload your screen."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I understand") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    private fun updateStatus() {
        val enabled = isServiceEnabled()
        statusText.text = if (enabled) getString(R.string.service_enabled) else getString(R.string.service_disabled)
        statusText.setTextColor(Color.parseColor(if (enabled) "#168A52" else "#B42318"))
        enableButton.text = if (enabled) getString(R.string.open_settings) else getString(R.string.enable_service)
    }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                serviceInfo.packageName == packageName &&
                    serviceInfo.name == CopyTextAccessibilityService::class.java.name
            }
    }
}

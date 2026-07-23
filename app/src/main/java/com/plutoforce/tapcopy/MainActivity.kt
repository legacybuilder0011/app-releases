package com.plutoforce.tapcopy

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    private val mainHandler = Handler(Looper.getMainLooper())
    private var checkedThisLaunch = false

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

        findViewById<Button>(R.id.updateButton).setOnClickListener {
            checkForUpdate(userInitiated = true)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (!checkedThisLaunch) {
            checkedThisLaunch = true
            checkForUpdate(userInitiated = false)
        }
    }

    // --- Update flow ---

    private fun checkForUpdate(userInitiated: Boolean) {
        if (userInitiated) toast("Checking for updates…")
        Thread {
            val latest = UpdateChecker.fetchLatest()
            val current = UpdateChecker.currentVersionCode(this)
            mainHandler.post {
                when {
                    latest == null ->
                        if (userInitiated) toast("Couldn't check right now. Try again later.")
                    latest.versionCode > current -> promptUpdate(latest)
                    else ->
                        if (userInitiated) toast("You're on the latest version.")
                }
            }
        }.start()
    }

    private fun promptUpdate(info: UpdateChecker.Info) {
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage("A newer TapCopy (${info.versionName}) is available. Update now?")
            .setNegativeButton("Later", null)
            .setPositiveButton("Update") { _, _ -> startUpdate(info) }
            .show()
    }

    private fun startUpdate(info: UpdateChecker.Info) {
        // Android needs permission for this app to install packages.
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Allow updates")
                .setMessage("To install updates, allow TapCopy to install apps on the next screen, then tap Update again.")
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        toast("Downloading update…")
        Thread {
            val file = UpdateChecker.downloadApk(this, info.apkUrl)
            mainHandler.post {
                if (file == null) {
                    toast("Update download failed. Try again later.")
                } else {
                    UpdateChecker.installApk(this, file)
                }
            }
        }.start()
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

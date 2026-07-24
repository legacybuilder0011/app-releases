package com.plutoforce.tapcopy

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/** Settings — manual update check, accessibility shortcut, privacy note, version. */
class SettingsActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        findViewById<TextView>(R.id.rowUpdates).setOnClickListener { checkForUpdate() }
        findViewById<TextView>(R.id.rowAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: ""
        findViewById<TextView>(R.id.versionText).text = "TapCopy $version"
    }

    private fun checkForUpdate() {
        toast("Checking for updates…")
        Thread {
            val latest = UpdateChecker.fetchLatest()
            val current = UpdateChecker.currentVersionCode(this)
            mainHandler.post {
                when {
                    latest == null -> toast("Couldn't check right now. Try again later.")
                    latest.versionCode > current -> promptUpdate(latest)
                    else -> toast("You're on the latest version.")
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
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Allow updates")
                .setMessage("Allow TapCopy to install apps on the next screen, then tap again.")
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
                if (file == null) toast("Update download failed. Try again later.")
                else UpdateChecker.installApk(this, file)
            }
        }.start()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

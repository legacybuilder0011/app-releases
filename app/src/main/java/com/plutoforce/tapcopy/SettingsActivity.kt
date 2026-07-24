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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Full settings screen. Every control here works on-device; a few advanced
 *  items (translation, extra OCR languages, light theme, accent, text size)
 *  show "coming soon". */
class SettingsActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        wireFloatingButton()
        wireCaptureCopy()
        wireOcrLanguage()
        wirePrivacyStorage()
        wireNotifications()
        wireAppearance()
        wireHelpAbout()
    }

    // 1. Floating button
    private fun wireFloatingButton() {
        val valSize = findViewById<TextView>(R.id.valBtnSize)
        val valOpacity = findViewById<TextView>(R.id.valBtnOpacity)
        valSize.text = SettingsPrefs.buttonSize(this).replaceFirstChar { it.uppercase() }
        valOpacity.text = "${SettingsPrefs.buttonOpacity(this)}%"

        findViewById<android.view.View>(R.id.rowBtnSize).setOnClickListener {
            choice("Button size", arrayOf("Small", "Medium", "Large"), arrayOf("small", "medium", "large"),
                SettingsPrefs.buttonSize(this)) {
                SettingsPrefs.setButtonSize(this, it)
                valSize.text = it.replaceFirstChar { c -> c.uppercase() }
                refreshBubble()
            }
        }
        findViewById<android.view.View>(R.id.rowBtnOpacity).setOnClickListener {
            choice("Button opacity", arrayOf("100%", "80%", "60%", "40%"), arrayOf("100", "80", "60", "40"),
                SettingsPrefs.buttonOpacity(this).toString()) {
                SettingsPrefs.setButtonOpacity(this, it.toInt())
                valOpacity.text = "$it%"
                refreshBubble()
            }
        }
        findViewById<android.view.View>(R.id.rowPositionReset).setOnClickListener {
            SettingsPrefs.resetBubblePos(this)
            refreshBubble()
            toast("Floating button position reset")
        }
        switch(R.id.swAutoHide, SettingsPrefs.autoHide(this)) {
            SettingsPrefs.setAutoHide(this, it); refreshBubble()
        }
    }

    // 2. Capture & copy
    private fun wireCaptureCopy() {
        switch(R.id.swAutoDetect, SettingsPrefs.autoDetectCaptions(this)) {
            SettingsPrefs.setAutoDetectCaptions(this, it)
        }
        val valDefault = findViewById<TextView>(R.id.valDefaultAction)
        valDefault.text = actionLabel(SettingsPrefs.defaultAction(this))
        findViewById<android.view.View>(R.id.rowDefaultAction).setOnClickListener {
            choice("Default action", arrayOf("Ask me", "Copy caption", "Copy all"),
                arrayOf("ask", "caption", "all"), SettingsPrefs.defaultAction(this)) {
                SettingsPrefs.setDefaultAction(this, it); valDefault.text = actionLabel(it)
            }
        }
        val valSel = findViewById<TextView>(R.id.valSelectionMode)
        valSel.text = SettingsPrefs.selectionMode(this).replaceFirstChar { it.uppercase() }
        findViewById<android.view.View>(R.id.rowSelectionMode).setOnClickListener {
            choice("Selection mode", arrayOf("Block", "Line"), arrayOf("block", "line"),
                SettingsPrefs.selectionMode(this)) {
                SettingsPrefs.setSelectionMode(this, it); valSel.text = it.replaceFirstChar { c -> c.uppercase() }
            }
        }
        switch(R.id.swStitch, SettingsPrefs.stitchEnabled(this)) { SettingsPrefs.setStitchEnabled(this, it) }
    }

    private fun actionLabel(v: String) = when (v) {
        "caption" -> "Copy caption"; "all" -> "Copy all"; else -> "Ask me"
    }

    // 3. OCR & language
    private fun wireOcrLanguage() {
        comingSoon(R.id.rowRecogLang)
        comingSoon(R.id.rowTransLang)
        switch(R.id.swImproveCleanup, SettingsPrefs.improveCleanup(this)) {
            SettingsPrefs.setImproveCleanup(this, it)
        }
    }

    // 4. Privacy & storage
    private fun wirePrivacyStorage() {
        switch(R.id.swOnDevice, true) { checked ->
            // OCR is always on-device; keep it on.
            if (!checked) {
                findViewById<Switch>(R.id.swOnDevice).isChecked = true
                toast("Text recognition always stays on your device.")
            }
        }
        switch(R.id.swDeleteShots, SettingsPrefs.deleteScreenshots(this)) {
            SettingsPrefs.setDeleteScreenshots(this, it)
        }
        val valCache = findViewById<TextView>(R.id.valCacheSize)
        valCache.text = cacheSizeText()
        findViewById<android.view.View>(R.id.rowClearCache).setOnClickListener {
            clearCache()
            valCache.text = cacheSizeText()
            toast("Cache cleared")
        }
        val valRet = findViewById<TextView>(R.id.valRetention)
        valRet.text = retentionLabel(SettingsPrefs.retentionDays(this))
        findViewById<android.view.View>(R.id.rowRetention).setOnClickListener {
            choice("Keep copied items for", arrayOf("7 days", "30 days", "90 days", "Forever"),
                arrayOf("7", "30", "90", "0"), SettingsPrefs.retentionDays(this).toString()) {
                val days = it.toInt()
                SettingsPrefs.setRetentionDays(this, days)
                valRet.text = retentionLabel(days)
                if (days > 0) CopyStore.pruneOlderThan(this, days)
            }
        }
    }

    private fun retentionLabel(d: Int) = if (d <= 0) "Forever" else "$d days"

    // 5. Notifications & feedback
    private fun wireNotifications() {
        switch(R.id.swHaptics, SettingsPrefs.haptics(this)) { SettingsPrefs.setHaptics(this, it) }
        switch(R.id.swCopyToast, SettingsPrefs.copyToast(this)) { SettingsPrefs.setCopyToast(this, it) }
        switch(R.id.swSound, SettingsPrefs.soundOnCopy(this)) { SettingsPrefs.setSoundOnCopy(this, it) }
    }

    // 6. Appearance (all coming soon — app ships dark)
    private fun wireAppearance() {
        val dark = findViewById<Switch>(R.id.swDarkMode)
        dark.isChecked = true
        dark.setOnCheckedChangeListener { _, checked ->
            if (!checked) { dark.isChecked = true; toast("Light mode is coming soon.") }
        }
        comingSoon(R.id.rowAccent)
        comingSoon(R.id.rowTextSize)
    }

    // 7. Help & about
    private fun wireHelpAbout() {
        findViewById<android.view.View>(R.id.rowHowItWorks).setOnClickListener {
            info("How TapCopy works",
                "1. Enable the accessibility service.\n2. A floating T button appears over other apps.\n" +
                    "3. Pause a video, tap the T button.\n4. Tap the caption block (or use Clean caption / Copy all).\n\n" +
                    "The screenshot is processed on your device to recognise text. Nothing is uploaded.")
        }
        findViewById<android.view.View>(R.id.rowPrivacy).setOnClickListener {
            info("Privacy",
                "OCR runs on your device and your screen text is never uploaded. TapCopy uses the internet " +
                    "only to check for and download app updates. The temporary screenshot is deleted after use.")
        }
        findViewById<android.view.View>(R.id.rowContact).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@example.com"))
                putExtra(Intent.EXTRA_SUBJECT, "TapCopy feedback")
            }
            runCatching { startActivity(intent) }.onFailure { toast("No email app found") }
        }

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        findViewById<TextView>(R.id.versionText).text = "Current version $version"
        findViewById<android.view.View>(R.id.rowUpdates).setOnClickListener { checkForUpdate() }
    }

    // --- helpers ---

    private fun switch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun comingSoon(rowId: Int) {
        findViewById<android.view.View>(rowId).setOnClickListener { toast("Coming soon") }
    }

    private fun choice(
        title: String, labels: Array<String>, values: Array<String>, current: String, onPick: (String) -> Unit
    ) {
        val checked = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, checked) { d, which ->
                onPick(values[which]); d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun info(title: String, body: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun refreshBubble() {
        sendBroadcast(
            Intent(CopyTextAccessibilityService.ACTION_REFRESH).setPackage(packageName)
        )
    }

    private fun cacheSizeText(): String {
        val bytes = cacheDir.walkBottomUp().filter { it.isFile }.map { it.length() }.sum()
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    private fun clearCache() {
        runCatching {
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            File(getExternalFilesDir(null), "").listFiles()?.forEach { if (it.isFile) it.delete() }
        }
    }

    // Update flow
    private fun checkForUpdate() {
        val status = findViewById<TextView>(R.id.valUpdateStatus)
        status.text = "Checking…"
        Thread {
            val latest = UpdateChecker.fetchLatest()
            val current = UpdateChecker.currentVersionCode(this)
            mainHandler.post {
                when {
                    latest == null -> { status.text = "Check"; toast("Couldn't check right now.") }
                    latest.versionCode > current -> { status.text = "Update"; promptUpdate(latest) }
                    else -> status.text = "Up to date"
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
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
            toast("Allow installs, then tap again")
            return
        }
        toast("Downloading update…")
        Thread {
            val file = UpdateChecker.downloadApk(this, info.apkUrl)
            mainHandler.post {
                if (file == null) toast("Update download failed.") else UpdateChecker.installApk(this, file)
            }
        }.start()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}

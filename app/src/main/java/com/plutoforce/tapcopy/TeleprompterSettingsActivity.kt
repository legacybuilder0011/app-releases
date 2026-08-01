package com.plutoforce.tapcopy

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/**
 * Teleprompter settings. Every control here applies for real — the live preview
 * updates as values change, and the running overlay picks new values up the next
 * time it's opened.
 */
class TeleprompterSettingsActivity : ThemedActivity() {

    private lateinit var previewBox: View
    private lateinit var previewText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter_settings)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        previewBox = findViewById(R.id.previewBox)
        previewText = findViewById(R.id.previewText)

        wireStatusCard()
        wireQuickAccess()
        wireScript()
        wireTextAppearance()
        wireScrolling()
        wirePlayback()
        wirePosition()
        wireReading()
        wirePrivacy()
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshStatusCard()
        findViewById<TextView>(R.id.valScriptCount).text =
            getString(R.string.set_saved_scripts_count, ScriptStore.all(this).size)
    }

    // --- 1. Status card ----------------------------------------------------

    private fun wireStatusCard() {
        findViewById<View>(R.id.enableButton).setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) -> explainThenRequestPermission()
                !TeleprompterPrefs.enabled(this) -> {
                    TeleprompterPrefs.setEnabled(this, true)
                    refreshStatusCard()
                    toast(getString(R.string.prompter_enabled_toast))
                }
                else -> {
                    TeleprompterPrefs.setEnabled(this, false)
                    stopService(Intent(this, TeleprompterService::class.java))
                    refreshStatusCard()
                    toast(getString(R.string.prompter_disabled_toast))
                }
            }
        }

        findViewById<View>(R.id.testButton).setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) -> explainThenRequestPermission()
                !TeleprompterPrefs.enabled(this) -> toast(getString(R.string.prompter_enable_first))
                else -> {
                    startForegroundService(
                        Intent(this, TeleprompterService::class.java)
                            .setAction(TeleprompterService.ACTION_OPEN_EDITOR)
                    )
                    moveTaskToBack(true)
                }
            }
        }
    }

    private fun refreshStatusCard() {
        val granted = Settings.canDrawOverlays(this)
        val enabled = TeleprompterPrefs.enabled(this)
        val status = findViewById<TextView>(R.id.statusLine)
        val button = findViewById<TextView>(R.id.enableButton)

        when {
            !granted -> {
                status.text = getString(R.string.prompter_status_needs_permission)
                status.setTextColor(getColor(R.color.muted))
                button.text = getString(R.string.prompter_allow_overlay)
            }
            !enabled -> {
                status.text = getString(R.string.prompter_status_disabled)
                status.setTextColor(getColor(R.color.muted))
                button.text = getString(R.string.prompter_enable)
            }
            else -> {
                status.text = getString(R.string.prompter_status_active)
                status.setTextColor(getColor(R.color.success))
                button.text = getString(R.string.prompter_active_tap_disable)
            }
        }
    }

    /** Explain why the overlay permission is needed before leaving the app. */
    private fun explainThenRequestPermission() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.prompter_perm_title))
            .setMessage(getString(R.string.prompter_perm_body))
            .setNegativeButton(getString(R.string.prompter_not_now), null)
            .setPositiveButton(getString(R.string.prompter_continue)) { _, _ ->
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }.onFailure { toast(getString(R.string.prompter_open_settings_failed)) }
            }
            .show()
    }

    // --- 2. Quick access + floating button --------------------------------

    private fun wireQuickAccess() {
        val valSize = findViewById<TextView>(R.id.valBtnSize)
        val valOpacity = findViewById<TextView>(R.id.valBtnOpacity)
        valSize.text = SettingsPrefs.buttonSize(this).replaceFirstChar { it.uppercase() }
        valOpacity.text = "${SettingsPrefs.buttonOpacity(this)}%"

        findViewById<View>(R.id.rowBtnSize).setOnClickListener {
            choice(
                getString(R.string.set_btn_size),
                arrayOf("Small", "Medium", "Large"), arrayOf("small", "medium", "large"),
                SettingsPrefs.buttonSize(this)
            ) {
                SettingsPrefs.setButtonSize(this, it)
                valSize.text = it.replaceFirstChar { c -> c.uppercase() }
                refreshBubble()
            }
        }
        findViewById<View>(R.id.rowBtnOpacity).setOnClickListener {
            choice(
                getString(R.string.set_btn_opacity),
                arrayOf("100%", "80%", "70%", "60%", "40%"),
                arrayOf("100", "80", "70", "60", "40"),
                SettingsPrefs.buttonOpacity(this).toString()
            ) {
                SettingsPrefs.setButtonOpacity(this, it.toInt())
                valOpacity.text = "$it%"
                refreshBubble()
            }
        }
        switch(R.id.swAutoHide, SettingsPrefs.autoHide(this)) {
            SettingsPrefs.setAutoHide(this, it); refreshBubble()
        }
        findViewById<View>(R.id.rowResetButtonPos).setOnClickListener {
            SettingsPrefs.resetBubblePos(this)
            refreshBubble()
            toast(getString(R.string.set_btn_pos_reset))
        }
    }

    // --- 3. Script --------------------------------------------------------

    private fun wireScript() {
        switch(R.id.swUseLastCopied, TeleprompterPrefs.useLastCopied(this)) {
            TeleprompterPrefs.setUseLastCopied(this, it)
        }
        switch(R.id.swRememberUnfinished, TeleprompterPrefs.rememberUnfinished(this)) {
            TeleprompterPrefs.setRememberUnfinished(this, it)
        }
        switch(R.id.swSaveHistory, TeleprompterPrefs.saveToHistory(this)) {
            TeleprompterPrefs.setSaveToHistory(this, it)
        }
        switch(R.id.swClearAfter, TeleprompterPrefs.clearAfterFinish(this)) {
            TeleprompterPrefs.setClearAfterFinish(this, it)
        }
        findViewById<View>(R.id.rowScripts).setOnClickListener { showScriptManager() }
    }

    private fun showScriptManager() {
        val scripts = ScriptStore.all(this)
        if (scripts.isEmpty()) {
            info(getString(R.string.set_saved_scripts), getString(R.string.prompter_no_saved))
            return
        }
        val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val labels = scripts.map { s ->
            val star = if (s.favorite) "★ " else ""
            val seconds = TeleprompterPrefs.estimatedSeconds(this, s.text)
            "$star${s.title}\n${s.wordCount} words · ${TeleprompterPrefs.formatDuration(seconds)} · " +
                df.format(Date(s.createdAt))
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_saved_scripts))
            .setItems(labels) { _, which -> scriptActions(scripts[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun scriptActions(script: ScriptStore.Script) {
        val favLabel = if (script.favorite) R.string.script_unfavourite else R.string.script_favourite
        val actions = arrayOf(
            getString(R.string.script_rename),
            getString(R.string.script_duplicate),
            getString(favLabel),
            getString(R.string.script_delete)
        )
        AlertDialog.Builder(this)
            .setTitle(script.title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> renameScript(script)
                    1 -> {
                        ScriptStore.duplicate(this, script.id); onResume()
                        toast(getString(R.string.script_duplicated))
                    }
                    2 -> { ScriptStore.toggleFavorite(this, script.id); onResume() }
                    3 -> confirmDeleteScript(script)
                }
            }
            .show()
    }

    private fun renameScript(script: ScriptStore.Script) {
        val input = EditText(this).apply {
            setText(script.title)
            setSelection(text.length)
            setTextColor(getColor(R.color.ink))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.script_rename))
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    ScriptStore.rename(this, script.id, name)
                    onResume()
                }
            }
            .show()
    }

    private fun confirmDeleteScript(script: ScriptStore.Script) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.script_delete))
            .setMessage(getString(R.string.script_delete_confirm, script.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.script_delete)) { _, _ ->
                ScriptStore.delete(this, script.id)
                onResume()
            }
            .show()
    }

    // --- 4. Text appearance ------------------------------------------------

    private fun wireTextAppearance() {
        val valTextSize = findViewById<TextView>(R.id.valTextSize)
        val seekSize = findViewById<SeekBar>(R.id.seekTextSize)
        valTextSize.text = "${TeleprompterPrefs.textSize(this)}sp"
        seekSize.progress = (TeleprompterPrefs.textSize(this) - MIN_TEXT_SIZE).coerceIn(0, seekSize.max)
        seekSize.setOnSeekBarChangeListener(seek { progress ->
            val size = MIN_TEXT_SIZE + progress
            TeleprompterPrefs.setTextSize(this, size)
            valTextSize.text = "${size}sp"
            refreshPreview()
        })

        val valSpacing = findViewById<TextView>(R.id.valLineSpacing)
        valSpacing.text = spacingLabel(TeleprompterPrefs.lineSpacing(this))
        findViewById<View>(R.id.rowLineSpacing).setOnClickListener {
            choice(
                getString(R.string.set_line_spacing),
                arrayOf("Tight", "Comfortable", "Relaxed"),
                arrayOf("tight", "comfortable", "relaxed"),
                TeleprompterPrefs.lineSpacing(this)
            ) {
                TeleprompterPrefs.setLineSpacing(this, it)
                valSpacing.text = spacingLabel(it)
                refreshPreview()
            }
        }

        val valAlign = findViewById<TextView>(R.id.valAlignment)
        valAlign.text = alignLabel(TeleprompterPrefs.alignment(this))
        findViewById<View>(R.id.rowAlignment).setOnClickListener {
            choice(
                getString(R.string.set_alignment),
                arrayOf("Left", "Centre", "Right"), arrayOf("left", "centre", "right"),
                TeleprompterPrefs.alignment(this)
            ) {
                TeleprompterPrefs.setAlignment(this, it)
                valAlign.text = alignLabel(it)
                refreshPreview()
            }
        }

        switch(R.id.swHighlight, TeleprompterPrefs.highlightCurrentLine(this)) {
            TeleprompterPrefs.setHighlightCurrentLine(this, it); refreshPreview()
        }
        switch(R.id.swDimPrevious, TeleprompterPrefs.dimPrevious(this)) {
            TeleprompterPrefs.setDimPrevious(this, it); refreshPreview()
        }

        val valOpacity = findViewById<TextView>(R.id.valOpacity)
        val seekOpacity = findViewById<SeekBar>(R.id.seekOpacity)
        valOpacity.text = "${TeleprompterPrefs.backgroundOpacity(this)}%"
        seekOpacity.progress = TeleprompterPrefs.backgroundOpacity(this)
        seekOpacity.setOnSeekBarChangeListener(seek { progress ->
            TeleprompterPrefs.setBackgroundOpacity(this, progress)
            valOpacity.text = "$progress%"
            refreshPreview()
        })

        val valWidth = findViewById<TextView>(R.id.valBoxWidth)
        val seekWidth = findViewById<SeekBar>(R.id.seekBoxWidth)
        valWidth.text = "${TeleprompterPrefs.boxWidth(this)}%"
        seekWidth.progress = TeleprompterPrefs.boxWidth(this)
        seekWidth.setOnSeekBarChangeListener(seek { progress ->
            val width = progress.coerceAtLeast(MIN_BOX_WIDTH)
            TeleprompterPrefs.setBoxWidth(this, width)
            valWidth.text = "$width%"
        })

        val valHeight = findViewById<TextView>(R.id.valBoxHeight)
        valHeight.text = sizeLabel(TeleprompterPrefs.boxHeight(this))
        findViewById<View>(R.id.rowBoxHeight).setOnClickListener {
            choice(
                getString(R.string.set_box_height),
                arrayOf("Small", "Medium", "Large"), arrayOf("small", "medium", "large"),
                TeleprompterPrefs.boxHeight(this)
            ) {
                TeleprompterPrefs.setBoxHeight(this, it)
                valHeight.text = sizeLabel(it)
            }
        }

        switch(R.id.swMirror, TeleprompterPrefs.mirrorText(this)) {
            TeleprompterPrefs.setMirrorText(this, it); refreshPreview()
        }
    }

    /** Shows the current text settings exactly as they'll look while reading. */
    private fun refreshPreview() {
        val dyslexia = TeleprompterPrefs.dyslexiaFriendly(this)
        val size = TeleprompterPrefs.textSize(this) + if (dyslexia) 4 else 0
        previewText.textSize = size.toFloat()
        val spacing = if (dyslexia) {
            maxOf(1.6f, TeleprompterPrefs.lineSpacingMultiplier(this))
        } else {
            TeleprompterPrefs.lineSpacingMultiplier(this)
        }
        previewText.setLineSpacing(0f, spacing)
        previewText.letterSpacing = if (dyslexia) 0.03f else 0f
        previewText.gravity = when (TeleprompterPrefs.alignment(this)) {
            "centre" -> Gravity.CENTER_HORIZONTAL
            "right" -> Gravity.END
            else -> Gravity.START
        }
        previewText.scaleX = if (TeleprompterPrefs.mirrorText(this)) -1f else 1f

        // Mirror the reading emphasis: the middle line bright, the rest dimmed.
        val lines = getString(R.string.prompter_preview_text).lines()
        val highlight = TeleprompterPrefs.highlightCurrentLine(this)
        val dim = TeleprompterPrefs.dimPrevious(this)
        val builder = android.text.SpannableStringBuilder()
        lines.forEachIndexed { index, line ->
            val start = builder.length
            builder.append(line)
            if (index < lines.lastIndex) builder.append("\n")
            val color = when {
                index == 1 && highlight -> Color.WHITE
                index < 1 && dim -> Color.argb(115, 255, 255, 255)
                index > 1 && highlight -> Color.argb(140, 255, 255, 255)
                else -> Color.WHITE
            }
            builder.setSpan(
                android.text.style.ForegroundColorSpan(color),
                start, builder.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        previewText.text = builder

        val opacity = TeleprompterPrefs.backgroundOpacity(this).coerceIn(0, 100)
        previewBox.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18 * resources.displayMetrics.density
            setColor(Color.argb(opacity * 255 / 100, 12, 12, 18))
        }
    }

    // --- 5. Scrolling ------------------------------------------------------

    private fun wireScrolling() {
        val valSpeed = findViewById<TextView>(R.id.valSpeed)
        val hint = findViewById<TextView>(R.id.valSpeedHint)
        val seekSpeed = findViewById<SeekBar>(R.id.seekSpeed)
        fun showSpeed(wpm: Int) {
            valSpeed.text = getString(R.string.prompter_wpm_short, wpm)
            hint.text = getString(R.string.set_speed_hint, wpm)
        }
        showSpeed(TeleprompterPrefs.speedWpm(this))
        seekSpeed.progress = (TeleprompterPrefs.speedWpm(this) - MIN_WPM).coerceIn(0, seekSpeed.max)
        seekSpeed.setOnSeekBarChangeListener(seek { progress ->
            val wpm = MIN_WPM + progress
            TeleprompterPrefs.setSpeedWpm(this, wpm)
            showSpeed(TeleprompterPrefs.speedWpm(this))
        })

        val valCountdown = findViewById<TextView>(R.id.valCountdown)
        valCountdown.text = countdownLabel(TeleprompterPrefs.countdownSeconds(this))
        findViewById<View>(R.id.rowCountdown).setOnClickListener {
            choice(
                getString(R.string.set_countdown),
                arrayOf("No countdown", "3 seconds", "5 seconds", "10 seconds"),
                arrayOf("0", "3", "5", "10"),
                TeleprompterPrefs.countdownSeconds(this).toString()
            ) {
                TeleprompterPrefs.setCountdownSeconds(this, it.toInt())
                valCountdown.text = countdownLabel(it.toInt())
            }
        }

        switch(R.id.swPauseParagraphs, TeleprompterPrefs.pauseAtParagraphs(this)) {
            TeleprompterPrefs.setPauseAtParagraphs(this, it)
        }

        val valPause = findViewById<TextView>(R.id.valParagraphPause)
        valPause.text = tenthsLabel(TeleprompterPrefs.paragraphPauseTenths(this))
        findViewById<View>(R.id.rowParagraphPause).setOnClickListener {
            choice(
                getString(R.string.set_paragraph_pause),
                arrayOf("0.3 sec", "0.5 sec", "1.0 sec", "1.5 sec"),
                arrayOf("3", "5", "10", "15"),
                TeleprompterPrefs.paragraphPauseTenths(this).toString()
            ) {
                TeleprompterPrefs.setParagraphPauseTenths(this, it.toInt())
                valPause.text = tenthsLabel(it.toInt())
            }
        }

        switch(R.id.swLoop, TeleprompterPrefs.loopScript(this)) {
            TeleprompterPrefs.setLoopScript(this, it)
        }
        switch(R.id.swStartLast, TeleprompterPrefs.startFromLastPosition(this)) {
            TeleprompterPrefs.setStartFromLastPosition(this, it)
        }
    }

    // --- 6. Playback ------------------------------------------------------

    private fun wirePlayback() {
        switch(R.id.swHideControls, TeleprompterPrefs.hideControls(this)) {
            TeleprompterPrefs.setHideControls(this, it)
        }
        switch(R.id.swTapPause, TeleprompterPrefs.tapToPause(this)) {
            TeleprompterPrefs.setTapToPause(this, it)
        }
        switch(R.id.swLongPressControls, TeleprompterPrefs.longPressControls(this)) {
            TeleprompterPrefs.setLongPressControls(this, it)
        }
        switch(R.id.swDragPaused, TeleprompterPrefs.dragWhilePaused(this)) {
            TeleprompterPrefs.setDragWhilePaused(this, it)
        }
        switch(R.id.swKeepAwake, TeleprompterPrefs.keepScreenAwake(this)) {
            TeleprompterPrefs.setKeepScreenAwake(this, it)
        }
        switch(R.id.swConfirmRestart, TeleprompterPrefs.confirmRestart(this)) {
            TeleprompterPrefs.setConfirmRestart(this, it)
        }
    }

    // --- 7. Position & size ------------------------------------------------

    private fun wirePosition() {
        val valPos = findViewById<TextView>(R.id.valDefaultPosition)
        valPos.text = positionLabel(TeleprompterPrefs.defaultPosition(this))
        findViewById<View>(R.id.rowDefaultPosition).setOnClickListener {
            choice(
                getString(R.string.set_default_position),
                arrayOf("Top (near camera)", "Middle", "Bottom"),
                arrayOf("top", "middle", "bottom"),
                TeleprompterPrefs.defaultPosition(this)
            ) {
                TeleprompterPrefs.setDefaultPosition(this, it)
                valPos.text = positionLabel(it)
            }
        }
        switch(R.id.swAllowDrag, TeleprompterPrefs.allowDragging(this)) {
            TeleprompterPrefs.setAllowDragging(this, it)
        }
        switch(R.id.swRememberPosition, TeleprompterPrefs.rememberPosition(this)) {
            TeleprompterPrefs.setRememberPosition(this, it)
        }
        findViewById<View>(R.id.rowResetPosition).setOnClickListener {
            TeleprompterPrefs.resetPositions(this)
            toast(getString(R.string.set_position_reset))
        }
    }

    // --- 8. Reading assistance --------------------------------------------

    private fun wireReading() {
        switch(R.id.swFewerWords, TeleprompterPrefs.fewerWordsPerLine(this)) {
            TeleprompterPrefs.setFewerWordsPerLine(this, it)
        }
        switch(R.id.swDyslexia, TeleprompterPrefs.dyslexiaFriendly(this)) {
            TeleprompterPrefs.setDyslexiaFriendly(this, it)
            refreshPreview()
        }
        switch(R.id.swEmphasizePunct, TeleprompterPrefs.emphasizePunctuation(this)) {
            TeleprompterPrefs.setEmphasizePunctuation(this, it)
        }
        switch(R.id.swAutoParagraphs, TeleprompterPrefs.autoParagraphBreaks(this)) {
            TeleprompterPrefs.setAutoParagraphBreaks(this, it)
        }
    }

    // --- 9. Privacy -------------------------------------------------------

    private fun wirePrivacy() {
        // Scripts are always local; the switch stays on to make that explicit.
        val keep = findViewById<Switch>(R.id.swKeepDevice)
        keep.isChecked = true
        keep.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                keep.isChecked = true
                toast(getString(R.string.set_keep_device_locked))
            }
        }

        val valDraft = findViewById<TextView>(R.id.valDraftDelete)
        valDraft.text = draftLabel(TeleprompterPrefs.autoDeleteDraftDays(this))
        findViewById<View>(R.id.rowDraftDelete).setOnClickListener {
            choice(
                getString(R.string.set_auto_delete_drafts),
                arrayOf("Never", "After 1 day", "After 7 days", "After 30 days"),
                arrayOf("0", "1", "7", "30"),
                TeleprompterPrefs.autoDeleteDraftDays(this).toString()
            ) {
                TeleprompterPrefs.setAutoDeleteDraftDays(this, it.toInt())
                valDraft.text = draftLabel(it.toInt())
            }
        }

        switch(R.id.swHideLock, TeleprompterPrefs.hideOnLockScreen(this)) {
            TeleprompterPrefs.setHideOnLockScreen(this, it)
        }
        switch(R.id.swHideSensitive, TeleprompterPrefs.hideOverSensitiveApps(this)) {
            TeleprompterPrefs.setHideOverSensitiveApps(this, it)
        }
    }

    // --- Labels & helpers -------------------------------------------------

    private fun spacingLabel(v: String) = when (v) {
        "tight" -> "Tight"; "relaxed" -> "Relaxed"; else -> "Comfortable"
    }

    private fun alignLabel(v: String) = when (v) {
        "centre" -> "Centre"; "right" -> "Right"; else -> "Left"
    }

    private fun sizeLabel(v: String) = when (v) {
        "small" -> "Small"; "large" -> "Large"; else -> "Medium"
    }

    private fun positionLabel(v: String) = when (v) {
        "middle" -> "Middle"; "bottom" -> "Bottom"; else -> "Top"
    }

    private fun countdownLabel(v: Int) = if (v <= 0) "Off" else "$v seconds"

    private fun tenthsLabel(v: Int) = String.format("%.1f sec", v / 10f)

    private fun draftLabel(days: Int) = if (days <= 0) "Never" else "After $days days"

    private fun switch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun seek(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) =
            onProgress(progress)

        override fun onStartTrackingTouch(bar: SeekBar?) = Unit
        override fun onStopTrackingTouch(bar: SeekBar?) = Unit
    }

    private fun choice(
        title: String, labels: Array<String>, values: Array<String>, current: String,
        onPick: (String) -> Unit
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
        sendBroadcast(Intent(CopyTextAccessibilityService.ACTION_REFRESH).setPackage(packageName))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val MIN_TEXT_SIZE = 18   // seekTextSize max 40 → 18..58 sp
        const val MIN_WPM = 50         // seekSpeed max 200 → 50..250 wpm
        const val MIN_BOX_WIDTH = 40
    }
}

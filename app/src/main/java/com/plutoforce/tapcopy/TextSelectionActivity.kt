package com.plutoforce.tapcopy

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class TextSelectionActivity : Activity() {

    companion object {
        const val EXTRA_SCREENSHOT_PATH = "screenshot_path"
    }

    private lateinit var overlay: TextOverlayView
    private lateinit var instructionText: TextView
    private lateinit var copySelectedButton: Button
    private lateinit var copyAllButton: Button
    private lateinit var stitchButton: Button
    private lateinit var addSelectedButton: Button
    private lateinit var addScreenButton: Button
    private lateinit var finishButton: Button
    private lateinit var normalButtons: LinearLayout
    private lateinit var stitchButtons: LinearLayout
    private lateinit var categoryButtons: List<Button>

    private var recognizedText: String = ""
    private var textReady: Boolean = false
    private var screenshotPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_selection)

        overlay = findViewById(R.id.textOverlay)
        instructionText = findViewById(R.id.instructionText)
        copySelectedButton = findViewById(R.id.copySelectedButton)
        copyAllButton = findViewById(R.id.copyAllButton)
        stitchButton = findViewById(R.id.stitchButton)
        addSelectedButton = findViewById(R.id.addSelectedButton)
        addScreenButton = findViewById(R.id.addScreenButton)
        finishButton = findViewById(R.id.finishButton)
        normalButtons = findViewById(R.id.normalButtons)
        stitchButtons = findViewById(R.id.stitchButtons)

        findViewById<Button>(R.id.closeButton).setOnClickListener { closeAndCleanUp() }
        findViewById<Button>(R.id.cancelStitchButton).setOnClickListener {
            StitchBuffer.clear(this)
            closeAndCleanUp()
        }

        copySelectedButton.setOnClickListener { copySelection() }
        copyAllButton.setOnClickListener {
            if (recognizedText.isNotBlank()) copyAndReturn(recognizedText)
        }
        stitchButton.setOnClickListener {
            StitchBuffer.start(this)
            renderMode()
        }
        addSelectedButton.setOnClickListener {
            val text = overlay.selectedText()
            if (text.isBlank()) toast(getString(R.string.need_selection)) else stitchAddAndReturn(text)
        }
        addScreenButton.setOnClickListener {
            if (recognizedText.isNotBlank()) stitchAddAndReturn(recognizedText)
        }
        finishButton.setOnClickListener { finishStitch() }

        val btnCaption = findViewById<Button>(R.id.btnCaption)
        val btnHashtags = findViewById<Button>(R.id.btnHashtags)
        val btnMentions = findViewById<Button>(R.id.btnMentions)
        val btnLinks = findViewById<Button>(R.id.btnLinks)
        val btnEmails = findViewById<Button>(R.id.btnEmails)
        val btnPhones = findViewById<Button>(R.id.btnPhones)
        categoryButtons = listOf(btnCaption, btnHashtags, btnMentions, btnLinks, btnEmails, btnPhones)

        btnCaption.setOnClickListener { copyCaption() }
        btnHashtags.setOnClickListener {
            copyExtracted("hashtags") { TextExtractor.hashtags(it).joinToString(" ") }
        }
        btnMentions.setOnClickListener {
            copyExtracted("mentions") { TextExtractor.mentions(it).joinToString(" ") }
        }
        btnLinks.setOnClickListener {
            copyExtracted("links") { TextExtractor.links(it).joinToString("\n") }
        }
        btnEmails.setOnClickListener {
            copyExtracted("emails") { TextExtractor.emails(it).joinToString("\n") }
        }
        btnPhones.setOnClickListener {
            copyExtracted("phone numbers") { TextExtractor.phones(it).joinToString("\n") }
        }

        screenshotPath = intent.getStringExtra(EXTRA_SCREENSHOT_PATH)
        val path = screenshotPath
        if (path.isNullOrBlank()) {
            toast("Screenshot not found")
            finish()
            return
        }

        val bitmap = BitmapFactory.decodeFile(path)
        if (bitmap == null) {
            toast("Could not open screenshot")
            closeAndCleanUp()
            return
        }

        overlay.setScreenshot(bitmap)
        overlay.onSelectionChanged = { count -> updateSelectionButtons(count) }

        renderMode()
        updateSelectionButtons(0)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                recognizedText = result.text.trim()
                textReady = recognizedText.isNotBlank()
                val regions = if (SettingsPrefs.selectionMode(this) == "line") {
                    result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        val text = line.text.trim()
                        if (text.isBlank()) null else TextOverlayView.TextRegion(text, box)
                    }
                } else {
                    result.textBlocks.mapNotNull { block ->
                        val box = block.boundingBox ?: return@mapNotNull null
                        val text = block.text.trim()
                        if (text.isBlank()) null else TextOverlayView.TextRegion(text, box)
                    }
                }
                overlay.setRegions(regions)
                if (SettingsPrefs.autoDetectCaptions(this)) overlay.selectCaption()
                renderMode()
                updateSelectionButtons(overlay.selectedCount())
                applyDefaultAction()
            }
            .addOnFailureListener { error ->
                textReady = false
                instructionText.text =
                    "Text recognition failed: ${error.localizedMessage ?: "unknown error"}"
                renderMode()
            }
            .addOnCompleteListener {
                recognizer.close()
            }
    }

    override fun onBackPressed() {
        closeAndCleanUp()
    }

    /** Toggles button rows and instructions between normal and stitch modes. */
    private fun renderMode() {
        val stitching = StitchBuffer.isActive(this)
        normalButtons.visibility = if (stitching) View.GONE else View.VISIBLE
        stitchButtons.visibility = if (stitching) View.VISIBLE else View.GONE

        copyAllButton.isEnabled = textReady
        stitchButton.isEnabled = textReady
        stitchButton.visibility = if (SettingsPrefs.stitchEnabled(this)) View.VISIBLE else View.GONE
        addScreenButton.isEnabled = textReady
        categoryButtons.forEach { it.isEnabled = textReady }

        val pieces = StitchBuffer.count(this)
        finishButton.text = if (pieces > 0) {
            "${getString(R.string.stitch_finish)} ($pieces)"
        } else {
            getString(R.string.stitch_finish)
        }
        finishButton.isEnabled = pieces > 0

        instructionText.text = when {
            !textReady && !stitching -> "No readable text found. Try pausing the video first."
            stitching && pieces > 0 -> getString(R.string.stitch_hint, pieces)
            stitching -> getString(R.string.stitch_hint_empty)
            else -> getString(R.string.tap_text)
        }
    }

    /** Reflects how many blocks are ticked in the Copy/Add-selected buttons. */
    private fun updateSelectionButtons(count: Int) {
        val hasSelection = count > 0
        copySelectedButton.isEnabled = hasSelection
        addSelectedButton.isEnabled = hasSelection

        copySelectedButton.text = if (hasSelection) {
            "${getString(R.string.copy_selected)} ($count)"
        } else {
            getString(R.string.copy_selected)
        }
        addSelectedButton.text = if (hasSelection) {
            "${getString(R.string.add_selected)} ($count)"
        } else {
            getString(R.string.add_selected)
        }
    }

    private fun copySelection() {
        val text = overlay.selectedText()
        if (text.isBlank()) {
            toast(getString(R.string.need_selection))
            return
        }
        copyAndReturn(text)
    }

    /** Selected blocks if any are ticked, otherwise the whole screen's text. */
    private fun sourceText(): String {
        val selected = overlay.selectedText()
        return if (selected.isNotBlank()) selected else recognizedText
    }

    /**
     * Clean caption: use ticked blocks if any; otherwise auto-pick the caption
     * block (longest prose) rather than the whole screen, then strip clutter.
     */
    private fun copyCaption() {
        val selected = overlay.selectedText()
        val source = when {
            selected.isNotBlank() -> selected
            overlay.bestCaptionText().isNotBlank() -> overlay.bestCaptionText()
            else -> recognizedText
        }
        val result = TextExtractor.cleanCaption(source).trim()
        if (result.isBlank()) {
            toast(getString(R.string.none_found, getString(R.string.cat_caption)))
            return
        }
        copyAndReturn(result)
    }

    private fun copyExtracted(label: String, extract: (String) -> String) {
        val src = sourceText()
        if (src.isBlank()) {
            toast("No readable text yet")
            return
        }
        val result = extract(src).trim()
        if (result.isBlank()) {
            toast(getString(R.string.none_found, label))
            return
        }
        copyAndReturn(result)
    }

    private fun stitchAddAndReturn(text: String) {
        StitchBuffer.append(this, text)
        toast(getString(R.string.stitch_added, StitchBuffer.count(this)))
        // Return to the previous app so the user can scroll and capture the next part.
        closeAndCleanUp()
    }

    private fun finishStitch() {
        val collected = StitchBuffer.text(this).trim()
        if (collected.isBlank()) {
            toast(getString(R.string.stitch_empty_copy))
            return
        }
        copyToClipboard(collected)
        StitchBuffer.clear(this)
        toast("Copied to clipboard")
        window.decorView.postDelayed({ closeAndCleanUp() }, 250L)
    }

    /** Performs the user's chosen default action once text is ready. */
    private fun applyDefaultAction() {
        if (!textReady) return
        when (SettingsPrefs.defaultAction(this)) {
            "caption" -> copyCaption()
            "all" -> if (recognizedText.isNotBlank()) copyAndReturn(recognizedText)
            else -> Unit
        }
    }

    private fun copyAndReturn(text: String) {
        var clean = text.trim()
        if (clean.isBlank()) return
        if (SettingsPrefs.improveCleanup(this)) clean = TextExtractor.tidy(clean)
        copyToClipboard(clean)
        onCopiedFeedback()
        window.decorView.postDelayed({ closeAndCleanUp() }, 250L)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Copied screen text", text))
        CopyStore.add(this, text)
    }

    /** Haptic / sound / toast feedback per the user's settings. */
    private fun onCopiedFeedback() {
        if (SettingsPrefs.haptics(this)) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") vibrator?.vibrate(30)
                }
            }
        }
        if (SettingsPrefs.soundOnCopy(this)) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_SYSTEM, 80)
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                window.decorView.postDelayed({ runCatching { tone.release() } }, 300)
            }
        }
        if (SettingsPrefs.copyToast(this)) toast(getString(R.string.copied))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun closeAndCleanUp() {
        if (SettingsPrefs.deleteScreenshots(this)) {
            screenshotPath?.let { path -> runCatching { File(path).delete() } }
        }
        finishAndRemoveTask()
    }
}

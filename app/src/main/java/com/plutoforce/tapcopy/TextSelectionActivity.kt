package com.plutoforce.tapcopy

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.os.Bundle
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
                val regions = result.textBlocks.mapNotNull { block ->
                    val box = block.boundingBox ?: return@mapNotNull null
                    val text = block.text.trim()
                    if (text.isBlank()) null else TextOverlayView.TextRegion(text, box)
                }
                overlay.setRegions(regions)
                renderMode()
                updateSelectionButtons(overlay.selectedCount())
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
        addScreenButton.isEnabled = textReady

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

    private fun copyAndReturn(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        copyToClipboard(cleanText)
        toast("Copied to clipboard")
        window.decorView.postDelayed({ closeAndCleanUp() }, 250L)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Copied screen text", text))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun closeAndCleanUp() {
        screenshotPath?.let { path -> runCatching { File(path).delete() } }
        finishAndRemoveTask()
    }
}

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
    private lateinit var copyAllButton: Button
    private lateinit var stitchButton: Button
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
        copyAllButton = findViewById(R.id.copyAllButton)
        stitchButton = findViewById(R.id.stitchButton)
        addScreenButton = findViewById(R.id.addScreenButton)
        finishButton = findViewById(R.id.finishButton)
        normalButtons = findViewById(R.id.normalButtons)
        stitchButtons = findViewById(R.id.stitchButtons)

        findViewById<Button>(R.id.closeButton).setOnClickListener { closeAndCleanUp() }
        findViewById<Button>(R.id.cancelStitchButton).setOnClickListener {
            StitchBuffer.clear(this)
            closeAndCleanUp()
        }

        copyAllButton.setOnClickListener {
            if (recognizedText.isNotBlank()) copyAndReturn(recognizedText)
        }
        stitchButton.setOnClickListener {
            StitchBuffer.start(this)
            renderMode()
        }
        addScreenButton.setOnClickListener {
            if (recognizedText.isNotBlank()) stitchAddAndReturn(recognizedText)
        }
        finishButton.setOnClickListener { finishStitch() }

        screenshotPath = intent.getStringExtra(EXTRA_SCREENSHOT_PATH)
        val path = screenshotPath
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "Screenshot not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val bitmap = BitmapFactory.decodeFile(path)
        if (bitmap == null) {
            Toast.makeText(this, "Could not open screenshot", Toast.LENGTH_SHORT).show()
            closeAndCleanUp()
            return
        }

        overlay.setScreenshot(bitmap)
        overlay.onRegionTapped = { region ->
            if (StitchBuffer.isActive(this)) stitchAddAndReturn(region.text)
            else copyAndReturn(region.text)
        }

        renderMode()

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

    /** Updates buttons and instructions for the current (normal vs. stitch) mode. */
    private fun renderMode() {
        val stitching = StitchBuffer.isActive(this)
        normalButtons.visibility = if (stitching) View.GONE else View.VISIBLE
        stitchButtons.visibility = if (stitching) View.VISIBLE else View.GONE

        copyAllButton.isEnabled = textReady
        stitchButton.isEnabled = textReady
        addScreenButton.isEnabled = textReady

        val count = StitchBuffer.count(this)
        finishButton.text = if (count > 0) {
            "${getString(R.string.stitch_finish)} ($count)"
        } else {
            getString(R.string.stitch_finish)
        }
        finishButton.isEnabled = count > 0

        instructionText.text = when {
            !textReady && !stitching ->
                "No readable text found. Try pausing the video first."
            stitching && count > 0 -> getString(R.string.stitch_hint, count)
            stitching -> getString(R.string.stitch_hint_empty)
            else -> getString(R.string.tap_text)
        }
    }

    private fun stitchAddAndReturn(text: String) {
        StitchBuffer.append(this, text)
        Toast.makeText(
            this,
            getString(R.string.stitch_added, StitchBuffer.count(this)),
            Toast.LENGTH_SHORT
        ).show()
        // Return to the previous app so the user can scroll and capture the next part.
        closeAndCleanUp()
    }

    private fun finishStitch() {
        val collected = StitchBuffer.text(this).trim()
        if (collected.isBlank()) {
            Toast.makeText(this, getString(R.string.stitch_empty_copy), Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(collected)
        StitchBuffer.clear(this)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        window.decorView.postDelayed({ closeAndCleanUp() }, 250L)
    }

    private fun copyAndReturn(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        copyToClipboard(cleanText)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        window.decorView.postDelayed({ closeAndCleanUp() }, 250L)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Copied screen text", text))
    }

    private fun closeAndCleanUp() {
        screenshotPath?.let { path -> runCatching { File(path).delete() } }
        finishAndRemoveTask()
    }
}

package com.plutoforce.tapcopy

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
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
    private var recognizedText: String = ""
    private var screenshotPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_selection)

        overlay = findViewById(R.id.textOverlay)
        instructionText = findViewById(R.id.instructionText)
        copyAllButton = findViewById(R.id.copyAllButton)
        findViewById<Button>(R.id.closeButton).setOnClickListener { closeAndCleanUp() }

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
        overlay.onRegionTapped = { region -> copyAndReturn(region.text) }

        copyAllButton.setOnClickListener {
            if (recognizedText.isNotBlank()) copyAndReturn(recognizedText)
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                recognizedText = result.text.trim()
                val regions = result.textBlocks.mapNotNull { block ->
                    val box = block.boundingBox ?: return@mapNotNull null
                    val text = block.text.trim()
                    if (text.isBlank()) null else TextOverlayView.TextRegion(text, box)
                }
                overlay.setRegions(regions)
                copyAllButton.isEnabled = recognizedText.isNotBlank()
                instructionText.text = if (regions.isEmpty()) {
                    "No readable text found. Try pausing the video first."
                } else {
                    getString(R.string.tap_text)
                }
            }
            .addOnFailureListener { error ->
                instructionText.text = "Text recognition failed: ${error.localizedMessage ?: "unknown error"}"
            }
            .addOnCompleteListener {
                recognizer.close()
            }
    }

    override fun onBackPressed() {
        closeAndCleanUp()
    }

    private fun copyAndReturn(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Copied screen text", cleanText))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        window.decorView.postDelayed({ closeAndCleanUp() }, 250L)
    }

    private fun closeAndCleanUp() {
        screenshotPath?.let { path -> runCatching { File(path).delete() } }
        finishAndRemoveTask()
    }
}

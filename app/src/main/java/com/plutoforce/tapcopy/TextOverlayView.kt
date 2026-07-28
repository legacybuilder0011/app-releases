package com.plutoforce.tapcopy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class TextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class TextRegion(val text: String, val box: Rect)

    private var screenshot: Bitmap? = null
    private var regions: List<TextRegion> = emptyList()
    private val selected = linkedSetOf<Int>()
    private val imageMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val density = resources.displayMetrics.density

    private val accent = Color.rgb(108, 77, 255)

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = accent
    }
    private val selectedOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = accent
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 108, 77, 255)
    }
    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(110, 108, 77, 255)
    }
    private val circleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(140, 0, 0, 0)
    }
    private val circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = accent
    }
    // White badge with a purple check, matching the design.
    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = accent
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Called with the number of currently selected blocks whenever it changes. */
    var onSelectionChanged: ((Int) -> Unit)? = null

    fun setScreenshot(bitmap: Bitmap) {
        screenshot = bitmap
        requestLayout()
        invalidate()
    }

    fun setRegions(items: List<TextRegion>) {
        regions = items
        selected.clear()
        invalidate()
        onSelectionChanged?.invoke(0)
    }

    fun selectedCount(): Int = selected.size

    /** The selected blocks' text, joined top-to-bottom in reading order. */
    fun selectedText(): String =
        selected.map { regions[it] }
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))
            .joinToString("\n") { it.text }

    /** The caption's text: every block in the caption cluster, in reading order. */
    fun bestCaptionText(): String {
        val indices = captionIndices()
        if (indices.isEmpty()) return ""
        return indices.map { regions[it] }
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))
            .joinToString("\n") { it.text }
    }

    private fun wordCount(s: String): Int =
        s.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    /**
     * Finds the caption rather than the biggest block of text on screen.
     *
     * On TikTok/Instagram the caption is left-aligned against the edge and sits
     * in the lower part of the screen, while text burned into the video is
     * usually centred and higher up. So we keep blocks that hug the left edge
     * below the midpoint, drop obvious screen furniture, and take the run of
     * them — a caption is several paragraphs, not one.
     */
    private fun captionIndices(): List<Int> {
        val bitmap = screenshot ?: return emptyList()
        val leftLimit = bitmap.width * 0.25f
        val topLimit = bitmap.height * 0.35f

        val candidates = regions.indices.filter { i ->
            val region = regions[i]
            !TextExtractor.isNoise(region.text) &&
                region.box.left <= leftLimit &&
                region.box.top >= topLimit
        }
        if (candidates.isEmpty()) return emptyList()

        // Keep the blocks that share the caption's left margin: burned-in text
        // that happens to reach the left edge sits at a different indent.
        val ordered = candidates.sortedBy { regions[it].box.top }
        val margin = ordered.map { regions[it].box.left.toFloat() }.sorted()
            .let { it[it.size / 2] }
        val tolerance = bitmap.width * 0.06f
        val aligned = ordered.filter { abs(regions[it].box.left - margin) <= tolerance }

        // Ignore a stray word — a caption has something to say.
        val words = aligned.sumOf { wordCount(regions[it].text) }
        return if (words >= 3) aligned else emptyList()
    }

    /** Ticks the whole caption, ready to copy. */
    fun selectCaption() {
        val indices = captionIndices()
        if (indices.isEmpty()) return
        selected.clear()
        selected.addAll(indices)
        invalidate()
        onSelectionChanged?.invoke(selected.size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = screenshot ?: return
        updateImageMatrix(bitmap)
        canvas.drawBitmap(bitmap, imageMatrix, null)

        regions.forEachIndexed { index, region ->
            val mapped = RectF(region.box)
            imageMatrix.mapRect(mapped)
            val isSelected = index in selected
            canvas.drawRoundRect(mapped, 10f, 10f, if (isSelected) selectedFillPaint else fillPaint)
            canvas.drawRoundRect(mapped, 10f, 10f, if (isSelected) selectedOutlinePaint else outlinePaint)
            drawCheckCircle(canvas, mapped, isSelected)
        }
    }

    /** A purple check badge at the block's top-right corner, only when selected. */
    private fun drawCheckCircle(canvas: Canvas, box: RectF, isSelected: Boolean) {
        if (!isSelected) return
        val r = 12f * density
        val cx = box.right - r - 2f * density
        val cy = box.top + r + 2f * density
        canvas.drawCircle(cx, cy, r, circleFillPaint)      // white fill
        canvas.drawCircle(cx, cy, r, circleStrokePaint)    // thin purple ring
        val check = Path().apply {
            moveTo(cx - r * 0.42f, cy + r * 0.02f)
            lineTo(cx - r * 0.10f, cy + r * 0.34f)
            lineTo(cx + r * 0.46f, cy - r * 0.32f)
        }
        canvas.drawPath(check, checkPaint)                 // purple check
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val bitmap = screenshot ?: return true
        updateImageMatrix(bitmap)
        imageMatrix.invert(inverseMatrix)

        val point = floatArrayOf(event.x, event.y)
        inverseMatrix.mapPoints(point)
        val imageX = point[0].toInt()
        val imageY = point[1].toInt()

        val tappedIndex = regions.indices
            .filter { regions[it].box.contains(imageX, imageY) }
            .minByOrNull { regions[it].box.width() * regions[it].box.height() }

        if (tappedIndex != null) {
            performClick()
            if (tappedIndex in selected) selected.remove(tappedIndex) else selected.add(tappedIndex)
            invalidate()
            onSelectionChanged?.invoke(selected.size)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateImageMatrix(bitmap: Bitmap) {
        val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val dx = (width - bitmap.width * scale) / 2f
        val dy = (height - bitmap.height * scale) / 2f
        imageMatrix.reset()
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(dx, dy)
    }
}

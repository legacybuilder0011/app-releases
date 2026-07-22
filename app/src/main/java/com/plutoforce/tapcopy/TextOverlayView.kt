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
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accent
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
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

    private fun drawCheckCircle(canvas: Canvas, box: RectF, isSelected: Boolean) {
        val r = 11f * density
        val cx = box.left + r + 3f * density
        val cy = box.top + r + 3f * density
        if (isSelected) {
            canvas.drawCircle(cx, cy, r, circleFillPaint)
            val check = Path().apply {
                moveTo(cx - r * 0.45f, cy + r * 0.05f)
                lineTo(cx - r * 0.10f, cy + r * 0.40f)
                lineTo(cx + r * 0.50f, cy - r * 0.35f)
            }
            canvas.drawPath(check, checkPaint)
        } else {
            canvas.drawCircle(cx, cy, r, circleBgPaint)
            canvas.drawCircle(cx, cy, r, circleStrokePaint)
        }
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

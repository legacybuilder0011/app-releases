package com.plutoforce.tapcopy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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
    private val imageMatrix = Matrix()
    private val inverseMatrix = Matrix()

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.rgb(108, 77, 255)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(55, 108, 77, 255)
    }

    var onRegionTapped: ((TextRegion) -> Unit)? = null

    fun setScreenshot(bitmap: Bitmap) {
        screenshot = bitmap
        requestLayout()
        invalidate()
    }

    fun setRegions(items: List<TextRegion>) {
        regions = items
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = screenshot ?: return
        updateImageMatrix(bitmap)
        canvas.drawBitmap(bitmap, imageMatrix, null)

        for (region in regions) {
            val mapped = RectF(region.box)
            imageMatrix.mapRect(mapped)
            canvas.drawRoundRect(mapped, 10f, 10f, fillPaint)
            canvas.drawRoundRect(mapped, 10f, 10f, outlinePaint)
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

        val tapped = regions
            .filter { it.box.contains(imageX, imageY) }
            .minByOrNull { it.box.width() * it.box.height() }

        if (tapped != null) {
            performClick()
            onRegionTapped?.invoke(tapped)
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

package com.plutoforce.tapcopy

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class CopyTextAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isCapturing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        bubbleView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        bubbleView = null
        super.onDestroy()
    }

    private fun showFloatingBubble() {
        if (bubbleView != null) return

        val size = dp(44)
        val bubble = TextView(this).apply {
            text = "T"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundResource(R.drawable.bubble_background)
            elevation = dp(8).toFloat()
            contentDescription = "Tap to copy visible screen text"
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(12)
            y = resources.displayMetrics.heightPixels / 2
        }

        bubble.setOnTouchListener(DragOrTapTouchListener(params))
        windowManager.addView(bubble, params)
        bubbleView = bubble
        bubbleParams = params
    }

    private inner class DragOrTapTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var downTime = 0L

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = System.currentTimeMillis()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - initialTouchX) + abs(event.rawY - initialTouchY)
                    val duration = System.currentTimeMillis() - downTime
                    if (moved < dp(12) && duration < 600L) {
                        captureScreen()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun captureScreen() {
        if (isCapturing) return
        isCapturing = true
        bubbleView?.visibility = View.INVISIBLE

        handler.postDelayed({
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                            hardwareBuffer,
                            screenshot.colorSpace
                        )
                        val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBuffer.close()

                        if (softwareBitmap == null) {
                            finishCaptureWithError("Could not read this screen")
                            return
                        }

                        val screenshotFile = File(cacheDir, "tapcopy_latest.png")
                        runCatching {
                            FileOutputStream(screenshotFile).use { stream ->
                                softwareBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            }
                        }.onSuccess {
                            bubbleView?.visibility = View.VISIBLE
                            isCapturing = false
                            val intent = android.content.Intent(
                                this@CopyTextAccessibilityService,
                                TextSelectionActivity::class.java
                            ).apply {
                                putExtra(TextSelectionActivity.EXTRA_SCREENSHOT_PATH, screenshotFile.absolutePath)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            }
                            startActivity(intent)
                        }.onFailure {
                            finishCaptureWithError("Could not save the screenshot")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val message = when (errorCode) {
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "This app blocks screenshots for privacy"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Please wait a moment and tap again"
                            else -> "Screen capture failed"
                        }
                        finishCaptureWithError(message)
                    }
                }
            )
        }, 140L)
    }

    private fun finishCaptureWithError(message: String) {
        bubbleView?.visibility = View.VISIBLE
        isCapturing = false
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

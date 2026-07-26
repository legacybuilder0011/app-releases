package com.plutoforce.tapcopy

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
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

    companion object {
        const val ACTION_REFRESH = "com.plutoforce.tapcopy.REFRESH_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "com.plutoforce.tapcopy.HIDE_BUBBLE"
        const val ACTION_SHOW_BUBBLE = "com.plutoforce.tapcopy.SHOW_BUBBLE"

        /** How long to wait for a second tap before treating it as a single tap. */
        private const val DOUBLE_TAP_MS = 280L
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var quickActionsView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isCapturing = false
    private val hideRunnable = Runnable { applyIdleFade() }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // Keep the button out of the way (and out of screenshots) while
                // the Teleprompter is playing.
                ACTION_HIDE_BUBBLE -> bubbleView?.visibility = View.GONE
                ACTION_SHOW_BUBBLE -> {
                    bubbleView?.visibility = View.VISIBLE
                    wake()
                }
                else -> applyBubbleStyle()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingBubble()
        val filter = IntentFilter(ACTION_REFRESH).apply {
            addAction(ACTION_HIDE_BUBBLE)
            addAction(ACTION_SHOW_BUBBLE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(refreshReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only used to let a running Teleprompter hide itself over sensitive apps.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            TeleprompterService.isActive
        ) {
            TeleprompterService.onForegroundApp(event.packageName?.toString())
        }
    }

    /** Double tap: open the floating Teleprompter (needs the overlay permission). */
    private fun openTeleprompter() {
        if (!TeleprompterPrefs.enabled(this)) {
            Toast.makeText(this, R.string.prompter_enable_first, Toast.LENGTH_LONG).show()
            return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.prompter_permission_needed, Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(
            Intent(this, TeleprompterService::class.java)
                .setAction(TeleprompterService.ACTION_OPEN_EDITOR)
        )
    }

    /** Long press: a small menu beside the button. */
    private fun showQuickActions() {
        if (quickActionsView != null) {
            hideQuickActions()
            return
        }
        val view = android.view.LayoutInflater.from(this)
            .inflate(R.layout.overlay_quick_actions, null)

        view.findViewById<View>(R.id.qaCopy).setOnClickListener {
            hideQuickActions(); captureScreen()
        }
        view.findViewById<View>(R.id.qaPrompter).setOnClickListener {
            hideQuickActions(); openTeleprompter()
        }
        view.findViewById<View>(R.id.qaHistory).setOnClickListener {
            hideQuickActions()
            startActivity(
                Intent(this, HistoryActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        view.findViewById<View>(R.id.qaDismiss).setOnClickListener { hideQuickActions() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = (bubbleParams?.y ?: 0) + dp(56)
        }
        runCatching { windowManager.addView(view, params) }
            .onSuccess { quickActionsView = view }
        handler.postDelayed({ hideQuickActions() }, 6000L)
    }

    private fun hideQuickActions() {
        quickActionsView?.let { v -> runCatching { windowManager.removeView(v) } }
        quickActionsView = null
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(refreshReceiver) }
        hideQuickActions()
        bubbleView?.let { view -> runCatching { windowManager.removeView(view) } }
        bubbleView = null
        super.onDestroy()
    }

    private fun showFloatingBubble() {
        if (bubbleView != null) return

        val sizePx = dp(SettingsPrefs.buttonSizeDp(this))
        val bubble = TextView(this).apply {
            text = "T"
            textSize = SettingsPrefs.buttonSizeDp(this@CopyTextAccessibilityService) * 0.4f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundResource(R.drawable.bubble_background)
            elevation = dp(8).toFloat()
            contentDescription = "Tap to copy visible screen text"
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        positionParams(params, sizePx)

        bubble.setOnTouchListener(DragOrTapTouchListener(params))
        windowManager.addView(bubble, params)
        bubbleView = bubble
        bubbleParams = params
        bubble.alpha = baseAlpha()
        scheduleIdleFade()
    }

    private fun positionParams(params: WindowManager.LayoutParams, sizePx: Int) {
        val savedX = SettingsPrefs.bubbleX(this)
        val savedY = SettingsPrefs.bubbleY(this)
        if (savedX >= 0 && savedY >= 0) {
            params.x = savedX
            params.y = savedY
        } else {
            params.x = resources.displayMetrics.widthPixels - sizePx - dp(12)
            params.y = resources.displayMetrics.heightPixels / 3
        }
    }

    /** Re-apply size / opacity / position to the live bubble (from Settings). */
    private fun applyBubbleStyle() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val sizePx = dp(SettingsPrefs.buttonSizeDp(this))
        params.width = sizePx
        params.height = sizePx
        positionParams(params, sizePx)
        view.textSize = SettingsPrefs.buttonSizeDp(this) * 0.4f
        view.alpha = baseAlpha()
        runCatching { windowManager.updateViewLayout(view, params) }
        scheduleIdleFade()
    }

    private fun baseAlpha(): Float = (SettingsPrefs.buttonOpacity(this).coerceIn(20, 100)) / 100f

    private fun scheduleIdleFade() {
        handler.removeCallbacks(hideRunnable)
        if (SettingsPrefs.autoHide(this)) handler.postDelayed(hideRunnable, 4000L)
    }

    private fun applyIdleFade() {
        if (SettingsPrefs.autoHide(this)) bubbleView?.alpha = minOf(0.3f, baseAlpha())
    }

    private fun wake() {
        bubbleView?.alpha = baseAlpha()
        scheduleIdleFade()
    }

    /**
     * The floating button understands three gestures:
     *   single tap  → copy visible screen text
     *   double tap  → open the Teleprompter
     *   long press  → quick actions menu
     * Dragging still moves the button.
     */
    private inner class DragOrTapTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var downTime = 0L
        private var awaitingSecondTap = false
        private var longPressFired = false

        private val singleTapRunnable = Runnable {
            awaitingSecondTap = false
            captureScreen()
        }
        private val longPressRunnable = Runnable {
            longPressFired = true
            showQuickActions()
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = System.currentTimeMillis()
                    longPressFired = false
                    wake()
                    handler.postDelayed(longPressRunnable, 500L)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val moved = abs(event.rawX - initialTouchX) + abs(event.rawY - initialTouchY)
                    if (moved > dp(12)) {
                        handler.removeCallbacks(longPressRunnable)
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    val moved = abs(event.rawX - initialTouchX) + abs(event.rawY - initialTouchY)
                    val duration = System.currentTimeMillis() - downTime
                    val wasTap = moved < dp(12) && duration < 600L && !longPressFired

                    when {
                        !wasTap && !longPressFired -> {
                            // Remember where the user parked the button.
                            SettingsPrefs.setBubblePos(
                                this@CopyTextAccessibilityService, params.x, params.y
                            )
                            scheduleIdleFade()
                        }
                        wasTap && awaitingSecondTap -> {
                            // Second tap inside the window → Teleprompter.
                            handler.removeCallbacks(singleTapRunnable)
                            awaitingSecondTap = false
                            openTeleprompter()
                        }
                        wasTap -> {
                            awaitingSecondTap = true
                            handler.postDelayed(singleTapRunnable, DOUBLE_TAP_MS)
                        }
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
                            wake()
                            isCapturing = false
                            val intent = Intent(
                                this@CopyTextAccessibilityService,
                                TextSelectionActivity::class.java
                            ).apply {
                                putExtra(TextSelectionActivity.EXTRA_SCREENSHOT_PATH, screenshotFile.absolutePath)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
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
        wake()
        isCapturing = false
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

package com.plutoforce.tapcopy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * The floating Teleprompter. Runs as a foreground service so the script keeps
 * scrolling over the camera or any other app.
 *
 * It deliberately uses the overlay permission (SYSTEM_ALERT_WINDOW) rather than
 * the accessibility service — accessibility stays limited to TapCopy's
 * screen-text capture.
 *
 * States: EDITING → COUNTDOWN → PLAYING ⇄ PAUSED → FINISHED.
 */
class TeleprompterService : Service() {

    companion object {
        const val ACTION_OPEN_EDITOR = "com.plutoforce.tapcopy.PROMPTER_EDIT"
        const val ACTION_STOP = "com.plutoforce.tapcopy.PROMPTER_STOP"

        private const val CHANNEL_ID = "tapcopy_prompter"
        private const val NOTIFICATION_ID = 77

        /** True while any Teleprompter window is showing. */
        @Volatile
        var isActive = false
            private set

        /** True only during playback, so the floating button can stay hidden. */
        @Volatile
        var isPlaying = false
            private set

        private var instance: TeleprompterService? = null

        /**
         * Called by the accessibility service when the foreground app changes, so
         * the script can hide itself over banking / password style apps.
         */
        fun onForegroundApp(packageName: String?) {
            instance?.handleForegroundApp(packageName)
        }

        private val SENSITIVE_HINTS = listOf(
            "bank", "wallet", "pay", "upi", "money", "finance", "card",
            "password", "authenticator", "vault", "keychain", "crypto"
        )
    }

    private enum class State { EDITING, COUNTDOWN, PLAYING, PAUSED, FINISHED }

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var state = State.EDITING

    private var editorView: View? = null
    private var playView: View? = null
    private var countdownView: View? = null
    private var playParams: WindowManager.LayoutParams? = null

    private var scriptText: TextView? = null
    private var scrollClip: View? = null
    private var spannable: SpannableString? = null
    private val appliedSpans = ArrayList<Any>()

    private var script = ""
    private var scriptId: Long = -1L

    // Scroll engine state
    private var offset = 0f
    private var lastFrameNanos = 0L
    private var pauseUntil = 0L
    private var pausedAtLine = -1
    private var currentLine = -1
    private var hiddenForSensitive = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            stepScroll()
            if (state == State.PLAYING) handler.postDelayed(this, 16L)
        }
    }

    private val hideBarRunnable = Runnable { view(R.id.controlBar)?.visibility = View.GONE }

    /** Hide the script while the phone is locked, restore when unlocked. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!TeleprompterPrefs.hideOnLockScreen(this@TeleprompterService)) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> setWindowsVisible(false)
                Intent.ACTION_USER_PRESENT -> setWindowsVisible(true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Android 14+ can refuse a foreground start from the background; if that
        // happens we stop cleanly instead of crashing.
        val started = runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }
        if (started.isFailure) {
            toast(getString(R.string.prompter_start_blocked))
            stopSelf()
            return
        }
        isActive = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            else -> if (playView == null && editorView == null) showEditor()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        saveProgress()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        removeView(editorView); editorView = null
        removeView(playView); playView = null
        removeView(countdownView); countdownView = null
        isActive = false
        isPlaying = false
        showBubble(true)
        instance = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-fit the script box to the new screen size instead of losing it.
        val params = playParams ?: return
        val view = playView ?: return
        applyBoxMetrics(params)
        runCatching { windowManager.updateViewLayout(view, params) }
        handler.post { rebuildScriptLayout() }
    }

    // --- Editor ------------------------------------------------------------

    private fun showEditor() {
        removeView(playView); playView = null
        stopScrolling()
        state = State.EDITING
        showBubble(true)

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_prompter_editor, null)
        view.background = boxBackground()

        val input = view.findViewById<EditText>(R.id.scriptInput)
        val estimate = view.findViewById<TextView>(R.id.estimateText)

        val initial = when {
            script.isNotBlank() -> script
            TeleprompterPrefs.rememberUnfinished(this) &&
                TeleprompterPrefs.draft(this).isNotBlank() -> TeleprompterPrefs.draft(this)
            TeleprompterPrefs.useLastCopied(this) -> CopyStore.all(this).firstOrNull()?.text.orEmpty()
            else -> ""
        }
        input.setText(initial)
        input.setSelection(input.text.length)
        updateEstimate(estimate, initial)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) =
                updateEstimate(estimate, s?.toString().orEmpty())

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        view.findViewById<View>(R.id.pasteButton).setOnClickListener {
            val clip = clipboardText()
            if (clip.isNullOrBlank()) toast("Nothing to paste") else {
                input.setText(clip); input.setSelection(input.text.length)
            }
        }

        val savedList = view.findViewById<android.widget.LinearLayout>(R.id.savedList)
        view.findViewById<View>(R.id.savedButton).setOnClickListener {
            if (savedList.visibility == View.VISIBLE) {
                savedList.visibility = View.GONE
            } else {
                populateSavedList(savedList, input)
                savedList.visibility = View.VISIBLE
            }
        }

        view.findViewById<View>(R.id.settingsButton).setOnClickListener {
            saveDraft(input.text.toString())
            startActivity(
                Intent(this, TeleprompterSettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        view.findViewById<View>(R.id.closeEditor).setOnClickListener {
            saveDraft(input.text.toString())
            stopEverything()
        }

        view.findViewById<View>(R.id.playButton).setOnClickListener {
            val text = input.text.toString()
            if (TeleprompterPrefs.wordCount(text) == 0) {
                toast("Add a script first")
                return@setOnClickListener
            }
            script = text
            saveDraft(text)
            if (TeleprompterPrefs.saveToHistory(this)) {
                scriptId = ScriptStore.save(this, text, if (scriptId > 0) scriptId else null)
            }
            removeView(editorView); editorView = null
            startCountdown()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // Focusable so the user can actually type into the script box.
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dp(12)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        runCatching { windowManager.addView(view, params) }
            .onFailure { permissionProblem(); return }
        editorView = view
    }

    private fun populateSavedList(container: android.widget.LinearLayout, input: EditText) {
        container.removeAllViews()
        val scripts = ScriptStore.all(this).take(6)
        if (scripts.isEmpty()) {
            container.addView(hintRow(getString(R.string.prompter_no_saved)))
            return
        }
        scripts.forEach { s ->
            val row = TextView(this).apply {
                text = "${s.title}  ·  ${s.wordCount} words"
                setTextColor(getColor(R.color.ink))
                textSize = 14f
                setPadding(dp(10), dp(11), dp(10), dp(11))
                setBackgroundResource(android.R.color.transparent)
                isClickable = true
                setOnClickListener {
                    input.setText(s.text)
                    input.setSelection(input.text.length)
                    scriptId = s.id
                    container.visibility = View.GONE
                }
            }
            container.addView(row)
        }
    }

    private fun hintRow(text: String) = TextView(this).apply {
        setText(text)
        setTextColor(getColor(R.color.muted))
        textSize = 13f
        setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    private fun updateEstimate(view: TextView, text: String) {
        val words = TeleprompterPrefs.wordCount(text)
        if (words == 0) {
            view.text = ""
            return
        }
        val seconds = TeleprompterPrefs.estimatedSeconds(this, text)
        view.text = getString(
            R.string.prompter_estimate, words, TeleprompterPrefs.formatDuration(seconds)
        )
    }

    // --- Countdown ---------------------------------------------------------

    private fun startCountdown() {
        val seconds = TeleprompterPrefs.countdownSeconds(this)
        if (seconds <= 0) {
            startPlayback()
            return
        }
        state = State.COUNTDOWN
        showBubble(false)

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_prompter_countdown, null)
        val label = view.findViewById<TextView>(R.id.countdownText)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        runCatching { windowManager.addView(view, params) }
            .onFailure { permissionProblem(); return }
        countdownView = view

        // One countdown only: this chain is the single scheduled source.
        fun tick(remaining: Int) {
            if (state != State.COUNTDOWN) return
            if (remaining <= 0) {
                removeView(countdownView); countdownView = null
                startPlayback()
                return
            }
            label.text = remaining.toString()
            handler.postDelayed({ tick(remaining - 1) }, 1000L)
        }
        tick(seconds)
    }

    // --- Playback ----------------------------------------------------------

    private fun startPlayback() {
        removeView(editorView); editorView = null
        showBubble(false)

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_prompter_play, null)
        view.background = boxBackground()
        playView = view

        scrollClip = view.findViewById(R.id.scrollClip)
        val text = view.findViewById<TextView>(R.id.scriptText)
        scriptText = text
        styleScriptText(text)

        view.findViewById<View>(R.id.barRestart).setOnClickListener { restart() }
        view.findViewById<View>(R.id.barSpeed).setOnClickListener { bumpSpeed() }
        view.findViewById<View>(R.id.barEdit).setOnClickListener {
            stopScrolling(); showEditor()
        }
        view.findViewById<View>(R.id.barClose).setOnClickListener { askClose() }
        view.findViewById<View>(R.id.confirmCancel).setOnClickListener {
            view.findViewById<View>(R.id.confirmClose).visibility = View.GONE
        }
        view.findViewById<View>(R.id.confirmCloseYes).setOnClickListener { stopEverything() }

        attachGestures(view)

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (TeleprompterPrefs.keepScreenAwake(this)) {
            flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        applyBoxMetrics(params)
        playParams = params

        runCatching { windowManager.addView(view, params) }
            .onFailure { permissionProblem(); return }

        offset = if (TeleprompterPrefs.startFromLastPosition(this)) {
            TeleprompterPrefs.lastScrollY(this).toFloat()
        } else 0f

        state = State.PLAYING
        isPlaying = true
        handler.post { rebuildScriptLayout(); resumeScrolling() }
    }

    /** Applies the size/position settings to the script window. */
    private fun applyBoxMetrics(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        var widthPercent = TeleprompterPrefs.boxWidth(this)
        if (TeleprompterPrefs.fewerWordsPerLine(this) ||
            TeleprompterPrefs.dyslexiaFriendly(this)
        ) {
            widthPercent = minOf(widthPercent, 72)
        }
        params.width = (metrics.widthPixels * widthPercent / 100f).toInt()
        params.height = (metrics.heightPixels * TeleprompterPrefs.boxHeightFraction(this)).toInt()

        val landscape = resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        val saved = if (TeleprompterPrefs.rememberPosition(this)) {
            TeleprompterPrefs.savedY(this, landscape)
        } else -1

        params.y = if (saved >= 0) {
            saved
        } else {
            when (TeleprompterPrefs.defaultPosition(this)) {
                "middle" -> (metrics.heightPixels - params.height) / 2
                "bottom" -> metrics.heightPixels - params.height - dp(24)
                else -> dp(8)
            }
        }
        // Never let the box sit under the status bar / cutout or off-screen.
        params.y = params.y.coerceIn(0, maxOf(0, metrics.heightPixels - params.height))
    }

    private fun styleScriptText(text: TextView) {
        val dyslexia = TeleprompterPrefs.dyslexiaFriendly(this)
        val size = TeleprompterPrefs.textSize(this) + if (dyslexia) 4 else 0
        text.textSize = size.toFloat()
        val spacing = if (dyslexia) {
            maxOf(1.6f, TeleprompterPrefs.lineSpacingMultiplier(this))
        } else {
            TeleprompterPrefs.lineSpacingMultiplier(this)
        }
        text.setLineSpacing(0f, spacing)
        if (dyslexia) text.letterSpacing = 0.03f
        text.gravity = when (TeleprompterPrefs.alignment(this)) {
            "centre" -> Gravity.CENTER_HORIZONTAL
            "right" -> Gravity.END
            else -> Gravity.START
        }
        text.scaleX = if (TeleprompterPrefs.mirrorText(this)) -1f else 1f
    }

    /** Sets the script into the view and prepares highlight spans. */
    private fun rebuildScriptLayout() {
        val text = scriptText ?: return
        val prepared = prepareScript(script)
        val sp = SpannableString(prepared)
        if (TeleprompterPrefs.emphasizePunctuation(this)) {
            Regex("[.,!?;:]").findAll(prepared).forEach { m ->
                sp.setSpan(
                    StyleSpan(android.graphics.Typeface.BOLD),
                    m.range.first, m.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        spannable = sp
        appliedSpans.clear()
        text.text = sp
        currentLine = -1
        text.translationY = -offset
    }

    private fun prepareScript(raw: String): String {
        var t = raw.trim()
        if (TeleprompterPrefs.autoParagraphBreaks(this)) {
            t = t.lines().joinToString("\n") { it.trim() }
                .replace(Regex("\\n{3,}"), "\n\n")
        }
        return t
    }

    private fun resumeScrolling() {
        state = State.PLAYING
        isPlaying = true
        view(R.id.pausedBadge)?.visibility = View.GONE
        lastFrameNanos = System.nanoTime()
        handler.removeCallbacks(frameRunnable)
        handler.post(frameRunnable)
    }

    private fun stopScrolling() {
        handler.removeCallbacks(frameRunnable)
        isPlaying = false
    }

    /** One frame of time-based scrolling: position never depends on frame rate. */
    private fun stepScroll() {
        val text = scriptText ?: return
        val clip = scrollClip ?: return
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.25f)
        lastFrameNanos = now

        if (System.currentTimeMillis() < pauseUntil) {
            text.translationY = -offset
            return
        }

        val distance = (text.height - clip.height + clip.paddingTop).toFloat()
        if (distance <= 0f) {
            finishPlayback()
            return
        }

        val seconds = TeleprompterPrefs.estimatedSeconds(this, script).coerceAtLeast(1)
        val pxPerSecond = distance / seconds
        offset += pxPerSecond * dt

        if (offset >= distance) {
            offset = distance
            text.translationY = -offset
            if (TeleprompterPrefs.loopScript(this)) {
                offset = 0f
            } else {
                finishPlayback()
                return
            }
        }

        text.translationY = -offset
        updateEmphasis()
    }

    /** Highlights the line being read and dims the rest. */
    private fun updateEmphasis() {
        val text = scriptText ?: return
        val clip = scrollClip ?: return
        val layout = text.layout ?: return
        val sp = spannable ?: return

        val readingY = (offset + clip.height * 0.30f).toInt()
        val line = layout.getLineForVertical(readingY.coerceAtLeast(0))
        if (line == currentLine) return
        currentLine = line

        // Pause briefly at paragraph gaps so the delivery sounds natural.
        if (TeleprompterPrefs.pauseAtParagraphs(this) && line != pausedAtLine) {
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(line)
            if (end > start && sp.subSequence(start, end).isBlank()) {
                pausedAtLine = line
                pauseUntil = System.currentTimeMillis() +
                    TeleprompterPrefs.paragraphPauseTenths(this) * 100L
            }
        }

        val highlight = TeleprompterPrefs.highlightCurrentLine(this)
        val dim = TeleprompterPrefs.dimPrevious(this)
        if (!highlight && !dim) return

        appliedSpans.forEach { sp.removeSpan(it) }
        appliedSpans.clear()
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            if (end <= start) continue
            val color = when {
                i < line && dim -> Color.argb(115, 255, 255, 255)   // ~45% read
                i == line && highlight -> Color.WHITE               // 100% current
                i > line && highlight -> Color.argb(140, 255, 255, 255) // ~55% upcoming
                else -> Color.WHITE
            }
            val span = ForegroundColorSpan(color)
            sp.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            appliedSpans.add(span)
        }
    }

    private fun finishPlayback() {
        stopScrolling()
        state = State.FINISHED
        view(R.id.pausedBadge)?.let {
            (it as TextView).setText(R.string.prompter_finished)
            it.visibility = View.VISIBLE
        }
        showControlBar()
        if (TeleprompterPrefs.clearAfterFinish(this)) {
            script = ""
            TeleprompterPrefs.setDraft(this, "")
            TeleprompterPrefs.setLastScrollY(this, 0)
        } else {
            saveProgress()
        }
    }

    private fun togglePause() {
        when (state) {
            State.PLAYING -> {
                stopScrolling()
                state = State.PAUSED
                view(R.id.pausedBadge)?.let {
                    (it as TextView).setText(R.string.prompter_paused)
                    it.visibility = View.VISIBLE
                }
                saveProgress()
            }
            State.PAUSED -> resumeScrolling()
            State.FINISHED -> restart()
            else -> Unit
        }
    }

    private fun restart() {
        // With confirmation on, the first tap asks and a second tap within a
        // couple of seconds actually restarts.
        if (TeleprompterPrefs.confirmRestart(this) && state != State.FINISHED) {
            val now = System.currentTimeMillis()
            if (now - lastRestartPrompt > 2500L) {
                lastRestartPrompt = now
                toast(getString(R.string.prompter_restart_again))
                showControlBar()
                return
            }
        }
        lastRestartPrompt = 0L
        offset = 0f
        currentLine = -1
        pausedAtLine = -1
        pauseUntil = 0L
        TeleprompterPrefs.setLastScrollY(this, 0)
        scriptText?.translationY = 0f
        resumeScrolling()
    }

    private var lastRestartPrompt = 0L

    private fun bumpSpeed() {
        val next = when (val wpm = TeleprompterPrefs.speedWpm(this)) {
            in 0..79 -> wpm + 20
            in 80..199 -> wpm + 25
            else -> 50
        }
        TeleprompterPrefs.setSpeedWpm(this, next)
        (view(R.id.barSpeedValue) as? TextView)?.text =
            getString(R.string.prompter_wpm_short, TeleprompterPrefs.speedWpm(this))
        showControlBar()
    }

    private fun askClose() {
        view(R.id.confirmClose)?.visibility = View.VISIBLE
    }

    private fun showControlBar() {
        if (!TeleprompterPrefs.longPressControls(this) && state == State.PLAYING) return
        val bar = view(R.id.controlBar) ?: return
        (view(R.id.barSpeedValue) as? TextView)?.text =
            getString(R.string.prompter_wpm_short, TeleprompterPrefs.speedWpm(this))
        bar.visibility = View.VISIBLE
        handler.removeCallbacks(hideBarRunnable)
        handler.postDelayed(hideBarRunnable, 3000L)
    }

    // --- Gestures on the script box ---------------------------------------

    private fun attachGestures(root: View) {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (TeleprompterPrefs.tapToPause(this@TeleprompterService)) togglePause()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (TeleprompterPrefs.longPressControls(this@TeleprompterService)) showControlBar()
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float
            ): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (dx < -dp(60) && abs(vx) > 400f) {
                    askClose()
                    return true
                }
                return false
            }
        })

        var startY = 0
        var touchY = 0f
        var dragging = false

        root.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            val params = playParams
            val canDrag = TeleprompterPrefs.allowDragging(this) &&
                TeleprompterPrefs.dragWhilePaused(this) &&
                (state == State.PAUSED || state == State.FINISHED)

            if (canDrag && params != null) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = params.y
                        touchY = event.rawY
                        dragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - touchY
                        if (abs(dy) > dp(6)) {
                            dragging = true
                            val max = resources.displayMetrics.heightPixels - params.height
                            params.y = (startY + dy.toInt()).coerceIn(0, maxOf(0, max))
                            runCatching { windowManager.updateViewLayout(root, params) }
                        }
                    }
                    MotionEvent.ACTION_UP -> if (dragging && TeleprompterPrefs.rememberPosition(this)) {
                        val landscape = resources.configuration.orientation ==
                            Configuration.ORIENTATION_LANDSCAPE
                        TeleprompterPrefs.setSavedY(this, landscape, params.y)
                    }
                }
            }
            true
        }
    }

    // --- Sensitive apps / lock screen -------------------------------------

    private fun handleForegroundApp(packageName: String?) {
        if (!TeleprompterPrefs.hideOverSensitiveApps(this)) return
        val pkg = packageName?.lowercase() ?: return
        if (pkg == this.packageName) return
        val sensitive = SENSITIVE_HINTS.any { pkg.contains(it) }
        if (sensitive == hiddenForSensitive) return
        hiddenForSensitive = sensitive
        handler.post { setWindowsVisible(!sensitive) }
    }

    private fun setWindowsVisible(visible: Boolean) {
        val target = if (visible) View.VISIBLE else View.GONE
        playView?.visibility = target
        editorView?.visibility = target
        countdownView?.visibility = target
    }

    // --- Helpers ----------------------------------------------------------

    private fun view(id: Int): View? = playView?.findViewById(id)

    /** Rounded, semi-transparent panel used by the editor and the script box. */
    private fun boxBackground(): GradientDrawable {
        val opacity = TeleprompterPrefs.backgroundOpacity(this).coerceIn(0, 100)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(Color.argb(opacity * 255 / 100, 12, 12, 18))
        }
    }

    private fun saveDraft(text: String) {
        script = text
        if (TeleprompterPrefs.rememberUnfinished(this)) TeleprompterPrefs.setDraft(this, text)
    }

    private fun saveProgress() {
        if (!TeleprompterPrefs.rememberUnfinished(this)) return
        TeleprompterPrefs.setLastScrollY(this, offset.toInt())
        if (scriptId > 0) ScriptStore.setLastPosition(this, scriptId, offset.toInt())
    }

    private fun stopEverything() {
        saveProgress()
        stopForeground(true)
        stopSelf()
    }

    private fun clipboardText(): String? = runCatching {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip ?: return@runCatching null
        if (clip.itemCount <= 0) null else clip.getItemAt(0).coerceToText(this)?.toString()
    }.getOrNull()

    private fun showBubble(show: Boolean) {
        val action = if (show) CopyTextAccessibilityService.ACTION_SHOW_BUBBLE
        else CopyTextAccessibilityService.ACTION_HIDE_BUBBLE
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    private fun permissionProblem() {
        toast(getString(R.string.prompter_overlay_blocked))
        stopEverything()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun removeView(view: View?) {
        if (view != null) runCatching { windowManager.removeView(view) }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.prompter_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TeleprompterService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.prompter_notification_title))
            .setContentText(getString(R.string.prompter_notification_text))
            .setSmallIcon(R.drawable.ic_teleprompter)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.prompter_stop), stopIntent
                ).build()
            )
            .build()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

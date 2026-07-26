package com.plutoforce.tapcopy

import android.content.Context

/**
 * Every Teleprompter setting, with the recommended defaults. Stored in
 * SharedPreferences (same as the rest of the app) so values survive a reboot.
 */
object TeleprompterPrefs {

    private const val NAME = "tapcopy_teleprompter"

    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // --- Feature toggle -----------------------------------------------------
    fun enabled(c: Context): Boolean = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    // --- Script ------------------------------------------------------------
    fun useLastCopied(c: Context): Boolean = p(c).getBoolean("use_last_copied", true)
    fun setUseLastCopied(c: Context, v: Boolean) = p(c).edit().putBoolean("use_last_copied", v).apply()

    fun rememberUnfinished(c: Context): Boolean = p(c).getBoolean("remember_unfinished", true)
    fun setRememberUnfinished(c: Context, v: Boolean) = p(c).edit().putBoolean("remember_unfinished", v).apply()

    fun saveToHistory(c: Context): Boolean = p(c).getBoolean("save_history", true)
    fun setSaveToHistory(c: Context, v: Boolean) = p(c).edit().putBoolean("save_history", v).apply()

    fun clearAfterFinish(c: Context): Boolean = p(c).getBoolean("clear_after_finish", false)
    fun setClearAfterFinish(c: Context, v: Boolean) = p(c).edit().putBoolean("clear_after_finish", v).apply()

    /** The in-progress script, kept so an accidental close doesn't lose it. */
    fun draft(c: Context): String = p(c).getString("draft", "").orEmpty()
    fun setDraft(c: Context, v: String) = p(c).edit().putString("draft", v).apply()

    /** Scroll offset in pixels where reading stopped. */
    fun lastScrollY(c: Context): Int = p(c).getInt("last_scroll_y", 0)
    fun setLastScrollY(c: Context, v: Int) = p(c).edit().putInt("last_scroll_y", v).apply()

    // --- Text appearance ---------------------------------------------------
    fun textSize(c: Context): Int = p(c).getInt("text_size", 34) // sp
    fun setTextSize(c: Context, v: Int) = p(c).edit().putInt("text_size", v).apply()

    /** "tight" 1.0, "comfortable" 1.3, "relaxed" 1.6 */
    fun lineSpacing(c: Context): String = p(c).getString("line_spacing", "comfortable").orEmpty()
    fun setLineSpacing(c: Context, v: String) = p(c).edit().putString("line_spacing", v).apply()
    fun lineSpacingMultiplier(c: Context): Float = when (lineSpacing(c)) {
        "tight" -> 1.0f
        "relaxed" -> 1.6f
        else -> 1.3f
    }

    /** "left", "centre", "right" */
    fun alignment(c: Context): String = p(c).getString("alignment", "left").orEmpty()
    fun setAlignment(c: Context, v: String) = p(c).edit().putString("alignment", v).apply()

    fun highlightCurrentLine(c: Context): Boolean = p(c).getBoolean("highlight_line", true)
    fun setHighlightCurrentLine(c: Context, v: Boolean) = p(c).edit().putBoolean("highlight_line", v).apply()

    fun dimPrevious(c: Context): Boolean = p(c).getBoolean("dim_previous", true)
    fun setDimPrevious(c: Context, v: Boolean) = p(c).edit().putBoolean("dim_previous", v).apply()

    fun backgroundOpacity(c: Context): Int = p(c).getInt("bg_opacity", 75) // percent
    fun setBackgroundOpacity(c: Context, v: Int) = p(c).edit().putInt("bg_opacity", v).apply()

    fun boxWidth(c: Context): Int = p(c).getInt("box_width", 90) // percent of screen
    fun setBoxWidth(c: Context, v: Int) = p(c).edit().putInt("box_width", v).apply()

    /** "small" 22%, "medium" 28%, "large" 36% of screen height */
    fun boxHeight(c: Context): String = p(c).getString("box_height", "medium").orEmpty()
    fun setBoxHeight(c: Context, v: String) = p(c).edit().putString("box_height", v).apply()
    fun boxHeightFraction(c: Context): Float = when (boxHeight(c)) {
        "small" -> 0.22f
        "large" -> 0.36f
        else -> 0.28f
    }

    fun mirrorText(c: Context): Boolean = p(c).getBoolean("mirror", false)
    fun setMirrorText(c: Context, v: Boolean) = p(c).edit().putBoolean("mirror", v).apply()

    // --- Scrolling ---------------------------------------------------------
    fun speedWpm(c: Context): Int = p(c).getInt("speed_wpm", 100) // 50..250
    fun setSpeedWpm(c: Context, v: Int) = p(c).edit().putInt("speed_wpm", v.coerceIn(50, 250)).apply()

    fun countdownSeconds(c: Context): Int = p(c).getInt("countdown", 3) // 0/3/5/10
    fun setCountdownSeconds(c: Context, v: Int) = p(c).edit().putInt("countdown", v).apply()

    fun pauseAtParagraphs(c: Context): Boolean = p(c).getBoolean("pause_paragraphs", true)
    fun setPauseAtParagraphs(c: Context, v: Boolean) = p(c).edit().putBoolean("pause_paragraphs", v).apply()

    /** Tenths of a second, so it stores cleanly as an int. */
    fun paragraphPauseTenths(c: Context): Int = p(c).getInt("paragraph_pause", 5)
    fun setParagraphPauseTenths(c: Context, v: Int) = p(c).edit().putInt("paragraph_pause", v).apply()

    fun loopScript(c: Context): Boolean = p(c).getBoolean("loop", false)
    fun setLoopScript(c: Context, v: Boolean) = p(c).edit().putBoolean("loop", v).apply()

    fun startFromLastPosition(c: Context): Boolean = p(c).getBoolean("start_last_pos", true)
    fun setStartFromLastPosition(c: Context, v: Boolean) = p(c).edit().putBoolean("start_last_pos", v).apply()

    // --- Playback ----------------------------------------------------------
    fun hideControls(c: Context): Boolean = p(c).getBoolean("hide_controls", true)
    fun setHideControls(c: Context, v: Boolean) = p(c).edit().putBoolean("hide_controls", v).apply()

    fun tapToPause(c: Context): Boolean = p(c).getBoolean("tap_pause", true)
    fun setTapToPause(c: Context, v: Boolean) = p(c).edit().putBoolean("tap_pause", v).apply()

    fun longPressControls(c: Context): Boolean = p(c).getBoolean("longpress_controls", true)
    fun setLongPressControls(c: Context, v: Boolean) = p(c).edit().putBoolean("longpress_controls", v).apply()

    fun dragWhilePaused(c: Context): Boolean = p(c).getBoolean("drag_paused", true)
    fun setDragWhilePaused(c: Context, v: Boolean) = p(c).edit().putBoolean("drag_paused", v).apply()

    fun keepScreenAwake(c: Context): Boolean = p(c).getBoolean("keep_awake", true)
    fun setKeepScreenAwake(c: Context, v: Boolean) = p(c).edit().putBoolean("keep_awake", v).apply()

    fun confirmRestart(c: Context): Boolean = p(c).getBoolean("confirm_restart", true)
    fun setConfirmRestart(c: Context, v: Boolean) = p(c).edit().putBoolean("confirm_restart", v).apply()

    fun volumeKeysSpeed(c: Context): Boolean = p(c).getBoolean("volume_speed", false)
    fun setVolumeKeysSpeed(c: Context, v: Boolean) = p(c).edit().putBoolean("volume_speed", v).apply()

    // --- Position & size ---------------------------------------------------
    /** "top", "middle", "bottom" — used when there's no saved position. */
    fun defaultPosition(c: Context): String = p(c).getString("default_pos", "top").orEmpty()
    fun setDefaultPosition(c: Context, v: String) = p(c).edit().putString("default_pos", v).apply()

    fun allowDragging(c: Context): Boolean = p(c).getBoolean("allow_drag", true)
    fun setAllowDragging(c: Context, v: Boolean) = p(c).edit().putBoolean("allow_drag", v).apply()

    fun rememberPosition(c: Context): Boolean = p(c).getBoolean("remember_pos", true)
    fun setRememberPosition(c: Context, v: Boolean) = p(c).edit().putBoolean("remember_pos", v).apply()

    /** Separate saved positions for portrait and landscape. -1 = unset. */
    fun savedY(c: Context, landscape: Boolean): Int =
        p(c).getInt(if (landscape) "pos_y_land" else "pos_y_port", -1)

    fun setSavedY(c: Context, landscape: Boolean, y: Int) =
        p(c).edit().putInt(if (landscape) "pos_y_land" else "pos_y_port", y).apply()

    fun resetPositions(c: Context) =
        p(c).edit().remove("pos_y_port").remove("pos_y_land").apply()

    // --- Reading assistance ------------------------------------------------
    fun fewerWordsPerLine(c: Context): Boolean = p(c).getBoolean("fewer_words", false)
    fun setFewerWordsPerLine(c: Context, v: Boolean) = p(c).edit().putBoolean("fewer_words", v).apply()

    fun dyslexiaFriendly(c: Context): Boolean = p(c).getBoolean("dyslexia", false)
    fun setDyslexiaFriendly(c: Context, v: Boolean) = p(c).edit().putBoolean("dyslexia", v).apply()

    fun emphasizePunctuation(c: Context): Boolean = p(c).getBoolean("emphasize_punct", false)
    fun setEmphasizePunctuation(c: Context, v: Boolean) = p(c).edit().putBoolean("emphasize_punct", v).apply()

    fun autoParagraphBreaks(c: Context): Boolean = p(c).getBoolean("auto_paragraphs", true)
    fun setAutoParagraphBreaks(c: Context, v: Boolean) = p(c).edit().putBoolean("auto_paragraphs", v).apply()

    // --- Privacy -----------------------------------------------------------
    fun keepOnDevice(c: Context): Boolean = true // scripts never leave the phone

    fun hideOnLockScreen(c: Context): Boolean = p(c).getBoolean("hide_lock", true)
    fun setHideOnLockScreen(c: Context, v: Boolean) = p(c).edit().putBoolean("hide_lock", v).apply()

    fun hideOverSensitiveApps(c: Context): Boolean = p(c).getBoolean("hide_sensitive", true)
    fun setHideOverSensitiveApps(c: Context, v: Boolean) = p(c).edit().putBoolean("hide_sensitive", v).apply()

    /** Days after which drafts are dropped; 0 = never. */
    fun autoDeleteDraftDays(c: Context): Int = p(c).getInt("draft_days", 0)
    fun setAutoDeleteDraftDays(c: Context, v: Int) = p(c).edit().putInt("draft_days", v).apply()

    // --- Derived helpers ---------------------------------------------------

    /** Words in [script], used for the duration estimate. */
    fun wordCount(script: String): Int =
        script.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    /** Estimated speaking time in seconds at the chosen speed. */
    fun estimatedSeconds(c: Context, script: String): Int {
        val words = wordCount(script)
        val wpm = speedWpm(c).coerceAtLeast(1)
        return Math.round(words * 60f / wpm)
    }

    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) String.format("%d min %02d sec", m, s) else "$s sec"
    }
}

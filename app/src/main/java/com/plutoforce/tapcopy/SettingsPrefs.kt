package com.plutoforce.tapcopy

import android.content.Context

/** Central store for user settings, with sensible defaults. */
object SettingsPrefs {

    private const val NAME = "tapcopy_settings"

    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // Floating button
    fun buttonSize(c: Context): String = p(c).getString("btn_size", "medium").orEmpty() // small/medium/large
    fun setButtonSize(c: Context, v: String) = p(c).edit().putString("btn_size", v).apply()
    fun buttonSizeDp(c: Context): Int = when (buttonSize(c)) {
        "small" -> 40; "large" -> 60; else -> 48
    }

    fun buttonOpacity(c: Context): Int = p(c).getInt("btn_opacity", 100) // 40..100
    fun setButtonOpacity(c: Context, v: Int) = p(c).edit().putInt("btn_opacity", v).apply()

    fun autoHide(c: Context): Boolean = p(c).getBoolean("auto_hide", false)
    fun setAutoHide(c: Context, v: Boolean) = p(c).edit().putBoolean("auto_hide", v).apply()

    // Persisted bubble position (-1 = use default corner)
    fun bubbleX(c: Context): Int = p(c).getInt("bubble_x", -1)
    fun bubbleY(c: Context): Int = p(c).getInt("bubble_y", -1)
    fun setBubblePos(c: Context, x: Int, y: Int) = p(c).edit().putInt("bubble_x", x).putInt("bubble_y", y).apply()
    fun resetBubblePos(c: Context) = p(c).edit().remove("bubble_x").remove("bubble_y").apply()

    // Capture & copy
    // Off by default: guessing which blocks are the caption reached too far and
    // ticked search bars and comment boxes. Choosing is the user's job; this is
    // here for anyone who wants the guess back.
    fun autoDetectCaptions(c: Context): Boolean = p(c).getBoolean("auto_detect", false)
    fun setAutoDetectCaptions(c: Context, v: Boolean) = p(c).edit().putBoolean("auto_detect", v).apply()

    fun defaultAction(c: Context): String = p(c).getString("default_action", "ask").orEmpty() // ask/caption/all
    fun setDefaultAction(c: Context, v: String) = p(c).edit().putString("default_action", v).apply()

    fun selectionMode(c: Context): String = p(c).getString("selection_mode", "block").orEmpty() // block/line
    fun setSelectionMode(c: Context, v: String) = p(c).edit().putString("selection_mode", v).apply()

    fun stitchEnabled(c: Context): Boolean = p(c).getBoolean("stitch_enabled", true)
    fun setStitchEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("stitch_enabled", v).apply()

    // OCR & language
    fun improveCleanup(c: Context): Boolean = p(c).getBoolean("improve_cleanup", true)
    fun setImproveCleanup(c: Context, v: Boolean) = p(c).edit().putBoolean("improve_cleanup", v).apply()

    // Privacy & storage
    fun deleteScreenshots(c: Context): Boolean = p(c).getBoolean("delete_shots", true)
    fun setDeleteScreenshots(c: Context, v: Boolean) = p(c).edit().putBoolean("delete_shots", v).apply()

    fun retentionDays(c: Context): Int = p(c).getInt("retention_days", 30) // 0 = forever
    fun setRetentionDays(c: Context, v: Int) = p(c).edit().putInt("retention_days", v).apply()

    // Notifications & feedback
    fun haptics(c: Context): Boolean = p(c).getBoolean("haptics", true)
    fun setHaptics(c: Context, v: Boolean) = p(c).edit().putBoolean("haptics", v).apply()

    fun copyToast(c: Context): Boolean = p(c).getBoolean("copy_toast", true)
    fun setCopyToast(c: Context, v: Boolean) = p(c).edit().putBoolean("copy_toast", v).apply()

    fun soundOnCopy(c: Context): Boolean = p(c).getBoolean("sound_copy", false)
    fun setSoundOnCopy(c: Context, v: Boolean) = p(c).edit().putBoolean("sound_copy", v).apply()

    // Appearance. The app picks its own dark/light rather than following the
    // phone, because the floating button is used inside other people's apps and
    // a surprise switch mid-session is worse than a setting.
    fun darkMode(c: Context): Boolean = p(c).getBoolean("dark_mode", true)
    fun setDarkMode(c: Context, v: Boolean) = p(c).edit().putBoolean("dark_mode", v).apply()

    fun accent(c: Context): String = p(c).getString("accent", "purple").orEmpty()
    fun setAccent(c: Context, v: String) = p(c).edit().putString("accent", v).apply()

    fun textScale(c: Context): Float = p(c).getFloat("text_scale", 1.0f)
    fun setTextScale(c: Context, v: Float) = p(c).edit().putFloat("text_scale", v).apply()

    /** Target language for Translate. Empty means "ask me each time". */
    fun translateLang(c: Context): String = p(c).getString("translate_lang", "").orEmpty()
    fun setTranslateLang(c: Context, v: String) = p(c).edit().putString("translate_lang", v).apply()
}

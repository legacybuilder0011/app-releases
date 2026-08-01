package com.plutoforce.tapcopy

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/**
 * Look and feel: dark or light, app text size, and the accent colour.
 *
 * Dark/light and text size ride on the resource system — [wrap] hands an
 * activity a context whose configuration says "night" (or not) and carries the
 * font scale, so `values/colors.xml` and `values-night/colors.xml` do the work
 * and no layout needs to know.
 *
 * The accent is different: it is one colour used in dozens of drawables, and
 * resources can't be swapped at runtime. So [applyAccent] walks the inflated
 * screen and repaints anything drawn in the default purple.
 */
object AppTheme {

    data class Accent(
        val key: String,
        val label: String,
        val light: Int,
        val base: Int,
        val dark: Int,
    )

    val ACCENTS = listOf(
        Accent("purple", "Purple", 0xFF8B6BFF.toInt(), 0xFF7C5CFF.toInt(), 0xFF5A3FE0.toInt()),
        Accent("blue", "Blue", 0xFF60A5FA.toInt(), 0xFF3B82F6.toInt(), 0xFF1D4ED8.toInt()),
        Accent("green", "Green", 0xFF34D399.toInt(), 0xFF10B981.toInt(), 0xFF047857.toInt()),
        Accent("orange", "Orange", 0xFFFB923C.toInt(), 0xFFF97316.toInt(), 0xFFC2410C.toInt()),
        Accent("pink", "Pink", 0xFFF472B6.toInt(), 0xFFEC4899.toInt(), 0xFFBE185D.toInt()),
        Accent("red", "Red", 0xFFF87171.toInt(), 0xFFEF4444.toInt(), 0xFFB91C1C.toInt()),
        Accent("teal", "Teal", 0xFF2DD4BF.toInt(), 0xFF14B8A6.toInt(), 0xFF0F766E.toInt()),
        Accent("gold", "Gold", 0xFFFBBF24.toInt(), 0xFFF59E0B.toInt(), 0xFFB45309.toInt()),
    )

    val DEFAULT = ACCENTS.first()

    // Every purple the design ships with, so all of them move together.
    private const val RGB = 0x00FFFFFF
    private const val BRAND_LIGHT = 0x8B6BFF
    private const val BRAND_LIGHT2 = 0x9B7BFF
    private const val BRAND_BASE = 0x7C5CFF
    private const val BRAND_DARK = 0x5A3FE0
    private const val BRAND_DARK2 = 0x6A47F5

    fun accent(context: Context): Accent =
        ACCENTS.firstOrNull { it.key == SettingsPrefs.accent(context) } ?: DEFAULT

    /**
     * A context carrying the user's dark/light choice and text size.
     *
     * [forceDark] is for the capture sheet and the floating overlays: they are
     * drawn on top of whatever app is open, over a screenshot or a video, and
     * light panels there wash out against the content underneath. The Dark mode
     * switch governs TapCopy's own screens.
     */
    fun wrap(base: Context, forceDark: Boolean = false): Context {
        val config = Configuration(base.resources.configuration)
        config.fontScale = SettingsPrefs.textScale(base)
        val night =
            if (forceDark || SettingsPrefs.darkMode(base)) Configuration.UI_MODE_NIGHT_YES
            else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(config)
    }

    /** Changes when any look-and-feel choice changes, so screens know to redraw. */
    fun stamp(context: Context): String =
        "${SettingsPrefs.darkMode(context)}|${SettingsPrefs.accent(context)}|${SettingsPrefs.textScale(context)}"

    /** Repaints the default purple with the chosen accent, top to bottom. */
    fun applyAccent(root: View, context: Context) {
        val accent = accent(context)
        if (accent.key == DEFAULT.key) return
        paint(root, accent)
    }

    private fun paint(view: View, accent: Accent) {
        view.background?.let { view.background = recolor(it, accent) }
        when (view) {
            is TextView -> {
                map(view.currentTextColor, accent)?.let { view.setTextColor(it) }
                view.compoundDrawablesRelative.filterNotNull().forEach { recolor(it, accent) }
            }
            is ImageView -> {
                val tint = view.imageTintList?.defaultColor
                if (tint != null) {
                    map(tint, accent)?.let { view.imageTintList = ColorStateList.valueOf(it) }
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) paint(view.getChildAt(i), accent)
        }
    }

    /**
     * Repaints one drawable in place. mutate() first, because drawables loaded
     * from the same resource share their state — without it, recolouring one
     * button recolours every copy of it, including screens using the default.
     */
    private fun recolor(drawable: Drawable, accent: Accent): Drawable {
        val mutable = drawable.mutate()
        when (val d = mutable) {
            is GradientDrawable -> {
                d.color?.defaultColor?.let { solid ->
                    map(solid, accent)?.let { d.setColor(it) }
                }
                d.colors?.let { gradient ->
                    var changed = false
                    val out = IntArray(gradient.size) { i ->
                        val mapped = map(gradient[i], accent)
                        if (mapped != null) {
                            changed = true
                            mapped
                        } else {
                            gradient[i]
                        }
                    }
                    if (changed) d.colors = out
                }
            }
            is LayerDrawable -> {
                for (i in 0 until d.numberOfLayers) d.getDrawable(i)?.let { recolor(it, accent) }
            }
            is StateListDrawable -> {
                for (i in 0 until d.stateCount) d.getStateDrawable(i)?.let { recolor(it, accent) }
            }
        }
        return mutable
    }

    /** The accent equivalent of a brand colour, keeping its transparency. */
    private fun map(color: Int, accent: Accent): Int? {
        val alpha = color.toLong() and 0xFF000000L
        return when (color and RGB) {
            BRAND_LIGHT, BRAND_LIGHT2 -> (alpha or (accent.light.toLong() and RGB.toLong())).toInt()
            BRAND_BASE -> (alpha or (accent.base.toLong() and RGB.toLong())).toInt()
            BRAND_DARK, BRAND_DARK2 -> (alpha or (accent.dark.toLong() and RGB.toLong())).toInt()
            else -> null
        }
    }
}

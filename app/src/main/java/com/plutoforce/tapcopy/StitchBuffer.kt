package com.plutoforce.tapcopy

import android.content.Context

/**
 * Holds text collected across several captures so a caption that is longer than
 * one screen can be stitched into a single copy.
 *
 * The buffer lives in SharedPreferences because the user leaves TapCopy between
 * captures (to scroll the underlying app), which tears down the selection
 * activity each time. Persisting here lets the next capture resume the same
 * stitch session.
 */
object StitchBuffer {

    private const val PREFS = "tapcopy_stitch"
    private const val KEY_TEXT = "text"
    private const val KEY_ACTIVE = "active"
    private const val KEY_COUNT = "count"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun count(context: Context): Int = prefs(context).getInt(KEY_COUNT, 0)

    fun text(context: Context): String = prefs(context).getString(KEY_TEXT, "").orEmpty()

    /** Begins a fresh stitch session with an empty buffer. */
    fun start(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_TEXT, "")
            .putInt(KEY_COUNT, 0)
            .apply()
    }

    /** Adds another piece, dropping any lines that overlap what we already have. */
    fun append(context: Context, newText: String) {
        val addition = newText.trim()
        if (addition.isBlank()) return
        val merged = dropRepeats(mergeWithOverlap(text(context), addition))
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_TEXT, merged)
            .putInt(KEY_COUNT, count(context) + 1)
            .apply()
    }

    /** Ends the session and empties the buffer. */
    fun clear(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .putString(KEY_TEXT, "")
            .putInt(KEY_COUNT, 0)
            .apply()
    }

    private fun normalize(line: String): String =
        line.trim().replace(Regex("\\s+"), " ").lowercase()

    /**
     * Drops lines already in the buffer.
     *
     * Two captures of the same post rarely line up exactly — the user scrolls a
     * little, so the overlap merge above only catches a run of lines repeated
     * end-to-start. Anything that comes back a second time further down (the
     * whole caption, when the second capture caught most of the first) is
     * dropped here.
     *
     * Only lines with something to them are compared: a caption may legitimately
     * repeat a short line like "daily", and losing one would be worse than
     * keeping a duplicate.
     */
    private fun dropRepeats(text: String): String {
        val seen = HashSet<String>()
        val kept = ArrayList<String>()
        for (line in text.lines()) {
            val key = normalize(line)
            if (key.length >= 8 && !seen.add(key)) continue
            kept.add(line)
        }
        return kept.joinToString("\n")
    }

    /**
     * Joins two blocks of text so that lines repeated at the end of [existing]
     * and again at the start of [addition] are only kept once. This is what
     * removes the duplicate lines produced when two scrolled captures overlap.
     */
    private fun mergeWithOverlap(existing: String, addition: String): String {
        if (existing.isBlank()) return addition

        val existingLines = existing.lines()
        val additionLines = addition.lines()
        val maxOverlap = minOf(existingLines.size, additionLines.size)

        var overlap = 0
        for (k in maxOverlap downTo 1) {
            val tail = existingLines.takeLast(k).map(::normalize)
            val head = additionLines.take(k).map(::normalize)
            if (tail == head) {
                overlap = k
                break
            }
        }

        val remainder = additionLines.drop(overlap)
        if (remainder.isEmpty()) return existing
        return (existingLines + remainder).joinToString("\n")
    }
}

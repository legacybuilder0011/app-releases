package com.plutoforce.tapcopy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists a history of everything copied, so a caption is never lost. Stored as
 * a JSON array in SharedPreferences. Newest first, capped so it can't grow
 * without bound.
 */
object CopyStore {

    private const val PREFS = "tapcopy_history"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 300

    data class Entry(val id: Long, val text: String, val favorite: Boolean)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Entry> {
        val raw = prefs(context).getString(KEY, "[]").orEmpty()
        val out = ArrayList<Entry>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                out.add(Entry(o.getLong("id"), o.getString("text"), o.optBoolean("fav", false)))
            }
        }
        return out
    }

    /** Adds a copy to the top. Skips if identical to the most recent entry. */
    fun add(context: Context, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val entries = all(context).toMutableList()
        if (entries.firstOrNull()?.text == clean) return
        entries.add(0, Entry(System.currentTimeMillis(), clean, false))
        save(context, entries.take(MAX_ENTRIES))
    }

    fun toggleFavorite(context: Context, id: Long) {
        val entries = all(context).map {
            if (it.id == id) it.copy(favorite = !it.favorite) else it
        }
        save(context, entries)
    }

    fun delete(context: Context, id: Long) {
        save(context, all(context).filterNot { it.id == id })
    }

    fun clearAll(context: Context) {
        save(context, emptyList())
    }

    /** Drops entries older than [days] (entry id is its creation time in millis). */
    fun pruneOlderThan(context: Context, days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        val kept = all(context).filter { it.id >= cutoff }
        save(context, kept)
    }

    private fun save(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("text", e.text)
                    .put("fav", e.favorite)
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }
}

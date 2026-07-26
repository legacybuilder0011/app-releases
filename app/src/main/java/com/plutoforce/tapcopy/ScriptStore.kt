package com.plutoforce.tapcopy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved Teleprompter scripts. Everything stays on the device — a JSON array in
 * SharedPreferences, newest first.
 */
object ScriptStore {

    private const val PREFS = "tapcopy_scripts"
    private const val KEY = "items"
    private const val MAX = 200

    data class Script(
        val id: Long,
        val title: String,
        val text: String,
        val createdAt: Long,
        val lastPosition: Int,
        val favorite: Boolean
    ) {
        val wordCount: Int get() = TeleprompterPrefs.wordCount(text)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Script> {
        val out = ArrayList<Script>()
        runCatching {
            val array = JSONArray(prefs(context).getString(KEY, "[]").orEmpty())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                out.add(
                    Script(
                        o.getLong("id"),
                        o.optString("title"),
                        o.optString("text"),
                        o.optLong("created", o.getLong("id")),
                        o.optInt("pos", 0),
                        o.optBoolean("fav", false)
                    )
                )
            }
        }
        return out
    }

    /** Saves a script, or updates the one with a matching id. Returns its id. */
    fun save(context: Context, text: String, id: Long? = null, title: String? = null): Long {
        val clean = text.trim()
        if (clean.isBlank()) return -1L
        val items = all(context).toMutableList()
        val existingIndex = id?.let { wanted -> items.indexOfFirst { it.id == wanted } } ?: -1

        return if (existingIndex >= 0) {
            val existing = items[existingIndex]
            items[existingIndex] = existing.copy(text = clean, title = title ?: existing.title)
            persist(context, items)
            existing.id
        } else {
            val newId = System.currentTimeMillis()
            items.add(
                0,
                Script(newId, title ?: titleFor(clean), clean, newId, 0, false)
            )
            persist(context, items.take(MAX))
            newId
        }
    }

    /** First few words, used as a readable title. */
    fun titleFor(text: String): String {
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val words = firstLine.split(Regex("\\s+")).filter { it.isNotBlank() }
        val short = words.take(6).joinToString(" ")
        return when {
            short.isBlank() -> "Untitled script"
            words.size > 6 -> "$short…"
            else -> short
        }
    }

    fun rename(context: Context, id: Long, title: String) {
        persist(context, all(context).map { if (it.id == id) it.copy(title = title.trim()) else it })
    }

    fun duplicate(context: Context, id: Long) {
        val source = all(context).firstOrNull { it.id == id } ?: return
        val items = all(context).toMutableList()
        val newId = System.currentTimeMillis()
        items.add(0, source.copy(id = newId, title = source.title + " (copy)", createdAt = newId, lastPosition = 0))
        persist(context, items.take(MAX))
    }

    fun toggleFavorite(context: Context, id: Long) {
        persist(context, all(context).map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    }

    fun setLastPosition(context: Context, id: Long, position: Int) {
        persist(context, all(context).map { if (it.id == id) it.copy(lastPosition = position) else it })
    }

    fun delete(context: Context, id: Long) {
        persist(context, all(context).filterNot { it.id == id })
    }

    fun clearAll(context: Context) = persist(context, emptyList())

    private fun persist(context: Context, items: List<Script>) {
        val array = JSONArray()
        items.forEach { s ->
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("text", s.text)
                    .put("created", s.createdAt)
                    .put("pos", s.lastPosition)
                    .put("fav", s.favorite)
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }
}

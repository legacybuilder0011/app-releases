package com.plutoforce.tapcopy

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Browse, search, favourite, re-copy and delete previously copied text. */
class HistoryActivity : ThemedActivity() {

    companion object {
        const val EXTRA_FAVORITES = "favorites"
    }

    private lateinit var listView: ListView
    private lateinit var searchBox: EditText
    private lateinit var favOnly: CheckBox
    private lateinit var emptyText: TextView
    private lateinit var adapter: HistoryAdapter

    private var shown: List<CopyStore.Entry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        listView = findViewById(R.id.historyList)
        searchBox = findViewById(R.id.searchBox)
        favOnly = findViewById(R.id.favOnly)
        emptyText = findViewById(R.id.emptyText)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        // Opened from "Favourites" → start filtered to favourites.
        if (intent.getBooleanExtra(EXTRA_FAVORITES, false)) {
            favOnly.isChecked = true
            findViewById<TextView>(R.id.screenTitle).text = getString(R.string.favorites_only)
        }

        adapter = HistoryAdapter()
        listView.adapter = adapter

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = refresh()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        favOnly.setOnCheckedChangeListener { _, _ -> refresh() }

        findViewById<Button>(R.id.clearAllButton).setOnClickListener {
            if (shown.isEmpty() && CopyStore.all(this).isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_all))
                .setMessage("Delete all copy history?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    CopyStore.clearAll(this)
                    refresh()
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val query = searchBox.text.toString().trim().lowercase()
        shown = CopyStore.all(this).filter { entry ->
            (!favOnly.isChecked || entry.favorite) &&
                (query.isEmpty() || entry.text.lowercase().contains(query))
        }
        adapter.notifyDataSetChanged()
        emptyText.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Copied screen text", text))
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    private inner class HistoryAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = shown[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(this@HistoryActivity)
                    .inflate(R.layout.item_history, parent, false)

            val entry = shown[position]
            view.findViewById<TextView>(R.id.itemText).text = entry.text

            val fav = view.findViewById<Button>(R.id.itemFav)
            fav.text = if (entry.favorite) "★" else "☆"
            fav.setOnClickListener {
                CopyStore.toggleFavorite(this@HistoryActivity, entry.id)
                refresh()
            }

            view.findViewById<Button>(R.id.itemCopy).setOnClickListener {
                copyToClipboard(entry.text)
            }
            view.findViewById<Button>(R.id.itemDelete).setOnClickListener {
                CopyStore.delete(this@HistoryActivity, entry.id)
                refresh()
            }
            return view
        }
    }
}

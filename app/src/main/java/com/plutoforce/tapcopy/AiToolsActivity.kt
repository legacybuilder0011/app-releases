package com.plutoforce.tapcopy

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * AI Tools — rewrite, shorten, translate and friends for whatever you just
 * copied.
 *
 * The work happens on the TapSave backend, which already holds the key for
 * TapSave's transcripts. Only the text in the box on this screen is sent.
 */
class AiToolsActivity : ThemedActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var scroll: ScrollView
    private lateinit var source: EditText
    private lateinit var status: TextView
    private lateinit var resultCard: View
    private lateinit var resultTitle: TextView
    private lateinit var resultBody: TextView

    private var running = false
    private var result = ""

    companion object {
        /** Text to work on, when another screen hands it over. */
        const val EXTRA_TOOL = "tool"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_tools)

        scroll = findViewById(R.id.aiScroll)
        source = findViewById(R.id.sourceText)
        status = findViewById(R.id.aiStatus)
        resultCard = findViewById(R.id.resultCard)
        resultTitle = findViewById(R.id.resultTitle)
        resultBody = findViewById(R.id.resultText)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        source.setText(startingText())

        findViewById<View>(R.id.useLastCopy).setOnClickListener {
            val last = CopyStore.all(this).firstOrNull()?.text
            if (last.isNullOrBlank()) toast("Nothing copied yet") else source.setText(last)
        }
        findViewById<View>(R.id.usePaste).setOnClickListener {
            val clip = clipboardText()
            if (clip.isNullOrBlank()) toast("Clipboard is empty") else source.setText(clip)
        }
        findViewById<View>(R.id.useClear).setOnClickListener {
            source.setText("")
            hideResult()
        }

        wire(R.id.toolCleanup, AiClient.CLEANUP, "Cleaned up")
        wire(R.id.toolRewrite, AiClient.REWRITE, "Rewritten")
        wire(R.id.toolShorten, AiClient.SHORTEN, "Shortened")
        wire(R.id.toolTranslate, AiClient.TRANSLATE, "Translated")
        wire(R.id.toolHook, AiClient.HOOK, "With a hook")
        wire(R.id.toolTone, AiClient.TONE, "New tone")
        wire(R.id.toolReword, AiClient.REWORD, "Reworded")

        findViewById<View>(R.id.resultCopy).setOnClickListener { copyResult() }
        findViewById<View>(R.id.resultShare).setOnClickListener { shareResult() }
        findViewById<View>(R.id.resultReuse).setOnClickListener {
            source.setText(result)
            hideResult()
        }

        // Arriving straight from the copy sheet with a job already chosen.
        intent?.getStringExtra(EXTRA_TOOL)?.let { tool ->
            val label = labelFor(tool)
            source.post { start(tool, label) }
        }
    }

    private fun labelFor(tool: String): String = when (tool) {
        AiClient.CLEANUP -> "Cleaned up"
        AiClient.REWRITE -> "Rewritten"
        AiClient.SHORTEN -> "Shortened"
        AiClient.TRANSLATE -> "Translated"
        AiClient.HOOK -> "With a hook"
        AiClient.TONE -> "New tone"
        else -> "Reworded"
    }

    /** Whatever the user most likely wants to work on, so the box is rarely empty. */
    private fun startingText(): String {
        intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { return it }
        CopyStore.all(this).firstOrNull()?.text?.let { return it }
        return clipboardText().orEmpty()
    }

    private fun wire(viewId: Int, tool: String, label: String) {
        findViewById<View>(viewId).setOnClickListener { start(tool, label) }
    }

    private fun start(tool: String, label: String) {
        val text = source.text.toString().trim()
        if (text.isBlank()) {
            toast("Add some text first")
            return
        }
        when (tool) {
            AiClient.TRANSLATE -> {
                // Settings can pin a language; otherwise ask, saved one first.
                val preferred = SettingsPrefs.translateLang(this)
                if (preferred.isNotBlank()) {
                    run(tool, text, preferred, "$label → $preferred")
                } else {
                    choose("Translate to", AiClient.LANGUAGES) { run(tool, text, it, "$label → $it") }
                }
            }
            AiClient.TONE -> choose("Make it sound", AiClient.TONES) { run(tool, text, it, "Tone: $it") }
            else -> run(tool, text, "", label)
        }
    }

    /** A plain list picker, with a way in for anything not on the list. */
    private fun choose(title: String, options: List<String>, onPick: (String) -> Unit) {
        val items = (options + "Something else…").map { it as CharSequence }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, index ->
                if (index == options.size) askForOption(title, onPick) else onPick(options[index])
            }
            .show()
    }

    private fun askForOption(title: String, onPick: (String) -> Unit) {
        val input = EditText(this).apply { hint = "Type it" }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val typed = input.text.toString().trim()
                if (typed.isNotBlank()) onPick(typed)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun run(tool: String, text: String, option: String, label: String) {
        if (running) return
        running = true
        hideResult()
        setStatus("Working…")

        // The free server sleeps between uses, so say so instead of looking stuck.
        val waking = Runnable {
            if (running) setStatus("Still working — the server may be waking up.")
        }
        handler.postDelayed(waking, 12_000)

        Thread {
            val outcome = runCatching { AiClient.run(tool, text, option) }
            handler.post {
                handler.removeCallbacks(waking)
                running = false
                outcome
                    .onSuccess {
                        setStatus(null)
                        showResult(label, it)
                    }
                    .onFailure {
                        setStatus(it.message ?: "That didn't work. Try again.")
                    }
            }
        }.start()
    }

    private fun showResult(label: String, text: String) {
        result = text
        resultTitle.text = label
        resultBody.text = text
        resultCard.visibility = View.VISIBLE
        scroll.post { scroll.smoothScrollTo(0, resultCard.top) }
    }

    private fun hideResult() {
        resultCard.visibility = View.GONE
        result = ""
    }

    private fun setStatus(message: String?) {
        if (message == null) {
            status.visibility = View.GONE
        } else {
            status.text = message
            status.visibility = View.VISIBLE
        }
    }

    private fun copyResult() {
        clipboard().setPrimaryClip(ClipData.newPlainText("TapCopy", result))
        CopyStore.add(this, result)
        toast("Copied")
    }

    private fun shareResult() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, result)
        }
        startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun clipboard() = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipboardText(): String? = runCatching {
        val clip = clipboard().primaryClip ?: return@runCatching null
        if (clip.itemCount <= 0) null else clip.getItemAt(0).coerceToText(this)?.toString()
    }.getOrNull()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

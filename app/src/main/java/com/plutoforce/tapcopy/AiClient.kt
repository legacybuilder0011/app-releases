package com.plutoforce.tapcopy

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the TapSave backend's /ai endpoint — the same server that already
 * does TapSave's transcripts, and the same key behind it.
 *
 * The key stays on the server on purpose: anything shipped inside an APK can be
 * pulled back out of it. Only the text the user hands to the AI Tools screen is
 * ever sent; screenshots and recognized text stay on the phone as before.
 */
object AiClient {

    private const val BASE = "https://tapsave-backend.onrender.com"

    /** Offered for Translate, in Settings and on the AI Tools screen. */
    val LANGUAGES = listOf(
        "English", "Pidgin", "Yoruba", "Igbo", "Hausa", "French", "Spanish",
        "Portuguese", "Arabic", "Swahili", "German", "Hindi", "Chinese"
    )

    val TONES = listOf(
        "professional", "friendly", "funny", "confident", "casual", "polite",
        "urgent", "gentle"
    )

    const val REWRITE = "rewrite"
    const val SHORTEN = "shorten"
    const val TRANSLATE = "translate"
    const val HOOK = "hook"
    const val TONE = "tone"
    const val REWORD = "reword"

    /**
     * Runs one tool and returns the finished text. Blocking — call it off the
     * main thread. Throws with the server's own words when it refuses, since a
     * generic "something went wrong" hides the one useful detail.
     */
    fun run(tool: String, text: String, option: String = ""): String {
        val body = JSONObject()
            .put("tool", tool)
            .put("text", text)
            .put("option", option)
            .toString()

        val conn = (URL("$BASE/ai").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            // The free server sleeps when idle; a cold start costs about a
            // minute, and waiting beats failing.
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        return conn.use {
            it.outputStream.use { out -> out.write(body.toByteArray()) }
            if (it.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(readError(it))
            }
            val reply = it.inputStream.bufferedReader().use { r -> r.readText() }
            val answer = JSONObject(reply).optString("result").trim()
            if (answer.isEmpty()) throw IllegalStateException("The AI sent nothing back.")
            answer
        }
    }

    /** FastAPI puts the readable reason in "detail"; fall back to the raw body. */
    private fun readError(conn: HttpURLConnection): String {
        val raw = runCatching {
            conn.errorStream?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        val detail = runCatching { JSONObject(raw).optString("detail") }.getOrNull()
        return when {
            !detail.isNullOrBlank() -> detail
            raw.isNotBlank() -> raw.take(200)
            else -> "The server said ${conn.responseCode}."
        }
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}

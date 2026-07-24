package com.plutoforce.tapcopy

/**
 * Pulls structured pieces out of recognized screen text: hashtags, mentions,
 * links, emails, phone numbers, and a "clean caption" with the usual social-app
 * clutter (like counts, buttons, dates, music titles, bare usernames) removed.
 */
object TextExtractor {

    private val HASHTAG = Regex("#[\\p{L}0-9_]+")
    private val MENTION = Regex("@[\\p{L}0-9_.]{2,}")
    private val URL = Regex("(?:https?://|www\\.)[^\\s]+", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.-]+")
    private val PHONE = Regex("\\+?\\d[\\d\\s()./-]{6,}\\d")

    // Lines that are almost always UI chrome rather than caption text.
    private val BUTTON_WORDS = setOf(
        "like", "likes", "reply", "replies", "share", "shares", "follow",
        "following", "followed", "subscribe", "subscribed", "comment", "comments",
        "save", "saved", "remix", "download", "duet", "stitch", "send",
        "add comment", "view all", "see more", "see less", "more", "less",
        "see translation", "show translation", "original", "sponsored", "promoted",
        "suggested for you", "verified",
        // App navigation / tab chrome that isn't part of a caption.
        "home", "search", "friends", "inbox", "profile", "shop", "live",
        "following", "for you", "community", "explore", "explore now",
        "messages", "notifications", "back", "reels", "discover", "upload",
        "activity", "add", "post", "log in", "sign up", "subscribe"
    )

    private val COUNT = Regex("^\\d+([.,]\\d+)?\\s*[kmb]?\\s*(likes|views|comments|shares|plays)?$", RegexOption.IGNORE_CASE)
    private val REL_DATE = Regex("^\\d+\\s*(s|m|h|d|w|y|sec|secs|second|seconds|min|mins|minute|minutes|hour|hours|day|days|week|weeks|month|months|year|years)( ago)?$", RegexOption.IGNORE_CASE)
    private val MONTH_DATE = Regex("^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s*\\d{1,2}(,?\\s*\\d{4})?$", RegexOption.IGNORE_CASE)
    private val ONLY_HANDLE = Regex("^@[\\p{L}0-9_.]+$")
    private val MUSIC = Regex("(original sound|original audio|♪|🎵|•\\s*audio)", RegexOption.IGNORE_CASE)

    fun hashtags(text: String): List<String> = distinctMatches(HASHTAG, text)
    fun mentions(text: String): List<String> = distinctMatches(MENTION, text)
    fun links(text: String): List<String> = distinctMatches(URL, text)
    fun emails(text: String): List<String> = distinctMatches(EMAIL, text)
    fun phones(text: String): List<String> =
        PHONE.findAll(text).map { it.value.trim() }.filter { it.count(Char::isDigit) >= 7 }.distinct().toList()

    private fun distinctMatches(regex: Regex, text: String): List<String> =
        regex.findAll(text).map { it.value.trim() }.filter { it.isNotBlank() }.distinct().toList()

    /** Light tidy: trim lines, collapse repeated spaces, drop blank lines. */
    fun tidy(text: String): String =
        text.lines()
            .map { it.trim().replace(Regex("[ \\t]{2,}"), " ") }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    /** Removes counts, buttons, dates, music titles and bare @handles line by line. */
    fun cleanCaption(text: String): String {
        val kept = text.lines().map { it.trim() }.filter { line ->
            if (line.isBlank()) return@filter false
            val lower = line.lowercase()
            when {
                lower in BUTTON_WORDS -> false
                COUNT.matches(line) -> false
                REL_DATE.matches(line) -> false
                MONTH_DATE.matches(line) -> false
                ONLY_HANDLE.matches(line) -> false
                MUSIC.containsMatchIn(line) -> false
                else -> true
            }
        }
        return kept.joinToString("\n").trim()
    }
}

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

    // Search boxes, tab bars and the like — never worth drawing a box around.
    private val NOISE_PHRASES = setOf(
        "find related content", "add comment...", "add comment…", "add comment",
        "search", "less", "more", "see more", "see less", "follow", "following",
        "add yours", "send message", "view profile", "share", "save", "report",
        "log in", "sign up", "watch again", "swipe up", "tap to unmute"
    )

    private val COUNT_ONLY = Regex("^\\d+([.,]\\d+)?\\s*[kmb]?$", RegexOption.IGNORE_CASE)
    // Status bar leftovers: "10%", "88 %", "3:07 4•", "3:08".
    private val PERCENT = Regex("^\\d{1,3}\\s*%$")
    private val STATUS_CLOCK = Regex("^\\d{1,2}:\\d{2}\\b.{0,12}$")
    // The search pill reads as "Q Find related content" — the magnifier becomes
    // a letter, so allow a stray glyph before the word.
    private val SEARCH_PILL = Regex("^\\W?\\s*\\w?\\s*(find related content|search .{0,30}|find .{0,30})$", RegexOption.IGNORE_CASE)
    // A music credit: a note glyph (which OCR usually reads as J) then
    // "Title - Artist". Kept narrow so a caption with a dash isn't caught.
    private val MUSIC_CREDIT = Regex("^[J\\u266a\\u266b\\u2669\\ud83c\\udfb5]\\s+.{1,40}\\s[-–]\\s.{1,30}$")
    // Music attribution and in-app search rows sit right beside the caption.
    private val ATTRIBUTION = Regex("^[\u266a\u266b\u2669\ud83c\udfb5]?\\s*(contains|original sound|sound)\\s*:", RegexOption.IGNORE_CASE)
    private val SEARCH_ROW = Regex("^(search|find)\\b.{0,60}$", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("^\\d{1,2}:\\d{2}(\\s*[ap]m)?$", RegexOption.IGNORE_CASE)
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /**
     * True for screen furniture — like counts, clocks, search fields, button
     * labels. These are highlighted as text but nobody wants to copy them, so
     * they only get in the way of picking the caption.
     */
    fun isNoise(text: String): Boolean {
        val line = text.trim()
        if (line.length <= 1) return true
        val lower = line.lowercase()
        // "Add comment.." and "Add comment…" are the same button.
        val bare = lower.trimEnd('.', '\u2026', ':', ' ')
        return when {
            lower in NOISE_PHRASES -> true
            bare in NOISE_PHRASES -> true
            lower in BUTTON_WORDS -> true
            bare in BUTTON_WORDS -> true
            PERCENT.matches(line) -> true
            STATUS_CLOCK.matches(line) -> true
            SEARCH_PILL.matches(line) -> true
            MUSIC_CREDIT.matches(line) -> true
            COUNT_ONLY.matches(line) -> true
            ATTRIBUTION.containsMatchIn(line) -> true
            SEARCH_ROW.matches(line) -> true
            CLOCK.matches(line) -> true
            ISO_DATE.matches(line) -> true
            REL_DATE.matches(line) -> true
            // Pure punctuation / emoji-only fragments.
            line.none { it.isLetterOrDigit() } -> true
            else -> false
        }
    }

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

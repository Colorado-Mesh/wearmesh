package org.coloradomesh.wearmesh.ui

/**
 * The quick replies (decided for M3): a starting set, which the user can change, add to and
 * remove from on the watch. Kept in settings as one reply per line. A quick reply sends at once,
 * without a preview.
 */
object QuickReplies {
    val DEFAULTS = listOf("OK", "Yes", "No", "On my way", "Copy", "Call me", "73")

    /** Enough to choose from on a watch; more would be a long scroll. */
    const val MAX = 12

    /** The saved list, or the starting set when none is saved. A saved empty list stays empty. */
    fun parse(saved: String?): List<String> =
        if (saved == null) DEFAULTS else saved.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun format(replies: List<String>): String = replies.joinToString("\n")

    /** [text] added at the end, unless it is blank, already there, or the list is full. */
    fun add(replies: List<String>, text: String): List<String> {
        val clean = clean(text) ?: return replies
        return if (clean in replies || replies.size >= MAX) replies else replies + clean
    }

    /** The reply at [index] changed to [text]; blank text removes it. */
    fun change(replies: List<String>, index: Int, text: String): List<String> {
        if (index !in replies.indices) return replies
        val clean = clean(text) ?: return remove(replies, index)
        return replies.toMutableList().also { it[index] = clean }.distinct()
    }

    fun remove(replies: List<String>, index: Int): List<String> = replies.filterIndexed { at, _ -> at != index }

    /** One line, trimmed; a reply cannot hold a line break, since the list is kept a line each. */
    private fun clean(text: String): String? = text.replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }
}

package org.coloradomesh.wearmesh

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How the log keeps message text out, and names a saved copy (M5 step 2). */
object LogPrivacy {
    /** [text] itself when [show], otherwise a placeholder with its length in UTF-16 code units. */
    fun text(text: String, show: Boolean): String = if (show) text else "(text hidden, ${text.length} characters)"

    /** A saved log's file name for [at], milliseconds since the Unix epoch, in the default time zone: wearmesh-log-2026-11-02-183005.txt. */
    fun saveName(at: Long): String = "wearmesh-log-${SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date(at))}.txt"
}

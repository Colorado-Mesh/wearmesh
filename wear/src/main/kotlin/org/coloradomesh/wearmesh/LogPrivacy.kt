package org.coloradomesh.wearmesh

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How the log keeps message text out, and names a saved copy (M5 step 2). */
object LogPrivacy {
    /** [text] itself when [show], otherwise only how long it was, so a shared log says what happened but not what was said. */
    fun text(text: String, show: Boolean): String = if (show) text else "(text hidden, ${text.length} characters)"

    /** A saved log's file name, by the watch's clock: wearmesh-log-2026-11-02-183005.txt. */
    fun saveName(at: Long): String = "wearmesh-log-${SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date(at))}.txt"
}

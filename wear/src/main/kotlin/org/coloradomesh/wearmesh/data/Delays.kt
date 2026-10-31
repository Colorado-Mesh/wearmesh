package org.coloradomesh.wearmesh.data

/**
 * How long messages took to reach the watch: the watch's time on storing each one less the time
 * its sender gave it. The sender's clock may be off, so a message "from the future" is counted
 * apart rather than as a delay of zero, and so is one more than [BEHIND_MS] old: a radio holds
 * messages for hours at most, so that is a sender whose clock is far behind (one in the M4 overnight
 * log claimed 21567 h).
 */
object Delays {
    /** For the log, such as "delay median 7 s, longest 6 min"; null with no messages. */
    fun summary(messages: List<IncomingMessage>): String? {
        if (messages.isEmpty()) return null
        val delays = messages.map { it.receivedAt - it.sentAt * 1000 }
        val ahead = delays.count { it < 0 }
        val behind = delays.count { it > BEHIND_MS }
        val known = delays.filter { it in 0..BEHIND_MS }.sorted()
        val parts = ArrayList<String>()
        if (known.isNotEmpty()) {
            parts.add("delay median ${format(known[(known.size - 1) / 2])}")
            if (known.size > 1) parts.add("longest ${format(known.last())}")
        }
        if (ahead > 0) parts.add("$ahead with the sender's clock ahead")
        if (behind > 0) parts.add("$behind with the sender's clock far behind")
        return parts.joinToString(", ")
    }

    /** Longer than any message waits on a radio: a day. */
    const val BEHIND_MS = 24 * 3_600_000L

    /** Whole seconds under 2 minutes, then minutes under 2 hours, then hours. */
    fun format(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 120 -> "$seconds s"
            seconds < 7200 -> "${seconds / 60} min"
            else -> "${seconds / 3600} h"
        }
    }
}

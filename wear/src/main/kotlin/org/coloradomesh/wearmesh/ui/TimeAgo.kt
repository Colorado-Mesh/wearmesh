package org.coloradomesh.wearmesh.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How long ago a message came, by the watch's clock: "now", "4 min", "2 h", then the weekday
 * within the week ("Mon") and the date after that ("Oct 27"). A sender's own clock can be years
 * off, so the time a message was received is what is shown.
 */
object TimeAgo {
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val date = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun format(nowMs: Long, thenMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val seconds = (nowMs - thenMs) / 1000
        return when {
            seconds < 60 -> "now"
            seconds < 3600 -> "${seconds / 60} min"
            seconds < 24 * 3600 -> "${seconds / 3600} h"
            seconds < 7 * 24 * 3600 -> weekday.format(Instant.ofEpochMilli(thenMs).atZone(zone))
            else -> date.format(Instant.ofEpochMilli(thenMs).atZone(zone))
        }
    }
}

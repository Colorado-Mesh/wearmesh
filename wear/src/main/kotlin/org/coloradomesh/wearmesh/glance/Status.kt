package org.coloradomesh.wearmesh.glance

import org.coloradomesh.meshcore.protocol.RadioBattery

/**
 * What the Status tile shows (decided 2 November 2026): the mode, when the watch last reached the
 * radio and when it will next, the radio's battery and name, and a failed last try. All from
 * saved settings, so the tile never wakes the radio.
 */
data class Status(
    val mode: Mode,
    val intervalMinutes: Int,
    /** Live's link state while Live runs, such as "Connected". */
    val liveLink: String?,
    val radioName: String?,
    val lastSyncAt: Long,
    val lastAttemptAt: Long,
    val lastProblem: String?,
    val nextCheckInAt: Long,
    val radioMillivolts: Int,
    val radioMillivoltsAt: Long,
) {
    enum class Mode { LIVE, CHECK_IN, QUIT }

    /** The tile's lines; [time] formats a watch-clock time as the watch shows times. */
    data class Lines(val heading: String, val big: String, val label: String, val details: List<String>, val warning: String?)

    fun lines(now: Long, time: (Long) -> String): Lines {
        val heading = when (mode) {
            Mode.LIVE -> "Live"
            Mode.CHECK_IN -> "Check-in every $intervalMinutes min"
            Mode.QUIT -> "Quit"
        }
        val (big, label) = when {
            mode == Mode.LIVE && liveLink != null -> liveLink to "Radio link"
            lastSyncAt > 0 -> time(lastSyncAt) to "Last synced"
            else -> "–" to "Not synced yet"
        }
        val details = ArrayList<String>()
        if (mode == Mode.CHECK_IN && nextCheckInAt > now) details.add("Next ${time(nextCheckInAt)}")
        val battery = RadioBattery.percent(radioMillivolts)?.takeIf { now - radioMillivoltsAt < BATTERY_FRESH_MS }
        val radio = listOfNotNull(battery?.let { "Radio $it%" }, radioName).joinToString(" · ")
        if (radio.isNotEmpty()) details.add(radio)
        // A try that failed after the last sync that worked.
        val warning = lastProblem?.takeIf { lastAttemptAt > lastSyncAt }?.let { "Last try failed" }
        return Lines(heading, big, label, details, warning)
    }

    companion object {
        /** A battery reading older than this is not shown: the radio may have charged or run down since. */
        const val BATTERY_FRESH_MS = 6 * 3_600_000L
    }
}

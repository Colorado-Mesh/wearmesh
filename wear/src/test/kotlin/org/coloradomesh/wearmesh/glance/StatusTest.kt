package org.coloradomesh.wearmesh.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusTest {
    private val now = 1_800_000_000_000L
    private val time: (Long) -> String = { "t+${(it - now) / 60_000}" }

    private fun status(
        mode: Status.Mode = Status.Mode.CHECK_IN,
        liveLink: String? = null,
        lastSyncAt: Long = now - 4 * 60_000,
        lastAttemptAt: Long = now - 4 * 60_000,
        lastProblem: String? = null,
        nextCheckInAt: Long = now + 11 * 60_000,
        millivolts: Int = 3_990,
        millivoltsAt: Long = now - 60_000,
    ) = Status(mode, 15, liveLink, "W0RMT-T1n", lastSyncAt, lastAttemptAt, lastProblem, nextCheckInAt, millivolts, millivoltsAt)

    @Test
    fun checkInShowsTheLastSyncTheNextAndTheRadio() {
        val lines = status().lines(now, time)
        assertEquals("Check-in every 15 min", lines.heading)
        assertEquals("t+-4", lines.big)
        assertEquals("Last synced", lines.label)
        // 3.99 V is about 80% on a lithium cell.
        assertEquals(listOf("Next t+11", "Radio 80% · W0RMT-T1n"), lines.details)
        assertNull(lines.warning)
    }

    @Test
    fun liveShowsItsLink() {
        val lines = status(mode = Status.Mode.LIVE, liveLink = "Connected").lines(now, time)
        assertEquals("Live", lines.heading)
        assertEquals("Connected", lines.big)
        assertEquals(listOf("Radio 80% · W0RMT-T1n"), lines.details)
    }

    @Test
    fun aFailedTryAfterTheLastSyncIsShown() {
        val lines = status(lastAttemptAt = now - 60_000, lastProblem = "Could not connect").lines(now, time)
        assertEquals("Last try failed", lines.warning)
        // An old failure, before the last sync, is not.
        assertNull(status(lastAttemptAt = now - 10 * 60_000, lastSyncAt = now - 4 * 60_000, lastProblem = "x").lines(now, time).warning)
    }

    @Test
    fun anOldBatteryReadingOrAPastAlarmIsLeftOut() {
        val lines = status(millivoltsAt = now - Status.BATTERY_FRESH_MS - 1, nextCheckInAt = now - 1).lines(now, time)
        assertEquals(listOf("W0RMT-T1n"), lines.details)
    }

    @Test
    fun neverSynced() {
        val lines = status(lastSyncAt = 0, lastAttemptAt = 0, mode = Status.Mode.QUIT).lines(now, time)
        assertEquals("Quit", lines.heading)
        assertEquals("Not synced yet", lines.label)
    }
}

package org.coloradomesh.wearmesh.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeAgoTest {
    private val denver = ZoneId.of("America/Denver")
    private val now = ZonedDateTime.of(2026, 10, 28, 9, 30, 0, 0, denver).toInstant().toEpochMilli()
    private fun ago(seconds: Long) = TimeAgo.format(now, now - seconds * 1000, denver)

    @Test
    fun recentTimesCountMinutesThenHours() {
        assertEquals("now", ago(0))
        assertEquals("now", ago(59))
        assertEquals("1 min", ago(60))
        assertEquals("59 min", ago(3599))
        assertEquals("1 h", ago(3600))
        assertEquals("23 h", ago(24 * 3600 - 1))
    }

    @Test
    fun olderTimesShowTheDay() {
        // Tuesday 27 October, 09:00 in Denver.
        assertEquals("Tue", ago(24 * 3600 + 1800))
        assertEquals("Oct 21", ago(7 * 24 * 3600))
    }

    @Test
    fun aTimeAheadOfTheWatchClockIsNow() {
        assertEquals("now", ago(-30))
    }
}

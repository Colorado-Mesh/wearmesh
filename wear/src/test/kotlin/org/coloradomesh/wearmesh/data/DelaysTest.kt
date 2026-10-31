package org.coloradomesh.wearmesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DelaysTest {
    private val sent = 1_800_000_000L

    private fun after(ms: Long) = IncomingMessage("c:0", "Ann", "hi", sent, sent * 1000 + ms)

    @Test
    fun noMessagesNoLine() {
        assertNull(Delays.summary(emptyList()))
    }

    @Test
    fun oneMessageGivesItsDelay() {
        assertEquals("delay median 7 s", Delays.summary(listOf(after(7_400))))
    }

    @Test
    fun medianAndLongest() {
        val messages = listOf(after(4_000), after(9 * 60_000), after(7_000), after(5_000), after(30_000))
        assertEquals("delay median 7 s, longest 9 min", Delays.summary(messages))
    }

    @Test
    fun aSenderClockAheadIsCountedApart() {
        assertEquals("delay median 3 s, longest 5 s, 1 with the sender's clock ahead", Delays.summary(listOf(after(3_000), after(-60_000), after(5_000))))
        assertEquals("1 with the sender's clock ahead", Delays.summary(listOf(after(-1))))
    }

    @Test
    fun aSenderClockFarBehindIsCountedApart() {
        val years = 21_567L * 3_600_000
        assertEquals("delay median 6 min, longest 9 min, 1 with the sender's clock far behind", Delays.summary(listOf(after(6 * 60_000), after(years), after(9 * 60_000))))
    }

    @Test
    fun formats() {
        assertEquals("119 s", Delays.format(119_999))
        assertEquals("2 min", Delays.format(120_000))
        assertEquals("119 min", Delays.format(7_199_000))
        assertEquals("2 h", Delays.format(7_200_000))
    }
}

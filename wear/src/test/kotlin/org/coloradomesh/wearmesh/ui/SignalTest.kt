package org.coloradomesh.wearmesh.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.coloradomesh.wearmesh.data.MessageRow
import org.junit.Test

class SignalTest {
    @Test
    fun signalAndHops() {
        assertEquals("SNR 6.5 dB · 3 hops", Signal.describe(6.5, 3))
        assertEquals("SNR -7.3 dB · 1 hop", Signal.describe(-7.25, 1))
        assertEquals("SNR 10.0 dB · no repeaters", Signal.describe(10.0, 0))
    }

    @Test
    fun aRoutedMessageHasNoHops() {
        assertEquals("SNR 7.0 dB", Signal.describe(7.0, null))
    }

    @Test
    fun olderFirmwareGivesHopsAlone() {
        assertEquals("2 hops", Signal.describe(null, 2))
        assertNull(Signal.describe(null, null))
    }

    @Test
    fun onlyAReceivedMessageHasALine() {
        val received = MessageRow(radio = "R", conversation = "c:0", sender = "Ann", body = "hi", sentAt = 1, receivedAt = 1, snr = 6.5, hops = 3)
        assertEquals("SNR 6.5 dB · 3 hops", Signal.forMessage(received))
        assertNull(Signal.forMessage(received.copy(outgoing = true)))
    }
}

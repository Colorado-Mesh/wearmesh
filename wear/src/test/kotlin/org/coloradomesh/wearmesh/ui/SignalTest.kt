package org.coloradomesh.wearmesh.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun olderFirmwareGivesNoLine() {
        assertNull(Signal.describe(null, null))
    }
}

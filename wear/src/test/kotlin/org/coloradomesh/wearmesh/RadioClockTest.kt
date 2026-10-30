package org.coloradomesh.wearmesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioClockTest {
    private val watch = 1_800_000_000L

    @Test
    fun clocksThatAgreeNeedNoLine() {
        assertNull(RadioClock.describe(watch - 3, watch, setting = false))
    }

    @Test
    fun aClockOffWithinTheToleranceIsReported() {
        assertEquals("Radio clock 40 s ahead of the watch; within 60 s, left as it is", RadioClock.describe(watch + 40, watch, setting = false))
    }

    @Test
    fun aClockBeingSet() {
        assertEquals("Radio clock 3600 s behind the watch; setting it", RadioClock.describe(watch - 3_600, watch, setting = true))
    }
}

package org.coloradomesh.wearmesh.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentStatusTest {
    @Test
    fun labels() {
        assertNull(SentStatus.label(null))
        assertEquals("Sending…", SentStatus.label("SENDING"))
        assertEquals("Sent to radio", SentStatus.label("SENT"))
        assertEquals("Delivered", SentStatus.label("DELIVERED"))
        assertEquals("Not confirmed", SentStatus.label("UNCONFIRMED"))
        assertEquals("Failed", SentStatus.label("FAILED"))
    }

    @Test
    fun anUnknownStatusShowsNothing() {
        assertNull(SentStatus.label("QUEUED"))
    }
}

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
        assertEquals("Failed: contact not on radio", SentStatus.label("NO_CONTACT"))
    }

    @Test
    fun onlyFailuresCanBeRetried() {
        assertEquals(listOf("FAILED", "NO_CONTACT"), listOf("SENDING", "SENT", "DELIVERED", "UNCONFIRMED", "FAILED", "NO_CONTACT", null).filter { SentStatus.canRetry(it) })
    }

    @Test
    fun anUnknownStatusShowsNothing() {
        assertNull(SentStatus.label("QUEUED"))
    }
}

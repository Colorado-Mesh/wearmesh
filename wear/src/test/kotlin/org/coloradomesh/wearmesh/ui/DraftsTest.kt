package org.coloradomesh.wearmesh.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftsTest {
    @Test
    fun aReplyToSomeoneStartsWithTheirName() {
        assertEquals("@[🌀flux] on my way", Drafts.body("  on my way ", "🌀flux"))
        assertEquals("on my way", Drafts.body("on my way", null))
    }

    @Test
    fun aChannelLeavesRoomForTheRadiosName() {
        // "W0RMT" and ": " take 7 of the 160 bytes.
        assertEquals(153, Drafts.limit("c:0", "W0RMT"))
        assertEquals(160, Drafts.limit("d:a1b2c3d4e5f6", "W0RMT"))
    }

    @Test
    fun bytesNotCharactersCount() {
        // Each of these emoji is 4 bytes in UTF-8.
        val text = "🔥".repeat(40)
        assertNull(Drafts.problem("d:a1b2c3d4e5f6", text, null))
        assertEquals("Too long by 4 bytes", Drafts.problem("d:a1b2c3d4e5f6", text + "🔥", null))
        assertEquals("Nothing to send", Drafts.problem("c:0", " ", "W0RMT"))
        assertEquals("160 of 160 bytes", Drafts.size("d:a1b2c3d4e5f6", text, null))
    }
}

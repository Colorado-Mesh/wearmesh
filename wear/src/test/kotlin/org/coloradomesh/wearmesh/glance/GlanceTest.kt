package org.coloradomesh.wearmesh.glance

import org.coloradomesh.wearmesh.data.ChannelRow
import org.coloradomesh.wearmesh.data.ConversationLast
import org.junit.Assert.assertEquals
import org.junit.Test

class GlanceTest {
    private fun last(conversation: String, unread: Int) = ConversationLast(conversation, "Ann", "hi", 1, unread, null)

    private val channels = listOf(
        ChannelRow("R", 0, "Public", "PUBLIC"),
        ChannelRow("R", 3, "#testing", "HASHTAG"),
        ChannelRow("R", 14, "mesh-ops", "PRIVATE"),
    )

    @Test
    fun countsOnlyConversationsThatNotify() {
        val lasts = listOf(last("d:aaaaaaaaaaaa", 2), last("c:0", 30), last("c:3", 5), last("c:14", 1))
        // By default: direct messages and private channels.
        assertEquals(3, Glance.unread(lasts, channels, emptyMap()))
        // #testing turned on, mesh-ops turned off.
        assertEquals(7, Glance.unread(lasts, channels, mapOf("c:3" to true, "c:14" to false)))
    }

    @Test
    fun nothingUnreadIsTheIconAlone() {
        assertEquals("", Glance.countText(0))
        assertEquals("7", Glance.countText(7))
        assertEquals("99+", Glance.countText(140))
    }
}

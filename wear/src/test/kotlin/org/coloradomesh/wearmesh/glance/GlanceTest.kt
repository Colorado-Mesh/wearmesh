package org.coloradomesh.wearmesh.glance

import org.coloradomesh.wearmesh.data.ChannelRow
import org.coloradomesh.wearmesh.data.ConversationLast
import org.coloradomesh.wearmesh.data.MessageRow
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

    private fun row(id: Long, conversation: String, read: Boolean = false, outgoing: Boolean = false) =
        MessageRow(id = id, radio = "R", conversation = conversation, sender = "Ann", body = "m$id", sentAt = id, receivedAt = id, read = read, outgoing = outgoing)

    @Test
    fun tileShowsTheNewestTwoThatCount() {
        val rows = listOf(
            row(1, "d:aaaaaaaaaaaa"),
            row(2, "c:14"),
            row(3, "c:0"), // Public: quiet by default
            row(4, "d:aaaaaaaaaaaa", read = true),
            row(5, "c:14", outgoing = true),
            row(6, "d:bbbbbbbbbbbb"),
        )
        assertEquals(listOf(6L, 2L), Glance.newest(rows.shuffled(), channels, emptyMap()).map { it.id })
        // Public turned on.
        assertEquals(listOf(6L, 3L), Glance.newest(rows, channels, mapOf("c:0" to true)).map { it.id })
        assertEquals(emptyList<Long>(), Glance.newest(rows, channels, mapOf("d:aaaaaaaaaaaa" to false, "d:bbbbbbbbbbbb" to false, "c:14" to false)).map { it.id })
    }

    @Test
    fun whoAMessageIsFrom() {
        assertEquals("KC0SIQ-05", GlanceMessage("d:aaaaaaaaaaaa", "KC0SIQ-05", "", "hi").from)
        assertEquals("Ann · Public", GlanceMessage("c:0", "Public", "Ann", "hi").from)
        assertEquals("Public", GlanceMessage("c:0", "Public", "", "hi").from)
    }

    @Test
    fun tileShowsTheLastTwoHeardWhereverTheyAre() {
        val rows = listOf(
            row(1, "d:aaaaaaaaaaaa"),
            row(2, "c:0", read = true), // Public, quiet, already read: still heard
            row(3, "c:14", outgoing = true), // sent by the watch: not heard
            row(4, "c:3"),
        )
        assertEquals(listOf(4L, 2L), Glance.heard(rows.shuffled()).map { it.id })
        assertEquals(listOf(4L), Glance.heard(rows, limit = 1).map { it.id })
    }

    @Test
    fun aChosenChannelCountsWhetherOrNotItNotifies() {
        val lasts = listOf(last("d:aaaaaaaaaaaa", 2), last("c:0", 30))
        assertEquals(30, Glance.unreadIn(lasts, "c:0"))
        assertEquals(0, Glance.unreadIn(lasts, "c:3"))
    }

    @Test
    fun shortNames() {
        assertEquals("Publ", Glance.shortName("Public"))
        assertEquals("test", Glance.shortName("#testing"))
        assertEquals("ops", Glance.shortName("#ops"))
        assertEquals("Ham", Glance.shortName(" Ham "))
        assertEquals("#", Glance.shortName("#"))
        assertEquals("\uD83D\uDD25Den", Glance.shortName("\uD83D\uDD25Denver"))
        assertEquals("Publ", ChannelCount("Public", 3).short)
    }
}
